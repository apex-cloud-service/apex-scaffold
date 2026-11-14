package com.apex.scaffold.knowledgeservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * 知识库服务安全配置。
 *
 * <p>知识库服务独立验证访问 JWT（{@code KnowledgeIdentityRelayAuthenticationFilter}），
 * 身份头不作为认证依据。本配置定义 Spring Security 响应式过滤链：
 * <ul>
 *   <li>关闭 CSRF、HTTP Basic、表单登录与登出等单应用形态的能力</li>
 *   <li>放行监控端点与 OpenAPI 文档</li>
 *   <li>其余请求一律要求已认证（认证上下文由独立验签过滤器构建）</li>
 *   <li>未认证请求统一返回 HTTP 401</li>
 * </ul>
 * 细粒度权限由 {@code KnowledgeSecurityAspect} 在 Handler 边界按权限码校验。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
@EnableWebFluxSecurity
public class KnowledgeSecurityConfig {

    /**
     * 知识库服务 Spring Security 过滤链。
     *
     * @param http ServerHttpSecurity 构建器
     * @return SecurityWebFilterChain 实例
     */
    @Bean
    public SecurityWebFilterChain knowledgeSecurityWebFilterChain(ServerHttpSecurity http) {
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
