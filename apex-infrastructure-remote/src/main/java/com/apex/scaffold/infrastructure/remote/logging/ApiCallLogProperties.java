package com.apex.scaffold.infrastructure.remote.logging;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * API 调用日志配置属性。
 *
 * <p>控制对外部 HTTP 服务调用日志的采集行为。通过 Spring Boot
 * {@code api-call-log.*} 前缀的配置项注入。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * api-call-log:
 *   enabled: true
 *   max-body-length: 4096
 *   log-request-body: true
 *   log-response-body: true
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "api-call-log")
public class ApiCallLogProperties {

    /** 是否启用 API 调用日志采集，默认开启。 */
    private boolean enabled = true;

    /** 请求体 / 响应体的最大记录长度（字符数），超出部分截断。默认 4096。 */
    private int maxBodyLength = 4096;

    /** 是否记录请求体。默认 true。 */
    private boolean logRequestBody = true;

    /** 是否记录响应体。默认 true。 */
    private boolean logResponseBody = true;
}
