package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.JwtConfigRequest;
import com.apex.scaffold.application.dto.admin.JwtTestRequest;
import com.apex.scaffold.application.dto.admin.JwtTestResponse;
import com.apex.scaffold.application.dto.admin.PermissionSyncResult;
import com.apex.scaffold.application.dto.admin.SecurityPolicyRequest;
import com.apex.scaffold.application.dto.admin.SessionConfigRequest;
import com.apex.scaffold.application.service.*;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 认证授权配置处理器(函数式端点)。
 *
 * <p>提供系统认证授权配置的管理 API,包括 JWT 配置、会话配置、安全策略配置的查询和更新,
 * 以及权限同步功能。同时提供别名路由,复用现有的 OAuth2 客户端、在线会话、登录日志和权限列表查询服务。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>JWT 配置管理:查询和更新 JWT 签发者、密钥、令牌有效期等配置</li>
 *   <li>会话配置管理:查询和更新会话超时、并发控制、记住我等配置</li>
 *   <li>安全策略管理:查询和更新密码策略、登录锁定、验证码等配置</li>
 *   <li>权限同步:扫描代码中的 @RequirePermission 注解,对比数据库,返回新增和废弃的权限码清单</li>
 *   <li>别名路由:复用现有服务,提供统一的认证授权配置入口</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>GET /admin-api/v1/auth/config/jwt - 查询 JWT 配置</li>
 *   <li>PUT /admin-api/v1/auth/config/jwt - 更新 JWT 配置</li>
 *   <li>GET /admin-api/v1/auth/config/session - 查询会话配置</li>
 *   <li>PUT /admin-api/v1/auth/config/session - 更新会话配置</li>
 *   <li>GET /admin-api/v1/auth/config/security - 查询安全策略</li>
 *   <li>PUT /admin-api/v1/auth/config/security - 更新安全策略</li>
 *   <li>POST /admin-api/v1/auth/config/permissions/sync - 同步权限码</li>
 *   <li>GET /admin-api/v1/auth/config/oauth2/clients - OAuth2 客户端列表(别名)</li>
 *   <li>GET /admin-api/v1/auth/config/sessions - 在线会话列表(别名)</li>
 *   <li>POST /admin-api/v1/auth/config/sessions/kickout - 踢出会话(别名)</li>
 *   <li>GET /admin-api/v1/auth/config/login-logs - 登录日志列表(别名)</li>
 *   <li>GET /admin-api/v1/auth/config/permissions - 权限列表(别名)</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Tag(name = "后台-认证授权配置", description = "系统认证授权配置管理,包括 JWT、会话、安全策略和权限同步")
public class AuthConfigHandler extends BaseHandler {

    private final SysJwtConfigService jwtConfigService;
    private final SysSessionConfigService sessionConfigService;
    private final SysSecurityPolicyService securityPolicyService;
    private final PermissionSyncService permissionSyncService;
    private final OAuth2ClientManagementService oauth2ClientManagementService;
    private final OnlineUserQueryService onlineUserQueryService;
    private final LoginLogService loginLogService;
    private final PermissionService permissionService;

    /**
     * 创建后台认证授权配置处理器。
     *
     * @param jwtConfigService JWT 配置服务
     * @param sessionConfigService 会话配置服务
     * @param securityPolicyService 安全策略服务
     * @param permissionSyncService 权限同步服务
     * @param oauth2ClientManagementService OAuth2 客户端管理服务
     * @param onlineUserQueryService 在线用户查询服务
     * @param loginLogService 登录日志服务
     * @param permissionService 权限服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public AuthConfigHandler(
            SysJwtConfigService jwtConfigService,
            SysSessionConfigService sessionConfigService,
            SysSecurityPolicyService securityPolicyService,
            PermissionSyncService permissionSyncService,
            OAuth2ClientManagementService oauth2ClientManagementService,
            OnlineUserQueryService onlineUserQueryService,
            LoginLogService loginLogService,
            PermissionService permissionService,
            MessageUtils messageUtils,
            Validator validator) {
        super(messageUtils, validator);
        this.jwtConfigService = jwtConfigService;
        this.sessionConfigService = sessionConfigService;
        this.securityPolicyService = securityPolicyService;
        this.permissionSyncService = permissionSyncService;
        this.oauth2ClientManagementService = oauth2ClientManagementService;
        this.onlineUserQueryService = onlineUserQueryService;
        this.loginLogService = loginLogService;
        this.permissionService = permissionService;
    }

    /**
     * 查询当前租户的 JWT 配置。
     *
     * <p>返回 JWT 签发者、签名算法、令牌有效期等配置信息。
     * 密钥字段自动掩码为 "******",不暴露真实密钥。
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应,data 为 JWT 配置 DTO
     */
    @RequirePermission("system:auth:config:jwt:query")
    @Operation(
            operationId = "getJwtConfig",
            summary = "查询 JWT 配置",
            description = "查询当前租户的 JWT 令牌配置,包括签发者、签名算法、令牌有效期等。密钥字段已脱敏")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:auth:config:jwt:query 权限"),
        @ApiResponse(responseCode = "404", description = "JWT 配置不存在")
    })
    @OperationLog(
            module = "AuthConfig",
            type = OperationLog.OperationType.SELECT,
            description = "getJwtConfig",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getJwtConfig(ServerRequest request) {
        return jwtConfigService.getByTenantId().flatMap(this::buildSuccess);
    }

    /**
     * 更新当前租户的 JWT 配置
     *
     * <p>校验请求体后委托 JWT 配置服务更新签发者、签名算法、令牌有效期等参数；更新成功后发布
     * {@code JwtConfigUpdatedEvent} 事件通知相关组件刷新配置，成功结果统一包装为带 traceId 的响应。
     *
     * @param request HTTP 请求，请求体包含 JWT 配置信息
     * @return 带 traceId 的统一成功响应，data 为更新后的 JWT 配置 DTO
     * @throws BusinessException 请求体缺失或校验失败时抛出，错误码为 {@code PARAM_ERROR}
     */
    @RequirePermission("system:auth:config:jwt:edit")
    @Operation(operationId = "updateJwtConfig", summary = "更新 JWT 配置", description = "更新当前租户的 JWT 令牌配置,更新后立即生效")
    @RequestBody(description = "JWT 配置更新请求", required = true, content = @Content(mediaType = "application/json"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:auth:config:jwt:edit 权限"),
        @ApiResponse(responseCode = "404", description = "JWT 配置不存在")
    })
    @OperationLog(
            module = "认证授权配置",
            type = OperationLog.OperationType.UPDATE,
            description = "更新 JWT 配置",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> updateJwtConfig(ServerRequest request) {
        return parseBody(request, JwtConfigRequest.class)
                .flatMap(jwtConfigService::update)
                .flatMap(this::buildSuccess);
    }

    /**
     * 查询当前租户的会话配置。
     *
     * <p>返回会话超时时间、最大并发会话数、并发控制策略、记住我功能等配置信息。
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应,data 为会话配置 DTO
     */
    @RequirePermission("system:auth:config:session:query")
    @Operation(operationId = "getSessionConfig", summary = "查询会话配置", description = "查询当前租户的会话管理配置,包括会话超时、并发控制、记住我功能等")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:auth:config:session:query 权限"),
        @ApiResponse(responseCode = "404", description = "会话配置不存在")
    })
    @OperationLog(
            module = "AuthConfig",
            type = OperationLog.OperationType.SELECT,
            description = "getSessionConfig",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getSessionConfig(ServerRequest request) {
        return sessionConfigService.getByTenantId().flatMap(this::buildSuccess);
    }

    /**
     * 更新当前租户的会话配置
     *
     * <p>校验请求体后委托会话配置服务更新会话超时、最大并发会话数、并发控制策略与记住我等功能，
     * 更新后对新建立的会话立即生效，成功结果统一包装为带 traceId 的响应。
     *
     * @param request HTTP 请求，请求体包含会话配置信息
     * @return 带 traceId 的统一成功响应，data 为更新后的会话配置 DTO
     * @throws BusinessException 请求体缺失或校验失败时抛出，错误码为 {@code PARAM_ERROR}
     */
    @RequirePermission("system:auth:config:session:edit")
    @Operation(operationId = "updateSessionConfig", summary = "更新会话配置", description = "更新当前租户的会话管理配置,更新后对新会话立即生效")
    @RequestBody(description = "会话配置更新请求", required = true, content = @Content(mediaType = "application/json"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:auth:config:session:edit 权限"),
        @ApiResponse(responseCode = "404", description = "会话配置不存在")
    })
    @OperationLog(
            module = "认证授权配置",
            type = OperationLog.OperationType.UPDATE,
            description = "更新会话配置",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> updateSessionConfig(ServerRequest request) {
        return parseBody(request, SessionConfigRequest.class)
                .flatMap(sessionConfigService::update)
                .flatMap(this::buildSuccess);
    }

    /**
     * 查询当前租户的安全策略配置。
     *
     * <p>返回密码策略(最小长度、复杂度要求、过期天数)、登录锁定策略(失败次数、锁定时长)、
     * 验证码策略等配置信息。
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应,data 为安全策略配置 DTO
     */
    @RequirePermission("system:auth:config:security:query")
    @Operation(
            operationId = "getSecurityPolicy",
            summary = "查询安全策略",
            description = "查询当前租户的安全策略配置,包括密码策略、登录锁定策略、验证码策略等")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:auth:config:security:query 权限"),
        @ApiResponse(responseCode = "404", description = "安全策略不存在")
    })
    @OperationLog(
            module = "AuthConfig",
            type = OperationLog.OperationType.SELECT,
            description = "getSecurityPolicy",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getSecurityPolicy(ServerRequest request) {
        return securityPolicyService.getByTenantId().flatMap(this::buildSuccess);
    }

    /**
     * 更新当前租户的安全策略配置
     *
     * <p>校验请求体后委托安全策略服务更新密码策略、登录锁定策略与验证码策略等配置，
     * 更新后对新的登录与密码校验立即生效，成功结果统一包装为带 traceId 的响应。
     *
     * @param request HTTP 请求，请求体包含安全策略配置信息
     * @return 带 traceId 的统一成功响应，data 为更新后的安全策略配置 DTO
     * @throws BusinessException 请求体缺失或校验失败时抛出，错误码为 {@code PARAM_ERROR}
     */
    @RequirePermission("system:auth:config:security:edit")
    @Operation(operationId = "updateSecurityPolicy", summary = "更新安全策略", description = "更新当前租户的安全策略配置,更新后立即生效")
    @RequestBody(description = "安全策略配置更新请求", required = true, content = @Content(mediaType = "application/json"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:auth:config:security:edit 权限"),
        @ApiResponse(responseCode = "404", description = "安全策略不存在")
    })
    @OperationLog(
            module = "认证授权配置",
            type = OperationLog.OperationType.UPDATE,
            description = "更新安全策略",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> updateSecurityPolicy(ServerRequest request) {
        return parseBody(request, SecurityPolicyRequest.class)
                .flatMap(securityPolicyService::update)
                .flatMap(this::buildSuccess);
    }

    /**
     * 同步权限码
     *
     * <p>扫描 Spring 容器中 Bean 方法上的 {@code @RequirePermission} 注解提取权限码，与数据库
     * {@code sys_permission} 表比对，返回新增与废弃两份清单；仅返回差异，是否落库由管理员决定。
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为权限同步结果（含新增与废弃列表）
     */
    @RequirePermission("system:auth:config:permission:sync")
    @Operation(
            operationId = "syncPermissions",
            summary = "同步权限码",
            description = "扫描代码中的 @RequirePermission 注解,对比数据库,返回新增和废弃的权限码清单")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "同步成功",
                content = @Content(schema = @Schema(implementation = PermissionSyncResult.class))),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:auth:config:permission:sync 权限")
    })
    @OperationLog(
            module = "认证授权配置",
            type = OperationLog.OperationType.SELECT,
            description = "同步权限码",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> syncPermissions(ServerRequest request) {
        return permissionSyncService.syncPermissions().flatMap(this::buildSuccess);
    }

    /**
     * 查询 OAuth2 客户端列表（别名路由）
     *
     * <p>解析可选分页参数 page / size（缺省 1 / 20），委托 {@link OAuth2ClientManagementService}
     * 复用既有客户端查询逻辑，将结果收敛为列表后统一包装为带 traceId 的响应。
     *
     * @param request HTTP 请求，支持分页参数 page、size
     * @return 带 traceId 的统一成功响应，data 为 OAuth2 客户端分页列表
     */
    @RequirePermission("system:oauth2:client:list")
    @Operation(
            operationId = "listOauth2ClientsAlias",
            summary = "查询 OAuth2 客户端列表",
            description = "分页查询 OAuth2 客户端列表,支持按客户端 ID 和名称模糊查询")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:oauth2:client:list 权限")
    })
    @OperationLog(
            module = "AuthConfig",
            type = OperationLog.OperationType.SELECT,
            description = "listOauth2Clients",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listOauth2Clients(ServerRequest request) {
        int page = safeParseInt(request.queryParam("page").orElse("1"), 1);
        int size = safeParseInt(request.queryParam("size").orElse("20"), 20);

        return oauth2ClientManagementService
                .listClients(page, size)
                .collectList()
                .flatMap(this::buildSuccess);
    }

    /**
     * 查询在线会话列表（别名路由）
     *
     * <p>解析可选分页参数 page / size（缺省 1 / 20），委托 {@link OnlineUserQueryService}
     * 复用在线用户查询逻辑，结果统一包装为带 traceId 的响应。
     *
     * @param request HTTP 请求，支持分页参数 page、size
     * @return 带 traceId 的统一成功响应，data 为在线用户分页列表
     */
    @RequirePermission("system:online-user:list")
    @Operation(operationId = "listOnlineSessionsAlias", summary = "查询在线会话列表", description = "分页查询最近5分钟内有心跳的在线用户列表")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:online-user:list 权限")
    })
    @OperationLog(
            module = "AuthConfig",
            type = OperationLog.OperationType.SELECT,
            description = "listOnlineSessions",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listOnlineSessions(ServerRequest request) {
        int page = safeParseInt(request.queryParam("page").orElse("1"), 1);
        int size = safeParseInt(request.queryParam("size").orElse("20"), 20);

        return onlineUserQueryService.findOnlineUsers(page, size).flatMap(this::buildSuccess);
    }

    /**
     * 踢出会话（别名路由）
     *
     * <p>本方法仅为兼容旧前端路由的占位实现，不执行真实撤销；实际踢出能力由
     * {@code AdminUserManagementHandler#revokeSessions} 提供，调用方应改用
     * {@code POST /admin-api/v1/users/{userId}/sessions/revoke}。
     *
     * @param request HTTP 请求（内容未被使用，仅保持别名端点签名一致）
     * @return 带 traceId 的统一成功响应，data 为引导调用正确接口的提示信息
     */
    @RequirePermission("staff:session:revoke")
    @Operation(
            operationId = "kickoutSessionAlias",
            summary = "踢出会话",
            description = "强制撤销指定用户的全部会话,使其需要重新登录。建议直接调用 POST /admin-api/v1/users/{userId}/sessions/revoke")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "踢出成功"),
        @ApiResponse(responseCode = "400", description = "用户 ID 格式错误"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 staff:session:revoke 权限"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @OperationLog(
            module = "认证授权配置",
            type = OperationLog.OperationType.UPDATE,
            description = "踢出会话",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> kickoutSession(ServerRequest request) {
        return buildSuccess(java.util.Map.of("message", "请直接调用 POST /admin-api/v1/users/{userId}/sessions/revoke 接口"));
    }

    /**
     * 查询登录日志列表（别名路由）
     *
     * <p>读取可选查询参数 username、status 与分页参数 page / size（缺省 1 / 20），
     * 委托 {@link LoginLogService} 复用登录日志查询逻辑，结果统一包装为带 traceId 的响应。
     *
     * @param request HTTP 请求，支持分页参数 page、size 和查询参数 username、status
     * @return 带 traceId 的统一成功响应，data 为登录日志分页列表
     */
    @RequirePermission("system:log:login:list")
    @Operation(operationId = "listLoginLogsAlias", summary = "查询登录日志列表", description = "分页查询登录日志,支持按账号和状态筛选")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:log:login:list 权限")
    })
    @OperationLog(
            module = "AuthConfig",
            type = OperationLog.OperationType.SELECT,
            description = "listLoginLogs",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listLoginLogs(ServerRequest request) {
        String username = request.queryParam("username").orElse(null);
        Integer status = request.queryParam("status").map(Integer::parseInt).orElse(null);
        int page = safeParseInt(request.queryParam("page").orElse("1"), 1);
        int size = safeParseInt(request.queryParam("size").orElse("20"), 20);

        return loginLogService.search(username, status, page, size).flatMap(this::buildSuccess);
    }

    /**
     * 查询权限列表（别名路由）
     *
     * <p>委托 {@link PermissionService} 复用既有权限查询逻辑，返回系统全部权限定义，
     * 结果统一包装为带 traceId 的响应。
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为权限列表
     */
    @RequirePermission("system:permission:list")
    @Operation(operationId = "listPermissionsAlias", summary = "查询权限列表", description = "查询系统中所有已定义的权限列表")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:permission:list 权限")
    })
    @OperationLog(
            module = "AuthConfig",
            type = OperationLog.OperationType.SELECT,
            description = "listPermissions",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listPermissions(ServerRequest request) {
        return permissionService.getAllPermissions().flatMap(this::buildSuccess);
    }

    /**
     * JWT 配置测试
     *
     * <p>管理员提交测试载荷与可选过期时间后，使用当前租户 JWT 配置生成测试 Token 再解码验证，
     * 返回 Token 字符串、解码结果与验证状态，用于在修改签名算法或密钥后快速确认配置生效。
     *
     * <p>测试 Token 仅供配置诊断，不应用于生产身份认证；操作会记录操作日志便于审计。
     *
     * @param request HTTP 请求，请求体包含测试载荷
     * @return 带 traceId 的统一成功响应，data 为测试结果（Token 和解码信息）
     * @throws BusinessException 请求体缺失或校验失败时抛出，错误码为 {@code PARAM_ERROR}
     */
    @RequirePermission("system:auth:config:jwt:test")
    @Operation(
            operationId = "testJwtConfig",
            summary = "JWT 配置测试",
            description = "使用当前 JWT 配置生成测试 Token 并验证，用于测试配置是否正确")
    @RequestBody(
            description = "JWT 测试请求",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = JwtTestRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "测试成功，返回生成的 Token 和解码信息",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = JwtTestResponse.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:auth:config:jwt:test 权限"),
        @ApiResponse(responseCode = "404", description = "JWT 配置不存在"),
        @ApiResponse(responseCode = "500", description = "JWT 配置错误或测试失败")
    })
    @OperationLog(
            module = "认证授权配置",
            type = OperationLog.OperationType.OTHER,
            description = "JWT 配置测试",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> testJwtConfig(ServerRequest request) {
        return parseBody(request, JwtTestRequest.class)
                .flatMap(jwtConfigService::testJwt)
                .flatMap(this::buildSuccess);
    }
}
