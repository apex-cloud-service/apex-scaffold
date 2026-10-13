package com.apex.scaffold.officeservice.config;

import com.apex.scaffold.common.config.JwtProperties;
import com.apex.scaffold.common.util.JwtUtil;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * 办公服务安全配置。
 *
 * <p>办公服务部署于网关之后，身份由网关验签并透传，服务侧无需再次解析 JWT。
 * 本配置定义 Spring Security 响应式过滤链：
 * <ul>
 *   <li>关闭 CSRF、HTTP Basic、表单登录与登出等单应用形态的能力</li>
 *   <li>放行监控端点、OpenAPI 文档与集群内部契约路由 {@code /internal/v1/**}</li>
 *   <li>其余请求一律要求已认证（认证上下文由
 *       {@code IdentityRelayAuthenticationFilter} 依据网关注入的可信头构建）</li>
 *   <li>未认证请求统一返回 HTTP 401</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class OfficeSecurityConfig {

    /** 内部契约路由前缀（迁移方案 Phase 2.3）：不经网关，仅集群内服务直连调用 */
    private static final String INTERNAL_API_PREFIX = "/internal/v1/**";

    /**
     * 办公服务 Spring Security 过滤链。
     *
     * <p>同时在此完成 JWT 运行时配置注入：将 YAML 中的 {@code jwt.*} 属性应用到静态工具类
     * {@link JwtUtil}，使办公服务自包含的令牌签发/解析链路（{@code OfficeTokenServiceImpl}）
     * 在首次业务调用前即具备正确的密钥与过期时间配置。此方式与认证中心
     * {@code AuthCenterConfig} 保持一致。</p>
     *
     * @param http ServerHttpSecurity 构建器
     * @param jwt  JWT 配置属性
     * @return SecurityWebFilterChain 实例
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, JwtProperties jwt) {
        JwtUtil.applyConfig(jwt);
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
                                INTERNAL_API_PREFIX)
                        .permitAll()
                        .anyExchange()
                        .authenticated())
                .build();
    }
}
