package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 系统数据源配置持久化对象。
 *
 * <p>对应数据库表 {@code sys_datasource}，存储管理后台"数据库配置"模块注册的
 * 动态数据源连接配置，包括连接信息、认证凭据和连接池参数。
 *
 * <h2>主要字段</h2>
 * <ul>
 *   <li>{@link #name} - 数据源名称，租户内唯一</li>
 *   <li>{@link #type} - 数据源类型（master/slave/backup）</li>
 *   <li>{@link #driver} - 驱动类型（mysql/postgresql/oracle/sqlserver）</li>
 *   <li>{@link #url} - JDBC 连接 URL</li>
 *   <li>{@link #username}/{@link #password} - 认证信息，密码 AES 加密存储</li>
 *   <li>{@link #minIdle}/{@link #maxPoolSize} - 连接池容量参数</li>
 *   <li>{@link #connectionTimeout}/{@link #idleTimeout} - 连接池超时参数（毫秒）</li>
 *   <li>{@link #status} - 启用状态（1-启用，0-禁用）</li>
 *   <li>{@link #tenantId} - 租户标识，实现多租户隔离</li>
 * </ul>
 *
 * <h2>安全约束</h2>
 * <ul>
 *   <li>密码字段必须由应用层使用 EncryptionService 加密后写入，禁止明文落库</li>
 *   <li>禁止在日志或接口响应中输出密码明文</li>
 *   <li>删除数据源时应级联关闭对应的 HikariCP 连接池</li>
 * </ul>
 *
 * <h2>唯一约束</h2>
 * <p>数据库唯一索引 {@code uk_sys_datasource_name_tenant(name, tenant_id, deleted)}
 * 确保同一租户下数据源名称唯一（逻辑删除后不参与唯一性校验）。
 *
 * <h2>审计字段</h2>
 * <p>主键（雪花算法）、创建/更新时间、乐观锁版本、逻辑删除标记由 {@link BasePO} 统一提供。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.domain.model.system.SysDatasource
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("sys_datasource")
public class SysDatasourcePO extends BasePO {

    /**
     * 数据源名称。
     *
     * <p>同一租户内唯一，长度限制 100 字符。
     */
    private String name;

    /**
     * 数据源类型。
     *
     * <p>取值：master（主库）、slave（从库）、backup（备份库），长度限制 20 字符。
     */
    private String type;

    /**
     * 驱动类型。
     *
     * <p>取值：mysql、postgresql、oracle、sqlserver，长度限制 30 字符。
     */
    private String driver;

    /**
     * JDBC 连接 URL。
     *
     * <p>完整连接串，长度限制 500 字符。
     */
    private String url;

    /**
     * 数据库用户名。
     *
     * <p>长度限制 100 字符。
     */
    private String username;

    /**
     * 数据库密码（应用层加密后的密文）。
     *
     * <p><strong>禁止明文存储</strong>：由应用服务通过 EncryptionService 加密后写入，
     * 仅在建立连接池时解密使用。加密后长度最多 500 字符。
     */
    private String password;

    /**
     * 最小空闲连接数。
     */
    private Integer minIdle;

    /**
     * 最大连接池大小。
     */
    private Integer maxPoolSize;

    /**
     * 获取连接超时时间（毫秒）。
     */
    private Long connectionTimeout;

    /**
     * 空闲连接超时时间（毫秒）。
     */
    private Long idleTimeout;

    /**
     * 启用状态。
     *
     * <p>取值：1-启用，0-禁用。禁用的数据源不参与连接池管理与监控。
     */
    private Integer status;

    /**
     * 租户 ID。
     *
     * <p>多租户隔离标识，长度限制 64 字符。
     */
    private String tenantId;
}
