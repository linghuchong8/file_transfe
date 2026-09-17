package com.transfer.psbcx.util;

/**
 * 文件流转状态码。
 */
public interface TransferFlowStatus {

    /** 就绪。 */
    String READY = "01";

    /** 完成。 */
    String DONE = "00";

    /** 运行中。 */
    String RUNNING = "02";

    /** 错误。 */
    String ERROR = "03";

    /** 无需处理。 */
    String EMPTY = "04";
}
