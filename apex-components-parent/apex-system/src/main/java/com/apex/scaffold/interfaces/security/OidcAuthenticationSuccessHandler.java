package com.apex.scaffold.interfaces.security;

import com.apex.scaffold.application.service.TenantGuard;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.UserRepository;
import com.apex.scaffold.domain.service.TokenService;
import com.apex.scaffold.interfaces.config.KeycloakConfig;
import java.net.URI;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * OIDC 认证成功处理器
 *
 * <p>实现 Spring Security WebFlux 的 {@link ServerAuthenticationSuccessHandler}，
 * 处理 Keycloak OIDC 认证成功后的用户信息同步和 JWT Token 生成。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>解析 OIDC 用户信息（sub、email、username）</li>
 *   <li>自动创建或更新本地用户数据</li>
 *   <li>生成本地 JWT Token（accessToken 和 refreshToken）</li>
 *   <li>重定向到前端回调地址，携带 Token 和用户信息</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <pre>
 * 1. 用户通过 Keycloak 完成 OIDC 认证
 * 2. Spring Security OAuth2 Client 接收回调
 * 3. 本处理器提取 OidcUser 信息
 * 4. 根据 keycloak_sub 查询本地用户
 * 5. 如果用户不存在且启用同步，则创建新用户
 * 6. 更新用户最后登录时间
 * 7. 生成 JWT Token
 * 8. 重定向到前端 callback URL，携带 Token 参数
 * </pre>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code keycloak.enabled=true} - 启用 Keycloak 集成</li>
 *   <li>{@code keycloak.user-sync-enabled=true} - 启用用户自动同步</li>
 *   <li>{@code keycloak.frontend-callback-url} - 前端回调地址</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>完全基于 Reactor 响应式编程：
 * <ul>
 *   <li>使用 {@link Mono} 处理异步数据流</li>
 *   <li>使用 {@link BlockingIoSchedulers#jdbc()} 隔离数据库阻塞操作</li>
 *   <li>避免使用 {@code .block()} 阻塞调用</li>
 *   <li>使用 {@code flatMap} 和 {@code map} 进行链式转换</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.4.0
 * @see ServerAuthenticationSuccessHandler
 * @see OidcUser
 * @see KeycloakConfig
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
public class OidcAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    /** 用户仓储 */
    private final UserRepository userRepository;

    /** Token 服务 */
    private final TokenService tokenService;

    /** Keycloak 配置 */
    private final KeycloakConfig keycloakConfig;

    /** 租户写链路守卫：镜像建档前校验租户状态与用户配额 */
    private final TenantGuard tenantGuard;

    /**
     * 处理 OIDC 认证成功事件
     *
     * <p>当用户通过 Keycloak 完成 OIDC 认证后，Spring Security 会调用此方法。
     * 本方法负责提取用户信息、同步到本地数据库、生成 JWT Token，并重定向到前端。
     *
     * <p><strong>处理流程</strong></p>
     * <ol>
     *   <li>验证 Authentication Principal 是否为 {@link OidcUser}</li>
     *   <li>提取 Keycloak Sub（用户唯一标识）、Email、Username</li>
     *   <li>根据 keycloak_sub 查询本地用户</li>
     *   <li>如果用户不存在：
     *     <ul>
     *       <li>检查 {@code keycloak.user-sync-enabled} 配置</li>
     *       <li>启用则创建新用户，禁用则返回 403 错误</li>
     *     </ul>
     *   </li>
     *   <li>更新用户最后登录时间</li>
     *   <li>调用 {@link TokenService} 生成 JWT Token</li>
     *   <li>构造重定向 URL：{@code frontend-callback-url?accessToken=xxx&refreshToken=xxx&userId=xxx}</li>
     *   <li>设置 302 重定向响应</li>
     * </ol>
     *
     * <p><strong>错误处理</strong></p>
     * <ul>
     *   <li>Principal 类型不匹配 → {@link IllegalStateException}</li>
     *   <li>用户同步禁用且用户不存在 → 403 Forbidden</li>
     *   <li>数据库操作失败 → 500 Internal Server Error（由全局异常处理器捕获）</li>
     * </ul>
     *
     * @param webFilterExchange WebFlux 过滤器交换对象，包含 Request 和 Response
     * @param authentication 认证对象，Principal 应为 {@link OidcUser}
     */
    @Override
    @SuppressWarnings("PMD.MethodTooLongRule")
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {

        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            log.error(
                    "OIDC authentication principal is not OidcUser: {}",
                    authentication.getPrincipal().getClass());
            return Mono.error(new IllegalStateException("Invalid OIDC authentication principal"));
        }

        String keycloakSub = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String username = oidcUser.getPreferredUsername();

        log.info("OIDC authentication success: sub={}, email={}, username={}", keycloakSub, email, username);

        return userRepository
                .findByKeycloakSub(keycloakSub)
                .switchIfEmpty(Mono.defer(() -> {
                    if (!keycloakConfig.isUserSyncEnabled()) {
                        return Mono.error(new IllegalStateException(
                                "User not found and user-sync is disabled: keycloakSub=" + keycloakSub));
                    }

                    log.info(
                            "Creating new user from OIDC: keycloakSub={}, email={}, username={}",
                            keycloakSub,
                            email,
                            username);

                    return tenantGuard
                            .assertUserQuota(1)
                            .then(Mono.fromCallable(() -> {
                                        UserEntity user = new UserEntity();
                                        user.setUsername(username != null ? username : email);
                                        user.setEmail(email);
                                        user.setKeycloakSub(keycloakSub);
                                        user.setNickName(
                                                oidcUser.getGivenName() != null ? oidcUser.getGivenName() : username);
                                        user.setPassword("");
                                        user.setStatus(1);
                                        user.setDeleted(0);
                                        user.setCreateTime(LocalDateTime.now());
                                        user.setUpdateTime(LocalDateTime.now());
                                        return user;
                                    })
                                    .subscribeOn(BlockingIoSchedulers.jdbc())
                                    .flatMap(userRepository::save));
                }))
                .flatMap(user -> {
                    log.debug("Updating last login time for user: userId={}", user.getId());

                    user.setUpdateTime(LocalDateTime.now());
                    return userRepository.save(user);
                })
                .flatMap(user -> {
                    String accessToken =
                            tokenService.generateAccessToken(user.getId().toString(), "keycloak-oidc");
                    String refreshToken =
                            tokenService.generateRefreshToken(user.getId().toString(), "keycloak-oidc");

                    log.info(
                            "Generated JWT tokens for OIDC user: userId={}, username={}",
                            user.getId(),
                            user.getUsername());

                    String frontendUrl = UriComponentsBuilder.fromUriString(keycloakConfig.getFrontendCallbackUrl())
                            .queryParam("accessToken", accessToken)
                            .queryParam("refreshToken", refreshToken)
                            .queryParam("userId", user.getId())
                            .toUriString();

                    ServerHttpResponse response =
                            webFilterExchange.getExchange().getResponse();
                    response.setStatusCode(HttpStatus.FOUND);
                    response.getHeaders().setLocation(URI.create(frontendUrl));

                    log.info("Redirecting to frontend: url={}", frontendUrl);
                    return response.setComplete();
                })
                .onErrorResume(e -> {
                    log.error("OIDC authentication failed: {}", e.getMessage(), e);
                    ServerHttpResponse response =
                            webFilterExchange.getExchange().getResponse();
                    response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                    return response.setComplete();
                });
    }
}
