package com.transfer.psbcx.task;

import com.google.common.base.Preconditions;
import com.transfer.psbcx.mapper.TransferSendFileInformationMapper;
import com.transfer.psbcx.pojo.TransferSendFileInformation;
import com.transfer.psbcx.service.TransferTransferRestActionService;
import com.transfer.psbcx.util.TransferFlowStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件下发任务。
 */
@Component
@EnableScheduling
@Slf4j
public class TransferPushJob implements TransferJob {

    private final TransferSendFileInformationMapper sendFileMapper;
    private final TransferTransferRestActionService restActionService;

    public TransferPushJob(TransferSendFileInformationMapper sendFileMapper, TransferTransferRestActionService restActionService) {
        this.sendFileMapper = sendFileMapper;
        this.restActionService = restActionService;
    }

    @Scheduled(initialDelayString = "${scheduled.task.push.initialDelay}",
            fixedRateString = "${scheduled.task.push.fixedRate}")
    @Override
    public synchronized void run() {
        List<TransferSendFileInformation> pending = sendFileMapper.selectPendingPush();
        if (pending == null || pending.isEmpty()) {
            log.info("没有待下发文件");
            return;
        }

        for (TransferSendFileInformation file : pending) {
            Preconditions.checkArgument(file != null, "文件记录不能为空");
            Preconditions.checkArgument(file.getFileName() != null, "文件名不能为空");
            Preconditions.checkArgument(file.getFilePath() != null, "文件路径不能为空");

            Map<String, Object> params = new HashMap<>();
            params.put("fileName", file.getFileName());
            params.put("sendFlag", TransferFlowStatus.RUNNING);
            sendFileMapper.updatePushStatus(params);
            log.info("文件 {} 已置为下发中", file.getFileName());
        }

        log.info("本次待下发文件数量: {}", pending.size());
        pending.forEach(restActionService::push);
    }
}
