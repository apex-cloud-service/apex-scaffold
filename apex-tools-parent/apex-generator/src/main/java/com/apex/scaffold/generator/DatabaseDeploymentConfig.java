package com.apex.scaffold.generator;

import lombok.Data;

/**
 * 数据库部署配置（前端页面采集后的结构化输入）。
 *
 * <p>描述后端运行时所需的数据库连接信息，由前端部署配置页面采集并传入
 * 代码生成器，渲染为可直接并入 {@code application-*.yml} 的数据源、
 * Flyway 与 MyBatis-Plus 方言配置片段。</p>
 *
 * <p><b>安全边界：</b>本模型不承载数据库密码。渲染产物中密码统一使用
 * 环境变量占位符（如 {@code ${DB_MASTER_PASSWORD}}），由部署系统或
 * 密钥中心注入，避免密码进入代码仓库或前端工程。</p>
 *
 * <pre>{@code
 * {
 *   "dbType": "postgresql",
 *   "host": "127.0.0.1",
 *   "port": 5432,
 *   "database": "apex_data",
 *   "username": "apex",
 *   "schema": "public"
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see CodeGenerator#generateDatabaseDeploymentConfig(DatabaseDeploymentConfig, java.nio.file.Path)
 */
@Data
public class DatabaseDeploymentConfig {

    /**
     * 数据库类型。
     *
     * <p>首期支持：{@code mysql}、{@code postgresql}。
     * 不区分大小写，内部统一转为小写处理。</p>
     */
    private String dbType;

    /**
     * 数据库主机地址。
     */
    private String host;

    /**
     * 数据库端口。
     */
    private int port;

    /**
     * 数据库名（catalog / schema）。
     */
    private String database;

    /**
     * 数据库用户名。
     */
    private String username;

    /**
     * Schema 名称（PostgreSQL 等使用；MySQL 可留空）。
     */
    private String schema;

    /**
     * JDBC 连接参数字符串（不含 {@code ?} 前缀）。
     *
     * <p>例如 {@code useUnicode=true&characterEncoding=utf8}。
     * 为空时按数据库类型渲染默认参数。</p>
     */
    private String connectionParams;
}
