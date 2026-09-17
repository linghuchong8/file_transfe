package com.transfer.medical.enums;

/**
 * 批次完成标志。
 */
public enum MedicalDoneFlag {

    /** 未完成。 */
    UNFINISHED(0),

    /** 已完成。 */
    DONE(1),

    /** 忽略。 */
    IGNORED(2),

    /** 已触发。 */
    TRIGGERED(3),

    /** 校验文件已核对。 */
    VERIFIED(4);

    private final Integer code;

    MedicalDoneFlag(Integer code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String toString() {
        return code.toString();
    }

    /**
     * 按编码反查完成标志。
     *
     * @param code 标志编码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static MedicalDoneFlag fromCode(Integer code) {
        for (MedicalDoneFlag flag : values()) {
            if (flag.toString().equals(String.valueOf(code))) {
                return flag;
            }
        }
        return null;
    }
}
