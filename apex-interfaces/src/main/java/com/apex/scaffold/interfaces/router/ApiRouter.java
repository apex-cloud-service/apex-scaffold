package com.apex.scaffold.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.interfaces.handler.AdminUserManagementHandler;
import com.apex.scaffold.interfaces.handler.ApiCallLogHandler;
import com.apex.scaffold.interfaces.handler.AuditLogHandler;
import com.apex.scaffold.interfaces.handler.AuthHandler;
import com.apex.scaffold.interfaces.handler.AuthorizationHandler;
import com.apex.scaffold.interfaces.handler.CaptchaHandler;
import com.apex.scaffold.interfaces.handler.ConfigHandler;
import com.apex.scaffold.interfaces.handler.DatabaseConfigHandler;
import com.apex.scaffold.interfaces.handler.DeptHandler;
import com.apex.scaffold.interfaces.handler.DictHandler;
import com.apex.scaffold.interfaces.handler.ExcelExportHandler;
import com.apex.scaffold.interfaces.handler.FileHandler;
import com.apex.scaffold.interfaces.handler.HealthHandler;
import com.apex.scaffold.interfaces.handler.JobHandler;
import com.apex.scaffold.interfaces.handler.LoginLogHandler;
import com.apex.scaffold.interfaces.handler.MenuHandler;
import com.apex.scaffold.interfaces.handler.MonitorDashboardHandler;
import com.apex.scaffold.interfaces.handler.NotificationSseHandler;
import com.apex.scaffold.interfaces.handler.OfficeHandler;
import com.apex.scaffold.interfaces.handler.OnlineUserHandler;
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

    /** 办公平台统一认证与状态路由。 */
    @Bean
    @org.springdoc.core.annotations.RouterOperations({
        @org.springdoc.core.annotations.RouterOperation(
                path = "/api/v1/office/status",
                method = org.springframework.web.bind.annotation.RequestMethod.GET,
                beanClass = OfficeHandler.class,
                beanMethod = "status"),
        @org.springdoc.core.annotations.RouterOperation(
                path = "/api/v1/office/login",
                method = org.springframework.web.bind.annotation.RequestMethod.GET,
                beanClass = OfficeHandler.class,
                beanMethod = "start"),
        @org.springdoc.core.annotations.RouterOperation(
                path = "/api/v1/office/callback",
                method = org.springframework.web.bind.annotation.RequestMethod.GET,
                beanClass = OfficeHandler.class,
                beanMethod = "callback")
    })
    public RouterFunction<ServerResponse> officeRoutes(OfficeHandler handler) {
        return RouterFunctions.route()
                .GET("/api/v1/office/status", handler::status)
                .GET("/api/v1/office/login", handler::start)
                .GET("/api/v1/office/callback", handler::callback)
                .build();
    }

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
     * 系统管理-菜单路由（/admin-api/v1/menus）。
     *
     * <p>提供菜单树的查询、详情、创建、更新、删除与启停管理端点，
     * 所有端点经 {@code RequirePermissionAspect} 校验 {@code system:menu:*} 权限。</p>
     *
     * @param handler 系统菜单处理器
     * @return 菜单管理路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> adminMenuRoutes(MenuHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/menus", routes -> routes.GET("", handler::tree)
                        .GET("/{menuId}", handler::detail)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::create)
                        .PUT("/{menuId}", contentType(MediaType.APPLICATION_JSON), handler::update)
                        .DELETE("/{menuId}", handler::delete)
                        .PUT("/{menuId}/status", contentType(MediaType.APPLICATION_JSON), handler::updateStatus))
                .build();
    }

    /**
     * 系统管理-字典路由（/admin-api/v1/dict）。
     *
     * <p>提供字典类型与字典数据的查询、创建、更新、删除与启停管理端点，
     * 所有端点经 {@code RequirePermissionAspect} 校验 {@code system:dict:*} 权限。</p>
     *
     * @param handler 系统字典处理器
     * @return 字典管理路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> adminDictRoutes(DictHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/dict/types", types -> types.GET("", handler::listTypes)
                        .GET("/{id}", handler::getType)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::createType)
                        .PUT("/{id}", contentType(MediaType.APPLICATION_JSON), handler::updateType)
                        .DELETE("/{id}", handler::deleteType)
                        .PUT("/{id}/status", contentType(MediaType.APPLICATION_JSON), handler::updateTypeStatus))
                .path("/admin-api/v1/dict/data", data -> data.GET("", handler::listData)
                        .GET("/{id}", handler::getData)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::createData)
                        .PUT("/{id}", contentType(MediaType.APPLICATION_JSON), handler::updateData)
                        .DELETE("/{id}", handler::deleteData)
                        .PUT("/{id}/status", contentType(MediaType.APPLICATION_JSON), handler::updateDataStatus))
                .build();
    }

    /**
     * 系统管理-登录日志路由（/admin-api/v1/login-logs）。
     *
     * <p>提供登录日志的分页回溯查询端点，经 {@code RequirePermissionAspect}
     * 校验 {@code system:login-log:list} 权限。</p>
     *
     * @param handler 登录日志处理器
     * @return 登录日志管理路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> adminLoginLogRoutes(LoginLogHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/login-logs", handler::list)
                .build();
    }

    /**
     * 系统管理-审计日志路由（/admin-api/v1/audit-logs）。
     *
     * <p>提供操作审计日志的分页查询端点，经 {@code RequirePermissionAspect}
     * 校验 {@code system:audit-log:list} 权限（V4 迁移预置）。</p>
     *
     * @param handler 审计日志处理器
     * @return 审计日志管理路由函数
     * @since 2.2.0
     */
    @Bean
    public RouterFunction<ServerResponse> adminAuditLogRoutes(AuditLogHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/audit-logs", handler::list)
                .build();
    }

    /**
     * 系统管理-API调用日志路由（/admin-api/v1/api-call-logs）。
     *
     * <p>提供对外部 HTTP 服务调用日志（{@code sys_api_call_log} 表）的分页查询端点，
     * 经 {@code RequirePermissionAspect} 校验 {@code system:api-call-log:list} 权限
     * （V13 迁移预置）。</p>
     *
     * @param handler API 调用日志处理器
     * @return API 调用日志管理路由函数
     * @since 2.3.0
     */
    @Bean
    public RouterFunction<ServerResponse> adminApiCallLogRoutes(ApiCallLogHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/api-call-logs", handler::list)
                .build();
    }
    /**
     * 系统管理-部门管理路由（/admin-api/v1/depts）。
     *
     * <p>提供部门树与 CRUD 端点（C3 数据权限基础），经
     * {@code RequirePermissionAspect} 校验 {@code system:dept:*} 权限（V6 迁移预置）。</p>
     *
     * @param handler 部门管理处理器
     * @return 部门管理路由函数
     * @since 2.2.0
     */
    @Bean
    public RouterFunction<ServerResponse> adminDeptRoutes(DeptHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/depts", handler::listTree)
                .GET("/admin-api/v1/depts/{id}", handler::detail)
                .POST("/admin-api/v1/depts", handler::create)
                .PUT("/admin-api/v1/depts/{id}", handler::update)
                .DELETE("/admin-api/v1/depts/{id}", handler::delete)
                .build();
    }
    /**
     * 数据导出路由（/admin-api/v1/export/users）。
     *
     * <p>提供 Excel 导出端点（C5），经 {@code RequirePermissionAspect}
     * 校验 {@code system:user:list} 权限。</p>
     *
     * @param handler Excel 导出处理器
     * @return 导出路由函数
     * @since 2.2.0
     */
    @Bean
    public RouterFunction<ServerResponse> adminExportRoutes(ExcelExportHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/export/users", handler::exportUsers)
                .build();
    }

    /**
     * 站内通知 SSE 路由（/api/v1/notifications/stream）。
     *
     * <p>提供实时通知推送端点（C6），基于认证 Principal 定向推送。</p>
     *
     * @param handler SSE 通知处理器
     * @return SSE 推送路由函数
     * @since 2.2.0
     */
    @Bean
    public RouterFunction<ServerResponse> notificationSseRoutes(NotificationSseHandler handler) {
        return RouterFunctions.route()
                .GET("/api/v1/notifications/stream", handler::stream)
                .build();
    }
    /**
     * 系统管理-在线用户路由（/admin-api/v1/online-users）。
     *
     * <p>提供在线用户列表查询端点（C4），经 {@code RequirePermissionAspect}
     * 校验 {@code system:online-user:list} 权限（V5 迁移预置）。</p>
     *
     * @param handler 在线用户处理器
     * @return 在线用户管理路由函数
     * @since 2.2.0
     */
    @Bean
    public RouterFunction<ServerResponse> adminOnlineUserRoutes(OnlineUserHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/online-users", handler::list)
                .build();
    }

    /**
     * 监控大盘路由（/admin-api/v1/monitor）。
     *
     * <p>提供监控概览、系统资源、中间件、治理与核心业务五类监控聚合数据，
     * 经 {@code RequirePermissionAspect} 校验 {@code system:monitor:view} 权限。</p>
     *
     * @param handler 监控大盘处理器
     * @return 监控路由函数
     * @since 2.3.0
     */
    @Bean
    public RouterFunction<ServerResponse> adminMonitorRoutes(MonitorDashboardHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/monitor", routes -> routes.GET("/overview", handler::overview)
                        .GET("/system", handler::system)
                        .GET("/middleware", handler::middleware)
                        .GET("/governance", handler::governance)
                        .GET("/business", handler::business))
                .build();
    }

    /**
     * 图形验证码路由（/api/v1/auth/captcha）。
     *
     * <p>提供验证码获取与一次性校验端点，供登录场景防自动化爆破使用。</p>
     *
     * @param handler 图形验证码处理器
     * @return 验证码路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> captchaRoutes(CaptchaHandler handler) {
        return RouterFunctions.route()
                .GET("/api/v1/auth/captcha", handler::generate)
                .POST("/api/v1/auth/captcha/verify", contentType(MediaType.APPLICATION_JSON), handler::verify)
                .build();
    }

    /**
     * 系统管理-定时任务路由（/admin-api/v1/jobs）。
     *
     * <p>提供定时任务的查询、创建、更新、删除、启停、手动触发与
     * 执行日志端点，所有端点经 {@code RequirePermissionAspect} 校验
     * {@code system:job:*} 权限。</p>
     *
     * @param handler 定时任务处理器
     * @return 定时任务管理路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> adminJobRoutes(JobHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/jobs", routes -> routes.GET("", handler::list)
                        .GET("/page", handler::page)
                        .GET("/{id}", handler::detail)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::create)
                        .PUT("/{id}", contentType(MediaType.APPLICATION_JSON), handler::update)
                        .DELETE("/{id}", handler::delete)
                        .PUT("/{id}/status", contentType(MediaType.APPLICATION_JSON), handler::updateStatus)
                        .POST("/{id}/run", handler::runOnce)
                        .GET("/{id}/logs", handler::logs))
                .build();
    }

    /**
     * 系统管理-数据库配置路由（/admin-api/v1/database）。
     *
     * <p>提供数据库连接测试、部署配置生成和当前数据库信息查询端点，
     * 所有端点经 {@code RequirePermissionAspect} 校验 {@code system:database:*} 权限。</p>
     *
     * @param handler 数据库配置处理器
     * @return 数据库配置管理路由函数
     * @since 1.2.0
     */
    @Bean
    public RouterFunction<ServerResponse> adminDatabaseConfigRoutes(DatabaseConfigHandler handler) {
        return RouterFunctions.route()
                .POST("/admin-api/v1/database/test", contentType(MediaType.APPLICATION_JSON), handler::testConnection)
                .POST(
                        "/admin-api/v1/database/generate-config",
                        contentType(MediaType.APPLICATION_JSON),
                        handler::generateConfig)
                .GET("/admin-api/v1/database/current", handler::getCurrentInfo)
                .build();
    }

    /**
     * 系统管理-参数配置路由（/admin-api/v1/configs）。
     *
     * <p>提供参数配置的查询、详情、创建、更新、删除与启停管理端点，
     * 所有端点经 {@code RequirePermissionAspect} 校验 {@code system:config:*} 权限。</p>
     *
     * @param handler 系统参数配置处理器
     * @return 参数配置管理路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> adminConfigRoutes(ConfigHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/configs", routes -> routes.GET("", handler::list)
                        .GET("/{id}", handler::detail)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::create)
                        .PUT("/{id}", contentType(MediaType.APPLICATION_JSON), handler::update)
                        .DELETE("/{id}", handler::delete)
                        .PUT("/{id}/status", contentType(MediaType.APPLICATION_JSON), handler::updateStatus))
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

    /**
     * 统一搜索路由（/admin-api/v1/search）。
     *
     * <p>提供全文搜索和索引管理功能。</p>
     *
     * @param handler 统一搜索处理器
     * @return 搜索路由函数
     * @since 1.3.0
     */
    @Bean
    public RouterFunction<ServerResponse> unifiedSearchRoutes(
            com.apex.scaffold.interfaces.handler.UnifiedSearchHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/search", handler::search)
                .POST("/admin-api/v1/search/rebuild", handler::rebuildIndex)
                .build();
    }

    /**
     * 运行时日志路由（/admin-api/v1/logs/runtime）。
     *
     * <p>提供运行时日志查询功能。</p>
     *
     * @param handler 运行时日志处理器
     * @return 日志路由函数
     * @since 1.3.0
     */
    @Bean
    public RouterFunction<ServerResponse> runtimeLogRoutes(
            com.apex.scaffold.interfaces.handler.RuntimeLogHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/logs/runtime", handler::queryLogs)
                .GET("/admin-api/v1/logs/runtime/count", handler::countLogs)
                .build();
    }

    /**
     * 业务日志统一查询路由（/admin-api/v1/logs/business）。
     *
     * <p>整合登录日志、操作日志、API调用日志，提供统一查询接口。</p>
     *
     * @param handler 业务日志处理器
     * @return 日志路由函数
     * @since 1.3.0
     */
    @Bean
    public RouterFunction<ServerResponse> businessLogRoutes(
            com.apex.scaffold.interfaces.handler.UnifiedBusinessLogHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/logs/business", handler::queryLogs)
                .GET("/admin-api/v1/logs/business/count", handler::countLogs)
                .build();
    }
}
