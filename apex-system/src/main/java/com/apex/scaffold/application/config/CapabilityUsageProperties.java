package com.apex.scaffold.application.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 能力调用用量计量配置（方案 D4 计量）。
 *
 * <p>定义能力编码 → 请求 URI 前缀清单的映射（调用次数维度），以及
 * 能力编码 → 存量指标来源的映射（存储用量/设备数/消息量维度），作为
 * {@code GET /api/v2/capability-usage} 的计量口径。
 *
 * <p>口径约束：能力编码与清单须与网关开放接口路由的
 * {@code metadata.open-api.capability} 保持一致（见 apex-gateway 的
 * {@code /open-api/**} 路由组）；前缀匹配的是操作日志中记录的
 * {@code request_uri} 值，网关转发路径变化时需同步调整。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.capability-usage")
public class CapabilityUsageProperties {

    /**
     * 能力编码 → 计量 URI 前缀清单。
     *
     * <p>默认值与网关四条开放路由及服务侧实际开放端点对齐；列表同时包含精确路径与
     * 其子路径前缀，确保 {@code /open-api/users}、{@code /open-api/messages} 这类
     * 无尾斜杠端点也能被调用次数统计命中。
     */
    private Map<String, List<String>> mappings = new LinkedHashMap<>(Map.of(
            "file-storage", List.of("/open-api/file/"),
            "workflow", List.of("/open-api/workflow/"),
            "user-org", List.of("/open-api/users", "/open-api/users/", "/open-api/user", "/open-api/user/"),
            "office-message", List.of("/open-api/messages", "/open-api/messages/")));

    /**
     * 能力编码 → 存量指标来源（D4 四维度计量）。
     *
     * <p>存量维度（存储用量/设备数/消息量）来自各域内部统计端点，仅在查询单个租户时
     * 拉取；{@code source} 为 {@code apex.overview.sources} 的数据源键（基址复用聚合门面
     * 配置），{@code uriTemplate} 的 {@code {tenantId}} 为租户占位符，{@code field} 为
     * 响应 {@code data} 节点中的取值字段。
     */
    private Map<String, MetricSource> metrics = new LinkedHashMap<>(Map.of(
            "file-storage",
                    new MetricSource("file", "/internal/v1/file/stats?tenantId={tenantId}", "storageUsageBytes"),
            "iot-device",
                    new MetricSource(
                            "integration", "/internal/v1/iot/devices/stats?tenantId={tenantId}", "deviceCount"),
            "office-message",
                    new MetricSource(
                            "office", "/internal/v1/office/messages/stats?tenantId={tenantId}", "messageCount")));

    /** 存量指标单次拉取超时（毫秒）；超时/失败按源降级为 {@code null}。 */
    private long metricTimeoutMillis = 2000;

    /** 存量指标本地缓存 TTL（毫秒）；存量数值变化不频繁，短 TTL 防抖即可。 */
    private long metricCacheTtlMillis = 30000;

    /** 单次查询最大时间跨度（天），防止全表范围聚合。 */
    private int maxRangeDays = 92;

    /**
     * 存量指标来源定义。
     *
     * @param source 数据源键（{@code apex.overview.sources} 的键）
     * @param uriTemplate 统计端点 URI 模板（{@code {tenantId}} 占位符）
     * @param field 响应 {@code data} 节点取值字段名
     */
    public record MetricSource(String source, String uriTemplate, String field) {}
}
