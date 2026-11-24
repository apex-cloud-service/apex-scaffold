package com.apex.scaffold.authservice.config;

import com.apex.scaffold.auth.application.service.RoleService;
import com.apex.scaffold.auth.config.AuthTrustedProxyProperties;
import com.apex.scaffold.auth.domain.repository.AuthorizationQueryPort;
import com.apex.scaffold.auth.domain.service.IpAccessControlService;
import com.apex.scaffold.auth.domain.service.ResourcePermissionService;
import com.apex.scaffold.auth.domain.service.ResourcePermissionStore;
import com.apex.scaffold.auth.domain.service.TokenService;
import com.apex.scaffold.auth.infrastructure.filter.IpAccessControlFilter;
import com.apex.scaffold.common.audit.AuditLogService;
import com.apex.scaffold.common.config.JwtProperties;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.util.JwtUtil;
import com.apex.scaffold.infrastructure.audit.DefaultAuditLogService;
import com.apex.scaffold.infrastructure.encryption.EncryptionKeyProvider;
import com.apex.scaffold.infrastructure.security.DefaultEncryptionService;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * 认证中心配置类
 *
 * <p>提供认证中心所需的基础Bean配置
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, AuthTrustedProxyProperties.class})
@Import({EncryptionKeyProvider.class, DefaultAuditLogService.class, DefaultEncryptionService.class})
public class AuthCenterConfig {
    /**
     * 资源权限领域服务Bean
     *
     * @param permissionStore 资源授权存取端口
     * @param authorizationQueryPort 授权快照查询端口
     * @return ResourcePermissionService实例
     */
    @Bean
    public ResourcePermissionService domainResourcePermissionService(
            ResourcePermissionStore permissionStore, AuthorizationQueryPort authorizationQueryPort) {
        return new ResourcePermissionService(permissionStore, authorizationQueryPort);
    }

    static List<org.springframework.security.core.authority.SimpleGrantedAuthority> toAuthorities(
            List<com.apex.scaffold.auth.domain.model.entity.RoleEntity> roles) {
        return roles.stream()
                .map(role -> role.getCode())
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.startsWith("ROLE_") ? code : "ROLE_" + code)
                .distinct()
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .toList();
    }

    private static final String OAUTH2_INTROSPECT_PATH = "/oauth2/introspect";
    private static final String OAUTH2_REVOKE_PATH = "/oauth2/revoke";
    private static final String OAUTH2_TOKEN_PATH = "/oauth2/token";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String LOGIN_PATH = "/auth/v1/login";

    /** MFA 二步验证路径：凭挑战凭据交换正式令牌，此时尚无访问令牌，必须匿名可达。 */
    private static final String MFA_VERIFY_PATH = "/auth/v1/mfa/verify";

    /** 自助密码找回：申请恢复单（匿名） */
    private static final String PASSWORD_RECOVERY_REQUESTS_PATH = "/auth/v2/password-recovery/requests";

    /** 自助密码找回：校验验证码（匿名） */
    private static final String PASSWORD_RECOVERY_VERIFICATIONS_PATH = "/auth/v2/password-recovery/verifications";

    /** 自助密码找回：确认重设（匿名） */
    private static final String PASSWORD_RECOVERY_CONFIRMATIONS_PATH = "/auth/v2/password-recovery/confirmations";

    /** 内部契约路由前缀（迁移方案 Phase 2.3）：不经网关，仅集群内服务直连调用 */
    private static final String INTERNAL_API_PREFIX = "/internal/v1/**";

    /** 内部契约路径前缀（不含通配符，用于运行时路径判定，迁移方案 Phase 2.3） */
    private static final String INTERNAL_API_PATH_PREFIX = "/internal/v1/";

    /** 租户透传请求头：由 FeignTenantTraceConfiguration 注入 */
    private static final String TENANT_HEADER = "X-Tenant-Id";

    /**
     * IP访问控制过滤器Bean
     *
     * <p>通过配置类注册（而非组件扫描），保证过滤器仅随应用主上下文装配；
     * 依赖的 {@link IpAccessControlService} 与 {@link AuditLogService} 由容器注入。
     *
     * @param ipAccessControlService IP访问控制服务
     * @param auditLogService 审计日志服务
     * @return IpAccessControlFilter实例
     */
    @Bean
    public IpAccessControlFilter ipAccessControlFilter(
            IpAccessControlService ipAccessControlService, AuditLogService auditLogService) {
        return new IpAccessControlFilter(ipAccessControlService, auditLogService);
    }

    /**
     * 密码编码器Bean
     *
     * <p>使用BCrypt算法加密密码
     *
     * @return PasswordEncoder实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 密码策略领域服务Bean
     *
     * <p>自助密码找回确认重设时执行密码策略校验（长度、字符类型、弱密码字典等）。
     *
     * @param redisTemplate Redis模板（密码历史等校验）
     * @param passwordEncoder 密码编码器（历史密码哈希比对）
     * @return PasswordPolicyService实例
     */
    @Bean
    public com.apex.scaffold.auth.domain.service.PasswordPolicyService passwordPolicyService(
            ReactiveStringRedisTemplate redisTemplate, PasswordEncoder passwordEncoder) {
        return new com.apex.scaffold.auth.domain.service.PasswordPolicyService(redisTemplate, passwordEncoder);
    }

    /**
     * 认证中心Spring Security过滤链Bean
     *
     * <p>禁用CSRF等表单类认证，公开登录与OAuth2端点，其余请求需Bearer Token认证，
     * 并在认证后写入租户上下文。
     *
     * @param http ServerHttpSecurity构建器
     * @param tokens 令牌服务
     * @param roles 角色服务
     * @param jwt JWT配置属性
     * @param environment 环境变量
     * @return SecurityWebFilterChain实例
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            TokenService tokens,
            RoleService roles,
            JwtProperties jwt,
            Environment environment) {
        JwtUtil.applyConfig(jwt);
        AuthenticationWebFilter bearer = bearerAuthenticationFilter(tokens, roles);
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
                                LOGIN_PATH,
                                MFA_VERIFY_PATH,
                                "/auth/v1/refresh",
                                "/.well-known/openid-configuration",
                                "/.well-known/jwks.json",
                                OAUTH2_TOKEN_PATH,
                                OAUTH2_REVOKE_PATH,
                                OAUTH2_INTROSPECT_PATH,
                                PASSWORD_RECOVERY_REQUESTS_PATH,
                                PASSWORD_RECOVERY_VERIFICATIONS_PATH,
                                PASSWORD_RECOVERY_CONFIRMATIONS_PATH,
                                INTERNAL_API_PREFIX,
                                // 容器 HEALTHCHECK 与编排就绪探针访问该端点；若被 401 拦截，
                                // 镜像永远无法进入 healthy，编排会一直判定启动失败。
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info")
                        .permitAll()
                        .anyExchange()
                        .authenticated())
                .addFilterAt(bearer, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(tenantContextFilter(environment), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    private WebFilter tenantContextFilter(Environment environment) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getPath().value();
            if (path.startsWith(INTERNAL_API_PATH_PREFIX)) {
                String tenant = exchange.getRequest().getHeaders().getFirst(TENANT_HEADER);
                if (tenant == null || tenant.isBlank()) {
                    tenant = environment.getProperty("auth-center.login-tenant", "default");
                }
                return TenantContext.withTenant(chain.filter(exchange)).contextWrite(TenantContext.withTenant(tenant));
            }
            if (isLoginOrOauthPath(path)) {
                String tenant = environment.getProperty("auth-center.login-tenant", "default");
                return TenantContext.withTenant(chain.filter(exchange)).contextWrite(TenantContext.withTenant(tenant));
            }
            return ReactiveSecurityContextHolder.getContext()
                    .map(org.springframework.security.core.context.SecurityContext::getAuthentication)
                    .filter(org.springframework.security.core.Authentication::isAuthenticated)
                    .flatMap(authentication -> {
                        Object details = authentication.getDetails();
                        String tenant = details instanceof String value ? value : null;
                        exchange.getAttributes().put("userId", authentication.getName());
                        if (tenant != null && !tenant.isBlank()) {
                            exchange.getAttributes().put("tenantId", tenant);
                        }
                        Mono<Void> next = chain.filter(exchange);
                        if (tenant == null || tenant.isBlank()) {
                            return next.thenReturn(true);
                        }
                        return TenantContext.withTenant(next)
                                .contextWrite(TenantContext.withTenant(tenant))
                                .thenReturn(true);
                    })
                    .switchIfEmpty(Mono.defer(() -> chain.filter(exchange).thenReturn(true)))
                    .then();
        };
    }

    private boolean isLoginOrOauthPath(String path) {
        return List.of(
                        LOGIN_PATH,
                        OAUTH2_TOKEN_PATH,
                        OAUTH2_REVOKE_PATH,
                        OAUTH2_INTROSPECT_PATH,
                        PASSWORD_RECOVERY_REQUESTS_PATH,
                        PASSWORD_RECOVERY_VERIFICATIONS_PATH,
                        PASSWORD_RECOVERY_CONFIRMATIONS_PATH)
                .contains(path);
    }

    private AuthenticationWebFilter bearerAuthenticationFilter(TokenService tokens, RoleService roles) {
        AuthenticationWebFilter bearer = new AuthenticationWebFilter(
                (org.springframework.security.authentication.ReactiveAuthenticationManager) authentication ->
                        tokens.validateToken(authentication.getCredentials().toString())
                                .filter(token -> "ACCESS".equals(token.getTokenType())
                                        && token.getUserId() != null
                                        && token.getUserId() > 0
                                        && token.getTenantId() != null
                                        && !token.getTenantId().isBlank())
                                .switchIfEmpty(Mono.error(new BadCredentialsException("Unauthorized")))
                                .flatMap(token -> Mono.defer(() -> roles.getUserRoles(
                                                token.getUserId(), token.getTenantId())
                                        .filter(role ->
                                                token.getTenantId().equals(role.getTenantId()) && role.isValid())
                                        .collectList()
                                        .map(assignedRoles -> {
                                            UsernamePasswordAuthenticationToken principal =
                                                    new UsernamePasswordAuthenticationToken(
                                                            token.getUserId().toString(),
                                                            "",
                                                            toAuthorities(assignedRoles));
                                            principal.setDetails(token.getTenantId());
                                            return (org.springframework.security.core.Authentication) principal;
                                        })
                                        .transformDeferred(mono -> TenantContext.withTenant(mono))
                                        .contextWrite(TenantContext.withTenant(token.getTenantId()))))
                                .onErrorMap(error -> new BadCredentialsException("Unauthorized")));
        bearer.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        bearer.setServerAuthenticationConverter(exchange -> {
            String header = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
                return Mono.empty();
            }
            return Mono.just(new UsernamePasswordAuthenticationToken("", header.substring(BEARER_PREFIX.length())));
        });
        bearer.setAuthenticationFailureHandler((filterExchange, exception) -> {
            filterExchange.getExchange().getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return filterExchange.getExchange().getResponse().setComplete();
        });
        return bearer;
    }
}
