package com.apex.scaffold.fileservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * 文件服务安全配置。
 *
 * <p>文件服务部署于网关之后，身份由网关验签并透传，服务侧无需再次解析 JWT。
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
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
@EnableWebFluxSecurity
public class FileSecurityConfig {

    /** 内部契约路由前缀（迁移方案 Phase 2.3）：不经网关，仅集群内服务直连调用 */
    private static final String INTERNAL_API_PREFIX = "/internal/v1/**";

    /**
     * 匿名分享兑换与下载路径：仅这两个精确入口对外开放，创建与撤销仍要求已认证身份。
     * 令牌经 POST 请求体传递，租户与文件归属由服务端分享记录确定，不信任任何匿名身份头。
     */
    private static final String SHARE_EXCHANGE_PATH = "/api/v2/file-shares/exchange";

    /** 匿名分享下载路径，见 {@link #SHARE_EXCHANGE_PATH} */
    private static final String SHARE_DOWNLOAD_PATH = "/api/v2/file-shares/download";

    /**
     * 文件服务 Spring Security 过滤链。
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
                                SHARE_EXCHANGE_PATH,
                                SHARE_DOWNLOAD_PATH)
                        .permitAll()
                        .anyExchange()
                        .authenticated())
                .build();
    }
}
