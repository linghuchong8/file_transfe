package com.transfer.medical.task;

import com.transfer.medical.mapper.MedicalDataShareMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 失败状态复位任务。
 */
@Component
@EnableScheduling
@Slf4j
public class MedicalTryJob implements MedicalJob {

    private final MedicalDataShareMessageMapper shareMsgMapper;

    public MedicalTryJob(MedicalDataShareMessageMapper shareMsgMapper) {
        this.shareMsgMapper = shareMsgMapper;
    }

    @Scheduled(initialDelayString = "${scheduled.task.try.initialDelay}",
            fixedRateString = "${scheduled.task.try.fixedRate}")
    @Override
    public synchronized void run() {
        log.info("开始复位下载/处理/上传异常状态");
        shareMsgMapper.resetAllDone();
        shareMsgMapper.resetVerifyDone();
        log.info("状态复位完成");
    }
}
