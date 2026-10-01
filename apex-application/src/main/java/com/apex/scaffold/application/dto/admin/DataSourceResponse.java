package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源响应 DTO。
 *
 * <p>用于返回数据源的详细信息，包括配置参数、运行状态和监控指标。
 * 为了安全考虑，敏感信息（如密码、完整的 URL）会进行脱敏处理。
 *
 * <h2>脱敏规则</h2>
 * <ul>
 *   <li><b>密码</b>: 不返回，完全隐藏</li>
 *   <li><b>URL</b>: 部分脱敏，如 jdbc:mysql://localhost:3306/***</li>
 *   <li><b>用户名</b>: 部分脱敏，如 ro***（保留前两个字符）</li>
 * </ul>
 *
 * <h2>状态说明</h2>
 * <ul>
 *   <li><b>ONLINE</b>: 数据源正常运行，连接池可用</li>
 *   <li><b>OFFLINE</b>: 数据源离线，连接池不可用</li>
 *   <li><b>ERROR</b>: 数据源异常，连接池存在错误</li>
 *   <li><b>INITIALIZING</b>: 数据源初始化中</li>
 * </ul>
 *
 * <h2>连接池指标</h2>
 * <ul>
 *   <li><b>poolSize</b>: 当前连接池配置的最大连接数</li>
 *   <li><b>activeConnections</b>: 当前正在使用的活跃连接数</li>
 *   <li>空闲连接数 = poolSize - activeConnections</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>数据源列表展示</li>
 *   <li>数据源详情查询</li>
 *   <li>数据源监控面板</li>
 *   <li>数据源健康检查</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DataSourceRequest
 * @see DataSourceMetricsResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据源响应")
public class DataSourceResponse {

    /**
     * 数据源 ID。
     *
     * <p>数据源的唯一标识符，用于更新、删除等操作。
     */
    @Schema(description = "ID", example = "1")
    private Long id;

    /**
     * 数据源名称。
     *
     * <p>数据源的标识名称，与创建时保持一致。
     */
    @Schema(description = "数据源名称", example = "主数据库")
    private String name;

    /**
     * 数据源类型。
     *
     * <p>数据源的类型标识，如 master（主库）、slave（从库）等。
     */
    @Schema(
            description = "数据源类型",
            example = "master",
            allowableValues = {"master", "slave", "backup"})
    private String type;

    /**
     * 驱动类型。
     *
     * <p>数据库驱动类型，如 mysql、postgresql 等。
     */
    @Schema(
            description = "驱动类型",
            example = "mysql",
            allowableValues = {"mysql", "postgresql", "oracle", "sqlserver"})
    private String driver;

    /**
     * 连接 URL（脱敏）。
     *
     * <p>为了安全考虑，数据库名称部分会被脱敏处理。
     * 脱敏规则：保留协议、主机、端口，数据库名称替换为 ***
     * <br>
     * 示例：
     * <ul>
     *   <li>原始: jdbc:mysql://localhost:3306/apex_db</li>
     *   <li>脱敏: jdbc:mysql://localhost:3306/***</li>
     * </ul>
     */
    @Schema(description = "连接URL（脱敏）", example = "jdbc:mysql://localhost:3306/***")
    private String url;

    /**
     * 用户名（脱敏）。
     *
     * <p>为了安全考虑，用户名会被部分脱敏。
     * 脱敏规则：保留前 2 个字符，其余替换为 ***
     * <br>
     * 示例：
     * <ul>
     *   <li>原始: root</li>
     *   <li>脱敏: ro***</li>
     * </ul>
     */
    @Schema(description = "用户名（脱敏）", example = "ro***")
    private String username;

    /**
     * 连接池大小。
     *
     * <p>连接池配置的最大连接数，即 maxPoolSize 的值。
     */
    @Schema(description = "连接池大小", example = "20")
    private Integer poolSize;

    /**
     * 活跃连接数。
     *
     * <p>当前正在使用的数据库连接数量。
     * 该指标实时变化，反映当前业务负载情况。
     * <ul>
     *   <li>活跃连接数 / 连接池大小 > 80%：连接池可能不足，需考虑扩容</li>
     *   <li>活跃连接数长期接近 0：可能存在数据源未使用或配置错误</li>
     * </ul>
     */
    @Schema(description = "活跃连接数", example = "5")
    private Integer activeConnections;

    /**
     * 数据源状态。
     *
     * <p>数据源当前的运行状态：
     * <ul>
     *   <li><b>ONLINE</b>: 正常运行，连接池可用</li>
     *   <li><b>OFFLINE</b>: 离线状态，连接池不可用</li>
     *   <li><b>ERROR</b>: 异常状态，连接池存在错误</li>
     *   <li><b>INITIALIZING</b>: 初始化中，连接池正在启动</li>
     * </ul>
     */
    @Schema(
            description = "状态",
            example = "ONLINE",
            allowableValues = {"ONLINE", "OFFLINE", "ERROR", "INITIALIZING"})
    private String status;

    /**
     * 创建时间。
     *
     * <p>数据源配置的创建时间。
     */
    @Schema(description = "创建时间", example = "2026-09-26T10:00:00")
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     *
     * <p>数据源配置的最后更新时间。
     */
    @Schema(description = "更新时间", example = "2026-09-26T15:30:00")
    private LocalDateTime updatedAt;
}
