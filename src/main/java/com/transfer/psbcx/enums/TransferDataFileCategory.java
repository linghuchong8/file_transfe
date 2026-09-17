package com.transfer.psbcx.enums;

/**
 * 数据共享文件类型。
 */
public enum TransferDataFileCategory {

    /** 校验文件。 */
    VERF("00"),

    /** 检查文件。 */
    CHECK("01"),

    /** 其他数据文件。 */
    DATA("02");

    private final String code;

    TransferDataFileCategory(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return code;
    }

    /**
     * 按编码反查文件类型。
     *
     * @param code 类型编码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static TransferDataFileCategory fromCode(String code) {
        for (TransferDataFileCategory category : values()) {
            if (category.code.equals(code)) {
                return category;
            }
        }
        return null;
    }
}
