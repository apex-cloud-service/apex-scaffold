package com.apex.scaffold.workflowservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * 工作流服务安全配置。
 *
 * <p>工作流服务通过 {@code IdentityRelayAuthenticationFilter} 独立验证 ACCESS JWT，
 * 认证身份取自已验签声明，不信任中继身份头。
 * 本配置定义 Spring Security 响应式过滤链：
 * <ul>
 *   <li>关闭 CSRF、HTTP Basic、表单登录与登出等单应用形态的能力</li>
 *   <li>放行监控端点与 OpenAPI 文档</li>
 *   <li>其余请求（包括 {@code /internal/v1/**}）一律要求已认证</li>
 *   <li>未认证请求统一返回 HTTP 401</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Configuration
@EnableWebFluxSecurity
public class WorkflowSecurityConfig {

    /**
     * 工作流服务 Spring Security 过滤链。
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
                                "/actuator/**", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**")
                        .permitAll()
                        .anyExchange()
                        .authenticated())
                .build();
    }
}
