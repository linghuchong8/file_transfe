package com.transfer.psbcx.task;

/**
 * 定时任务统一契约。
 */
public interface TransferJob {

    /** 执行一次任务。 */
    void run();
}
