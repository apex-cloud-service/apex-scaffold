package com.apex.scaffold.application.support;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据库类型到 Java 类型的映射工具类
 *
 * <p>提供数据库列类型到 Java 类型的自动转换，支持 MySQL 和 PostgreSQL 主流类型。
 * 用于代码生成时根据表结构自动推断实体类字段类型。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class TypeMapper {

    private static final Map<String, String> MYSQL_TYPE_MAP = new HashMap<>(16);
    private static final Map<String, String> POSTGRES_TYPE_MAP = new HashMap<>(16);

    static {
        MYSQL_TYPE_MAP.put("TINYINT", "Integer");
        MYSQL_TYPE_MAP.put("SMALLINT", "Integer");
        MYSQL_TYPE_MAP.put("MEDIUMINT", "Integer");
        MYSQL_TYPE_MAP.put("INT", "Integer");
        MYSQL_TYPE_MAP.put("INTEGER", "Integer");
        MYSQL_TYPE_MAP.put("BIGINT", "Long");
        MYSQL_TYPE_MAP.put("FLOAT", "Float");
        MYSQL_TYPE_MAP.put("DOUBLE", "Double");
        MYSQL_TYPE_MAP.put("DECIMAL", "BigDecimal");

        MYSQL_TYPE_MAP.put("CHAR", "String");
        MYSQL_TYPE_MAP.put("VARCHAR", "String");
        MYSQL_TYPE_MAP.put("TINYTEXT", "String");
        MYSQL_TYPE_MAP.put("TEXT", "String");
        MYSQL_TYPE_MAP.put("MEDIUMTEXT", "String");
        MYSQL_TYPE_MAP.put("LONGTEXT", "String");

        MYSQL_TYPE_MAP.put("DATE", "LocalDate");
        MYSQL_TYPE_MAP.put("TIME", "LocalTime");
        MYSQL_TYPE_MAP.put("DATETIME", "LocalDateTime");
        MYSQL_TYPE_MAP.put("TIMESTAMP", "LocalDateTime");
        MYSQL_TYPE_MAP.put("YEAR", "Integer");

        MYSQL_TYPE_MAP.put("BLOB", "byte[]");
        MYSQL_TYPE_MAP.put("TINYBLOB", "byte[]");
        MYSQL_TYPE_MAP.put("MEDIUMBLOB", "byte[]");
        MYSQL_TYPE_MAP.put("LONGBLOB", "byte[]");
        MYSQL_TYPE_MAP.put("BINARY", "byte[]");
        MYSQL_TYPE_MAP.put("VARBINARY", "byte[]");

        MYSQL_TYPE_MAP.put("BIT", "Boolean");
        MYSQL_TYPE_MAP.put("BOOLEAN", "Boolean");

        POSTGRES_TYPE_MAP.put("SMALLINT", "Integer");
        POSTGRES_TYPE_MAP.put("INT2", "Integer");
        POSTGRES_TYPE_MAP.put("INTEGER", "Integer");
        POSTGRES_TYPE_MAP.put("INT", "Integer");
        POSTGRES_TYPE_MAP.put("INT4", "Integer");
        POSTGRES_TYPE_MAP.put("BIGINT", "Long");
        POSTGRES_TYPE_MAP.put("INT8", "Long");

        POSTGRES_TYPE_MAP.put("REAL", "Float");
        POSTGRES_TYPE_MAP.put("FLOAT4", "Float");
        POSTGRES_TYPE_MAP.put("DOUBLE PRECISION", "Double");
        POSTGRES_TYPE_MAP.put("FLOAT8", "Double");
        POSTGRES_TYPE_MAP.put("NUMERIC", "BigDecimal");
        POSTGRES_TYPE_MAP.put("DECIMAL", "BigDecimal");

        POSTGRES_TYPE_MAP.put("VARCHAR", "String");
        POSTGRES_TYPE_MAP.put("CHARACTER VARYING", "String");
        POSTGRES_TYPE_MAP.put("CHAR", "String");
        POSTGRES_TYPE_MAP.put("CHARACTER", "String");
        POSTGRES_TYPE_MAP.put("TEXT", "String");

        POSTGRES_TYPE_MAP.put("DATE", "LocalDate");
        POSTGRES_TYPE_MAP.put("TIME", "LocalTime");
        POSTGRES_TYPE_MAP.put("TIME WITHOUT TIME ZONE", "LocalTime");
        POSTGRES_TYPE_MAP.put("TIMESTAMP", "LocalDateTime");
        POSTGRES_TYPE_MAP.put("TIMESTAMP WITHOUT TIME ZONE", "LocalDateTime");
        POSTGRES_TYPE_MAP.put("TIMESTAMPTZ", "LocalDateTime");
        POSTGRES_TYPE_MAP.put("TIMESTAMP WITH TIME ZONE", "LocalDateTime");

        POSTGRES_TYPE_MAP.put("BOOLEAN", "Boolean");
        POSTGRES_TYPE_MAP.put("BOOL", "Boolean");

        POSTGRES_TYPE_MAP.put("BYTEA", "byte[]");
        POSTGRES_TYPE_MAP.put("UUID", "String");
        POSTGRES_TYPE_MAP.put("JSON", "String");
        POSTGRES_TYPE_MAP.put("JSONB", "String");
    }

    private TypeMapper() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 根据数据库类型获取对应的 Java 类型
     *
     * @param dbType 数据库类型（mysql/postgresql）
     * @param columnType 列类型（如 VARCHAR、INT 等）
     * @return Java 类型全名（如 java.lang.String）
     */
    public static String getJavaType(String dbType, String columnType) {
        if (dbType == null || columnType == null) {
            return "String";
        }

        String upperType = columnType.toUpperCase().trim();
        int bracketIndex = upperType.indexOf('(');
        if (bracketIndex > 0) {
            upperType = upperType.substring(0, bracketIndex).trim();
        }

        Map<String, String> typeMap = "mysql".equalsIgnoreCase(dbType) || "mariadb".equalsIgnoreCase(dbType)
                ? MYSQL_TYPE_MAP
                : POSTGRES_TYPE_MAP;

        return typeMap.getOrDefault(upperType, "String");
    }

    /**
     * 将下划线命名转为驼峰命名
     *
     * @param name 下划线命名（如 user_name）
     * @return 驼峰命名（如 userName）
     */
    public static String toCamelCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        StringBuilder result = new StringBuilder();
        boolean toUpper = false;

        for (char c : name.toCharArray()) {
            if (c == '_') {
                toUpper = true;
            } else {
                result.append(toUpper ? Character.toUpperCase(c) : c);
                toUpper = false;
            }
        }

        return result.toString();
    }

    /**
     * 首字母大写
     *
     * @param name 字符串
     * @return 首字母大写的字符串
     */
    public static String capitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
