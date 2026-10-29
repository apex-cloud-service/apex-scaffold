package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源请求 DTO。
 *
 * <p>用于创建和更新数据源配置，支持动态添加和管理应用运行时的数据源连接池。
 * 该 DTO 包含完整的数据源连接信息和连接池配置参数。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>创建新的数据源配置（如多租户场景下的租户独立数据库）</li>
 *   <li>更新现有数据源的连接参数或连接池配置</li>
 *   <li>配置主从数据源、读写分离等复杂场景</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><b>name</b>: 数据源标识名称，建议使用有意义的名称（如 "tenant_001_db"）</li>
 *   <li><b>type</b>: 数据源类型，支持 master（主库）、slave（从库）等</li>
 *   <li><b>driver</b>: 驱动类型，如 mysql、postgresql、oracle 等</li>
 *   <li><b>url</b>: JDBC 连接 URL，格式如 jdbc:mysql://host:port/database</li>
 *   <li><b>username/password</b>: 数据库认证凭据</li>
 *   <li><b>连接池参数</b>: minIdle、maxPoolSize、connectionTimeout、idleTimeout</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>minIdle: 根据业务负载设置，一般 5-10 个</li>
 *   <li>maxPoolSize: 不宜过大，一般 20-50 个，避免数据库连接数耗尽</li>
 *   <li>connectionTimeout: 默认 30 秒，高并发场景可适当延长</li>
 *   <li>idleTimeout: 默认 10 分钟，避免长时间空闲连接占用资源</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>密码字段会被加密存储，不会明文保存到数据库</li>
 *   <li>创建数据源前建议先使用测试接口验证连接有效性</li>
 *   <li>生产环境建议通过配置中心管理敏感信息</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DataSourceResponse
 * @see DataSourceMetricsResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据源请求")
public class DataSourceRequest {

    /**
     * 数据源名称。
     *
     * <p>用于标识数据源的唯一名称，建议使用有意义的命名规则。
     * 例如：tenant_001_db、order_slave_db 等。
     */
    @Schema(description = "数据源名称", example = "主数据库", required = true)
    @NotBlank(message = "数据源名称不能为空")
    private String name;

    /**
     * 数据源类型。
     *
     * <p>支持的类型：
     * <ul>
     *   <li>master: 主库，支持读写操作</li>
     *   <li>slave: 从库，仅支持读操作</li>
     *   <li>backup: 备份库，用于数据备份和恢复</li>
     * </ul>
     */
    @Schema(
            description = "数据源类型",
            example = "master",
            required = true,
            allowableValues = {"master", "slave", "backup"})
    @NotBlank(message = "数据源类型不能为空")
    private String type;

    /**
     * 驱动类型。
     *
     * <p>支持的驱动类型：
     * <ul>
     *   <li>mysql: MySQL 数据库</li>
     *   <li>postgresql: PostgreSQL 数据库</li>
     *   <li>oracle: Oracle 数据库</li>
     *   <li>sqlserver: SQL Server 数据库</li>
     * </ul>
     */
    @Schema(
            description = "驱动类型",
            example = "mysql",
            required = true,
            allowableValues = {"mysql", "postgresql", "oracle", "sqlserver"})
    @NotBlank(message = "驱动类型不能为空")
    private String driver;

    /**
     * JDBC 连接 URL。
     *
     * <p>完整的 JDBC 连接字符串，包含主机、端口、数据库名称和连接参数。
     * 示例：
     * <ul>
     *   <li>MySQL: jdbc:mysql://localhost:3306/apex?useSSL=false&serverTimezone=Asia/Shanghai</li>
     *   <li>PostgreSQL: jdbc:postgresql://localhost:5432/apex?currentSchema=public</li>
     * </ul>
     */
    @Schema(description = "连接URL", example = "jdbc:mysql://localhost:3306/apex", required = true)
    @NotBlank(message = "连接URL不能为空")
    private String url;

    /**
     * 数据库用户名。
     *
     * <p>用于数据库认证的用户名，建议遵循最小权限原则，
     * 从库用户仅授予 SELECT 权限。
     */
    @Schema(description = "用户名", example = "root", required = true)
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 数据库密码。
     *
     * <p>用于数据库认证的密码，系统会自动加密存储，
     * 不会以明文形式保存到数据库或日志中。
     */
    @Schema(description = "密码", example = "password", required = true)
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 最小空闲连接数。
     *
     * <p>连接池中保持的最小空闲连接数量。
     * 该值应根据业务负载合理设置：
     * <ul>
     *   <li>低负载场景：3-5 个</li>
     *   <li>中等负载场景：5-10 个</li>
     *   <li>高负载场景：10-20 个</li>
     * </ul>
     */
    @Schema(description = "最小空闲连接数", example = "5")
    @Min(value = 1, message = "最小空闲连接数必须大于0")
    private Integer minIdle = 5;

    /**
     * 最大连接池大小。
     *
     * <p>连接池允许的最大连接数量。
     * 该值不宜设置过大，避免数据库连接数耗尽：
     * <ul>
     *   <li>单机应用：20-50 个</li>
     *   <li>集群应用：需考虑实例数量，总连接数 = 单实例连接数 × 实例数</li>
     *   <li>数据库最大连接数限制：MySQL 默认 151，需提前规划</li>
     * </ul>
     */
    @Schema(description = "最大连接池大小", example = "20")
    @Min(value = 1, message = "最大连接池大小必须大于0")
    @Max(value = 100, message = "最大连接池大小不能超过100")
    private Integer maxPoolSize = 20;

    /**
     * 连接超时时间（毫秒）。
     *
     * <p>从连接池获取连接的最大等待时间。
     * 超过该时间仍未获取到连接则抛出异常。
     * <ul>
     *   <li>默认值：30000 毫秒（30 秒）</li>
     *   <li>高并发场景：可适当延长至 60000 毫秒（60 秒）</li>
     * </ul>
     */
    @Schema(description = "连接超时时间（毫秒）", example = "30000")
    private Long connectionTimeout = 30000L;

    /**
     * 空闲超时时间（毫秒）。
     *
     * <p>连接在连接池中保持空闲的最大时间。
     * 超过该时间的空闲连接会被自动关闭。
     * <ul>
     *   <li>默认值：600000 毫秒（10 分钟）</li>
     *   <li>避免设置过短，频繁创建销毁连接会影响性能</li>
     * </ul>
     */
    @Schema(description = "空闲超时时间（毫秒）", example = "600000")
    private Long idleTimeout = 600000L;
}
