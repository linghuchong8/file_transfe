package com.transfer.medical.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 校验文件下载任务。
 *
 * 下载逻辑当前处于停用状态，仅保留调度入口。
 *
 * @author EB
 */
@Component
@EnableScheduling
@Slf4j
public class MedicalDownVerifyFileJob implements MedicalJob {

    @Scheduled(initialDelayString = "${scheduled.task.downloadVerify.initialDelay}",
            fixedRateString = "${scheduled.task.downloadVerify.fixedRate}")
    @Override
    public synchronized void run() {
        // 校验文件下载逻辑暂未启用。
    }
}
