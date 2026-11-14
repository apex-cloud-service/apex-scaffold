package com.apex.scaffold.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.interfaces.handler.AuthHandler;
import com.apex.scaffold.interfaces.handler.AuthorizationHandler;
import com.apex.scaffold.interfaces.handler.HealthHandler;
import com.apex.scaffold.interfaces.handler.UserHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 公共 API 路由配置（/api/v1）。
 *
 * <p>由原 {@code ApiRouter} 按子域拆分而来。</p>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
public class PublicApiRouter {

    /**
     * 注册所有 API 路由（/api/v1）。
     *
     * @param authHandler 认证处理器
     * @param authorizationHandler 授权处理器
     * @param userHandler 用户管理处理器
     * @param healthHandler 健康检查处理器
     * @return 路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/health",
                method = RequestMethod.GET,
                beanClass = HealthHandler.class,
                beanMethod = "health",
                operation =
                        @Operation(
                                summary = "健康检查",
                                description = "返回应用健康状态",
                                tags = {"健康检查"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/info",
                method = RequestMethod.GET,
                beanClass = HealthHandler.class,
                beanMethod = "info",
                operation =
                        @Operation(
                                summary = "查询应用信息",
                                description = "返回应用名称、版本等基本信息",
                                tags = {"健康检查"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/register",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "register",
                operation =
                        @Operation(
                                summary = "用户注册",
                                description = "使用一次性票据完成用户注册",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/wechat/bind-phone",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "bindWechatPhone",
                operation =
                        @Operation(
                                summary = "验证并绑定微信手机号",
                                description = "注册前验证并绑定微信手机号",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/wechat/mp/authorize",
                method = RequestMethod.GET,
                beanClass = AuthHandler.class,
                beanMethod = "mpAuthorize",
                operation =
                        @Operation(
                                summary = "公众号网页授权跳转",
                                description = "构造微信公众号 OAuth2 授权链接并 302 跳转；"
                                        + "授权后微信回跳 redirectUri 并携带 code/state，客户端以 loginType=WX_OAUTH 提交登录",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(responseCode = "302", description = "重定向到微信授权页"),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "缺少 redirectUri 参数或微信渠道未配置",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/login",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "login",
                operation =
                        @Operation(
                                summary = "用户登录",
                                description = "APP 登录，支持微信登录、创蓝一键登录与短信验证码登录",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/refresh",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "refresh",
                operation =
                        @Operation(
                                summary = "刷新令牌",
                                description = "使用刷新令牌换取新的访问令牌",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/logout",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "logout",
                operation =
                        @Operation(
                                summary = "退出登录",
                                description = "注销当前访问令牌并清除会话",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/sms/code",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "sendSmsCode",
                operation =
                        @Operation(
                                summary = "发送短信验证码",
                                description = "发送微信短信登录所需的短信验证码",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/cl/sms/code",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "sendClSmsCode",
                operation =
                        @Operation(
                                summary = "发送创蓝短信验证码",
                                description = "发送创蓝短信登录所需的短信验证码",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/cl/validate-mobile",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "validateMobile",
                operation =
                        @Operation(
                                summary = "本机号校验",
                                description = "校验本机号码是否可用于一键登录",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/wx/phone",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "bindPhone",
                operation =
                        @Operation(
                                summary = "绑定微信手机号",
                                description = "将微信授权手机号绑定到当前账号",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/roles/my",
                method = RequestMethod.GET,
                beanClass = AuthorizationHandler.class,
                beanMethod = "getMyRoles",
                operation =
                        @Operation(
                                summary = "查询我的角色",
                                description = "查询当前登录用户的角色列表",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/permissions/my",
                method = RequestMethod.GET,
                beanClass = AuthorizationHandler.class,
                beanMethod = "getMyPermissions",
                operation =
                        @Operation(
                                summary = "查询我的权限",
                                description = "查询当前登录用户的权限列表",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/roles",
                method = RequestMethod.GET,
                beanClass = AuthorizationHandler.class,
                beanMethod = "getAllRoles",
                operation =
                        @Operation(
                                summary = "查询全部角色",
                                description = "查询系统中全部角色列表",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/permissions",
                method = RequestMethod.GET,
                beanClass = AuthorizationHandler.class,
                beanMethod = "getAllPermissions",
                operation =
                        @Operation(
                                summary = "查询全部权限",
                                description = "查询系统中全部权限列表",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/roles",
                method = RequestMethod.POST,
                beanClass = AuthorizationHandler.class,
                beanMethod = "createRole",
                operation =
                        @Operation(
                                summary = "创建角色",
                                description = "创建新的系统角色",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/roles/update",
                method = RequestMethod.POST,
                beanClass = AuthorizationHandler.class,
                beanMethod = "updateRole",
                operation =
                        @Operation(
                                summary = "更新角色",
                                description = "更新指定角色的信息",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/roles/{id}",
                method = RequestMethod.DELETE,
                beanClass = AuthorizationHandler.class,
                beanMethod = "deleteRole",
                operation =
                        @Operation(
                                summary = "删除角色",
                                description = "根据角色 ID 删除角色",
                                tags = {"认证授权"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "角色 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/permissions",
                method = RequestMethod.POST,
                beanClass = AuthorizationHandler.class,
                beanMethod = "createPermission",
                operation =
                        @Operation(
                                summary = "创建权限",
                                description = "创建新的系统权限",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/permissions/update",
                method = RequestMethod.POST,
                beanClass = AuthorizationHandler.class,
                beanMethod = "updatePermission",
                operation =
                        @Operation(
                                summary = "更新权限",
                                description = "更新指定权限的信息",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/permissions/{id}",
                method = RequestMethod.DELETE,
                beanClass = AuthorizationHandler.class,
                beanMethod = "deletePermission",
                operation =
                        @Operation(
                                summary = "删除权限",
                                description = "根据权限 ID 删除权限",
                                tags = {"认证授权"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "权限 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/users/{userId}/roles/{roleId}",
                method = RequestMethod.POST,
                beanClass = AuthorizationHandler.class,
                beanMethod = "assignRoleToUser",
                operation =
                        @Operation(
                                summary = "为用户分配角色",
                                description = "将指定角色分配给指定用户",
                                tags = {"认证授权"},
                                parameters = {
                                    @Parameter(
                                            name = "userId",
                                            description = "用户 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string")),
                                    @Parameter(
                                            name = "roleId",
                                            description = "角色 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/users/{userId}/roles/{roleId}",
                method = RequestMethod.DELETE,
                beanClass = AuthorizationHandler.class,
                beanMethod = "removeRoleFromUser",
                operation =
                        @Operation(
                                summary = "移除用户角色",
                                description = "移除指定用户的指定角色",
                                tags = {"认证授权"},
                                parameters = {
                                    @Parameter(
                                            name = "userId",
                                            description = "用户 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string")),
                                    @Parameter(
                                            name = "roleId",
                                            description = "角色 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/user/info",
                method = RequestMethod.GET,
                beanClass = UserHandler.class,
                beanMethod = "getCurrentUserInfo",
                operation =
                        @Operation(
                                summary = "获取当前用户信息",
                                description = "获取当前登录用户的详细信息",
                                tags = {"用户管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/user/me/deletion-code",
                method = RequestMethod.POST,
                beanClass = UserHandler.class,
                beanMethod = "sendAccountDeletionCode",
                operation =
                        @Operation(
                                summary = "发送账号注销验证码",
                                description = "向当前账号绑定手机号发送注销二次验证短信",
                                tags = {"用户管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/user/me",
                method = RequestMethod.DELETE,
                beanClass = UserHandler.class,
                beanMethod = "deleteCurrentUser",
                operation =
                        @Operation(
                                summary = "注销当前账号",
                                description = "校验注销验证码并注销当前登录账号",
                                tags = {"用户管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/user/{userId}",
                method = RequestMethod.GET,
                beanClass = UserHandler.class,
                beanMethod = "getUserById",
                operation =
                        @Operation(
                                summary = "查询用户信息",
                                description = "根据用户 ID 查询用户信息",
                                tags = {"用户管理"},
                                parameters = {
                                    @Parameter(
                                            name = "userId",
                                            description = "用户 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/user/list",
                method = RequestMethod.POST,
                beanClass = UserHandler.class,
                beanMethod = "listUsers",
                operation =
                        @Operation(
                                summary = "查询用户列表",
                                description = "按条件查询用户列表",
                                tags = {"用户管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/",
                method = RequestMethod.GET,
                beanClass = HealthHandler.class,
                beanMethod = "root",
                operation =
                        @Operation(
                                summary = "服务根路径",
                                description = "返回服务欢迎信息，用于确认服务可用",
                                tags = {"健康检查"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> apiRoutes(
            AuthHandler authHandler,
            AuthorizationHandler authorizationHandler,
            UserHandler userHandler,
            HealthHandler healthHandler) {
        RouterFunctions.Builder routerBuilder = RouterFunctions.route();
        routerBuilder.path(
                "/api/v1", api -> buildApiRoutes(api, authHandler, authorizationHandler, userHandler, healthHandler));
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
     * @param healthHandler 健康检查处理器
     * @return 构建后的路由构建器
     */
    private RouterFunctions.Builder buildApiRoutes(
            RouterFunctions.Builder api,
            AuthHandler authHandler,
            AuthorizationHandler authorizationHandler,
            UserHandler userHandler,
            HealthHandler healthHandler) {
        return api.GET("/health", healthHandler::health)
                .GET("/info", healthHandler::info)
                .path("/auth", auth -> buildAuthRoutes(auth, authHandler, authorizationHandler))
                .path("/user", user -> buildUserRoutes(user, userHandler));
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
        auth.GET("/wechat/mp/authorize", authHandler::mpAuthorize);
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
}
