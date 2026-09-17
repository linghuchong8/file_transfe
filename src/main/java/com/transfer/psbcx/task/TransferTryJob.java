package com.transfer.psbcx.task;

import com.transfer.psbcx.mapper.TransferDataShareMessageMapper;
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
public class TransferTryJob implements TransferJob {

    private final TransferDataShareMessageMapper shareMsgMapper;

    public TransferTryJob(TransferDataShareMessageMapper shareMsgMapper) {
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
