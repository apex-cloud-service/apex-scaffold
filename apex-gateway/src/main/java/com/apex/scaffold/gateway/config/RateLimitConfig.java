package com.apex.scaffold.gateway.config;

import com.apex.scaffold.gateway.ratelimit.GatewayRateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * 网关限流 Key 解析器配置.
 *
 * <p>提供三级限流维度（优先级：租户 → 用户 → IP），各解析器仅返回<b>所属维度的身份片段</b>：
 * <ul>
 *   <li>{@link #tenantKeyResolver()}：按租户维度限流（{@code X-Tenant-Id} 存在时）</li>
 *   <li>{@link #userKeyResolver()}：按用户维度限流（{@code X-User-Id} 存在时）</li>
 *   <li>{@link #ipKeyResolver()}：按客户端 IP 限流（主维度/匿名回退）</li>
 * </ul>
 *
 * <p><b>维度优先级由过滤器统一编排</b>：{@link com.apex.scaffold.gateway.filter.RequestRateLimitFilter}
 * 依次尝试 租户 → 用户 → IP，取首个非空的维度身份，再拼接
 * {@code apex.gateway.rate-limit.key-prefix} 与请求路径，交由
 * {@link com.apex.scaffold.gateway.ratelimit.TokenBucketRateLimitFilter} 判定。
 * 因此单个解析器在自身维度缺失时返回 {@link Mono#empty()}，而<b>不</b>自行回退。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration
@EnableConfigurationProperties(GatewayRateLimitProperties.class)
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    /** 用户身份头（由 {@link com.apex.scaffold.gateway.filter.UserContextRelayFilter} 注入） */
    private static final String HEADER_USER_ID = "X-User-Id";

    /** 租户身份头（由 {@link com.apex.scaffold.gateway.filter.UserContextRelayFilter} 注入） */
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    /**
     * 按租户 ID 限流的 Key 解析器（优先级最高）.
     *
     * @return 租户维度身份（{@code tenant:<id>}）；缺失时返回空
     */
    @Bean
    public KeyResolver tenantKeyResolver() {
        return exchange -> {
            String tenantId = exchange.getRequest().getHeaders().getFirst(HEADER_TENANT_ID);
            if (tenantId != null && !tenantId.isBlank()) {
                return Mono.just("tenant:" + tenantId);
            }
            return Mono.empty();
        };
    }

    /**
     * 按用户 ID 限流的 Key 解析器（优先级次之）.
     *
     * @return 用户维度身份（{@code user:<id>}）；缺失时返回空
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(HEADER_USER_ID);
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            return Mono.empty();
        };
    }

    /**
     * 按客户端 IP 限流的 Key 解析器（默认兜底维度）.
     *
     * <p>优先读取 {@code X-Forwarded-For} / {@code X-Real-IP}（经代理场景），
     * 最后回退到 socket 远端地址；始终返回非空值，保证匿名流量也被限流覆盖。
     *
     * @return IP 维度身份（{@code ip:<addr>}）
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = com.apex.scaffold.gateway.support.GatewayWebUtil.clientIp(exchange);
            log.debug("限流采用 IP 维度: ip={}", ip);
            return Mono.just("ip:" + ip);
        };
    }
}
