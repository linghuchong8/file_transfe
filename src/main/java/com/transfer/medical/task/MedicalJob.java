package com.transfer.medical.task;

/**
 * 定时任务统一契约。
 */
public interface MedicalJob {

    /** 执行一次任务。 */
    void run();
}
