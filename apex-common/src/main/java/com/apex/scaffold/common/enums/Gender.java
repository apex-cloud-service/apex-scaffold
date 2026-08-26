package com.apex.scaffold.common.enums;

import java.util.Arrays;
import java.util.Locale;

/**
 * 性别枚举
 *
 * <p>定义性别类型的枚举，包含未知、男、女三种性别
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum Gender {

    /**
     * 未知
     *
     * <p>默认性别，当无法确定性别时使用
     */
    UNKNOWN(0, "未知"),

    /**
     * 男
     *
     * <p>男性性别
     */
    MALE(1, "男"),

    /**
     * 女
     *
     * <p>女性性别
     */
    FEMALE(2, "女");

    /** 人脸分析服务使用的男性数字编码。 */
    private static final String ANALYSIS_MALE_CODE = "1";

    /** 人脸分析服务使用的女性数字编码。 */
    private static final String ANALYSIS_FEMALE_CODE = "2";

    /** 性别编码 */
    private final Integer code;

    /** 性别描述 */
    private final String desc;

    /**
     * 构造函数
     *
     * @param code 性别编码
     * @param desc 性别描述
     */
    Gender(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取性别编码
     *
     * @return 性别编码
     */
    public Integer getCode() {
        return code;
    }

    /**
     * 获取性别描述
     *
     * @return 性别描述
     */
    public String getDesc() {
        return desc;
    }

    /**
     * 根据code获取枚举
     *
     * <p>如果code为null或不存在，返回UNKNOWN
     *
     * @param code 状态码
     * @return 性别枚举
     */
    public static Gender getByCode(Integer code) {
        if (code == null) {
            return UNKNOWN;
        }
        return Arrays.stream(values())
                .filter(g -> g.getCode().equals(code))
                .findFirst()
                .orElse(UNKNOWN);
    }

    /**
     * 获取人脸分析/Python接口使用的性别编码。
     *
     * @return male、female；UNKNOWN返回空字符串
     */
    public String getAnalysisCode() {
        return switch (this) {
            case MALE -> "male";
            case FEMALE -> "female";
            case UNKNOWN -> "";
        };
    }

    /**
     * 根据人脸分析/Python接口编码获取通用性别枚举。
     *
     * @param value male/female 或 1/2，忽略大小写及首尾空格
     * @return 对应枚举；非法值返回UNKNOWN
     */
    public static Gender getByAnalysisCode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (ANALYSIS_MALE_CODE.equals(normalized)) {
            return MALE;
        }
        if (ANALYSIS_FEMALE_CODE.equals(normalized)) {
            return FEMALE;
        }
        return Arrays.stream(values())
                .filter(gender -> gender.getAnalysisCode().equals(normalized))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
