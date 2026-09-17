package com.transfer.psbcx.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件下载请求参数。
 *
 * 字段名称与 EDB 客户端接口约定一致，请勿随意调整。
 */
@Data
@NoArgsConstructor
public class TransferDownloadFileRequest {

    /** 服务名，必填。 */
    private String serverName;

    /** 发送方系统号，必填（7 或 11 位），取自消息 Send_sys_code。 */
    private String sendSysCode;

    /** 文件名，必填，取自消息 File_name。 */
    private String fileName;

    /** 接收方系统号，必填（7 或 11 位），取自消息 Recv_sys_code。 */
    private String recvSysCode;

    /** 系统密钥，必填，取值方为下载文件的系统。 */
    private String sysKey;

    /** 文件接收目录，必填，需具备读写权限。 */
    private String recvDir;

    /** 是否接收文件流：0-从 recvDir 拷贝，1-以文件流读取返回。 */
    private String receiveFileStreamStatus;

    /** 文件上传主中心，必填，如 HF/HI/LF，取自消息 File_from_center。 */
    private String fileMainCenterCode;

    /** 文件上传成功后返回的文件 ID，取自消息 File_id，可为空串。 */
    private String fileId;

    /** 文件申请时间戳，取自消息 File_date。 */
    private String applyTimestamp;

    /** 用户自定义信息，map 形式，非必填保留字段。 */
    private String user_param;
}
