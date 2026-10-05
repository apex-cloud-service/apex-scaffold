package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 代码生成器数据源配置持久化对象。
 *
 * <p>对应数据库表 {@code gen_datasource},存储代码生成器连接的外部数据库配置信息。
 * 每个数据源代表一个可用于代码生成的目标数据库,包含连接信息和认证凭据。
 *
 * <h3>主要字段</h3>
 * <ul>
 *   <li>{@link #name} - 数据源名称,租户内唯一</li>
 *   <li>{@link #dbType} - 数据库类型(MYSQL/POSTGRESQL/ORACLE/SQLSERVER/H2)</li>
 *   <li>{@link #host}/{@link #port}/{@link #databaseName} - 连接信息</li>
 *   <li>{@link #username}/{@link #password} - 认证信息,密码使用 AES 加密存储</li>
 *   <li>{@link #schemaName} - PostgreSQL/Oracle 的 schema 名称(可选)</li>
 *   <li>{@link #params} - 额外连接参数,JSON 格式(如 useSSL/serverTimezone)</li>
 *   <li>{@link #status} - 启用状态(0-禁用,1-启用)</li>
 *   <li>{@link #tenantId} - 租户标识,实现多租户隔离</li>
 * </ul>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>密码字段必须使用 AES 算法加密,密钥从环境变量读取</li>
 *   <li>禁止在日志或响应中输出明文密码</li>
 *   <li>删除数据源时应级联关闭相关的数据库连接池</li>
 * </ul>
 *
 * <h3>唯一约束</h3>
 * <p>数据库中存在唯一索引 {@code uk_datasource_name_tenant(name, tenant_id, deleted)},
 * 确保同一租户下数据源名称唯一(逻辑删除后不参与唯一性校验)。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.domain.model.generator.GenDatasourceEntity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("gen_datasource")
public class GenDatasourcePO extends BasePO {

    /**
     * 数据源名称。
     *
     * <p>用于标识数据源的唯一名称,建议使用有意义的名称如 "主库-生产环境"、"测试库"。
     * 名称在同一租户内必须唯一,长度限制 100 字符。
     */
    private String name;

    /**
     * 数据库类型。
     *
     * <p>支持的数据库类型:
     * <ul>
     *   <li>MYSQL - MySQL 5.7+ / 8.0+</li>
     *   <li>POSTGRESQL - PostgreSQL 10+</li>
     *   <li>ORACLE - Oracle 11g+</li>
     *   <li>SQLSERVER - SQL Server 2012+</li>
     *   <li>H2 - H2 Database</li>
     * </ul>
     *
     * <p>不同数据库类型的元数据读取逻辑和 JDBC URL 拼接格式不同。
     */
    private String dbType;

    /**
     * 数据库主机地址。
     *
     * <p>可以是 IP 地址(如 192.168.1.100)或域名(如 db.example.com)。
     * 长度限制 100 字符。
     */
    private String host;

    /**
     * 数据库端口号。
     *
     * <p>常见默认端口:
     * <ul>
     *   <li>MySQL: 3306</li>
     *   <li>PostgreSQL: 5432</li>
     *   <li>Oracle: 1521</li>
     *   <li>SQL Server: 1433</li>
     *   <li>H2: 9092 (TCP模式)</li>
     * </ul>
     */
    private Integer port;

    /**
     * 数据库名称(Database Name)。
     *
     * <p>MySQL/PostgreSQL 中的 database,Oracle 中的 service name。
     * 长度限制 100 字符。
     */
    private String databaseName;

    /**
     * 数据库用户名。
     *
     * <p>用于身份认证的用户名,应具有读取元数据(INFORMATION_SCHEMA)的权限。
     * 代码生成器仅读取表结构,无需 DML 权限。
     * 长度限制 100 字符。
     */
    private String username;

    /**
     * 数据库密码(AES 加密存储)。
     *
     * <p><strong>安全规范:</strong>
     * <ul>
     *   <li>存储前必须使用 AES 算法加密,密钥从环境变量 {@code DATASOURCE_PASSWORD_KEY} 读取</li>
     *   <li>查询时通过 Converter 转换为领域实体时保持加密状态</li>
     *   <li>仅在建立数据库连接时临时解密</li>
     *   <li>禁止在日志、响应 DTO 或异常信息中输出明文密码</li>
     * </ul>
     *
     * <p>加密后长度最多 500 字符。
     *
     * @see com.apex.scaffold.common.util.EncryptUtil
     */
    private String password;

    /**
     * 数据库 Schema 名称(可选)。
     *
     * <p>适用于 PostgreSQL 和 Oracle 数据库:
     * <ul>
     *   <li>PostgreSQL: schema 是 database 下的命名空间,默认为 {@code public}</li>
     *   <li>Oracle: schema 通常与用户名相同</li>
     *   <li>MySQL: 不支持 schema,此字段应为 {@code null}</li>
     * </ul>
     *
     * <p>长度限制 100 字符。
     */
    private String schemaName;

    /**
     * 额外连接参数(JSON 格式)。
     *
     * <p>用于存储 JDBC URL 的查询参数,JSON 格式示例:
     * <pre>{@code
     * {
     *   "useSSL": "false",
     *   "serverTimezone": "Asia/Shanghai",
     *   "characterEncoding": "utf8",
     *   "allowPublicKeyRetrieval": "true"
     * }
     * }</pre>
     *
     * <p>这些参数会附加到 JDBC URL 的查询字符串中。不同数据库支持的参数不同。
     */
    private String params;

    /**
     * 启用状态。
     *
     * <p>状态枚举:
     * <ul>
     *   <li>0 - 禁用:数据源不可用于代码生成,连接池已关闭</li>
     *   <li>1 - 启用:数据源正常可用</li>
     * </ul>
     *
     * <p>禁用数据源时应同步关闭其对应的 HikariCP 连接池,避免资源泄漏。
     */
    private Integer status;

    /**
     * 租户标识。
     *
     * <p>实现多租户数据隔离,确保不同租户的数据源配置相互独立。
     * 所有查询和操作都必须携带租户标识进行过滤。
     *
     * <p>长度限制 64 字符。
     */
    private String tenantId;
}
