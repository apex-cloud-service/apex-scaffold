package com.apex.scaffold.domain.enums;

import java.util.Arrays;
import java.util.Locale;

/**
 * 用户教育程度。
 *
 * <p>教育程度是用户可主动维护的资料字段，同时也是单用户推荐硬门禁允许使用的
 * 离散条件。枚举编码使用稳定的大写字符串，持久化和 API 返回均使用该编码；不要依赖
 * 枚举声明顺序作为学历高低排序。</p>
 * @author weiqiang.ling
 */
public enum EducationLevel {

    /** 高中及以下。 */
    HIGH_SCHOOL("高中及以下"),

    /** 大专。 */
    ASSOCIATE("大专"),

    /** 本科。 */
    BACHELOR("本科"),

    /** 硕士。 */
    MASTER("硕士"),

    /** 博士。 */
    DOCTOR("博士"),

    /** 其他或不便归类的教育背景。 */
    OTHER("其他");

    private final String description;

    /**
     * 使用给定依赖和配置创建EducationLevel实例。
     *
     * @param description 方法输入 {@code description}，类型为 {@code String}
     */
    EducationLevel(String description) {
        this.description = description;
    }

    /**
     * 获取稳定的持久化/API编码。
     *
     * @return 枚举名称编码
     */
    public String getCode() {
        return name();
    }

    /**
     * 获取中文展示描述。
     *
     * @return 展示描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 按编码解析教育程度。
     *
     * @param code 编码，忽略大小写及首尾空格
     * @return 对应枚举；空值或非法编码返回 {@code null}
     */
    public static EducationLevel fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(level -> level.name().equals(normalized))
                .findFirst()
                .orElse(null);
    }
}
