package com.apex.scaffold.common.annotation;

/**
 * 脱敏类型枚举
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum MaskType {
    /**
     * 手机号
     */
    PHONE,

    /**
     * 身份证号
     */
    ID_CARD,

    /**
     * 邮箱
     */
    EMAIL,

    /**
     * 姓名
     */
    NAME,

    /**
     * 银行卡号
     */
    BANK_CARD,

    /**
     * 地址
     */
    ADDRESS,

    /**
     * IP地址
     */
    IP_ADDRESS,

    /**
     * URL
     */
    URL,

    /**
     * 通用脱敏(保留30%头部和20%尾部)
     */
    GENERAL,

    /**
     * 自定义规则
     */
    CUSTOM
}
