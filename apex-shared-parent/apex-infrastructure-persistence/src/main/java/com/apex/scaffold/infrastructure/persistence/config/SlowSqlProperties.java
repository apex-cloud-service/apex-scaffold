package com.apex.scaffold.infrastructure.persistence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 慢 SQL 指标配置属性。
 *
 * <p>控制 {@link SlowSqlMetricsInterceptor} 的行为：
 * <ul>
 *   <li>{@code apex.sql.slow.enabled}：是否启用慢 SQL 指标采集（默认 true）</li>
 *   <li>{@code apex.sql.slow.threshold-ms}：慢 SQL 判定阈值（默认 1000ms）</li>
 * </ul>
 * 触发阈值后除上报指标外，还会以 WARN 级别输出 SQL 文本与耗时，便于定位。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 */
@ConfigurationProperties(prefix = "apex.sql.slow")
@Data
public class SlowSqlProperties {

    /** 是否启用慢 SQL 指标采集。 */
    private boolean enabled = true;

    /** 慢 SQL 判定阈值（毫秒）。 */
    private long thresholdMs = 1000L;
}
