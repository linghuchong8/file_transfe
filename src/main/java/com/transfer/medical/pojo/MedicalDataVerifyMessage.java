package com.transfer.medical.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 校验文件消息实体。
 *
 * @author EB
 */
@Data
@NoArgsConstructor
public class MedicalDataVerifyMessage {

    private String serviceName;

    private String fileDir;

    private String sendSysCode;

    private String fileName;

    private String unitName;

    private String txDate;

    private String savePath;

    private String processFlag;

    private String createDate;

    private String downFlag;

    private String fileNum;

    private String dealType;

    private String sendHdfsFlag;

    private String sendHdfsPath;

    private Integer isDone;

    private String checkFlag;

    private String logMsg;

    private String sql;

    private int count;

    private String fileDesc;

    private String fileType;
}
