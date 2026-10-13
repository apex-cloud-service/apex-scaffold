package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源监控指标响应 DTO。
 *
 * <p>提供数据源连接池的详细监控指标，用于分析连接池性能、诊断连接问题和优化配置参数。
 * 这些指标通常用于运维监控面板、告警系统和性能分析工具。
 *
 * <h2>核心指标说明</h2>
 * <ul>
 *   <li><b>连接数指标</b>: 总连接数、活跃连接数、空闲连接数</li>
 *   <li><b>性能指标</b>: 平均获取时间、平均使用时间、最大等待时间</li>
 *   <li><b>统计指标</b>: 创建连接总数、失败总数、等待线程数</li>
 * </ul>
 *
 * <h2>性能分析</h2>
 * <ul>
 *   <li><b>活跃连接数 / 总连接数 > 80%</b>: 连接池可能不足，考虑增大 maxPoolSize</li>
 *   <li><b>空闲连接数长期为 0</b>: 连接池压力大，需要扩容或优化查询</li>
 *   <li><b>平均获取时间 > 100ms</b>: 连接池性能瓶颈，检查配置和数据库负载</li>
 *   <li><b>等待线程数 > 0</b>: 存在线程等待连接，可能导致超时</li>
 *   <li><b>失败总数持续增长</b>: 连接创建失败，检查数据库状态和网络</li>
 * </ul>
 *
 * <h2>监控告警建议</h2>
 * <ul>
 *   <li>活跃连接数 / 总连接数 > 90%：告警，连接池即将耗尽</li>
 *   <li>平均获取时间 > 500ms：告警，获取连接性能严重下降</li>
 *   <li>等待线程数 > 10：告警，存在大量线程等待连接</li>
 *   <li>连接失败率 > 1%：告警，连接创建失败率过高</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>数据源监控面板展示</li>
 *   <li>连接池性能分析</li>
 *   <li>容量规划和配置优化</li>
 *   <li>故障诊断和问题排查</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DataSourceRequest
 * @see DataSourceResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据源监控指标")
public class DataSourceMetricsResponse {

    /**
     * 总连接数。
     *
     * <p>连接池中所有连接的数量，包括活跃连接和空闲连接。
     * 该值应等于配置的 maxPoolSize，或在动态调整场景下小于等于 maxPoolSize。
     * <br>
     * 计算公式：总连接数 = 活跃连接数 + 空闲连接数
     */
    @Schema(description = "总连接数", example = "20")
    private Integer totalConnections;

    /**
     * 活跃连接数。
     *
     * <p>当前正在被业务使用的连接数量。
     * 该指标实时反映数据库负载情况，是最重要的监控指标之一。
     * <br>
     * <b>健康指标</b>:
     * <ul>
     *   <li>< 60%: 正常，连接池资源充足</li>
     *   <li>60% - 80%: 注意，连接池使用率较高</li>
     *   <li>80% - 90%: 告警，连接池接近饱和</li>
     *   <li>> 90%: 严重，连接池即将耗尽</li>
     * </ul>
     */
    @Schema(description = "活跃连接数", example = "5")
    private Integer activeConnections;

    /**
     * 空闲连接数。
     *
     * <p>连接池中空闲可用的连接数量。
     * 该值应保持在一定范围内，既不能长期为 0（说明连接池不足），
     * 也不应长期接近总连接数（说明资源浪费）。
     * <br>
     * 计算公式：空闲连接数 = 总连接数 - 活跃连接数
     * <br>
     * <b>健康指标</b>:
     * <ul>
     *   <li>> minIdle: 正常，有足够的预热连接</li>
     *   <li>< minIdle: 注意，空闲连接不足</li>
     *   <li>= 0: 告警，无空闲连接，新请求需等待</li>
     * </ul>
     */
    @Schema(description = "空闲连接数", example = "15")
    private Integer idleConnections;

    /**
     * 等待线程数。
     *
     * <p>当前等待获取连接的线程数量。
     * 该值通常应为 0，如果持续大于 0，说明连接池资源不足或存在性能瓶颈。
     * <br>
     * <b>健康指标</b>:
     * <ul>
     *   <li>= 0: 正常，无线程等待</li>
     *   <li>1-5: 注意，偶尔有线程等待</li>
     *   <li>5-10: 告警，等待线程较多</li>
     *   <li>> 10: 严重，大量线程等待，可能导致超时</li>
     * </ul>
     */
    @Schema(description = "等待线程数", example = "0")
    private Integer waitingThreads;

    /**
     * 平均获取连接时间（毫秒）。
     *
     * <p>从连接池获取一个可用连接的平均耗时。
     * 该指标反映连接池的响应性能，是衡量连接池效率的重要指标。
     * <br>
     * <b>性能参考</b>:
     * <ul>
     *   <li>< 10ms: 优秀，连接池性能良好</li>
     *   <li>10-50ms: 良好，正常的性能水平</li>
     *   <li>50-100ms: 注意，性能开始下降</li>
     *   <li>100-500ms: 告警，性能明显下降</li>
     *   <li>> 500ms: 严重，性能严重下降</li>
     * </ul>
     */
    @Schema(description = "平均获取连接时间（毫秒）", example = "10.5")
    private Double averageGetTime;

    /**
     * 平均使用时间（毫秒）。
     *
     * <p>连接被业务使用的平均时长，从获取连接到归还连接的时间间隔。
     * 该指标反映业务查询的平均执行时间，可用于优化 SQL 和业务逻辑。
     * <br>
     * <b>性能参考</b>:
     * <ul>
     *   <li>< 50ms: 优秀，查询响应快</li>
     *   <li>50-200ms: 良好，正常的查询时间</li>
     *   <li>200-500ms: 注意，查询较慢，可优化</li>
     *   <li>500-1000ms: 告警，查询很慢，需优化</li>
     *   <li>> 1000ms: 严重，查询过慢，严重影响性能</li>
     * </ul>
     */
    @Schema(description = "平均使用时间（毫秒）", example = "150.2")
    private Double averageUsageTime;

    /**
     * 最大等待时间（毫秒）。
     *
     * <p>所有等待获取连接的线程中，等待时间最长的记录。
     * 该值通常不应超过配置的 connectionTimeout，否则会触发超时异常。
     * <br>
     * <b>健康指标</b>:
     * <ul>
     *   <li>< 1000ms: 正常</li>
     *   <li>1000-5000ms: 注意，部分请求等待较久</li>
     *   <li>5000-30000ms: 告警，接近超时阈值</li>
     *   <li>> 30000ms: 严重，已触发超时异常</li>
     * </ul>
     */
    @Schema(description = "最大等待时间（毫秒）", example = "5000")
    private Long maxWaitTime;

    /**
     * 创建连接总数。
     *
     * <p>从连接池启动以来，累计创建的数据库连接总数。
     * 该指标用于分析连接池的使用历史和连接创建频率。
     * <br>
     * <b>分析参考</b>:
     * <ul>
     *   <li>稳定增长：正常，连接池按需创建连接</li>
     *   <li>快速增长：注意，频繁创建连接，可能是 minIdle 设置过小</li>
     *   <li>停滞不增：正常，连接池已稳定，复用现有连接</li>
     * </ul>
     */
    @Schema(description = "创建连接总数", example = "100")
    private Long totalConnectionsCreated;

    /**
     * 连接失败总数。
     *
     * <p>从连接池启动以来，累计创建连接失败的次数。
     * 该指标用于监控数据库连接的健康状况，失败次数应保持为 0 或极低水平。
     * <br>
     * <b>健康指标</b>:
     * <ul>
     *   <li>= 0: 优秀，无连接失败</li>
     *   <li>< 1%: 正常，偶尔失败可接受</li>
     *   <li>1-5%: 注意，失败率偏高</li>
     *   <li>> 5%: 告警，失败率过高，需排查数据库或网络问题</li>
     * </ul>
     * <br>
     * <b>常见失败原因</b>:
     * <ul>
     *   <li>数据库服务不可用或重启</li>
     *   <li>网络连接问题</li>
     *   <li>认证失败（用户名或密码错误）</li>
     *   <li>数据库连接数达到上限</li>
     * </ul>
     */
    @Schema(description = "连接失败总数", example = "2")
    private Long totalConnectionsFailed;
}
