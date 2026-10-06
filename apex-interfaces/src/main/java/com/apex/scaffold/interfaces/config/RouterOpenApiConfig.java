package com.apex.scaffold.interfaces.config;

import com.apex.scaffold.application.dto.AccountDeletionRequest;
import com.apex.scaffold.application.dto.FileUploadResult;
import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.dto.MobileValidateResponse;
import com.apex.scaffold.application.dto.PageRequest;
import com.apex.scaffold.application.dto.PageResponse;
import com.apex.scaffold.application.dto.PermissionDTO;
import com.apex.scaffold.application.dto.PermissionRequest;
import com.apex.scaffold.application.dto.RefreshTokenRequest;
import com.apex.scaffold.application.dto.RegisterRequest;
import com.apex.scaffold.application.dto.RoleDTO;
import com.apex.scaffold.application.dto.RoleRequest;
import com.apex.scaffold.application.dto.SendSmsRequest;
import com.apex.scaffold.application.dto.UserDTO;
import com.apex.scaffold.application.dto.WechatBindPhoneRequest;
import com.apex.scaffold.application.dto.WxPhoneRequest;
import com.apex.scaffold.application.dto.admin.AdminRoleResponse;
import com.apex.scaffold.application.dto.admin.AdminUserCreateRequest;
import com.apex.scaffold.application.dto.admin.AdminUserDetailResponse;
import com.apex.scaffold.application.dto.admin.AdminUserRolesRequest;
import com.apex.scaffold.application.dto.admin.AdminUserStatusRequest;
import com.apex.scaffold.application.dto.admin.AdminUserUpdateRequest;
import com.apex.scaffold.interfaces.dto.FileUploadRequestDoc;
import com.apex.scaffold.interfaces.dto.GenericFileUploadRequestDoc;
import com.apex.scaffold.interfaces.handler.AdminUserManagementHandler;
import com.apex.scaffold.interfaces.handler.ApiCallLogHandler;
import com.apex.scaffold.interfaces.handler.AuditLogHandler;
import com.apex.scaffold.interfaces.handler.AuthHandler;
import com.apex.scaffold.interfaces.handler.AuthorizationHandler;
import com.apex.scaffold.interfaces.handler.CaptchaHandler;
import com.apex.scaffold.interfaces.handler.ConfigHandler;
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
import com.apex.scaffold.interfaces.handler.OnlineUserHandler;
import com.apex.scaffold.interfaces.handler.UserHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
 * 函数式端点 OpenAPI 配置
 *
 * <p>用于让 SpringDoc 正确识别 RouterFunction 注册的接口并生成 Swagger 文档。
 * 除精确声明的登录前公开接口外，业务接口统一声明 Bearer JWT 认证要求。
 *
 * <h2>关于 @RouterOperations 注解的位置</h2>
 * <p>SpringDoc 要求 {@code @RouterOperations} 注解必须放在 {@code @Bean} 方法上才能被识别。
 * 为避免方法过长（P3C 规范 MethodTooLongRule），将路由按模块分组到多个 @Bean 方法中。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Configuration
public class RouterOpenApiConfig {

    /** 认证模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/auth/login",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "login",
                operation =
                        @Operation(
                                operationId = "authLogin",
                                summary = "用户登录",
                                description = "统一认证入口，支持微信授权码、手机号短信验证码和手机号一键登录。",
                                security = {},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema = @Schema(implementation = LoginRequest.class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        LoginResponse.class))))),
        @RouterOperation(
                path = "/api/v1/auth/refresh",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "refresh",
                operation =
                        @Operation(
                                operationId = "authRefresh",
                                summary = "刷新Token",
                                security = {},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        RefreshTokenRequest.class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        LoginResponse.class))))),
        @RouterOperation(
                path = "/api/v1/auth/logout",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "logout",
                operation =
                        @Operation(operationId = "authLogout", summary = "退出登录", description = "使当前Token失效，清除用户登录态")),
        @RouterOperation(
                path = "/api/v1/auth/register",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "register",
                operation =
                        @Operation(
                                operationId = "authRegister",
                                summary = "用户注册",
                                description = "使用登录或微信绑定流程签发的一次性票据完成注册",
                                security = {},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        RegisterRequest.class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        LoginResponse.class))))),
        @RouterOperation(
                path = "/api/v1/auth/wechat/bind-phone",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "bindWechatPhone",
                operation =
                        @Operation(
                                operationId = "bindWechatPhone",
                                summary = "微信绑定手机号",
                                description = "微信未绑定账号时验证手机号，已注册则绑定登录，未注册则返回注册票据",
                                security = {},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        WechatBindPhoneRequest.class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        LoginResponse.class))))),
        @RouterOperation(
                path = "/api/v1/auth/sms/code",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "sendSmsCode",
                operation =
                        @Operation(
                                operationId = "sendSmsCode",
                                summary = "发送短信验证码",
                                security = {},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        SendSmsRequest.class))))),
        @RouterOperation(
                path = "/api/v1/auth/cl/sms/code",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "sendClSmsCode",
                operation =
                        @Operation(
                                operationId = "sendClSmsCode",
                                summary = "发送创蓝短信验证码",
                                security = {},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        SendSmsRequest.class))))),
        @RouterOperation(
                path = "/api/v1/auth/cl/validate-mobile",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "validateMobile",
                operation =
                        @Operation(
                                operationId = "validateMobile",
                                summary = "本机号校验",
                                description = "调用创蓝闪验本机号校验API，校验当前流量卡的手机号与传入的手机号码是否一致",
                                security = {},
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        MobileValidateResponse
                                                                                                .class))))),
        @RouterOperation(
                path = "/api/v1/auth/wx/phone",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "bindPhone",
                operation =
                        @Operation(
                                operationId = "bindPhone",
                                summary = "绑定微信手机号",
                                description = "获取并绑定用户微信手机号",
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        WxPhoneRequest.class))))),
        @RouterOperation(
                path = "/api/v1/auth/wechat/mp/authorize",
                method = RequestMethod.GET,
                beanClass = AuthHandler.class,
                beanMethod = "mpAuthorize",
                operation =
                        @Operation(
                                operationId = "mpAuthorize",
                                summary = "公众号网页授权跳转",
                                description = "生成微信公众号网页授权URL并302重定向；签发一次性OAuth state防CSRF。"
                                        + "授权后微信回跳 redirectUri 并携带 code/state，客户端以 loginType=WX_OAUTH 提交登录。",
                                security = {},
                                responses = @ApiResponse(responseCode = "302", description = "重定向到微信授权页")))
    })
    @Bean
    /**
     * 构建登录认证接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 登录认证接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> authRoutesDocs() {
        return placeholderRoute("/__openapi_auth_placeholder");
    }

    /** 授权（角色与权限）模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/auth/roles/my",
                method = RequestMethod.GET,
                beanClass = AuthorizationHandler.class,
                beanMethod = "getMyRoles",
                operation =
                        @Operation(
                                operationId = "getMyRoles",
                                summary = "获取当前用户角色",
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content = @Content(schema = @Schema(implementation = RoleDTO.class))))),
        @RouterOperation(
                path = "/api/v1/auth/permissions/my",
                method = RequestMethod.GET,
                beanClass = AuthorizationHandler.class,
                beanMethod = "getMyPermissions",
                operation =
                        @Operation(
                                operationId = "getMyPermissions",
                                summary = "获取当前用户权限",
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        PermissionDTO.class))))),
        @RouterOperation(
                path = "/api/v1/auth/roles",
                method = RequestMethod.GET,
                beanClass = AuthorizationHandler.class,
                beanMethod = "getAllRoles",
                operation =
                        @Operation(
                                operationId = "getAllRoles",
                                summary = "获取所有角色",
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content = @Content(schema = @Schema(implementation = RoleDTO.class))))),
        @RouterOperation(
                path = "/api/v1/auth/permissions",
                method = RequestMethod.GET,
                beanClass = AuthorizationHandler.class,
                beanMethod = "getAllPermissions",
                operation =
                        @Operation(
                                operationId = "getAllPermissions",
                                summary = "获取所有权限",
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        PermissionDTO.class))))),
        @RouterOperation(
                path = "/api/v1/auth/roles",
                method = RequestMethod.POST,
                beanClass = AuthorizationHandler.class,
                beanMethod = "createRole",
                operation =
                        @Operation(
                                operationId = "createRole",
                                summary = "创建角色",
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema = @Schema(implementation = RoleRequest.class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content = @Content(schema = @Schema(implementation = RoleDTO.class))))),
        @RouterOperation(
                path = "/api/v1/auth/roles/update",
                method = RequestMethod.POST,
                beanClass = AuthorizationHandler.class,
                beanMethod = "updateRole",
                operation =
                        @Operation(
                                operationId = "updateRole",
                                summary = "更新角色",
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema = @Schema(implementation = RoleRequest.class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content = @Content(schema = @Schema(implementation = RoleDTO.class))))),
        @RouterOperation(
                path = "/api/v1/auth/roles/{id}",
                method = RequestMethod.DELETE,
                beanClass = AuthorizationHandler.class,
                beanMethod = "deleteRole",
                operation = @Operation(operationId = "deleteRole", summary = "删除角色")),
        @RouterOperation(
                path = "/api/v1/auth/permissions",
                method = RequestMethod.POST,
                beanClass = AuthorizationHandler.class,
                beanMethod = "createPermission",
                operation =
                        @Operation(
                                operationId = "createPermission",
                                summary = "创建权限",
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        PermissionRequest.class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        PermissionDTO.class))))),
        @RouterOperation(
                path = "/api/v1/auth/permissions/update",
                method = RequestMethod.POST,
                beanClass = AuthorizationHandler.class,
                beanMethod = "updatePermission",
                operation =
                        @Operation(
                                operationId = "updatePermission",
                                summary = "更新权限",
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        PermissionRequest.class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        PermissionDTO.class))))),
        @RouterOperation(
                path = "/api/v1/auth/permissions/{id}",
                method = RequestMethod.DELETE,
                beanClass = AuthorizationHandler.class,
                beanMethod = "deletePermission",
                operation = @Operation(operationId = "deletePermission", summary = "删除权限")),
        @RouterOperation(
                path = "/api/v1/auth/users/{userId}/roles/{roleId}",
                method = RequestMethod.POST,
                beanClass = AuthorizationHandler.class,
                beanMethod = "assignRoleToUser",
                operation = @Operation(operationId = "assignRoleToUser", summary = "为用户分配角色")),
        @RouterOperation(
                path = "/api/v1/auth/users/{userId}/roles/{roleId}",
                method = RequestMethod.DELETE,
                beanClass = AuthorizationHandler.class,
                beanMethod = "removeRoleFromUser",
                operation = @Operation(operationId = "removeRoleFromUser", summary = "移除用户角色"))
    })
    @Bean
    /**
     * 构建授权（角色与权限）接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 授权接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> authorizationRoutesDocs() {
        return placeholderRoute("/__openapi_authorization_placeholder");
    }

    /** 用户模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/user/info",
                method = RequestMethod.GET,
                beanClass = UserHandler.class,
                beanMethod = "getCurrentUserInfo",
                operation =
                        @Operation(
                                operationId = "getCurrentUserInfo",
                                summary = "获取当前用户信息",
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content = @Content(schema = @Schema(implementation = UserDTO.class))))),
        @RouterOperation(
                path = "/api/v1/user/{userId}",
                method = RequestMethod.GET,
                beanClass = UserHandler.class,
                beanMethod = "getUserById",
                operation =
                        @Operation(
                                operationId = "getUserById",
                                summary = "根据ID获取用户信息",
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content = @Content(schema = @Schema(implementation = UserDTO.class))))),
        @RouterOperation(
                path = "/api/v1/user/list",
                method = RequestMethod.POST,
                beanClass = UserHandler.class,
                beanMethod = "listUsers",
                operation =
                        @Operation(
                                operationId = "listUsers",
                                summary = "分页查询用户列表",
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema = @Schema(implementation = PageRequest.class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        PageResponse.class))))),
        @RouterOperation(
                path = "/api/v1/user/me/deletion-code",
                method = RequestMethod.POST,
                beanClass = UserHandler.class,
                beanMethod = "sendAccountDeletionCode",
                operation =
                        @Operation(
                                operationId = "sendAccountDeletionCode",
                                summary = "发送账号注销验证码",
                                description = "向当前账号已绑定手机号发送注销二次验证短信，不接受客户端传入手机号")),
        @RouterOperation(
                path = "/api/v1/user/me",
                method = RequestMethod.DELETE,
                beanClass = UserHandler.class,
                beanMethod = "deleteCurrentUser",
                operation =
                        @Operation(
                                operationId = "deleteCurrentUser",
                                summary = "注销当前账号",
                                description = "短信二次验证通过后立即失效账号，返回异步清理任务标识",
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        AccountDeletionRequest
                                                                                                .class)))))
    })
    @Bean
    /**
     * 构建个人中心（用户账号）接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 个人中心接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> userRoutesDocs() {
        return placeholderRoute("/__openapi_user_placeholder");
    }

    /** 文件模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/file/upload",
                method = RequestMethod.POST,
                beanClass = FileHandler.class,
                beanMethod = "upload",
                operation =
                        @Operation(
                                operationId = "fileUpload",
                                summary = "上传图片（已过时）",
                                deprecated = true,
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        FileUploadRequestDoc.class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        FileUploadResult.class))))),
        @RouterOperation(
                path = "/api/v1/files/generic",
                method = RequestMethod.POST,
                beanClass = FileHandler.class,
                beanMethod = "uploadGenericFile",
                operation =
                        @Operation(
                                operationId = "uploadGenericFile",
                                summary = "上传普通图片",
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        GenericFileUploadRequestDoc
                                                                                                .class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        FileUploadResult.class))))),
        @RouterOperation(
                path = "/api/v1/file/url",
                method = RequestMethod.GET,
                beanClass = FileHandler.class,
                beanMethod = "getUrl",
                operation = @Operation(operationId = "getFileUrl", summary = "获取文件URL")),
        @RouterOperation(
                path = "/api/v1/file/delete",
                method = RequestMethod.DELETE,
                beanClass = FileHandler.class,
                beanMethod = "delete",
                operation = @Operation(operationId = "deleteFile", summary = "删除文件")),
        @RouterOperation(
                path = "/api/v1/file/exists",
                method = RequestMethod.GET,
                beanClass = FileHandler.class,
                beanMethod = "exists",
                operation = @Operation(operationId = "fileExists", summary = "检查文件是否存在")),
        @RouterOperation(
                path = "/api/v1/file/content",
                method = RequestMethod.GET,
                beanClass = FileHandler.class,
                beanMethod = "content",
                operation = @Operation(operationId = "getLocalFileContent", summary = "读取本地存储文件"))
    })
    @Bean
    /**
     * 构建文件上传下载接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 文件接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> fileRoutesDocs() {
        return placeholderRoute("/__openapi_file_placeholder");
    }

    /** 健康检查模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/health",
                method = RequestMethod.GET,
                beanClass = HealthHandler.class,
                beanMethod = "health",
                operation = @Operation(operationId = "healthCheck", summary = "健康检查")),
        @RouterOperation(
                path = "/api/v1/info",
                method = RequestMethod.GET,
                beanClass = HealthHandler.class,
                beanMethod = "info",
                operation = @Operation(operationId = "getSystemInfo", summary = "系统信息")),
        @RouterOperation(
                path = "/",
                method = RequestMethod.GET,
                beanClass = HealthHandler.class,
                beanMethod = "root",
                operation = @Operation(operationId = "rootProbe", summary = "根路径探活"))
    })
    @Bean
    /**
     * 构建健康检查（探活）接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 健康检查接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> healthRoutesDocs() {
        return placeholderRoute("/__openapi_health_placeholder");
    }

    /** 后台用户管理模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/users",
                method = RequestMethod.GET,
                beanClass = AdminUserManagementHandler.class,
                beanMethod = "list",
                operation =
                        @Operation(
                                operationId = "adminListUsers",
                                summary = "分页检索用户",
                                security = @SecurityRequirement(name = "BearerAuth"),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.application
                                                                                                .dto.admin
                                                                                                .AdminUserSummaryResponse
                                                                                                .class))))),
        @RouterOperation(
                path = "/admin-api/v1/users",
                method = RequestMethod.POST,
                beanClass = AdminUserManagementHandler.class,
                beanMethod = "create",
                operation =
                        @Operation(
                                operationId = "adminCreateUser",
                                summary = "后台创建用户",
                                security = @SecurityRequirement(name = "BearerAuth"),
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        AdminUserCreateRequest.class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        AdminUserDetailResponse
                                                                                                .class))))),
        @RouterOperation(
                path = "/admin-api/v1/users/{userId}",
                method = RequestMethod.GET,
                beanClass = AdminUserManagementHandler.class,
                beanMethod = "detail",
                operation =
                        @Operation(
                                operationId = "adminGetUser",
                                summary = "查看用户资料",
                                security = @SecurityRequirement(name = "BearerAuth"),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        AdminUserDetailResponse
                                                                                                .class))))),
        @RouterOperation(
                path = "/admin-api/v1/users/{userId}",
                method = RequestMethod.PUT,
                beanClass = AdminUserManagementHandler.class,
                beanMethod = "update",
                operation =
                        @Operation(
                                operationId = "adminUpdateUser",
                                summary = "编辑用户资料",
                                security = @SecurityRequirement(name = "BearerAuth"),
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        AdminUserUpdateRequest.class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        AdminUserDetailResponse
                                                                                                .class))))),
        @RouterOperation(
                path = "/admin-api/v1/users/{userId}/roles",
                method = RequestMethod.PUT,
                beanClass = AdminUserManagementHandler.class,
                beanMethod = "replaceRoles",
                operation =
                        @Operation(
                                operationId = "adminReplaceUserRoles",
                                summary = "替换用户后台角色",
                                security = @SecurityRequirement(name = "BearerAuth"),
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        AdminUserRolesRequest.class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        AdminUserDetailResponse
                                                                                                .class))))),
        @RouterOperation(
                path = "/admin-api/v1/users/{userId}/sessions/revoke",
                method = RequestMethod.POST,
                beanClass = AdminUserManagementHandler.class,
                beanMethod = "revokeSessions",
                operation =
                        @Operation(
                                operationId = "adminRevokeUserSessions",
                                summary = "强制撤销用户全部会话",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/users/{userId}/status",
                method = RequestMethod.PUT,
                beanClass = AdminUserManagementHandler.class,
                beanMethod = "updateStatus",
                operation =
                        @Operation(
                                operationId = "adminUpdateUserStatus",
                                summary = "启用或停用用户",
                                security = @SecurityRequirement(name = "BearerAuth"),
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        AdminUserStatusRequest.class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        AdminUserDetailResponse
                                                                                                .class))))),
        @RouterOperation(
                path = "/admin-api/v1/staff-roles",
                method = RequestMethod.GET,
                beanClass = AdminUserManagementHandler.class,
                beanMethod = "roles",
                operation =
                        @Operation(
                                operationId = "adminListStaffRoles",
                                summary = "查询可配置的后台角色",
                                security = @SecurityRequirement(name = "BearerAuth"),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content =
                                                        @Content(
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        AdminRoleResponse.class)))))
    })
    @Bean
    /**
     * 构建系统管理-用户管理接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 用户管理接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> adminUserManagementRoutesDocs() {
        return placeholderRoute("/__openapi_admin_user_placeholder");
    }

    /** 系统管理-菜单模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/menus",
                method = RequestMethod.GET,
                beanClass = MenuHandler.class,
                beanMethod = "tree",
                operation =
                        @Operation(
                                operationId = "adminListMenus",
                                summary = "查询菜单树",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/menus/{menuId}",
                method = RequestMethod.GET,
                beanClass = MenuHandler.class,
                beanMethod = "detail",
                operation =
                        @Operation(
                                operationId = "adminGetMenu",
                                summary = "查询菜单详情",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/menus",
                method = RequestMethod.POST,
                beanClass = MenuHandler.class,
                beanMethod = "create",
                operation =
                        @Operation(
                                operationId = "adminCreateMenu",
                                summary = "创建菜单",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/menus/{menuId}",
                method = RequestMethod.PUT,
                beanClass = MenuHandler.class,
                beanMethod = "update",
                operation =
                        @Operation(
                                operationId = "adminUpdateMenu",
                                summary = "更新菜单",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/menus/{menuId}",
                method = RequestMethod.DELETE,
                beanClass = MenuHandler.class,
                beanMethod = "delete",
                operation =
                        @Operation(
                                operationId = "adminDeleteMenu",
                                summary = "删除菜单",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/menus/{menuId}/status",
                method = RequestMethod.PUT,
                beanClass = MenuHandler.class,
                beanMethod = "updateStatus",
                operation =
                        @Operation(
                                operationId = "adminUpdateMenuStatus",
                                summary = "启用或停用菜单",
                                security = @SecurityRequirement(name = "BearerAuth")))
    })
    @Bean
    /**
     * 构建系统管理-菜单管理接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 菜单管理接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> adminMenuRoutesDocs() {
        return placeholderRoute("/__openapi_admin_menu_placeholder");
    }

    /** 系统管理-字典模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/dict/types",
                method = RequestMethod.GET,
                beanClass = DictHandler.class,
                beanMethod = "listTypes",
                operation =
                        @Operation(
                                operationId = "adminListDictTypes",
                                summary = "查询字典类型列表",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/dict/types/{id}",
                method = RequestMethod.GET,
                beanClass = DictHandler.class,
                beanMethod = "getType",
                operation =
                        @Operation(
                                operationId = "adminGetDictType",
                                summary = "查询字典类型详情",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/dict/types",
                method = RequestMethod.POST,
                beanClass = DictHandler.class,
                beanMethod = "createType",
                operation =
                        @Operation(
                                operationId = "adminCreateDictType",
                                summary = "创建字典类型",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/dict/types/{id}",
                method = RequestMethod.PUT,
                beanClass = DictHandler.class,
                beanMethod = "updateType",
                operation =
                        @Operation(
                                operationId = "adminUpdateDictType",
                                summary = "更新字典类型",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/dict/types/{id}",
                method = RequestMethod.DELETE,
                beanClass = DictHandler.class,
                beanMethod = "deleteType",
                operation =
                        @Operation(
                                operationId = "adminDeleteDictType",
                                summary = "删除字典类型",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/dict/types/{id}/status",
                method = RequestMethod.PUT,
                beanClass = DictHandler.class,
                beanMethod = "updateTypeStatus",
                operation =
                        @Operation(
                                operationId = "adminUpdateDictTypeStatus",
                                summary = "启用或停用字典类型",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/dict/data",
                method = RequestMethod.GET,
                beanClass = DictHandler.class,
                beanMethod = "listData",
                operation =
                        @Operation(
                                operationId = "adminListDictData",
                                summary = "按类型查询字典数据",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/dict/data/{id}",
                method = RequestMethod.GET,
                beanClass = DictHandler.class,
                beanMethod = "getData",
                operation =
                        @Operation(
                                operationId = "adminGetDictData",
                                summary = "查询字典数据详情",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/dict/data",
                method = RequestMethod.POST,
                beanClass = DictHandler.class,
                beanMethod = "createData",
                operation =
                        @Operation(
                                operationId = "adminCreateDictData",
                                summary = "创建字典数据",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/dict/data/{id}",
                method = RequestMethod.PUT,
                beanClass = DictHandler.class,
                beanMethod = "updateData",
                operation =
                        @Operation(
                                operationId = "adminUpdateDictData",
                                summary = "更新字典数据",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/dict/data/{id}",
                method = RequestMethod.DELETE,
                beanClass = DictHandler.class,
                beanMethod = "deleteData",
                operation =
                        @Operation(
                                operationId = "adminDeleteDictData",
                                summary = "删除字典数据",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/dict/data/{id}/status",
                method = RequestMethod.PUT,
                beanClass = DictHandler.class,
                beanMethod = "updateDataStatus",
                operation =
                        @Operation(
                                operationId = "adminUpdateDictDataStatus",
                                summary = "启用或停用字典数据",
                                security = @SecurityRequirement(name = "BearerAuth")))
    })
    @Bean
    /**
     * 构建系统管理-字典管理接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 字典管理接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> adminDictRoutesDocs() {
        return placeholderRoute("/__openapi_admin_dict_placeholder");
    }

    /** 系统管理-参数配置模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/configs",
                method = RequestMethod.GET,
                beanClass = ConfigHandler.class,
                beanMethod = "list",
                operation =
                        @Operation(
                                operationId = "adminListConfigs",
                                summary = "查询参数配置列表",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/configs/{id}",
                method = RequestMethod.GET,
                beanClass = ConfigHandler.class,
                beanMethod = "detail",
                operation =
                        @Operation(
                                operationId = "adminGetConfig",
                                summary = "查询参数配置详情",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/configs",
                method = RequestMethod.POST,
                beanClass = ConfigHandler.class,
                beanMethod = "create",
                operation =
                        @Operation(
                                operationId = "adminCreateConfig",
                                summary = "创建参数配置",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/configs/{id}",
                method = RequestMethod.PUT,
                beanClass = ConfigHandler.class,
                beanMethod = "update",
                operation =
                        @Operation(
                                operationId = "adminUpdateConfig",
                                summary = "更新参数配置",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/configs/{id}",
                method = RequestMethod.DELETE,
                beanClass = ConfigHandler.class,
                beanMethod = "delete",
                operation =
                        @Operation(
                                operationId = "adminDeleteConfig",
                                summary = "删除参数配置",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/configs/{id}/status",
                method = RequestMethod.PUT,
                beanClass = ConfigHandler.class,
                beanMethod = "updateStatus",
                operation =
                        @Operation(
                                operationId = "adminUpdateConfigStatus",
                                summary = "启用或停用参数配置",
                                security = @SecurityRequirement(name = "BearerAuth")))
    })
    @Bean
    /**
     * 构建系统管理-参数配置接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 参数配置接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> adminConfigRoutesDocs() {
        return placeholderRoute("/__openapi_admin_config_placeholder");
    }

    /** 系统管理-定时任务模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/jobs",
                method = RequestMethod.GET,
                beanClass = JobHandler.class,
                beanMethod = "list",
                operation =
                        @Operation(
                                operationId = "adminListJobs",
                                summary = "查询定时任务列表",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/jobs/page",
                method = RequestMethod.GET,
                beanClass = JobHandler.class,
                beanMethod = "page",
                operation =
                        @Operation(
                                operationId = "adminPageJobs",
                                summary = "分页查询定时任务",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}",
                method = RequestMethod.GET,
                beanClass = JobHandler.class,
                beanMethod = "detail",
                operation =
                        @Operation(
                                operationId = "adminGetJob",
                                summary = "查询任务详情",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/jobs",
                method = RequestMethod.POST,
                beanClass = JobHandler.class,
                beanMethod = "create",
                operation =
                        @Operation(
                                operationId = "adminCreateJob",
                                summary = "创建定时任务",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}",
                method = RequestMethod.PUT,
                beanClass = JobHandler.class,
                beanMethod = "update",
                operation =
                        @Operation(
                                operationId = "adminUpdateJob",
                                summary = "更新定时任务",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}",
                method = RequestMethod.DELETE,
                beanClass = JobHandler.class,
                beanMethod = "delete",
                operation =
                        @Operation(
                                operationId = "adminDeleteJob",
                                summary = "删除定时任务",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}/status",
                method = RequestMethod.PUT,
                beanClass = JobHandler.class,
                beanMethod = "updateStatus",
                operation =
                        @Operation(
                                operationId = "adminUpdateJobStatus",
                                summary = "启用或停用定时任务",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}/run",
                method = RequestMethod.POST,
                beanClass = JobHandler.class,
                beanMethod = "runOnce",
                operation =
                        @Operation(
                                operationId = "adminRunJob",
                                summary = "立即执行一次任务",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}/logs",
                method = RequestMethod.GET,
                beanClass = JobHandler.class,
                beanMethod = "logs",
                operation =
                        @Operation(
                                operationId = "adminListJobLogs",
                                summary = "查询任务执行日志",
                                security = @SecurityRequirement(name = "BearerAuth")))
    })
    @Bean
    /**
     * 构建系统管理-定时任务接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 定时任务接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> adminJobRoutesDocs() {
        return placeholderRoute("/__openapi_admin_job_placeholder");
    }

    /** 系统管理-登录日志模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/login-logs",
                method = RequestMethod.GET,
                beanClass = LoginLogHandler.class,
                beanMethod = "list",
                operation =
                        @Operation(
                                operationId = "adminListLoginLogs",
                                summary = "分页查询登录日志",
                                security = @SecurityRequirement(name = "BearerAuth")))
    })
    @Bean
    /**
     * 构建系统管理-登录日志接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 登录日志接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> adminLoginLogRoutesDocs() {
        return placeholderRoute("/__openapi_admin_login_log_placeholder");
    }

    /** 系统管理-审计日志模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/audit-logs",
                method = RequestMethod.GET,
                beanClass = AuditLogHandler.class,
                beanMethod = "list",
                operation =
                        @Operation(
                                operationId = "adminListAuditLogs",
                                summary = "分页查询审计日志",
                                security = @SecurityRequirement(name = "BearerAuth")))
    })
    @Bean
    /**
     * 构建系统管理-审计日志接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 审计日志接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> adminAuditLogRoutesDocs() {
        return placeholderRoute("/__openapi_admin_audit_log_placeholder");
    }

    /** 系统管理-API调用日志模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/api-call-logs",
                method = RequestMethod.GET,
                beanClass = ApiCallLogHandler.class,
                beanMethod = "list",
                operation =
                        @Operation(
                                operationId = "adminListApiCallLogs",
                                summary = "分页查询API调用日志",
                                security = @SecurityRequirement(name = "BearerAuth")))
    })
    @Bean
    /**
     * API调用日志管理模块在线接口文档
     *
     * <p>提供API调用日志的查询接口,用于OpenAPI文档展示。
     *
     * @return 占位符路由函数
     */
    public RouterFunction<ServerResponse> adminApiCallLogRoutesDocs() {
        return placeholderRoute("/__openapi_admin_api_call_log_placeholder");
    }
    /** 系统管理-部门管理模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/depts",
                method = RequestMethod.GET,
                beanClass = DeptHandler.class,
                beanMethod = "listTree",
                operation =
                        @Operation(
                                operationId = "adminListDepts",
                                summary = "查询部门树",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/depts/{id}",
                method = RequestMethod.GET,
                beanClass = DeptHandler.class,
                beanMethod = "detail",
                operation =
                        @Operation(
                                operationId = "adminGetDept",
                                summary = "查询部门详情",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/depts",
                method = RequestMethod.POST,
                beanClass = DeptHandler.class,
                beanMethod = "create",
                operation =
                        @Operation(
                                operationId = "adminCreateDept",
                                summary = "新增部门",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/depts/{id}",
                method = RequestMethod.PUT,
                beanClass = DeptHandler.class,
                beanMethod = "update",
                operation =
                        @Operation(
                                operationId = "adminUpdateDept",
                                summary = "更新部门",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/depts/{id}",
                method = RequestMethod.DELETE,
                beanClass = DeptHandler.class,
                beanMethod = "delete",
                operation =
                        @Operation(
                                operationId = "adminDeleteDept",
                                summary = "删除部门",
                                security = @SecurityRequirement(name = "BearerAuth")))
    })
    @Bean
    /**
     * 构建系统管理-部门管理接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 部门管理接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> adminDeptRoutesDocs() {
        return placeholderRoute("/__openapi_admin_dept_placeholder");
    }
    /** 数据导出-Excel 在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/export/users",
                method = RequestMethod.GET,
                beanClass = ExcelExportHandler.class,
                beanMethod = "exportUsers",
                operation =
                        @Operation(
                                operationId = "adminExportUsers",
                                summary = "导出用户列表（xlsx）",
                                security = @SecurityRequirement(name = "BearerAuth")))
    })
    @Bean
    /**
     * 构建系统管理-数据导出接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 数据导出接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> adminExportRoutesDocs() {
        return placeholderRoute("/__openapi_admin_export_placeholder");
    }

    /** 站内通知 SSE 在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/notifications/stream",
                method = RequestMethod.GET,
                beanClass = NotificationSseHandler.class,
                beanMethod = "stream",
                operation =
                        @Operation(
                                operationId = "streamNotifications",
                                summary = "订阅当前用户通知流（SSE）",
                                security = @SecurityRequirement(name = "BearerAuth")))
    })
    @Bean
    /**
     * 构建消息通知（SSE 推送）接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 消息通知接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> notificationSseRoutesDocs() {
        return placeholderRoute("/__openapi_notification_sse_placeholder");
    }
    /** 系统管理-在线用户模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/online-users",
                method = RequestMethod.GET,
                beanClass = OnlineUserHandler.class,
                beanMethod = "list",
                operation =
                        @Operation(
                                operationId = "adminListOnlineUsers",
                                summary = "分页查询在线用户",
                                security = @SecurityRequirement(name = "BearerAuth")))
    })
    @Bean
    /**
     * 在线用户管理模块在线接口文档
     *
     * <p>提供在线用户查询接口,用于OpenAPI文档展示。
     *
     * @return 占位符路由函数
     */
    public RouterFunction<ServerResponse> adminOnlineUserRoutesDocs() {
        return placeholderRoute("/__openapi_admin_online_user_placeholder");
    }

    /** 监控大盘模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/monitor/overview",
                method = RequestMethod.GET,
                beanClass = MonitorDashboardHandler.class,
                beanMethod = "overview",
                operation =
                        @Operation(
                                operationId = "monitorOverview",
                                summary = "监控概览",
                                description = "应用状态、运行时长、堆内存、CPU、线程、会话与请求/治理/业务计数",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/monitor/system",
                method = RequestMethod.GET,
                beanClass = MonitorDashboardHandler.class,
                beanMethod = "system",
                operation =
                        @Operation(
                                operationId = "monitorSystem",
                                summary = "系统资源监控",
                                description = "JVM 内存/GC、CPU、进程、线程、磁盘、网络、阻塞 IO 线程池",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/monitor/middleware",
                method = RequestMethod.GET,
                beanClass = MonitorDashboardHandler.class,
                beanMethod = "middleware",
                operation =
                        @Operation(
                                operationId = "monitorMiddleware",
                                summary = "中间件监控",
                                description = "MySQL 连接池、Redis 健康与派生指标、消息中间件、链路追踪状态",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/monitor/governance",
                method = RequestMethod.GET,
                beanClass = MonitorDashboardHandler.class,
                beanMethod = "governance",
                operation =
                        @Operation(
                                operationId = "monitorGovernance",
                                summary = "治理监控",
                                description = "Sentinel 规则、安全计数、Resilience4j 熔断器状态、网关/API 流量",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/monitor/business",
                method = RequestMethod.GET,
                beanClass = MonitorDashboardHandler.class,
                beanMethod = "business",
                operation =
                        @Operation(
                                operationId = "monitorBusiness",
                                summary = "核心业务监控",
                                description = "登录/注册/上传/信用变动等核心业务指标与近 10 分钟趋势",
                                security = @SecurityRequirement(name = "BearerAuth")))
    })
    @Bean
    /**
     * 核心业务监控模块在线接口文档
     *
     * <p>提供核心业务指标查询接口,包括登录、注册、上传、信用变动等业务指标及近期趋势,用于OpenAPI文档展示。
     *
     * @return 占位符路由函数
     */
    public RouterFunction<ServerResponse> adminMonitorRoutesDocs() {
        return placeholderRoute("/__openapi_admin_monitor_placeholder");
    }

    /** 图形验证码模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/auth/captcha",
                method = RequestMethod.GET,
                beanClass = CaptchaHandler.class,
                beanMethod = "generate",
                operation =
                        @Operation(
                                operationId = "getCaptcha",
                                summary = "获取图形验证码",
                                security = {})),
        @RouterOperation(
                path = "/api/v1/auth/captcha/verify",
                method = RequestMethod.POST,
                beanClass = CaptchaHandler.class,
                beanMethod = "verify",
                operation =
                        @Operation(
                                operationId = "verifyCaptcha",
                                summary = "校验图形验证码",
                                security = {}))
    })
    @Bean
    /**
     * 构建图形验证码接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 图形验证码接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> captchaRoutesDocs() {
        return placeholderRoute("/__openapi_captcha_placeholder");
    }

    /** 统一搜索模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/search",
                method = RequestMethod.GET,
                beanClass = com.apex.scaffold.interfaces.handler.UnifiedSearchHandler.class,
                beanMethod = "search",
                operation =
                        @Operation(
                                operationId = "unifiedSearch",
                                summary = "全文搜索",
                                description = "在系统资源中执行全文搜索，支持多类型资源搜索和分页",
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/admin-api/v1/search/rebuild",
                method = RequestMethod.POST,
                beanClass = com.apex.scaffold.interfaces.handler.UnifiedSearchHandler.class,
                beanMethod = "rebuildIndex",
                operation =
                        @Operation(
                                operationId = "rebuildSearchIndex",
                                summary = "重建搜索索引",
                                description = "重建全文搜索索引，用于索引数据更新或修复",
                                security = @SecurityRequirement(name = "BearerAuth")))
    })
    @Bean
    /**
     * 构建统一搜索接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 统一搜索接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> unifiedSearchRoutesDocs() {
        return placeholderRoute("/__openapi_unified_search_placeholder");
    }

    /** 工作流操作模块在线接口文档。 */
    @RouterOperations({
        @RouterOperation(
                path = "/api/workflow/tasks/{taskId}/rollback",
                method = RequestMethod.POST,
                beanClass = com.apex.scaffold.interfaces.handler.WorkflowHandler.class,
                beanMethod = "rollbackTask",
                operation =
                        @Operation(
                                operationId = "rollbackTask",
                                summary = "流程回退",
                                description = "将当前任务回退到流程中的前置节点",
                                security = @SecurityRequirement(name = "BearerAuth"),
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.interfaces.dto
                                                                                                .workflow
                                                                                                .RollbackTaskRequest
                                                                                                .class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content = @Content(schema = @Schema(implementation = Void.class))))),
        @RouterOperation(
                path = "/api/workflow/tasks/{taskId}/transfer",
                method = RequestMethod.POST,
                beanClass = com.apex.scaffold.interfaces.handler.WorkflowHandler.class,
                beanMethod = "transferTask",
                operation =
                        @Operation(
                                operationId = "transferTask",
                                summary = "任务转办",
                                description = "将当前待办任务转交给其他用户处理",
                                security = @SecurityRequirement(name = "BearerAuth"),
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.interfaces.dto
                                                                                                .workflow
                                                                                                .TransferTaskRequest
                                                                                                .class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content = @Content(schema = @Schema(implementation = Void.class))))),
        @RouterOperation(
                path = "/api/workflow/tasks/{taskId}/delegate",
                method = RequestMethod.POST,
                beanClass = com.apex.scaffold.interfaces.handler.WorkflowHandler.class,
                beanMethod = "delegateTask",
                operation =
                        @Operation(
                                operationId = "delegateTask",
                                summary = "任务委派",
                                description = "将当前待办任务临时委托给其他用户处理",
                                security = @SecurityRequirement(name = "BearerAuth"),
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.interfaces.dto
                                                                                                .workflow
                                                                                                .DelegateTaskRequest
                                                                                                .class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content = @Content(schema = @Schema(implementation = Void.class))))),
        @RouterOperation(
                path = "/api/workflow/instances/{instanceId}/suspend",
                method = RequestMethod.POST,
                beanClass = com.apex.scaffold.interfaces.handler.WorkflowHandler.class,
                beanMethod = "suspendInstance",
                operation =
                        @Operation(
                                operationId = "suspendInstance",
                                summary = "挂起流程实例",
                                description = "暂停流程实例的执行，所有待办任务无法审批",
                                security = @SecurityRequirement(name = "BearerAuth"),
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.interfaces.dto
                                                                                                .workflow
                                                                                                .SuspendInstanceRequest
                                                                                                .class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content = @Content(schema = @Schema(implementation = Void.class))))),
        @RouterOperation(
                path = "/api/workflow/instances/{instanceId}/resume",
                method = RequestMethod.POST,
                beanClass = com.apex.scaffold.interfaces.handler.WorkflowHandler.class,
                beanMethod = "resumeInstance",
                operation =
                        @Operation(
                                operationId = "resumeInstance",
                                summary = "恢复流程实例",
                                description = "恢复挂起的流程实例，允许待办任务继续审批",
                                security = @SecurityRequirement(name = "BearerAuth"),
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.interfaces.dto
                                                                                                .workflow
                                                                                                .ResumeInstanceRequest
                                                                                                .class))),
                                responses =
                                        @ApiResponse(
                                                responseCode = "200",
                                                content = @Content(schema = @Schema(implementation = Void.class)))))
    })
    @Bean
    /**
     * 构建工作流操作接口的 OpenAPI 占位路由，保证 OpenAPI 文档分组展示。
     *
     * @return 工作流操作接口的 OpenAPI 占位路由
     */
    public RouterFunction<ServerResponse> workflowOperationRoutesDocs() {
        return placeholderRoute("/__openapi_workflow_operation_placeholder");
    }

    /**
     * 构造占位 RouterFunction，仅用于承载 {@code @RouterOperations} 注解。
     *
     * @param path 占位路径
     * @return 始终返回 404 的占位路由
     */
    private RouterFunction<ServerResponse> placeholderRoute(String path) {
        return RouterFunctions.route(
                org.springframework.web.reactive.function.server.RequestPredicates.GET(path),
                req -> ServerResponse.notFound().build());
    }
}
