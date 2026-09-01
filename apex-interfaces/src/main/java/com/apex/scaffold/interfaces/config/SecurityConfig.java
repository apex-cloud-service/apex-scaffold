package com.apex.scaffold.interfaces.config;

import com.apex.scaffold.common.constant.SystemConstants;
import com.apex.scaffold.interfaces.security.JwtAuthenticationFilter;
import com.apex.scaffold.interfaces.util.MessageUtils;
import com.apex.scaffold.interfaces.util.ResponseUtil;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Spring Security 响应式安全配置类。
 *
 * <p>
 * 配置 JWT 认证、接口授权规则、CORS、CSRF 等安全相关设置。
 * 基于 Spring WebFlux Security 实现全响应式安全防护。
 * 集成了国际化支持，认证失败的错误消息会根据客户端的 Accept-Language 自动切换。
 *
 * <h2>核心安全配置</h2>
 * <ul>
 *   <li><b>JWT 认证</b>：通过 {@link JwtAuthenticationFilter} 实现无状态 Token 认证</li>
 *   <li><b>授权规则</b>：区分公开接口、管理员接口和需认证接口，管理端路径通过
 *       {@link SecurityProperties} 配置，支持 Nacos 动态刷新</li>
 *   <li><b>CSRF 防护</b>：在微信小程序场景下禁用</li>
 *   <li><b>国际化异常处理 + Token 过期识别</b>：认证失败时返回通用
 *       401（{@code auth.login_required}）。Token 过期的专用错误码
 *       40101（{@code auth.token_expired}）由 {@link JwtAuthenticationFilter}
 *       精确处理，确保前端能区分"需重新登录"与"通用未授权"场景</li>
 * </ul>
 *
 * <h2>认证异常响应码策略</h2>
 * <table>
 *   <tr><th>异常类型</th><th>HTTP 状态码</th><th>业务错误码</th><th>消息键</th><th>处理位置</th></tr>
 *   <tr><td>Token 过期</td><td>401</td><td>40101</td>
 *       <td>{@code auth.token_expired}</td><td>{@link JwtAuthenticationFilter}</td></tr>
 *   <tr><td>其他认证异常</td><td>401</td><td>401</td>
 *       <td>{@code auth.login_required}</td><td>{@code authenticationEntryPoint}</td></tr>
 *   <tr><td>权限不足</td><td>403</td><td>403</td>
 *       <td>{@code auth.access_denied}</td><td>{@code accessDeniedHandler}</td></tr>
 * </table>
 *
 * <h2>设计模式</h2>
 * <ul>
 *   <li>过滤器链模式：通过 {@link SecurityWebFilterChain} 编排安全过滤器</li>
 *   <li>策略模式：认证和授权异常处理通过策略接口
 *       Spring Security 的响应式认证入口实现</li>
 * </ul>
 *
 * <h2>公开接口（无需认证）</h2>
 * <ul>
 *   <li>{@code /api/v1/auth/login}、{@code /api/v1/auth/refresh} - 认证接口</li>
 *   <li>{@code /api/v1/auth/sms/code} 等 - 建立登录态所需的短信认证接口</li>
 *   <li>{@code /api/v1/onboarding/policies} - 登录前协议与隐私政策</li>
 *   <li>{@code /api/v1/health}、Actuator liveness/readiness - 健康检查</li>
 *   <li>{@code /swagger-ui/**}、{@code /v3/api-docs/**} - 接口文档</li>
 * </ul>
 *
 * @author Apex Team
 * @version 3.1.0
 * @since 1.0.0
 * @see JwtAuthenticationFilter
 * @see MessageUtils
 * @see SecurityWebFilterChain
 */
@Configuration
@Profile("!it")
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** 是否启用 RBAC 细粒度角色授权（basic 版本关闭，仅保留 JWT 认证）。 */
    @Value("${apex.features.rbac:true}")
    private boolean rbacEnabled;

    /** JWT 认证过滤器，用于验证请求中的 JWT Token */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** 国际化消息工具，用于解析多语言错误消息 */
    private final MessageUtils messageUtils;

    /** 安全配置属性，包含管理端路径和角色等可配置项 */
    private final SecurityProperties securityProperties;

    /**
     * 配置安全过滤器链
     *
     * <p>
     * 构建 Spring Security 的核心过滤器链，包含以下配置：
     *
     * <h3>配置项说明</h3>
     * <ul>
     *   <li><b>CSRF</b>：禁用（微信小程序场景不需要 CSRF 防护）</li>
     *   <li><b>授权规则</b>：仅精确列出的登录前接口匿名放行，管理接口要求管理员角色，
     *       其余业务接口统一要求有效 JWT</li>
     *   <li><b>JWT 过滤器</b>：注册 {@link JwtAuthenticationFilter} 到认证过滤器链位置</li>
     *   <li><b>异常处理（国际化）</b>：分别处理未认证（401）和无权限（403）场景，
     *       通过 {@link MessageUtils} 从 Reactor Context 中获取 Locale 并返回对应语言的消息</li>
     * </ul>
     *
     * @param http {@link ServerHttpSecurity} 配置对象
     * @return 配置完成的 {@link SecurityWebFilterChain} 安全过滤器链
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http.csrf(csrf -> csrf.disable())
                .authorizeExchange(exchanges -> {
                    exchanges
                            .pathMatchers(SystemConstants.PUBLIC_PATHS.toArray(new String[0]))
                            .permitAll()
                            // 本人角色/权限查询会命中后续管理通配规则，需优先按普通登录用户授权。
                            .pathMatchers(SystemConstants.AUTHENTICATED_USER_PATHS.toArray(new String[0]))
                            .authenticated();
                    if (rbacEnabled) {
                        // standard / enterprise：启用 RBAC，管理端路径要求特定角色
                        exchanges
                                .pathMatchers("/internal/v1/interests/**")
                                .hasAnyRole("ADMIN", "INTEREST_GRAPH_EDITOR", "INTEREST_PUBLISHER")
                                .pathMatchers("/admin-api/v1/**")
                                .hasAnyRole(securityProperties.getAdminRoles().toArray(new String[0]))
                                .pathMatchers(securityProperties.getAdminPaths().toArray(new String[0]))
                                .hasAnyRole(securityProperties.getAdminRole(), "SUPER_ADMIN");
                    } else {
                        // basic：关闭 RBAC，管理端路径仅要求已认证（不做角色拦截）
                        exchanges
                                .pathMatchers("/internal/v1/interests/**")
                                .authenticated()
                                .pathMatchers("/admin-api/v1/**")
                                .authenticated()
                                .pathMatchers(securityProperties.getAdminPaths().toArray(new String[0]))
                                .authenticated();
                    }
                    exchanges.anyExchange().authenticated();
                })
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((exchange, ex) -> Mono.deferContextual(ctx -> {
                            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
                            /*
                             * 兜底认证入口：当请求绕过 JwtAuthenticationFilter
                             * 或 JwtAuthenticationFilter 未设置认证信息时触发。
                             * 返回通用 UNAUTHORIZED(401)，Token 过期的
                             * 场景已在 JwtAuthenticationFilter 中通过
                             * TOKEN_EXPIRED(40101) 精确处理。
                             */
                            String msg = messageUtils.get(locale, "auth.login_required");
                            return ResponseUtil.unauthorized(exchange, msg);
                        }))
                        .accessDeniedHandler((exchange, denied) -> Mono.deferContextual(ctx -> {
                            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
                            String msg = messageUtils.get(locale, "auth.access_denied");
                            return ResponseUtil.forbidden(exchange, msg);
                        })))
                .build();
    }
}
