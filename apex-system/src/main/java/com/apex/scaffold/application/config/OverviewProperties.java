package com.apex.scaffold.application.config;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 跨域只读聚合门面配置（方案 D5）。
 *
 * <p>{@code GET /internal/v1/overview} 聚合各业务域只读统计端点所需的数据源地址、
 * 短 TTL 缓存与单源调用超时。默认地址指向本机各服务开发端口，部署时按拓扑覆盖：
 *
 * <ul>
 *   <li>{@code apex.overview.sources.file}：文件服务基址（内部统计 {@code /internal/v1/file/stats}）</li>
 *   <li>{@code apex.overview.sources.workflow}：工作流服务基址（内部统计 {@code /internal/v1/workflow/stats}）</li>
 *   <li>{@code apex.overview.sources.integration}：集成服务基址（内部统计 {@code /internal/v1/iot/devices/stats}；
 *       缺省指向 8094 业务端口，部署时按实际拓扑覆盖）</li>
 *   <li>{@code apex.overview.cache-ttl-seconds}：聚合结果短 TTL 缓存秒数（默认 30）</li>
 *   <li>{@code apex.overview.timeout-millis}：单源调用超时毫秒数（默认 3000）</li>
 *   <li>{@code apex.overview.bulkhead.<source>}：单源并发隔离舱壁的最大并发数；未配置的源
 *       使用 {@code apex.overview.default-bulkhead-max-concurrency}（默认 8）。饱和即该源
 *       立即降级（不排队等待），与其他源的隔离语义一致</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.overview")
public class OverviewProperties {

    /** 数据源名称 → 服务基址；键固定为 file/workflow/integration/office。 */
    private Map<String, String> sources = new LinkedHashMap<>(Map.of(
            "file", "http://127.0.0.1:8085",
            "workflow", "http://127.0.0.1:8093",
            "integration", "http://127.0.0.1:8094",
            "office", "http://127.0.0.1:8084"));

    /** 聚合结果短 TTL 缓存秒数。 */
    private long cacheTtlSeconds = 30;

    /** 单数据源调用超时毫秒数。 */
    private long timeoutMillis = 3000;

    /** 数据源名称 → 舱壁最大并发数；仅对门面实际拉取的源生效。 */
    private Map<String, Integer> bulkhead = new LinkedHashMap<>();

    /** 未显式配置舱壁并发数的源的默认最大并发数。 */
    private int defaultBulkheadMaxConcurrency = 8;
}
