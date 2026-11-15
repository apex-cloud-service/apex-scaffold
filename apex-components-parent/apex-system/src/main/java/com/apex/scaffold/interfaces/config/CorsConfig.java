package com.apex.scaffold.interfaces.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * CORS 跨域配置类
 *
 * <p>配置 Spring WebFlux 的跨域资源共享（CORS）策略，支持动态配置允许的来源域名。
 * 提供开发环境宽松策略和生产环境严格策略两种模式。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code cors.allow-all-origins} — 是否允许所有来源（默认 false，建议生产环境保持关闭）</li>
 *   <li>{@code cors.allowed-origins} — 明确允许的来源域名列表（逗号分隔）</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>当 {@code cors.allow-all-origins=false} 但 {@code cors.allowed-origins} 为空时，
 * 直接抛出 {@link IllegalStateException}，防止静默 fallback 到允许所有来源的安全风险。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>GET — 查询资源</li>
 *   <li>POST — 创建资源</li>
 *   <li>PUT — 更新资源</li>
 *   <li>DELETE — 删除资源</li>
 *   <li>OPTIONS — 预检请求</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>Content-Type — 请求内容类型</li>
 *   <li>Authorization — Bearer JWT Token 认证</li>
 *   <li>X-Requested-With — Ajax 请求标识</li>
 *   <li>Accept — 客户端可接受的响应类型</li>
 *   <li>Origin — 请求来源标识</li>
 *   <li>X-Timestamp — 防重放时间戳</li>
 *   <li>X-Nonce — 防重放随机数</li>
 *   <li>X-Client-Type — 多端终端标识（多会话审计维度）</li>
 *   <li>X-Tenant-Id — 租户标识</li>
 *   <li>Idempotency-Key — 幂等键</li>
 *   <li>X-Device-Id — 设备指纹</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>X-Trace-Id — 链路追踪 ID，用于请求链路关联</li>
 *   <li>X-Request-Id — 请求唯一标识，用于日志关联</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.1.0
 * @since 1.0.0
 * @see CorsWebFilter
 * @see CorsConfiguration
 */
@Configuration
public class CorsConfig {

    /**
     * 创建 CORS Web 过滤器
     *
     * <p>根据配置项动态生成 CORS 策略：
     * <ul>
     *   <li>开发环境：{@code allowAllOrigins=true} 时允许所有来源</li>
     *   <li>生产环境：{@code allowAllOrigins=false} 时只允许配置的特定域名</li>
     * </ul>
     *
     * <p><strong>安全设计说明</strong></p>
     * <ul>
     *   <li>setAllowCredentials(true)：允许跨域请求携带凭证（Cookie/Authorization 头）。
     *       与 allowAllOrigins=true 同时使用时，浏览器会因安全策略拒绝请求，因此仅在白名单模式下生效。</li>
     *   <li>setExposedHeaders：明确暴露 X-Trace-Id 和 X-Request-Id 请求头，
     *       使前端可以读取这些响应头用于调试和链路追踪。</li>
     *   <li>禁止公开暴露敏感响应头（如 Set-Cookie）。</li>
     * </ul>
     *
     * @param allowedOrigins 允许的来源域名列表，多个用逗号分隔，默认空列表
     * @param allowAllOrigins 是否允许所有来源域名，默认 false（生产环境建议保持 false）
     * @return {@link CorsWebFilter} 实例
     * @throws IllegalStateException 生产环境未配置明确的允许来源域名
     */
    @Bean
    public CorsWebFilter corsWebFilter(
            @Value("${cors.allowed-origins:}") List<String> allowedOrigins,
            @Value("${cors.allow-all-origins:false}") boolean allowAllOrigins) {
        CorsConfiguration config = new CorsConfiguration();

        if (allowAllOrigins) {
            config.addAllowedOriginPattern("*");
        } else {
            List<String> origins =
                    allowedOrigins.stream().filter(StringUtils::hasText).toList();
            if (origins.isEmpty()) {
                throw new IllegalStateException(
                        "cors.allow-all-origins=false 但 cors.allowed-origins 未配置。请配置明确允许的来源域名。");
            }
            origins.forEach(config::addAllowedOriginPattern);
        }

        config.setAllowedHeaders(Arrays.asList(
                "Content-Type",
                "Authorization",
                "X-Requested-With",
                "Accept",
                "Origin",
                "X-Timestamp",
                "X-Nonce",
                // 多端接入请求头：终端标识、租户、幂等键与设备指纹（多会话/审计维度）
                "X-Client-Type",
                "X-Tenant-Id",
                "Idempotency-Key",
                "X-Device-Id"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setMaxAge(3600L);

        // 允许跨域请求携带凭证（Cookie/Authorization 头）
        config.setAllowCredentials(true);

        // 暴露链路追踪相关的响应头，便于前端调试和日志关联
        // 暴露 Token 滑动刷新头，让客户端自动更新 AccessToken 实现无感续期
        config.setExposedHeaders(Arrays.asList("X-Trace-Id", "X-Request-Id", "X-New-Access-Token"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
