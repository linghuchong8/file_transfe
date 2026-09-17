package com.transfer.medical.task;

import com.google.common.base.Preconditions;
import com.transfer.medical.mapper.MedicalDataShareMessageMapper;
import com.transfer.medical.pojo.MedicalDataShareMessage;
import com.transfer.medical.service.MedicalRestActionService;
import com.transfer.medical.util.MedicalFlowStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 待下载文件扫描任务（单线程）。
 */
@Component
@EnableScheduling
@Slf4j
public class MedicalDownloadFileJob implements MedicalJob {

    private final MedicalDataShareMessageMapper shareMsgMapper;
    private final MedicalRestActionService restActionService;

    public MedicalDownloadFileJob(MedicalDataShareMessageMapper shareMsgMapper, MedicalRestActionService restActionService) {
        this.shareMsgMapper = shareMsgMapper;
        this.restActionService = restActionService;
    }

    @Scheduled(initialDelayString = "${scheduled.task.download.initialDelay}",
            fixedRateString = "${scheduled.task.download.fixedRate}")
    @Override
    public synchronized void run() {
        List<MedicalDataShareMessage> pending = shareMsgMapper.selectPendingDownload();
        if (pending == null || pending.isEmpty()) {
            log.info("没有待下载文件");
            return;
        }

        markDownloading(pending);
        log.info("本次待下载文件数量: {}", pending.size());
        pending.forEach(restActionService::download);
    }

    /**
     * 批量把待下载记录标记为下载中，避免重复下载。
     */
    private void markDownloading(List<MedicalDataShareMessage> pending) {
        for (MedicalDataShareMessage msg : pending) {
            Preconditions.checkArgument(msg != null, "消息不能为空");
            Preconditions.checkArgument(msg.getFileName() != null, "文件名不能为空");
            Preconditions.checkArgument(msg.getSendSysCode() != null, "发送方系统编号不能为空");
            Preconditions.checkArgument(msg.getFileDir() != null, "存储文件路径不能为空");

            Map<String, Object> params = new HashMap<>();
            params.put("fileName", msg.getFileName());
            params.put("fileDir", msg.getFileDir());
            params.put("logMsg", "下载中");
            params.put("downFlag", MedicalFlowStatus.RUNNING);
            shareMsgMapper.updateMessage(params);
        }
    }
}
