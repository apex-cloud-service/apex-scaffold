package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.AdminUserCreateRequest;
import com.apex.scaffold.application.dto.admin.AdminUserRolesRequest;
import com.apex.scaffold.application.dto.admin.AdminUserStatusRequest;
import com.apex.scaffold.application.dto.admin.AdminUserUpdateRequest;
import com.apex.scaffold.application.service.AdminUserManagementService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
 * 后台用户管理处理器（函数式端点）。
 *
 * <p>超级管理员专用的用户查询及后台角色配置端点，
 * 覆盖用户创建、检索、详情、资料编辑、会话撤销、状态启停与后台角色配置；
 * 除会话撤销外均要求 {@code staff:manage} 权限。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>用户管理：创建、分页检索、查看详情、编辑资料、启用/停用用户</li>
 *   <li>角色配置：查询可配置的后台角色、替换用户的全部后台角色</li>
 *   <li>会话管理：强制撤销目标用户的全部会话</li>
 * </ul>
 *
 * <h2>接口列表</h2>
 * <ul>
 *   <li>GET /admin-api/v1/staff-roles — 查询可配置的后台角色</li>
 *   <li>GET /admin-api/v1/users — 分页检索用户</li>
 *   <li>POST /admin-api/v1/users — 后台创建用户</li>
 *   <li>GET /admin-api/v1/users/{userId} — 查看用户资料</li>
 *   <li>PUT /admin-api/v1/users/{userId} — 编辑用户资料</li>
 *   <li>POST /admin-api/v1/users/{userId}/sessions/revoke — 强制撤销用户全部会话</li>
 *   <li>PUT /admin-api/v1/users/{userId}/status — 启用或停用用户</li>
 *   <li>PUT /admin-api/v1/users/{userId}/roles — 替换用户后台角色</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Component
@Tag(name = "后台-用户管理", description = "超级管理员用户查询和后台角色配置")
public class AdminUserManagementHandler extends BaseHandler {

    private final AdminUserManagementService service;

    /**
     * 创建后台用户管理处理器。
     *
     * @param service 后台用户管理应用服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public AdminUserManagementHandler(
            AdminUserManagementService service, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.service = service;
    }

    /**
     * 后台创建用户。
     *
     * <p>请求体必填并经 Jakarta Bean Validation 校验；创建操作人身份取自当前登录上下文。</p>
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为创建结果
     * @throws BusinessException 当请求体为空时，错误码为 {@code PARAM_ERROR}
     */
    @RequirePermission("staff:manage")
    @Operation(
            operationId = "adminCreateUser",
            summary = "后台创建用户",
            description = "后台创建用户账号，请求体必填并经 Jakarta Bean Validation 校验，创建操作人身份取自当前登录上下文")
    @RequestBody(
            description = "用户创建请求信息",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminUserCreateRequest.class)))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "400", description = "参数校验失败或请求体为空"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 staff:manage 权限")
    })
    @OperationLog(module = "用户管理", type = OperationLog.OperationType.INSERT, description = "后台创建用户")
    public Mono<ServerResponse> create(ServerRequest request) {
        return parseBody(request, AdminUserCreateRequest.class)
                .flatMap(body -> currentOpenId(request).flatMap(openId -> service.create(openId, body)))
                .flatMap(this::buildSuccess);
    }

    /**
     * 分页检索用户。
     *
     * @param request HTTP 请求；{@code page} 缺省 1，{@code size} 缺省 30，
     *                {@code keyword} 与 {@code status} 可缺省
     * @return 带 traceId 的统一成功响应，data 为用户分页结果
     */
    @RequirePermission("staff:manage")
    @Operation(
            operationId = "adminListUsers",
            summary = "分页检索用户",
            description = "支持按关键词搜索和状态过滤的用户分页查询，page 缺省 1，size 缺省 30")
    @Parameters({
        @Parameter(
                name = "page",
                description = "页码，从 1 开始",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "integer", defaultValue = "1")),
        @Parameter(
                name = "size",
                description = "每页大小",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "integer", defaultValue = "30")),
        @Parameter(
                name = "keyword",
                description = "搜索关键词（用户名、手机号等）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")),
        @Parameter(
                name = "status",
                description = "用户状态（0-禁用，1-正常）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "integer"))
    })
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "400", description = "参数错误"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 staff:manage 权限")
    })
    public Mono<ServerResponse> list(ServerRequest request) {
        int page = safeParseInt(request.queryParam("page").orElse(null), 1);
        int size = safeParseInt(request.queryParam("size").orElse(null), 30);
        Integer status = request.queryParam("status")
                .filter(value -> !value.isBlank())
                .map(value -> safeParseInt(value, -1))
                .orElse(null);
        return currentOpenId(request)
                .flatMap(openId ->
                        service.search(openId, request.queryParam("keyword").orElse(null), status, page, size))
                .flatMap(this::buildSuccess);
    }

    /**
     * 查看用户资料。
     *
     * @param request HTTP 请求，路径参数 {@code userId} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为用户资料详情
     * @throws BusinessException 当 {@code userId} 非法时，错误码为 {@code PARAM_ERROR}
     */
    @RequirePermission("staff:manage")
    @Operation(operationId = "adminGetUser", summary = "查看用户资料", description = "根据用户ID查看用户的详细资料信息")
    @Parameter(
            name = "userId",
            description = "用户ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "400", description = "用户ID格式错误"),
        @ApiResponse(responseCode = "404", description = "用户不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 staff:manage 权限")
    })
    @OperationLog(module = "用户管理", type = OperationLog.OperationType.SELECT, description = "查看用户详情")
    public Mono<ServerResponse> detail(ServerRequest request) {
        return currentOpenId(request)
                .flatMap(openId -> service.detail(openId, userId(request)))
                .flatMap(this::buildSuccess);
    }

    /**
     * 编辑用户资料。
     *
     * <p>请求体必填并经 Jakarta Bean Validation 校验后透传给应用服务。</p>
     *
     * @param request HTTP 请求，路径参数 {@code userId} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为更新结果
     * @throws BusinessException 当 {@code userId} 非法或请求体为空时，错误码为 {@code PARAM_ERROR}
     */
    @RequirePermission("staff:manage")
    @Operation(
            operationId = "adminUpdateUser",
            summary = "编辑用户资料",
            description = "编辑指定用户的资料信息，请求体必填并经 Jakarta Bean Validation 校验")
    @Parameter(
            name = "userId",
            description = "用户ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "用户更新请求信息",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminUserUpdateRequest.class)))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "400", description = "参数校验失败或用户ID格式错误"),
        @ApiResponse(responseCode = "404", description = "用户不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 staff:manage 权限")
    })
    @OperationLog(module = "用户管理", type = OperationLog.OperationType.UPDATE, description = "编辑用户资料")
    public Mono<ServerResponse> update(ServerRequest request) {
        Long targetId = userId(request);
        return parseBody(request, AdminUserUpdateRequest.class)
                .flatMap(body -> currentOpenId(request).flatMap(openId -> service.update(openId, targetId, body)))
                .flatMap(this::buildSuccess);
    }

    /**
     * 查询当前操作人可配置的后台角色。
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为可配置角色列表
     */
    @RequirePermission("staff:manage")
    @Operation(operationId = "adminListStaffRoles", summary = "查询可配置的后台角色", description = "查询当前操作人可以配置给其他用户的后台角色列表")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 staff:manage 权限")
    })
    public Mono<ServerResponse> roles(ServerRequest request) {
        return currentOpenId(request).flatMap(service::manageableRoles).flatMap(this::buildSuccess);
    }

    /**
     * 替换用户的全部后台角色。
     *
     * <p>请求体必填并携带角色 ID 列表，经校验后整体替换（而非增量追加），
     * 目标用户 ID 取自路径参数。</p>
     *
     * @param request HTTP 请求，路径参数 {@code userId} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为替换结果
     * @throws BusinessException 当 {@code userId} 非法或请求体为空时，错误码为 {@code PARAM_ERROR}
     */
    @RequirePermission("staff:manage")
    @Operation(
            operationId = "adminReplaceUserRoles",
            summary = "替换用户后台角色",
            description = "替换用户的全部后台角色（整体替换，非增量追加），请求体必填并携带角色ID列表")
    @Parameter(
            name = "userId",
            description = "用户ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "角色ID列表",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminUserRolesRequest.class)))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "替换成功"),
        @ApiResponse(responseCode = "400", description = "参数校验失败或用户ID格式错误"),
        @ApiResponse(responseCode = "404", description = "用户不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 staff:manage 权限")
    })
    @OperationLog(module = "用户管理", type = OperationLog.OperationType.UPDATE, description = "配置用户后台角色")
    public Mono<ServerResponse> replaceRoles(ServerRequest request) {
        Long userId = userId(request);
        return parseBody(request, AdminUserRolesRequest.class)
                .flatMap(body ->
                        currentOpenId(request).flatMap(openId -> service.replaceRoles(openId, userId, body.roleIds())))
                .flatMap(this::buildSuccess);
    }

    /**
     * 强制撤销目标用户的全部会话。
     *
     * @param request HTTP 请求，路径参数 {@code userId} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为 {@code {"revokedTokens": 撤销数}}
     * @throws BusinessException 当 {@code userId} 非法时，错误码为 {@code PARAM_ERROR}
     */
    @RequirePermission("staff:session:revoke")
    @Operation(operationId = "adminRevokeUserSessions", summary = "强制撤销用户全部会话", description = "强制撤销目标用户的全部会话，使其需要重新登录")
    @Parameter(
            name = "userId",
            description = "用户ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "撤销成功，返回撤销的会话数"),
        @ApiResponse(responseCode = "400", description = "用户ID格式错误"),
        @ApiResponse(responseCode = "404", description = "用户不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 staff:session:revoke 权限")
    })
    @OperationLog(module = "用户管理", type = OperationLog.OperationType.UPDATE, description = "强制用户下线")
    public Mono<ServerResponse> revokeSessions(ServerRequest request) {
        return currentOpenId(request)
                .flatMap(openId -> service.revokeSessions(openId, userId(request)))
                .flatMap(deleted -> buildSuccess(java.util.Map.of("revokedTokens", deleted)));
    }

    /**
     * 启用或停用目标用户。
     *
     * <p>请求体必填并经校验；目标用户 ID 取自路径参数。</p>
     *
     * @param request HTTP 请求，路径参数 {@code userId} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为状态更新结果
     * @throws BusinessException 当 {@code userId} 非法或请求体为空时，错误码为 {@code PARAM_ERROR}
     */
    @RequirePermission("staff:manage")
    @Operation(operationId = "adminUpdateUserStatus", summary = "启用或停用用户", description = "启用或停用目标用户，请求体必填并经校验")
    @Parameter(
            name = "userId",
            description = "用户ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "用户状态更新请求信息",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminUserStatusRequest.class)))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "状态更新成功"),
        @ApiResponse(responseCode = "400", description = "参数校验失败或用户ID格式错误"),
        @ApiResponse(responseCode = "404", description = "用户不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 staff:manage 权限")
    })
    @OperationLog(module = "用户管理", type = OperationLog.OperationType.UPDATE, description = "更新用户状态")
    public Mono<ServerResponse> updateStatus(ServerRequest request) {
        Long targetId = userId(request);
        return parseBody(request, AdminUserStatusRequest.class)
                .flatMap(body -> currentOpenId(request)
                        .flatMap(openId -> service.updateStatus(openId, targetId, body.enabled())))
                .flatMap(this::buildSuccess);
    }

    /**
     * 解析并校验路径参数 {@code userId}。
     *
     * @param request HTTP 请求
     * @return 大于 0 的用户 ID
     * @throws BusinessException 当参数缺失、非数字或不大于 0 时，错误码为 {@code PARAM_ERROR}
     */
    private Long userId(ServerRequest request) {
        Long userId = safeParseLong(request.pathVariable("userId"));
        if (userId == null || userId <= 0) throw new BusinessException(ResultCode.PARAM_ERROR, "userId格式错误");
        return userId;
    }
}
