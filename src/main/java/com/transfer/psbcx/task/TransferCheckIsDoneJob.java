package com.transfer.psbcx.task;

import com.transfer.psbcx.enums.TransferDoneFlag;
import com.transfer.psbcx.mapper.TransferDataShareMessageMapper;
import com.transfer.psbcx.mapper.TransferVerifyConfigMapper;
import com.transfer.psbcx.mapper.TransferVerifyContentMapper;
import com.transfer.psbcx.pojo.TransferDataShareMessage;
import com.transfer.psbcx.pojo.TransferVerifyConfig;
import com.transfer.psbcx.util.TransferAppPropertiesUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 批次收齐校验任务。
 *
 * 按「工作流 + 日期」判断文件是否接收完整，支持固定次数、校验文件、
 * 消息头文件数三种策略。
 *
 * @author yuanjihong
 */
@Component
@EnableScheduling
@Slf4j
public class TransferCheckIsDoneJob implements TransferJob {

    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final TransferDataShareMessageMapper shareMsgMapper;
    private final TransferVerifyConfigMapper verifyConfigMapper;
    private final TransferVerifyContentMapper verifyContentMapper;

    public TransferCheckIsDoneJob(TransferDataShareMessageMapper shareMsgMapper,
                          TransferVerifyConfigMapper verifyConfigMapper,
                          TransferVerifyContentMapper verifyContentMapper) {
        this.shareMsgMapper = shareMsgMapper;
        this.verifyConfigMapper = verifyConfigMapper;
        this.verifyContentMapper = verifyContentMapper;
    }

    @Scheduled(initialDelayString = "${scheduled.task.check.initialDelay}",
            fixedRateString = "${scheduled.task.check.fixedRate}")
    @Override
    public synchronized void run() {
        List<TransferDataShareMessage> batches = shareMsgMapper.selectReceiveSummary();
        if (batches == null || batches.isEmpty()) {
            log.info("暂无需校验的批次");
            return;
        }
        log.info("开始批次收齐校验");

        Map<String, Integer> fixedTimes = verifyConfigMapper.selectManyStrategies().stream()
                .collect(Collectors.toMap(this::unitCode, TransferVerifyConfig::getManyNum));
        List<String> headerNumUnits = verifyConfigMapper.selectFileNumStrategies().stream()
                .map(this::unitCode)
                .collect(Collectors.toList());
        List<String> verfFileUnits = verifyConfigMapper.selectVerfStrategies().stream()
                .map(this::unitCode)
                .collect(Collectors.toList());

        for (TransferDataShareMessage batch : batches) {
            if (batch == null) {
                continue;
            }
            checkBatch(batch, fixedTimes, headerNumUnits, verfFileUnits);
        }
    }

    private String unitCode(TransferVerifyConfig config) {
        return config.getSendSysCode() + "_" + config.getServiceName() + "_0000";
    }

    private void checkBatch(TransferDataShareMessage batch, Map<String, Integer> fixedTimes,
                            List<String> headerNumUnits, List<String> verfFileUnits) {
        String unitName = batch.getUnitName();
        String txDate = batch.getTxDate();

        Map<String, Object> params = new HashMap<>();
        params.put("unitName", unitName);
        params.put("txDate", txDate);

        if (fixedTimes.containsKey(unitName)) {
            checkByFixedTimes(batch, params, unitName, txDate, fixedTimes.get(unitName));
            return;
        }
        if (verfFileUnits.contains(unitName)) {
            checkByVerfFile(params, unitName, txDate);
            return;
        }
        if (headerNumUnits.contains(unitName)) {
            checkByHeaderFileNum(params, unitName, txDate);
            return;
        }
        checkByProcessedCount(params, unitName, txDate);
    }

    /** 默认策略：接收数量与已完成处理数量一致。 */
    private void checkByProcessedCount(Map<String, Object> params, String unitName, String txDate) {
        int received = shareMsgMapper.countByUnitDate(params);
        int processed = shareMsgMapper.countProcessedByUnitDate(params);
        log.info("接口 {} 日期 {} 消息数 {}, 已完成文件数 {}", unitName, txDate, received, processed);
        if (processed == received) {
            markDone(params, unitName, txDate, "接收文件数量与上传hdfs完成文件数量一致");
        }
    }

    /** 校验文件策略：以 verf 文件登记的子文件数核对。 */
    private void checkByVerfFile(Map<String, Object> params, String unitName, String txDate) {
        int received = shareMsgMapper.countByUnitDate(params);
        List<TransferDataShareMessage> verfMessages = shareMsgMapper.selectUnverifiedByUnitDate(params);
        if (verfMessages == null || verfMessages.isEmpty()) {
            log.error("接口 {} 日期 {} 未查询到校验文件", unitName, txDate);
            return;
        }
        if (verfMessages.size() > 1) {
            log.error("接口 {} 日期 {} 未校验完成文件数 {} 超过 1", unitName, txDate, verfMessages.size());
            return;
        }

        TransferDataShareMessage verfMessage = verfMessages.get(0);
        params.put("datestr", verfMessage.getCreateDate());
        params.put("timestr", verfMessage.getCreateTime());
        int detailCount = verifyContentMapper.countDetailByUnitDate(params);
        if (detailCount != received) {
            log.info("接口 {} 日期 {} 消息数 {}, 校验文件数 {}, 不一致", unitName, txDate, received, detailCount);
            return;
        }
        markDone(params, unitName, txDate, "校验文件核对一致");
        params.put("isDone", TransferDoneFlag.VERIFIED.getCode());
        shareMsgMapper.updateVerifyDoneByUnitDate(params);
    }

    /** 消息头文件数策略：消息头声明的文件总数与实际消息数核对。 */
    private void checkByHeaderFileNum(Map<String, Object> params, String unitName, String txDate) {
        int received = shareMsgMapper.countByUnitDate(params);
        int headerNum = shareMsgMapper.sumHeaderFileNum(params);
        log.info("接口 {} 日期 {} 消息数 {}, 消息头文件数 {}", unitName, txDate, received, headerNum);
        if (headerNum == received) {
            markDone(params, unitName, txDate, "消息头文件数量核对一致");
        }
    }

    /** 固定次数策略：达到配置分发次数即完成，否则零点兜底。 */
    private void checkByFixedTimes(TransferDataShareMessage batch, Map<String, Object> params,
                                   String unitName, String txDate, int expected) {
        if (batch.getCount() == expected) {
            markDone(params, unitName, txDate, "固定值核对一致");
            return;
        }
        String now = LocalDateTime.now().format(MINUTE_FORMAT) + ":00";
        String completeTime = TransferAppPropertiesUtil.getProperty("set.complete.time", "00:00:00");
        if (Objects.equals(now, completeTime)) {
            markDone(params, unitName, txDate, "固定值零点核对");
        }
    }

    private void markDone(Map<String, Object> params, String unitName, String txDate, String logMsg) {
        params.put("isDone", TransferDoneFlag.DONE.getCode());
        params.put("logMsg", logMsg);
        shareMsgMapper.updateDoneByUnitDate(params);
        log.info("接口 {} 日期 {} 标记完成: {}", unitName, txDate, logMsg);
    }
}
