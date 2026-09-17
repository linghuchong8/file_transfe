package com.transfer.psbcx.util;

/**
 * Transfer 服务通用常量。
 *
 * @author xiehao
 */
public final class TransferTransferConstants {

    /** HTTP 请求头：内容类型。 */
    public static final String HEADER_CONTENT_TYPE = "Content-Type";

    /** JSON 内容类型。 */
    public static final String JSON_CONTENT_TYPE = "application/json";

    /** HTTP 请求头：内容处置。 */
    public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";

    /** 附件文件名标记。 */
    public static final String DISPOSITION_FILENAME = "filename";

    /** 接口调用最大重试次数。 */
    public static final Integer MAX_RETRIES = 0;

    private TransferTransferConstants() {
    }
}
