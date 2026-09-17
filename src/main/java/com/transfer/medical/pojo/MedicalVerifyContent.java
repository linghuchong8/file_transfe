package com.transfer.medical.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 校验文件内容明细，对应表 verf_content。
 *
 * @author xubingyu
 */
@Data
@NoArgsConstructor
public class MedicalVerifyContent {

    /** 校验文件名。 */
    private String fileName;

    /** 工作流编码。 */
    private String unitName;

    /** 子文件字节数。 */
    private String byteMum;

    /** 子文件记录数。 */
    private String recordCount;

    /** 日期字符串。 */
    private String datestr;

    /** 时间字符串。 */
    private String timestr;
}
