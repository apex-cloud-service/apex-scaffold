package com.apex.scaffold.generator;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 字段定义（代码生成器输入模型）。
 *
 * <p>描述表中的一个业务字段。{@code type} 为 Java 类型全名
 * （如 {@code String}、{@code Long}、{@code BigDecimal}、
 * {@code LocalDateTime}），生成器自动推导数据库列名（下划线）与
 * 建表 SQL 类型。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see TableDefinition
 */
@Data
public class FieldDefinition {

    /**
     * 字段名（小驼峰）。
     *
     * <p>例如 {@code productName}。</p>
     */
    @JsonProperty("name")
    private String name;

    /**
     * Java 类型全名。
     *
     * <p>例如 {@code String}、{@code Long}、{@code Integer}、
     * {@code BigDecimal}、{@code LocalDateTime}、{@code Boolean}。</p>
     */
    @JsonProperty("type")
    private String type;

    /**
     * 字段注释。
     */
    @JsonProperty("comment")
    private String comment = "";

    /**
     * 是否必填（用于校验注解与建表 NOT NULL）。
     */
    @JsonProperty("required")
    private boolean required;

    /**
     * 转换字段名为数据库列名（驼峰转下划线）。
     *
     * @return 下划线列名，例如 {@code product_name}
     */
    public String columnName() {
        if (name == null || name.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 获取 Java 简单类型名。
     *
     * @return 简单类型名（不含包），例如 {@code LocalDateTime}
     */
    public String simpleType() {
        if (type == null) {
            return "String";
        }
        int dot = type.lastIndexOf('.');
        return dot >= 0 ? type.substring(dot + 1) : type;
    }

    /**
     * 推导建表 SQL 列类型。
     *
     * @return MySQL 列类型
     */
    public String sqlType() {
        return switch (simpleType()) {
            case "Long" -> "BIGINT";
            case "Integer" -> "INT";
            case "BigDecimal" -> "DECIMAL(18,2)";
            case "LocalDateTime" -> "DATETIME";
            case "Boolean" -> "TINYINT(1)";
            case "Double" -> "DOUBLE";
            default -> "VARCHAR(255)";
        };
    }
}
