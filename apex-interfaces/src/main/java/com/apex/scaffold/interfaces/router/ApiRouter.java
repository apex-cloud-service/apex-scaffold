package com.apex.scaffold.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.interfaces.handler.AdminUserManagementHandler;
import com.apex.scaffold.interfaces.handler.AuthHandler;
import com.apex.scaffold.interfaces.handler.AuthorizationHandler;
import com.apex.scaffold.interfaces.handler.FileHandler;
import com.apex.scaffold.interfaces.handler.HealthHandler;
import com.apex.scaffold.interfaces.handler.UserHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * API 路由配置（函数式端点）
 *
 * <p>
 * 将所有接口以 {@link RouterFunction} 方式注册，替代基于注解的 Controller
 *
 * <h2>路由分组</h2>
 * <ul>
 * <li>/api/v1/auth - 认证与授权接口</li>
 * <li>/api/v1/user - 用户管理接口</li>
 * <li>/api/v1/file、/api/v1/files - 文件上传下载接口</li>
 * <li>/api/v1/health、/api/v1/info - 健康检查接口</li>
 * <li>/admin-api/v1 - 后台用户管理接口</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Configuration
public class ApiRouter {

    /** 超级管理员用户查询与角色配置路由。 */
    @Bean
    public RouterFunction<ServerResponse> adminUserManagementRoutes(AdminUserManagementHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/staff-roles", handler::roles)
                .path("/admin-api/v1/users", routes -> routes.GET("", handler::list)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::create)
                        .GET("/{userId}", handler::detail)
                        .PUT("/{userId}", contentType(MediaType.APPLICATION_JSON), handler::update)
                        .POST("/{userId}/sessions/revoke", handler::revokeSessions)
                        .PUT("/{userId}/status", contentType(MediaType.APPLICATION_JSON), handler::updateStatus)
                        .PUT("/{userId}/roles", contentType(MediaType.APPLICATION_JSON), handler::replaceRoles))
                .build();
    }

    /**
     * 注册所有 API 路由（/api/v1）。
     *
     * @param authHandler 认证处理器
     * @param authorizationHandler 授权处理器
     * @param userHandler 用户管理处理器
     * @param fileHandler 文件处理器
     * @param healthHandler 健康检查处理器
     * @return 路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> apiRoutes(
            AuthHandler authHandler,
            AuthorizationHandler authorizationHandler,
            UserHandler userHandler,
            FileHandler fileHandler,
            HealthHandler healthHandler) {
        RouterFunctions.Builder routerBuilder = RouterFunctions.route();
        routerBuilder.path(
                "/api/v1",
                api -> buildApiRoutes(api, authHandler, authorizationHandler, userHandler, fileHandler, healthHandler));
        routerBuilder.GET("/", healthHandler::root);
        return routerBuilder.build();
    }

    /**
     * 构建 /api/v1 下的所有通用路由。
     *
     * @param api 路由构建器
     * @param authHandler 认证处理器
     * @param authorizationHandler 授权处理器
     * @param userHandler 用户管理处理器
     * @param fileHandler 文件处理器
     * @param healthHandler 健康检查处理器
     * @return 构建后的路由构建器
     */
    private RouterFunctions.Builder buildApiRoutes(
            RouterFunctions.Builder api,
            AuthHandler authHandler,
            AuthorizationHandler authorizationHandler,
            UserHandler userHandler,
            FileHandler fileHandler,
            HealthHandler healthHandler) {
        return api.GET("/health", healthHandler::health)
                .GET("/info", healthHandler::info)
                .path("/auth", auth -> buildAuthRoutes(auth, authHandler, authorizationHandler))
                .path("/user", user -> buildUserRoutes(user, userHandler))
                .path("/file", file -> buildFileRoutes(file, fileHandler))
                .path(
                        "/files",
                        files -> files.POST(
                                "/generic",
                                contentType(MediaType.MULTIPART_FORM_DATA),
                                fileHandler::uploadGenericFile));
    }

    /**
     * 构建认证与授权相关路由。
     *
     * <p>
     * 路由前缀：/api/v1/auth
     *
     * <p>
     * 提供以下认证接口：
     * <ul>
     *   <li>POST /api/v1/auth/login - APP登录（支持微信登录、创蓝一键登录、短信验证码登录）</li>
     *   <li>POST /api/v1/auth/register - 使用一次性票据完成注册</li>
     *   <li>POST /api/v1/auth/wechat/bind-phone - 注册前验证并绑定微信手机号</li>
     *   <li>POST /api/v1/auth/refresh - 刷新Token</li>
     *   <li>POST /api/v1/auth/logout - 退出登录</li>
     *   <li>POST /api/v1/auth/sms/code - 发送短信验证码（微信短信登录）</li>
     *   <li>POST /api/v1/auth/cl/sms/code - 发送创蓝短信验证码（创蓝短信登录）</li>
     *   <li>POST /api/v1/auth/wx/phone - 绑定微信手机号</li>
     * </ul>
     *
     * @param auth 路由构建器
     * @param authHandler 认证处理器
     * @param authorizationHandler 授权处理器
     * @return 构建后的路由构建器
     */
    private RouterFunctions.Builder buildAuthRoutes(
            RouterFunctions.Builder auth, AuthHandler authHandler, AuthorizationHandler authorizationHandler) {
        auth.POST("/register", accept(MediaType.APPLICATION_JSON), authHandler::register);
        auth.POST("/wechat/bind-phone", accept(MediaType.APPLICATION_JSON), authHandler::bindWechatPhone);
        return auth.POST("/login", accept(MediaType.APPLICATION_JSON), authHandler::login)
                .POST("/refresh", authHandler::refresh)
                .POST("/logout", authHandler::logout)
                .POST("/sms/code", accept(MediaType.APPLICATION_JSON), authHandler::sendSmsCode)
                .POST("/cl/sms/code", accept(MediaType.APPLICATION_JSON), authHandler::sendClSmsCode)
                .POST("/cl/validate-mobile", authHandler::validateMobile)
                .POST("/wx/phone", accept(MediaType.APPLICATION_JSON), authHandler::bindPhone)
                .GET("/roles/my", authorizationHandler::getMyRoles)
                .GET("/permissions/my", authorizationHandler::getMyPermissions)
                .GET("/roles", authorizationHandler::getAllRoles)
                .GET("/permissions", authorizationHandler::getAllPermissions)
                .POST("/roles", accept(MediaType.APPLICATION_JSON), authorizationHandler::createRole)
                .POST("/roles/update", accept(MediaType.APPLICATION_JSON), authorizationHandler::updateRole)
                .DELETE("/roles/{id}", authorizationHandler::deleteRole)
                .POST("/permissions", accept(MediaType.APPLICATION_JSON), authorizationHandler::createPermission)
                .POST("/permissions/update", accept(MediaType.APPLICATION_JSON), authorizationHandler::updatePermission)
                .DELETE("/permissions/{id}", authorizationHandler::deletePermission)
                .POST("/users/{userId}/roles/{roleId}", authorizationHandler::assignRoleToUser)
                .DELETE("/users/{userId}/roles/{roleId}", authorizationHandler::removeRoleFromUser);
    }

    /**
     * 构建用户管理相关路由。
     *
     * <p>
     * 路由前缀：/api/v1/user
     *
     * @param user 路由构建器
     * @param handler 用户管理处理器
     * @return 构建后的路由构建器
     */
    private RouterFunctions.Builder buildUserRoutes(RouterFunctions.Builder user, UserHandler handler) {
        return user.GET("/info", handler::getCurrentUserInfo)
                .POST("/me/deletion-code", handler::sendAccountDeletionCode)
                .DELETE("/me", contentType(MediaType.APPLICATION_JSON), handler::deleteCurrentUser)
                .GET("/{userId}", handler::getUserById)
                .POST("/list", accept(MediaType.APPLICATION_JSON), handler::listUsers);
    }

    /**
     * 构建文件相关路由。
     *
     * <p>
     * 路由前缀：/api/v1/file
     *
     * @param file 路由构建器
     * @param handler 文件处理器
     * @return 构建后的路由构建器
     */
    private RouterFunctions.Builder buildFileRoutes(RouterFunctions.Builder file, FileHandler handler) {
        return file.POST("/upload", handler::upload)
                .GET("/content", handler::content)
                .GET("/url", handler::getUrl)
                .DELETE("/delete", handler::delete)
                .GET("/exists", handler::exists);
    }
}
