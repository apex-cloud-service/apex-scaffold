package com.apex.scaffold.domain.model.generator;

import lombok.Getter;

/**
 * 数据库类型枚举
 *
 * <p>定义代码生成器支持的数据库类型。不同数据库在表结构查询、数据类型映射和SQL语法上存在差异，
 * 生成器需要根据数据库类型执行不同的元数据查询策略和类型转换规则。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>{@link #MYSQL} - MySQL/MariaDB，使用 INFORMATION_SCHEMA 查询元数据</li>
 *   <li>{@link #POSTGRESQL} - PostgreSQL，使用 pg_catalog 系统表查询</li>
 *   <li>{@link #ORACLE} - Oracle Database，使用 USER_TABLES/ALL_TABLES 查询</li>
 *   <li>{@link #SQLSERVER} - Microsoft SQL Server，使用 sys.tables 和 INFORMATION_SCHEMA</li>
 *   <li>{@link #H2} - H2 Database，嵌入式数据库，兼容 MySQL 和 PostgreSQL 模式</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>数据源配置时指定目标数据库类型</li>
 *   <li>元数据查询时选择对应的SQL方言</li>
 *   <li>数据类型映射时应用数据库特定的类型转换规则</li>
 *   <li>DDL脚本生成时使用数据库特定的语法</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public enum DbType {
    /**
     * MySQL 数据库（包括 MariaDB）
     *
     * <p>特点：
     * <ul>
     *   <li>默认端口：3306</li>
     *   <li>元数据查询：INFORMATION_SCHEMA.TABLES/COLUMNS</li>
     *   <li>主键查询：TABLE_CONSTRAINTS + KEY_COLUMN_USAGE</li>
     *   <li>自增列：AUTO_INCREMENT 属性</li>
     * </ul>
     */
    MYSQL("MySQL", "com.mysql.cj.jdbc.Driver", 3306),

    /**
     * PostgreSQL 数据库
     *
     * <p>特点：
     * <ul>
     *   <li>默认端口：5432</li>
     *   <li>元数据查询：pg_catalog.pg_tables/pg_attribute</li>
     *   <li>Schema 支持：需要指定 schema_name（默认 public）</li>
     *   <li>自增列：SERIAL 类型或 IDENTITY 列</li>
     * </ul>
     */
    POSTGRESQL("PostgreSQL", "org.postgresql.Driver", 5432),

    /**
     * Oracle 数据库
     *
     * <p>特点：
     * <ul>
     *   <li>默认端口：1521</li>
     *   <li>元数据查询：USER_TABLES/ALL_TABLES/USER_TAB_COLUMNS</li>
     *   <li>Schema 支持：需要指定 schema_name（通常等于用户名）</li>
     *   <li>自增列：使用 SEQUENCE + TRIGGER 或 IDENTITY 列（12c+）</li>
     * </ul>
     */
    ORACLE("Oracle", "oracle.jdbc.OracleDriver", 1521),

    /**
     * Microsoft SQL Server 数据库
     *
     * <p>特点：
     * <ul>
     *   <li>默认端口：1433</li>
     *   <li>元数据查询：sys.tables/sys.columns 或 INFORMATION_SCHEMA</li>
     *   <li>Schema 支持：需要指定 schema_name（默认 dbo）</li>
     *   <li>自增列：IDENTITY 属性</li>
     * </ul>
     */
    SQLSERVER("SQLServer", "com.microsoft.sqlserver.jdbc.SQLServerDriver", 1433),

    /**
     * H2 数据库（嵌入式/内存数据库）
     *
     * <p>特点：
     * <ul>
     *   <li>默认端口：9092（TCP模式）</li>
     *   <li>元数据查询：INFORMATION_SCHEMA（兼容 MySQL 和 PostgreSQL）</li>
     *   <li>兼容模式：支持 MySQL、PostgreSQL、Oracle 等模式</li>
     *   <li>自增列：AUTO_INCREMENT 或 IDENTITY</li>
     * </ul>
     */
    H2("H2", "org.h2.Driver", 9092);

    private final String displayName;

    private final String driverClassName;

    private final int defaultPort;

    DbType(String displayName, String driverClassName, int defaultPort) {
        this.displayName = displayName;
        this.driverClassName = driverClassName;
        this.defaultPort = defaultPort;
    }

    /**
     * 根据名称查找数据库类型
     *
     * @param name 数据库类型名称（不区分大小写）
     * @return 匹配的数据库类型
     * @throws IllegalArgumentException 如果找不到匹配的类型
     */
    public static DbType fromName(String name) {
        for (DbType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported database type: " + name);
    }

    /**
     * 检查是否支持 Schema
     *
     * <p>MySQL 和 H2 在概念上不区分 Database 和 Schema（或者说 Database 即 Schema），
     * 而 PostgreSQL、Oracle、SQL Server 需要明确指定 Schema。
     *
     * @return true 表示需要显式指定 schema_name
     */
    public boolean supportsSchema() {
        return this == POSTGRESQL || this == ORACLE || this == SQLSERVER;
    }
}
