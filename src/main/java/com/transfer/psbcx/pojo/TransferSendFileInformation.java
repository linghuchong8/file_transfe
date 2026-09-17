package com.transfer.psbcx.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 待下发文件信息，对应表 send_file_info。
 *
 * @author yuanjihong
 */
@Data
@NoArgsConstructor
public class TransferSendFileInformation {

    /** 工作流编码。 */
    private String unitName;

    /** 发送日期。 */
    private String sendDate;

    /** HDFS 上的文件目录。 */
    private String filePath;

    /** 文件名。 */
    private String fileName;

    /** 文件个数。 */
    private String fileNum;

    /** 开始时间。 */
    private String startTime;

    /** 结束时间。 */
    private String endTime;

    /** 下发状态。 */
    private String sendFlag;

    /** 已发送次数。 */
    private Integer sendTimes;

    /** 接收方系统号。 */
    private String sysCode;

    /** 文件字节大小。 */
    private String fileBytes;

    /** 数据条数。 */
    private String dataNum;
}
