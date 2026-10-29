package com.apex.scaffold.interfaces.config;

import com.apex.scaffold.interfaces.handler.KeycloakAdminHandler;
import com.apex.scaffold.interfaces.handler.SocialLoginHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Keycloak 路由配置类
 *
 * <p>使用 WebFlux 函数式路由注册 Keycloak 相关的认证和管理接口。
 * 仅在 {@code keycloak.enabled=true} 时生效，确保集成开关可控。</p>
 *
 * <h2>主要功能</h2>
 * <ul>
 *   <li>注册 OIDC 登录与回调路由，支持重定向到 Keycloak 完成认证</li>
 *   <li>注册社交登录路由（GitHub / Google / 微信等第三方），统一回调处理</li>
 *   <li>注册 Keycloak 用户同步与状态查询管理路由，供管理端调用</li>
 *   <li>通过 {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty} 按 {@code keycloak.enabled} 开关装配</li>
 * </ul>
 *
 * <h2>注册的路由</h2>
 * <ul>
 *   <li><b>认证路由</b> ({@code /api/v1/auth}):
 *     <ul>
 *       <li>{@code GET /oidc/login} - 发起 OIDC 登录，重定向到 Keycloak</li>
 *       <li>{@code GET /social/{provider}/login} - 发起社交登录（GitHub/Google/微信）</li>
 *       <li>{@code GET /oidc/callback} - OIDC 回调处理，接收 Token 并返回前端</li>
 *     </ul>
 *   </li>
 *   <li><b>管理路由</b> ({@code /admin-api/v1/keycloak}):
 *     <ul>
 *       <li>{@code POST /sync/all} - 全量同步 Keycloak 用户到本地数据库</li>
 *       <li>{@code POST /sync/user/{keycloakSub}} - 同步指定 Keycloak 用户</li>
 *       <li>{@code GET /status} - 查询 Keycloak 集成状态</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>配置属性</h2>
 * <ul>
 *   <li>{@code keycloak.enabled} — 是否启用 Keycloak 集成（默认 false，为 true 时本配置类才生效）</li>
 * </ul>
 *
 * <h2>安全说明</h2>
 * <p>认证路由已在 {@link com.apex.scaffold.common.constant.SystemConstants#PUBLIC_PATHS}
 * 中配置为公开访问，管理路由需要管理员权限。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see SocialLoginHandler
 * @see KeycloakAdminHandler
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
public class KeycloakRouterConfig {

    /** 社交登录处理器 */
    private final SocialLoginHandler socialLoginHandler;

    /** Keycloak 管理处理器 */
    private final KeycloakAdminHandler keycloakAdminHandler;

    /**
     * 注册 Keycloak 相关的函数式路由
     *
     * <p>使用 WebFlux {@link RouterFunctions} 注册两组路由：
     * <ul>
     *   <li>认证路由 - 处理 OIDC 和社交登录流程</li>
     *   <li>管理路由 - 提供用户同步和状态查询接口</li>
     * </ul>
     *
     * @return Keycloak 路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/auth/oidc/login",
                method = RequestMethod.GET,
                beanClass = SocialLoginHandler.class,
                beanMethod = "initiateOidcLogin"),
        @RouterOperation(
                path = "/api/v1/auth/social/{provider}/login",
                method = RequestMethod.GET,
                beanClass = SocialLoginHandler.class,
                beanMethod = "initiateSocialLogin"),
        @RouterOperation(
                path = "/api/v1/auth/oidc/callback",
                method = RequestMethod.GET,
                beanClass = SocialLoginHandler.class,
                beanMethod = "handleOidcCallback"),
        @RouterOperation(
                path = "/admin-api/v1/keycloak/sync/all",
                method = RequestMethod.POST,
                beanClass = KeycloakAdminHandler.class,
                beanMethod = "syncAllUsers"),
        @RouterOperation(
                path = "/admin-api/v1/keycloak/sync/user/{keycloakSub}",
                method = RequestMethod.POST,
                beanClass = KeycloakAdminHandler.class,
                beanMethod = "syncUser"),
        @RouterOperation(
                path = "/admin-api/v1/keycloak/status",
                method = RequestMethod.GET,
                beanClass = KeycloakAdminHandler.class,
                beanMethod = "getKeycloakStatus")
    })
    public RouterFunction<ServerResponse> keycloakRoutes() {
        log.info("Registering Keycloak routes");

        return RouterFunctions.route()
                .path("/api/v1/auth", builder -> builder.GET("/oidc/login", socialLoginHandler::initiateOidcLogin)
                        .GET("/social/{provider}/login", socialLoginHandler::initiateSocialLogin)
                        .GET("/oidc/callback", socialLoginHandler::handleOidcCallback))
                .path("/admin-api/v1/keycloak", builder -> builder.POST("/sync/all", keycloakAdminHandler::syncAllUsers)
                        .POST("/sync/user/{keycloakSub}", keycloakAdminHandler::syncUser)
                        .GET("/status", keycloakAdminHandler::getKeycloakStatus))
                .build();
    }
}
