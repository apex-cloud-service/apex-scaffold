package com.apex.scaffold.interfaces.test;

import com.apex.scaffold.common.constant.SystemConstants;
import com.apex.scaffold.common.util.JsonUtil;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * ItProfileSecurityConfiguration 测试支持配置。
 *
 * <p>为相关集成测试提供隔离的依赖、环境和安全配置，避免测试行为影响生产配置。</p>
 *
 * @author Apex Team
 */
@Configuration
@Profile("it")
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
class ItProfileSecurityConfiguration {

    /**
     * 执行 {@code securityWebFilterChain} 操作。
     * @param http 参数 {@code http}
     * @param itJwtBypassFilter 参数 {@code itJwtBypassFilter}
     * @return 操作结果
     */
    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, WebFilter itJwtBypassFilter) {
        return http.csrf(csrf -> csrf.disable())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(SystemConstants.PUBLIC_PATHS.toArray(new String[0]))
                        .permitAll()
                        .anyExchange()
                        .permitAll())
                .addFilterAt(itJwtBypassFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    /**
     * 执行 {@code itJwtBypassFilter} 操作。
     * @return 操作结果
     */
    @Bean
    WebFilter itJwtBypassFilter() {
        return (exchange, chain) -> {
            String token = extractBearerToken(exchange);
            if (token == null) {
                return chain.filter(exchange);
            }
            String openId = extractSubject(token);
            if (openId == null || openId.isBlank()) {
                return chain.filter(exchange);
            }
            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(openId, token, Collections.emptyList());
            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                            Mono.just(new SecurityContextImpl(authentication))));
        };
    }

    /**
     * 执行 {@code extractBearerToken} 操作。
     * @param exchange 参数 {@code exchange}
     * @return 字符串结果
     */
    private static String extractBearerToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring("Bearer ".length()).trim();
    }

    /**
     * 执行 {@code extractSubject} 操作。
     * @param token 参数 {@code token}
     * @return 字符串结果
     */
    private static String extractSubject(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        byte[] payloadBytes;
        try {
            payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
        Map payload;
        try {
            payload = JsonUtil.fromJson(payloadJson, Map.class);
        } catch (Exception e) {
            return null;
        }
        Object tokenType = payload.get("type");
        if (tokenType != null && !"access".equals(String.valueOf(tokenType))) {
            return null;
        }
        Object subject = payload.get("sub");
        return subject == null ? null : String.valueOf(subject);
    }
}
