package com.apex.scaffold.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * 网关限流 Key 解析器配置.
 *
 * <p>提供两类限流维度：
 * <ul>
 *   <li>{@link #ipKeyResolver()}：按客户端 IP 限流（主维度，防单机刷接口）</li>
 *   <li>{@link #userKeyResolver()}：按用户维度限流（登录后精细化控制）</li>
 * </ul>
 *
 * <p>限流算法与容量在 {@code application.yml} 的
 * {@code spring.cloud.gateway.routes[].filters[].args} 或默认过滤器上以 Redis 令牌桶实现。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    /** 用户身份头（由 {@code JwtAuthGatewayFilter} 注入） */
    private static final String HEADER_USER_ID = "X-User-Id";

    /**
     * 按客户端 IP 限流的 Key 解析器（默认主维度）.
     *
     * @return IP 维度 Key
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getRemoteAddress() == null
                    ? "unknown"
                    : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            return Mono.just("gateway:rl:ip:" + ip);
        };
    }

    /**
     * 按用户 ID 限流的 Key 解析器（已认证请求优先使用）.
     *
     * <p>未认证（无 {@code X-User-Id}）时回退到 IP 维度，避免匿名流量绕过限流。
     *
     * @return 用户维度 Key
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(HEADER_USER_ID);
            if (userId != null && !userId.isBlank()) {
                return Mono.just("gateway:rl:user:" + userId);
            }
            String ip = exchange.getRequest().getRemoteAddress() == null
                    ? "unknown"
                    : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            log.debug("未识别用户, 限流回退到 IP 维度: ip={}", ip);
            return Mono.just("gateway:rl:ip:" + ip);
        };
    }
}
