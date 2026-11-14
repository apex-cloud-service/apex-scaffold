package com.apex.scaffold.infrastructure.remote.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 远程API调用配置属性
 *
 * <p>用于配置调用外部项目API的连接参数，支持多目标服务配置。
 * 通过 {@code remote.api} 前缀绑定配置文件中的相关属性。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code remote.api.default-connect-timeout} - 默认连接超时时间（毫秒）</li>
 *   <li>{@code remote.api.default-read-timeout} - 默认读取超时时间（毫秒）</li>
 *   <li>{@code remote.api.default-retry-count} - 默认重试次数</li>
 *   <li>{@code remote.api.default-retry-delay} - 默认重试间隔（毫秒）</li>
 *   <li>{@code remote.api.services} - 多目标服务配置（Map结构）</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * remote:
 *   api:
 *     default-connect-timeout: 5000
 *     default-read-timeout: 10000
 *     default-retry-count: 2
 *     default-retry-delay: 1000
 *     services:
 *       user-service:
 *         base-url: http://localhost:8081
 *         connect-timeout: 3000
 *         read-timeout: 8000
 *         default-headers:
 *           X-Request-Source: apex-scaffold
 *       order-service:
 *         base-url: http://localhost:8082
 *         connect-timeout: 5000
 *         read-timeout: 15000
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
// 不标注 @Configuration：本类由 @EnableConfigurationProperties / @ConfigurationPropertiesScan 注册。
// 若同时标注 @Configuration，宽范围组件扫描（如 apex-example 的 scanBasePackages=com.apex.scaffold）
// 会再注册一次，导致 "required a single bean, but 2 were found"。
@ConfigurationProperties(prefix = "remote.api")
public class RemoteApiProperties {

    /** 默认连接超时时间（毫秒），默认5000ms */
    private int defaultConnectTimeout = 5000;

    /** 默认读取超时时间（毫秒），默认10000ms */
    private int defaultReadTimeout = 10000;

    /** 默认重试次数，默认2次（共执行3次） */
    private int defaultRetryCount = 2;

    /** 默认重试间隔（毫秒），默认1000ms */
    private long defaultRetryDelay = 1000L;

    /** 远程响应聚合缓冲上限（字节），默认10MB */
    private int maxInMemorySize = 10 * 1024 * 1024;

    /** 多目标服务配置，key为服务名，value为服务配置 */
    private Map<String, ServiceConfig> services = new HashMap<>();

    /**
     * 远程服务配置
     *
     * <p>单个目标远程服务的连接配置，包括基础URL、超时时间、请求头等。
     */
    @Data
    public static class ServiceConfig {

        /** 目标服务的基础URL，如 http://localhost:8081 */
        private String baseUrl;

        /** 连接超时时间（毫秒），未配置时使用全局默认值 */
        private Integer connectTimeout;

        /** 读取超时时间（毫秒），未配置时使用全局默认值 */
        private Integer readTimeout;

        /** 重试次数，未配置时使用全局默认值 */
        private Integer retryCount;

        /** 重试间隔（毫秒），未配置时使用全局默认值 */
        private Long retryDelay;

        /** 默认请求头，自动附加到每个请求中 */
        private Map<String, String> defaultHeaders = new HashMap<>();
    }
}
