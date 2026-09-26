package com.apex.scaffold.domain.model.system;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 系统数据源配置领域实体。
 *
 * <p>表示管理后台"数据库配置"模块中注册的动态数据源连接配置，用于多租户
 * 场景下的数据源注册、连接池管理与运行状态监控。与代码生成器的
 * {@code GenDatasourceEntity}（面向元数据读取）不同，本实体面向运行时
 * 连接池管理，包含完整的连接池参数配置。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>封装数据源连接信息：JDBC URL、驱动类型、认证凭据</li>
 *   <li>封装连接池参数：最小空闲连接、最大连接数、超时时间</li>
 *   <li>标识数据源角色：主库（master）、从库（slave）、备份库（backup）</li>
 *   <li>多租户隔离：每个租户独立管理自己的数据源配置</li>
 * </ul>
 *
 * <h2>安全约束</h2>
 * <ul>
 *   <li><strong>密码加密存储</strong>：{@link #password} 必须在应用层加密后传入仓储，
 *       禁止明文落库；仅在建立连接池时临时解密</li>
 *   <li><strong>租户隔离</strong>：所有查询和操作必须限定 tenantId，防止跨租户访问</li>
 *   <li><strong>名称唯一</strong>：数据源名称在同一租户内唯一（数据库唯一索引保障）</li>
 *   <li><strong>脱敏展示</strong>：接口响应中不得返回密码明文</li>
 * </ul>
 *
 * <h2>生命周期</h2>
 * <ol>
 *   <li>创建：管理员录入连接信息（建议先测试连接有效性）</li>
 *   <li>启用：连接池建立，状态可用</li>
 *   <li>更新：连接参数变更后需重建连接池</li>
 *   <li>删除：逻辑删除（deleted=1），关联连接池同步关闭</li>
 * </ol>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class SysDatasource extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 数据源名称。
     *
     * <p>同一租户内唯一，建议使用有意义的命名（如 tenant_001_master）。
     */
    private String name;

    /**
     * 数据源类型。
     *
     * <p>取值：master（主库）、slave（从库）、backup（备份库）。
     */
    private String type;

    /**
     * 驱动类型。
     *
     * <p>取值：mysql、postgresql、oracle、sqlserver。
     */
    private String driver;

    /**
     * JDBC 连接 URL。
     *
     * <p>完整连接串，如 {@code jdbc:mysql://localhost:3306/apex}。
     */
    private String url;

    /**
     * 数据库用户名。
     */
    private String username;

    /**
     * 数据库密码（应用层加密后的密文）。
     *
     * <p><strong>禁止明文存储</strong>，由应用服务在写入前通过
     * EncryptionService 加密，仅在建立连接池时解密使用。
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
     * <p>多租户隔离标识，所有查询与操作必须限定该字段。
     */
    private String tenantId;
}
