package com.apex.scaffold.interfaces.filter;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * API 全局响应超时配置属性
 *
 * <p>通过 {@code apex.api.response-timeout} 控制单个请求的最大处理时长，
 * 超过阈值的请求由 {@link GlobalResponseTimeoutWebFilter} 以 504
 * （错误码 {@code REQUEST_TIMEOUT}）熔断，防止慢请求长期占用连接与线程资源。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * apex:
 *   api:
 *     response-timeout: 30s   # 0s 或负值表示关闭全局超时
 * }</pre>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see GlobalResponseTimeoutWebFilter
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.api")
public class ApiTimeoutProperties {

    /**
     * 全局响应超时时间
     *
     * <p>单个 HTTP 请求（含过滤器链与业务处理）的最大允许时长。
     * 设为 0 或负值可关闭全局超时熔断；SSE 长连接请求不受此配置约束。</p>
     */
    private Duration responseTimeout = Duration.ofSeconds(30);

    /**
     * 判断全局响应超时是否启用
     *
     * @return 超时时间有效（大于 0）返回 true
     */
    public boolean isTimeoutEnabled() {
        return responseTimeout != null && !responseTimeout.isZero() && !responseTimeout.isNegative();
    }
}
