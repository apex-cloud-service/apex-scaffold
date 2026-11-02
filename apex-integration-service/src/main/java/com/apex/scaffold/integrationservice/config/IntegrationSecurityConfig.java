package com.apex.scaffold.integrationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * 集成服务安全配置。
 *
 * <p>集成服务独立验证访问 JWT（见 {@code IdentityRelayAuthenticationFilter}）；
 * 任意网关身份头不作为认证依据。本配置定义 Spring Security 响应式过滤链：
 * <ul>
 *   <li>关闭 CSRF、HTTP Basic、表单登录与登出等单应用形态的能力</li>
 *   <li>放行监控端点、OpenAPI 文档与集群内部契约路由 {@code /internal/v1/**}
 *       （设备内部端点与 webhook 入站是否开放由集成组件自身路由定义）</li>
 *   <li>其余请求一律要求已认证（认证上下文由
 *       {@code IdentityRelayAuthenticationFilter} 依据已验签 JWT 构建）</li>
 *   <li>未认证请求统一返回 HTTP 401</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
@EnableWebFluxSecurity
public class IntegrationSecurityConfig {

    /** 内部契约路由前缀：不经网关，仅集群内服务直连调用 */
    private static final String INTERNAL_API_PREFIX = "/internal/v1/**";

    /**
     * 入站 webhook 端点：外部系统回调免用户认证，安全由 HMAC 签名 + 时间窗校验保证
     * （{@code X-Signature}/{@code X-Timestamp} 请求头，密钥与租户取自连接器配置）。
     */
    private static final String WEBHOOK_INBOUND_PATH = "/integration/v1/webhook/*";

    /**
     * 集成服务 Spring Security 过滤链。
     *
     * @param http ServerHttpSecurity 构建器
     * @return SecurityWebFilterChain 实例
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((exchange, exception) -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/webjars/**",
                                INTERNAL_API_PREFIX,
                                WEBHOOK_INBOUND_PATH)
                        .permitAll()
                        .anyExchange()
                        .authenticated())
                .build();
    }
}
