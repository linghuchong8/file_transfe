package com.transfer.psbcx.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 校验策略配置，对应表 verf_config。
 *
 * @author xubingyu
 */
@Data
@NoArgsConstructor
public class TransferVerifyConfig {

    /** 接口名。 */
    private String serviceName;

    /** 发送方系统号。 */
    private String sendSysCode;

    /** 校验类型：verf / filenum / many。 */
    private String verfType;

    /** 固定分发次数。 */
    private int manyNum;
}
