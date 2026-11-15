package com.apex.scaffold.gateway.filter;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * CORS 跨域过滤器配置（全局唯一一处）.
 *
 * <p>对应迁移方案 §5.3 过滤器链第 2 位（order = -100）。跨域策略从主应用
 * {@code CorsConfig} 迁移到网关统一收敛，下游服务不再各自放行 Origin，避免策略漂移。
 *
 * <p>配置项与主应用保持语义一致：
 * <ul>
 *   <li>{@code cors.allow-all-origins} — 是否允许所有来源（默认 false，生产建议关闭）</li>
 *   <li>{@code cors.allowed-origins} — 明确允许的来源域名列表（逗号分隔）</li>
 * </ul>
 *
 * <p>预检（OPTIONS）由 {@link CorsWebFilter} 直接应答，不再透传到下游；
 * 网关默认过滤器再以 {@code DedupeResponseHeader} 去重 Origin 响应头（见 application.yml）。
 *
 * @author Apex Team
 * @since 1.3.1
 * @see CorsConfiguration
 */
@Configuration
public class CorsGlobalConfig implements Ordered {

    /** 过滤器顺序：需要早于 JWT 验签与路由，放行预检不进入业务链 */
    public static final int ORDER = -100;

    /**
     * 创建网关级 CORS 过滤器.
     *
     * <p>策略：开发环境允许所有来源；生产环境仅允许显式配置的域名。
     * 允许携带凭证，暴露链路追踪 / 令牌续期响应头。
     *
     * @param allowedOrigins 允许的来源域名列表（逗号分隔，映射自 {@code cors.allowed-origins}）
     * @param allowAllOrigins 是否允许所有来源域名
     * @return {@link CorsWebFilter} 实例
     * @throws IllegalStateException 生产环境允许所有来源但未配置具体域名时抛出
     */
    @Bean
    public CorsWebFilter gatewayCorsWebFilter(
            @Value("${cors.allowed-origins:}") List<String> allowedOrigins,
            @Value("${cors.allow-all-origins:false}") boolean allowAllOrigins) {
        CorsConfiguration config = new CorsConfiguration();

        if (allowAllOrigins) {
            config.addAllowedOriginPattern("*");
        } else {
            List<String> origins =
                    allowedOrigins.stream().filter(StringUtils::hasText).toList();
            if (origins.isEmpty()) {
                // 防止静默放开所有来源的安全隐患
                throw new IllegalStateException(
                        "cors.allow-all-origins=false 但 cors.allowed-origins 未配置。请配置明确允许的来源域名。");
            }
            origins.forEach(config::addAllowedOriginPattern);
        }

        // 允许的请求头与主应用 CorsConfig 保持一致
        config.setAllowedHeaders(Arrays.asList(
                "Content-Type",
                "Authorization",
                "X-Requested-With",
                "Accept",
                "Origin",
                "X-Timestamp",
                "X-Nonce",
                "X-Client-Type",
                "X-Tenant-Id",
                "Idempotency-Key",
                "X-Device-Id"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setMaxAge(3600L);
        // 允许跨域携带凭证（Cookie / Authorization）
        config.setAllowCredentials(true);
        // 暴露链路追踪与令牌滑动续期响应头
        config.setExposedHeaders(Arrays.asList("X-Trace-Id", "X-Request-Id", "X-New-Access-Token"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
