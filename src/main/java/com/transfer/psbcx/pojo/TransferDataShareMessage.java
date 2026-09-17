package com.transfer.psbcx.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据共享消息实体，对应表 pm_data_share_msg。
 *
 * @author yuanjihong
 */
@Data
@NoArgsConstructor
public class TransferDataShareMessage {

    /** 文件消息 ID（幂等判断依据）。 */
    private String fileId;

    /** 文件名。 */
    private String fileName;

    /** 本地文件存储目录。 */
    private String fileDir;

    /** 文件类型：00 校验文件、01 检查文件、02 其他数据文件。 */
    private String fileType;

    /** 消息头声明的文件个数。 */
    private String fileNum;

    /** 接口名。 */
    private String serviceName;

    /** 工作流编码：发送方系统号_接口名_行政区划。 */
    private String unitName;

    /** 发送方系统号。 */
    private String sendSysCode;

    /** 业务日期。 */
    private String txDate;

    /** 消息产生日期。 */
    private String createDate;

    /** 消息产生时间。 */
    private String createTime;

    /** 下载状态。 */
    private String downFlag;

    /** 处理状态。 */
    private String processFlag;

    /** 上传 HDFS 状态。 */
    private String sendHdfsFlag;

    /** 上传 HDFS 后的目标路径。 */
    private String sendHdfsPath;

    /** 批次完成标志。 */
    private Integer isDone;

    /** 处理日志。 */
    private String logMsg;

    /** 失败重试次数。 */
    private int restartTimes;

    /** 分组统计计数。 */
    private int count;

    /** 预留 SQL 片段。 */
    private String sql;
}
