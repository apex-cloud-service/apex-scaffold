package com.apex.scaffold.authcenter.config;

import com.apex.scaffold.authcenter.domain.service.IpAccessControlService;
import com.apex.scaffold.authcenter.domain.service.TokenService;
import com.apex.scaffold.authcenter.infrastructure.filter.IpAccessControlFilter;
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
import reactor.core.publisher.Mono;

/**
 * 认证中心配置类
 *
 * <p>提供认证中心所需的基础Bean配置
 *
 * @author Apex Team
 * @since 2026-09-20
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@Import({EncryptionKeyProvider.class, DefaultAuditLogService.class, DefaultEncryptionService.class})
public class AuthCenterConfig {
    private static final String OAUTH2_INTROSPECT_PATH = "/oauth2/introspect";
    private static final String OAUTH2_REVOKE_PATH = "/oauth2/revoke";
    private static final String OAUTH2_TOKEN_PATH = "/oauth2/token";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String LOGIN_PATH = "/auth/v1/login";

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

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http, TokenService tokens, JwtProperties jwt, Environment environment) {
        JwtUtil.applyConfig(jwt);
        AuthenticationWebFilter bearer = new AuthenticationWebFilter(
                (org.springframework.security.authentication.ReactiveAuthenticationManager) authentication ->
                        tokens.validateToken(authentication.getCredentials().toString())
                                .filter(token -> "ACCESS".equals(token.getTokenType()))
                                .switchIfEmpty(Mono.error(new BadCredentialsException("Unauthorized")))
                                .map(token -> {
                                    UsernamePasswordAuthenticationToken principal =
                                            new UsernamePasswordAuthenticationToken(
                                                    token.getUserId().toString(), "", List.of());
                                    principal.setDetails(token.getTenantId());
                                    return (org.springframework.security.core.Authentication) principal;
                                })
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
                                "/auth/v1/refresh",
                                "/.well-known/openid-configuration",
                                "/.well-known/jwks.json",
                                OAUTH2_TOKEN_PATH,
                                OAUTH2_REVOKE_PATH,
                                OAUTH2_INTROSPECT_PATH)
                        .permitAll()
                        .anyExchange()
                        .authenticated())
                .addFilterAt(bearer, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(
                        (exchange, chain) -> {
                            if (List.of(LOGIN_PATH, OAUTH2_TOKEN_PATH, OAUTH2_REVOKE_PATH, OAUTH2_INTROSPECT_PATH)
                                    .contains(exchange.getRequest().getPath().value())) {
                                String tenant = environment.getProperty("auth-center.login-tenant", "default");
                                return chain.filter(exchange).contextWrite(TenantContext.withTenant(tenant));
                            }
                            return ReactiveSecurityContextHolder.getContext()
                                    .map(context -> context.getAuthentication().getDetails())
                                    .ofType(String.class)
                                    .flatMap(tenant -> chain.filter(exchange)
                                            .contextWrite(TenantContext.withTenant(tenant))
                                            .thenReturn(true))
                                    .switchIfEmpty(Mono.defer(
                                            () -> chain.filter(exchange).thenReturn(true)))
                                    .then();
                        },
                        SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
