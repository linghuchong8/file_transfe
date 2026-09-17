package com.transfer.medical.task;

import com.google.common.base.Preconditions;
import com.transfer.medical.mapper.MedicalDataShareMessageMapper;
import com.transfer.medical.pojo.MedicalDataShareMessage;
import com.transfer.medical.service.MedicalActionService;
import com.transfer.medical.util.MedicalFlowStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件处理任务（解压与转 txt）。
 */
@Component
@EnableScheduling
@Slf4j
public class MedicalProcessFileJob implements MedicalJob {

    private final MedicalActionService actionService;
    private final MedicalDataShareMessageMapper shareMsgMapper;

    public MedicalProcessFileJob(MedicalActionService actionService, MedicalDataShareMessageMapper shareMsgMapper) {
        this.actionService = actionService;
        this.shareMsgMapper = shareMsgMapper;
    }

    @Scheduled(initialDelayString = "${scheduled.task.process.initialDelay}",
            fixedRateString = "${scheduled.task.process.fixedRate}")
    @Override
    public synchronized void run() {
        List<MedicalDataShareMessage> pending = shareMsgMapper.selectPendingProcess();
        if (pending == null || pending.isEmpty()) {
            log.info("没有待处理文件");
            return;
        }

        for (MedicalDataShareMessage msg : pending) {
            Preconditions.checkArgument(msg != null, "消息不能为空");
            Preconditions.checkArgument(msg.getFileName() != null, "文件名不能为空");
            Preconditions.checkArgument(msg.getSendSysCode() != null, "发送方系统编号不能为空");
            Preconditions.checkArgument(msg.getFileDir() != null, "存储文件路径不能为空");
            Preconditions.checkArgument(msg.getTxDate() != null, "日期不能为空");
            Preconditions.checkArgument(msg.getUnitName() != null, "unitName不能为空");
            Preconditions.checkArgument(msg.getCreateDate() != null, "createDate不能为空");
            Preconditions.checkArgument(msg.getCreateTime() != null, "createTime不能为空");

            Map<String, Object> params = new HashMap<>();
            params.put("fileName", msg.getFileName());
            params.put("fileDir", msg.getFileDir());
            params.put("logMsg", "开始处理");
            params.put("processFlag", MedicalFlowStatus.RUNNING);
            params.put("oldFlag", MedicalFlowStatus.READY);
            shareMsgMapper.claimProcessFlag(params);
            log.info("开始处理文件 {}", msg.getFileName());
        }

        log.info("本次待处理文件数量: {}", pending.size());
        pending.forEach(actionService::process);
    }
}
