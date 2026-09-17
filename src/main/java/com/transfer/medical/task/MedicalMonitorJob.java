package com.transfer.medical.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 线程池运行状态监控任务。
 */
@Component
@EnableScheduling
@Slf4j
public class MedicalMonitorJob implements MedicalJob {

    private final ThreadPoolTaskExecutor taskExecutor;

    public MedicalMonitorJob(ThreadPoolTaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    @Scheduled(initialDelayString = "${scheduled.task.monitor.initialDelay}",
            fixedRateString = "${scheduled.task.monitor.fixedRate}")
    @Override
    public synchronized void run() {
        log.info("线程池状态: {}", taskExecutor.getThreadPoolExecutor());
    }
}
