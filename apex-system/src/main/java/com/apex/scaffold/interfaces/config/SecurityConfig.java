package com.apex.scaffold.interfaces.config;

import com.apex.scaffold.common.constant.SystemConstants;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.interfaces.security.JwtAuthenticationFilter;
import com.apex.scaffold.interfaces.security.OAuth2ResourceServerFilter;
import com.apex.scaffold.interfaces.security.OidcAuthenticationSuccessHandler;
import com.apex.scaffold.interfaces.util.MessageUtils;
import com.apex.scaffold.interfaces.util.ResponseUtil;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * Spring Security 响应式安全配置类。
 *
 * <p>
 * 配置 JWT 认证、接口授权规则、CORS、CSRF 等安全相关设置。
 * 基于 Spring WebFlux Security 实现全响应式安全防护。
 * 集成了国际化支持，认证失败的错误消息会根据客户端的 Accept-Language 自动切换。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li><b>JWT 认证</b>：通过 {@link JwtAuthenticationFilter} 实现无状态 Token 认证</li>
 *   <li><b>授权规则</b>：区分公开接口、管理员接口和需认证接口，管理端路径通过
 *       {@link SecurityProperties} 配置，支持 Nacos 动态刷新</li>
 *   <li><b>CSRF 防护</b>：在微信小程序场景下禁用</li>
 *   <li><b>国际化异常处理 + Token 过期识别</b>：认证失败时返回通用
 *       401（{@code auth.login_required}）。Token 过期的专用错误码
 *       40101（{@code auth.token_expired}）由 {@link JwtAuthenticationFilter}
 *       精确处理，确保前端能区分"需重新登录"与"通用未授权"场景</li>
 *   <li><b>Keycloak OIDC 登录</b>：当 {@code keycloak.enabled=true} 时启用 OAuth2 登录并绑定成功处理器</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code apex.features.rbac} — 是否启用 RBAC 细粒度角色授权（默认 true，basic 版本关闭）</li>
 *   <li>{@code keycloak.enabled} — 是否启用 Keycloak OIDC 登录（默认 false）</li>
 *   <li>{@code apex.security.admin-paths} / {@code apex.security.admin-roles} — 管理端路径与角色，
 *       由 {@link SecurityProperties} 读取，支持 Nacos 动态刷新</li>
 * </ul>
 *
 * <h2>设计说明</h2>
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
 * <h2>设计说明</h2>
 * <ul>
 *   <li>过滤器链模式：通过 {@link SecurityWebFilterChain} 编排安全过滤器</li>
 *   <li>策略模式：认证和授权异常处理通过策略接口
 *       Spring Security 的响应式认证入口实现</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>{@code /api/v1/auth/login}、{@code /api/v1/auth/refresh} - 认证接口</li>
 *   <li>{@code /api/v1/auth/sms/code} 等 - 建立登录态所需的短信认证接口</li>
 *   <li>{@code /api/v1/onboarding/policies} - 登录前协议与隐私政策</li>
 *   <li>{@code /api/v1/health}、Actuator liveness/readiness - 健康检查</li>
 *   <li>{@code /swagger-ui/**}、{@code /v3/api-docs/**} - 接口文档</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
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

    /** 内部调用声明目标租户的请求头，与共享租户透传约定一致。 */
    private static final String INTERNAL_TENANT_HEADER = "X-Tenant-Id";

    /** 内部组织接口路径匹配器。 */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** 是否启用 RBAC 细粒度角色授权（basic 版本关闭，仅保留 JWT 认证）。 */
    @Value("${apex.features.rbac:true}")
    private boolean rbacEnabled;

    /** JWT 认证过滤器，用于验证请求中的 JWT Token */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** OAuth2资源服务器过滤器，用于验证OAuth2 Token */
    private final OAuth2ResourceServerFilter oauth2ResourceServerFilter;

    /** 国际化消息工具，用于解析多语言错误消息 */
    private final MessageUtils messageUtils;

    /** 安全配置属性，包含管理端路径和角色等可配置项 */
    private final SecurityProperties securityProperties;

    /** Keycloak OIDC 认证成功处理器（可选，仅在 keycloak.enabled=true 时注入） */
    private final ObjectProvider<OidcAuthenticationSuccessHandler> oidcAuthenticationSuccessHandlerProvider;

    /** Keycloak 启用开关 */
    @Value("${keycloak.enabled:false}")
    private boolean keycloakEnabled;

    /**
     * 配置安全过滤器链
     *
     * <p>
     * 构建 Spring Security 的核心过滤器链，包含以下配置：
     *
     * <p><strong>配置项说明</strong></p>
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
        ServerHttpSecurity httpSecurity = http.csrf(csrf -> csrf.disable())
                .authorizeExchange(exchanges -> {
                    exchanges
                            .pathMatchers(SystemConstants.PUBLIC_PATHS.toArray(new String[0]))
                            .permitAll()
                            .pathMatchers(SystemConstants.INTERNAL_ORG_PATHS.toArray(new String[0]))
                            .permitAll()
                            .pathMatchers(SystemConstants.INTERNAL_TENANT_PATHS.toArray(new String[0]))
                            .permitAll()
                            .pathMatchers(SystemConstants.AUTHENTICATED_USER_PATHS.toArray(new String[0]))
                            .authenticated();
                    if (rbacEnabled) {
                        exchanges
                                .pathMatchers("/internal/v1/interests/**")
                                .hasAnyRole("ADMIN", "INTEREST_GRAPH_EDITOR", "INTEREST_PUBLISHER")
                                .pathMatchers("/admin-api/v1/**")
                                .hasAnyRole(securityProperties.getAdminRoles().toArray(new String[0]))
                                .pathMatchers(securityProperties.getAdminPaths().toArray(new String[0]))
                                .hasAnyRole(securityProperties.getAdminRole(), "SUPER_ADMIN");
                    } else {
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
                .addFilterAfter(oauth2ResourceServerFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(internalOrganizationTenantFilter(), SecurityWebFiltersOrder.AUTHORIZATION)
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((exchange, ex) -> Mono.deferContextual(ctx -> {
                            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
                            String msg = messageUtils.get(locale, "auth.login_required");
                            return ResponseUtil.unauthorized(exchange, msg);
                        }))
                        .accessDeniedHandler((exchange, denied) -> Mono.deferContextual(ctx -> {
                            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
                            String msg = messageUtils.get(locale, "auth.access_denied");
                            return ResponseUtil.forbidden(exchange, msg);
                        })));

        OidcAuthenticationSuccessHandler successHandler = oidcAuthenticationSuccessHandlerProvider.getIfAvailable();
        if (keycloakEnabled && successHandler != null) {
            httpSecurity.oauth2Login(oauth2 -> oauth2.authenticationSuccessHandler(successHandler));
        }

        return httpSecurity.build();
    }

    /**
     * 组织目录内部接口的租户桥接过滤器。
     *
     * <p>内部组织接口由集群内其他服务（如 office 组织同步）直连调用，不携带 JWT，
     * 因此无法使用从认证主体解析租户的 {@code @TenantAware} 切面。调用方必须通过
     * {@code X-Tenant-Id} 声明目标租户，本过滤器在匹配 {@link SystemConstants#INTERNAL_ORG_PATHS}
     * 时把该租户写入 Reactor Context，Handler 侧再用 {@link TenantContext#withTenant(reactor.core.publisher.Mono)}
     * 安装到执行线程，保证 MyBatis 租户拦截器能按租户过滤。</p>
     *
     * <p>路径不匹配或请求头缺失时直接透传，交由安全链与 Handler 决定拒绝方式，
     * 避免在过滤器层吞掉错误。</p>
     *
     * @return 内部组织接口租户桥接过滤器
     */
    private WebFilter internalOrganizationTenantFilter() {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getPath().value();
            boolean internalOrgPath =
                    SystemConstants.INTERNAL_ORG_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
            if (!internalOrgPath) {
                return chain.filter(exchange);
            }
            String tenantId = exchange.getRequest().getHeaders().getFirst(INTERNAL_TENANT_HEADER);
            if (tenantId == null || tenantId.isBlank()) {
                return chain.filter(exchange);
            }
            return TenantContext.withTenant(chain.filter(exchange))
                    .contextWrite(TenantContext.withTenant(tenantId.strip()));
        };
    }
}
