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
 * HDFS 上传任务。
 */
@Component
@EnableScheduling
@Slf4j
public class MedicalSendHdfsJob implements MedicalJob {

    /** 空文件标志，无需上传。 */
    private static final int EMPTY_FILE_FLAG = 4;

    private final MedicalDataShareMessageMapper shareMsgMapper;
    private final MedicalActionService actionService;

    public MedicalSendHdfsJob(MedicalDataShareMessageMapper shareMsgMapper, MedicalActionService actionService) {
        this.shareMsgMapper = shareMsgMapper;
        this.actionService = actionService;
    }

    @Scheduled(initialDelayString = "${scheduled.task.sendhdfs.initialDelay}",
            fixedRateString = "${scheduled.task.sendhdfs.fixedRate}")
    @Override
    public synchronized void run() {
        List<MedicalDataShareMessage> pending = shareMsgMapper.selectPendingHdfs();
        if (pending == null || pending.isEmpty()) {
            log.info("没有待上传 HDFS 的文件");
            return;
        }

        markUploading(pending);
        log.info("本次待上传 HDFS 文件数量: {}", pending.size());

        pending.stream()
                .filter(msg -> msg.getIsDone() != EMPTY_FILE_FLAG)
                .forEach(actionService::sendHdfs);
        pending.forEach(msg -> new Thread(() -> actionService.sendHdfs(msg),
                msg.getFileName() + " -- 线程").start());
    }

    /**
     * 批量把待上传记录标记为上传中，避免重复上传。
     */
    private void markUploading(List<MedicalDataShareMessage> pending) {
        for (MedicalDataShareMessage msg : pending) {
            Preconditions.checkArgument(msg != null, "消息不能为空");
            Preconditions.checkArgument(msg.getFileName() != null, "文件名不能为空");
            Preconditions.checkArgument(msg.getSendSysCode() != null, "发送方系统编号不能为空");
            Preconditions.checkArgument(msg.getFileDir() != null, "存储文件路径不能为空");

            Map<String, Object> params = new HashMap<>();
            params.put("fileName", msg.getFileName());
            params.put("fileDir", msg.getFileDir());
            params.put("logMsg", "开始上传");
            params.put("sendHdfsFlag", MedicalFlowStatus.RUNNING);
            shareMsgMapper.updateMessage(params);
        }
    }
}
