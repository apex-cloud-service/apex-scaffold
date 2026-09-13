package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.assembler.PermissionAssembler;
import com.apex.scaffold.application.assembler.RoleAssembler;
import com.apex.scaffold.application.dto.PermissionRequest;
import com.apex.scaffold.application.dto.RoleRequest;
import com.apex.scaffold.application.service.PermissionService;
import com.apex.scaffold.application.service.RoleService;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 授权管理处理器（函数式端点）。
 *
 * <p>提供角色和权限的管理接口，包括角色/权限的 CRUD、用户的角色分配与移除、
 * 当前用户的角色/权限查询。具体业务逻辑由
 * {@link com.apex.scaffold.application.service.RoleService}、
 * {@link com.apex.scaffold.application.service.PermissionService} 和
 * {@link com.apex.scaffold.application.service.UserService} 协作实现。
 * 路径参数中的数字字段通过 {@link #safeParseLong(String)} 安全解析，
 * 解析失败时返回 400 错误响应。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /api/v1/auth/roles/my - 获取当前用户角色</li>
 *   <li>GET /api/v1/auth/permissions/my - 获取当前用户权限</li>
 *   <li>GET /api/v1/auth/roles - 获取所有角色</li>
 *   <li>GET /api/v1/auth/permissions - 获取所有权限</li>
 *   <li>POST /api/v1/auth/roles - 创建角色</li>
 *   <li>POST /api/v1/auth/roles/update - 更新角色</li>
 *   <li>DELETE /api/v1/auth/roles/{id} - 删除角色</li>
 *   <li>POST /api/v1/auth/permissions - 创建权限</li>
 *   <li>POST /api/v1/auth/permissions/update - 更新权限</li>
 *   <li>DELETE /api/v1/auth/permissions/{id} - 删除权限</li>
 *   <li>POST /api/v1/auth/users/{userId}/roles/{roleId} - 分配角色</li>
 *   <li>DELETE /api/v1/auth/users/{userId}/roles/{roleId} - 移除角色</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Slf4j
@Component
@Tag(name = "授权管理", description = "角色和权限管理相关接口")
public class AuthorizationHandler extends BaseHandler {

    private final RoleService roleService;
    private final PermissionService permissionService;
    private final UserService userService;
    private final RoleAssembler roleAssembler;
    private final PermissionAssembler permissionAssembler;

    /**
     * 构造授权管理处理器
     *
     * @param roleService 角色服务
     * @param permissionService 权限服务
     * @param userService 用户服务
     * @param roleAssembler 角色装配器
     * @param permissionAssembler 权限装配器
     * @param messageUtils 国际化消息工具
     */
    public AuthorizationHandler(
            RoleService roleService,
            PermissionService permissionService,
            UserService userService,
            RoleAssembler roleAssembler,
            PermissionAssembler permissionAssembler,
            MessageUtils messageUtils) {
        super(messageUtils);
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.userService = userService;
        this.roleAssembler = roleAssembler;
        this.permissionAssembler = permissionAssembler;
    }

    /**
     * 获取当前登录用户的角色列表。
     *
     * <p>从鉴权主体解析 openId，查询用户实体后按 userId 获取其角色列表，最终转换为 DTO 返回。
     * 当用户没有任何角色时，返回空列表。
     *
     * @param request HTTP 请求（用于解析鉴权主体 openId）
     * @return 角色列表的标准响应
     */
    @Operation(operationId = "getMyRoles", summary = "获取当前用户角色", description = "返回当前登录用户的所有角色信息，包括角色代码、角色名称等")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<ServerResponse> getMyRoles(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return currentOpenId(request)
                .doOnNext(openId -> log.info("获取当前用户角色，openId: {}, traceId: {}", openId, traceId))
                .flatMap(userService::getByOpenId)
                .flatMap(user -> roleService.getRolesByUserId(user.getId()))
                .map(roleAssembler::toDTOList)
                .map(roles -> Result.success(roles, traceId))
                .switchIfEmpty(Mono.just(Result.success(List.of(), traceId)))
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    /**
     * 获取当前登录用户的权限列表。
     *
     * <p>从鉴权主体解析 openId，查询用户实体后按 userId 获取其权限列表，最终转换为 DTO 返回。
     * 当用户没有任何权限时，返回空列表。
     *
     * @param request HTTP 请求（用于解析鉴权主体 openId）
     * @return 权限列表的标准响应
     */
    @Operation(
            operationId = "getMyPermissions",
            summary = "获取当前用户权限",
            description = "返回当前登录用户的所有权限信息，包括权限代码、权限名称、资源标识等")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<ServerResponse> getMyPermissions(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return currentOpenId(request)
                .doOnNext(openId -> log.info("获取当前用户权限，openId: {}, traceId: {}", openId, traceId))
                .flatMap(userService::getByOpenId)
                .flatMap(user -> permissionService.getPermissionsByUserId(user.getId()))
                .map(permissionAssembler::toDTOList)
                .map(permissions -> Result.success(permissions, traceId))
                .switchIfEmpty(Mono.just(Result.success(List.of(), traceId)))
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    /**
     * 获取所有角色列表。
     *
     * <p>查询系统中所有已定义的角色，用于角色管理界面展示。
     *
     * @param request HTTP 请求
     * @return 角色列表的标准响应
     */
    @Operation(operationId = "getAllRoles", summary = "获取所有角色", description = "查询系统中所有已定义的角色列表，用于角色管理界面展示和角色分配选择")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<ServerResponse> getAllRoles(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return roleService
                .getAllRoles()
                .map(roleAssembler::toDTOList)
                .map(roles -> Result.success(roles, traceId))
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    /**
     * 获取所有权限列表。
     *
     * <p>查询系统中所有已定义的权限，用于权限管理界面展示。
     *
     * @param request HTTP 请求
     * @return 权限列表的标准响应
     */
    @Operation(operationId = "getAllPermissions", summary = "获取所有权限", description = "查询系统中所有已定义的权限列表，用于权限管理界面展示和权限配置")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public Mono<ServerResponse> getAllPermissions(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return permissionService
                .getAllPermissions()
                .map(permissionAssembler::toDTOList)
                .map(permissions -> Result.success(permissions, traceId))
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    /**
     * 创建角色。
     *
     * <p>从请求体读取 {@link RoleRequest}，交由业务层创建角色并返回创建后的角色 DTO。
     *
     * @param request HTTP 请求（包含角色创建请求体）
     * @return 创建结果的标准响应
     */
    @Operation(operationId = "createRole", summary = "创建角色", description = "创建一个新的角色，包括角色代码、角色名称、描述等信息")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "角色创建请求信息",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoleRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败或角色代码已存在"),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要角色管理权限"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @OperationLog(module = "权限管理", type = OperationLog.OperationType.INSERT, description = "创建角色")
    public Mono<ServerResponse> createRole(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return request.bodyToMono(RoleRequest.class)
                .doOnNext(body ->
                        log.info("创建角色，code: {}, name: {}, traceId: {}", body.getCode(), body.getName(), traceId))
                .flatMap(body -> roleService.createRole(roleAssembler.fromRequest(body)))
                .map(roleAssembler::toDTO)
                .map(created -> Result.success(created, traceId))
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    /**
     * 更新角色。
     *
     * <p>从请求体读取 {@link RoleRequest}，交由业务层更新角色并返回更新后的角色 DTO。
     *
     * @param request HTTP 请求（包含角色更新请求体，必须包含 id）
     * @return 更新结果的标准响应
     */
    @Operation(operationId = "updateRole", summary = "更新角色", description = "更新已有角色的信息，支持更新角色代码、名称、描述等字段")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "角色更新请求信息",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoleRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "404", description = "角色不存在"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @OperationLog(module = "权限管理", type = OperationLog.OperationType.UPDATE, description = "更新角色")
    public Mono<ServerResponse> updateRole(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return request.bodyToMono(RoleRequest.class)
                .doOnNext(body -> log.info("更新角色，id: {}, code: {}, traceId: {}", body.getId(), body.getCode(), traceId))
                .flatMap(body -> roleService.updateRole(roleAssembler.fromRequest(body)))
                .map(roleAssembler::toDTO)
                .map(updated -> Result.success(updated, traceId))
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    /**
     * 删除角色。
     *
     * <p>从路径参数读取角色 id，交由业务层执行删除（包括清理关联关系等）。
     *
     * @param request HTTP 请求（包含 pathVariable id）
     * @return 删除结果的标准响应
     */
    @Operation(operationId = "deleteRole", summary = "删除角色", description = "删除指定的角色，同时清理该角色的所有关联关系")
    @Parameter(
            name = "id",
            description = "角色ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "删除成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "400", description = "参数错误或路径参数无效"),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "404", description = "角色不存在"),
        @ApiResponse(responseCode = "409", description = "角色存在关联数据，无法删除"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @OperationLog(module = "权限管理", type = OperationLog.OperationType.DELETE, description = "删除角色")
    public Mono<ServerResponse> deleteRole(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long id = safeParseLong(request.pathVariable("id"));
        if (id == null) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Result.error(ResultCode.PARAM_ERROR.getCode(), "invalid id", traceId));
        }
        log.info("删除角色，id: {}, traceId: {}", id, traceId);
        return roleService
                .deleteRole(id)
                .map(ignore -> {
                    Result<Void> result = Result.success();
                    result.setTraceId(traceId);
                    return result;
                })
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    /**
     * 创建权限。
     *
     * <p>从请求体读取 {@link PermissionRequest}，交由业务层创建权限并返回创建后的权限 DTO。
     *
     * @param request HTTP 请求（包含权限创建请求体）
     * @return 创建结果的标准响应
     */
    @Operation(operationId = "createPermission", summary = "创建权限", description = "创建一个新的权限，包括权限代码、权限名称、资源标识等信息")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "权限创建请求信息",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PermissionRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败或权限代码已存在"),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要权限管理权限"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @OperationLog(module = "权限管理", type = OperationLog.OperationType.INSERT, description = "创建权限")
    public Mono<ServerResponse> createPermission(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return request.bodyToMono(PermissionRequest.class)
                .doOnNext(body ->
                        log.info("创建权限，code: {}, name: {}, traceId: {}", body.getCode(), body.getName(), traceId))
                .flatMap(body -> permissionService.createPermission(permissionAssembler.fromRequest(body)))
                .map(permissionAssembler::toDTO)
                .map(created -> Result.success(created, traceId))
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    /**
     * 更新权限。
     *
     * <p>从请求体读取 {@link PermissionRequest}，交由业务层更新权限并返回更新后的权限 DTO。
     *
     * @param request HTTP 请求（包含权限更新请求体，必须包含 id）
     * @return 更新结果的标准响应
     */
    @Operation(operationId = "updatePermission", summary = "更新权限", description = "更新已有权限的信息，支持更新权限代码、名称、资源标识等字段")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "权限更新请求信息",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PermissionRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "404", description = "权限不存在"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @OperationLog(module = "权限管理", type = OperationLog.OperationType.UPDATE, description = "更新权限")
    public Mono<ServerResponse> updatePermission(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return request.bodyToMono(PermissionRequest.class)
                .doOnNext(body -> log.info("更新权限，id: {}, code: {}, traceId: {}", body.getId(), body.getCode(), traceId))
                .flatMap(body -> permissionService.updatePermission(permissionAssembler.fromRequest(body)))
                .map(permissionAssembler::toDTO)
                .map(updated -> Result.success(updated, traceId))
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    /**
     * 删除权限。
     *
     * <p>从路径参数读取权限 id，交由业务层执行删除。
     *
     * @param request HTTP 请求（包含 pathVariable id）
     * @return 删除结果的标准响应
     */
    @Operation(operationId = "deletePermission", summary = "删除权限", description = "删除指定的权限，同时清理该权限的关联关系")
    @Parameter(
            name = "id",
            description = "权限ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "删除成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "400", description = "参数错误或路径参数无效"),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "404", description = "权限不存在"),
        @ApiResponse(responseCode = "409", description = "权限存在关联数据，无法删除"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @OperationLog(module = "权限管理", type = OperationLog.OperationType.DELETE, description = "删除权限")
    public Mono<ServerResponse> deletePermission(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long id = safeParseLong(request.pathVariable("id"));
        if (id == null) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Result.error(ResultCode.PARAM_ERROR.getCode(), "invalid id", traceId));
        }
        log.info("删除权限，id: {}, traceId: {}", id, traceId);
        return permissionService
                .deletePermission(id)
                .map(ignore -> {
                    Result<Void> result = Result.success();
                    result.setTraceId(traceId);
                    return result;
                })
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    /**
     * 分配角色。
     *
     * <p>为用户分配指定角色。路径参数 userId 和 roleId 使用 {@link #safeParseLong}
     * 安全解析，无效参数返回 400 错误。
     *
     * @param request HTTP 请求（包含 pathVariable userId 和 roleId）
     * @return 分配结果的标准响应
     */
    @Operation(operationId = "assignRoleToUser", summary = "分配角色", description = "为指定用户分配角色，建立用户与角色的关联关系")
    @Parameters({
        @Parameter(
                name = "userId",
                description = "用户ID",
                required = true,
                in = ParameterIn.PATH,
                schema = @Schema(type = "integer", format = "int64")),
        @Parameter(
                name = "roleId",
                description = "角色ID",
                required = true,
                in = ParameterIn.PATH,
                schema = @Schema(type = "integer", format = "int64"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "分配成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "400", description = "参数错误或路径参数无效"),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "404", description = "用户或角色不存在"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @OperationLog(module = "权限管理", type = OperationLog.OperationType.UPDATE, description = "为用户分配角色")
    public Mono<ServerResponse> assignRoleToUser(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long userId = safeParseLong(request.pathVariable("userId"));
        if (userId == null) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Result.error(ResultCode.PARAM_ERROR.getCode(), "invalid userId", traceId));
        }
        Long roleId = safeParseLong(request.pathVariable("roleId"));
        if (roleId == null) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Result.error(ResultCode.PARAM_ERROR.getCode(), "invalid roleId", traceId));
        }
        log.info("为用户分配角色，userId: {}, roleId: {}, traceId: {}", userId, roleId, traceId);
        return roleService
                .assignRoleToUser(userId, roleId)
                .map(ignore -> {
                    Result<Void> result = Result.success();
                    result.setTraceId(traceId);
                    return result;
                })
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    /**
     * 移除用户的指定角色。
     *
     * <p>从路径参数读取 userId 与 roleId，交由业务层移除该用户的角色关联关系。
     *
     * @param request HTTP 请求（包含 pathVariable userId/roleId）
     * @return 移除结果的标准响应
     */
    @Operation(operationId = "removeRoleFromUser", summary = "移除角色", description = "移除用户的指定角色，解除用户与角色的关联关系")
    @Parameters({
        @Parameter(
                name = "userId",
                description = "用户ID",
                required = true,
                in = ParameterIn.PATH,
                schema = @Schema(type = "integer", format = "int64")),
        @Parameter(
                name = "roleId",
                description = "角色ID",
                required = true,
                in = ParameterIn.PATH,
                schema = @Schema(type = "integer", format = "int64"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "移除成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "400", description = "参数错误或路径参数无效"),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足"),
        @ApiResponse(responseCode = "404", description = "用户或角色不存在"),
        @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @OperationLog(module = "权限管理", type = OperationLog.OperationType.UPDATE, description = "移除用户角色")
    public Mono<ServerResponse> removeRoleFromUser(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long userId = safeParseLong(request.pathVariable("userId"));
        if (userId == null) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Result.error(ResultCode.PARAM_ERROR.getCode(), "invalid userId", traceId));
        }
        Long roleId = safeParseLong(request.pathVariable("roleId"));
        if (roleId == null) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Result.error(ResultCode.PARAM_ERROR.getCode(), "invalid roleId", traceId));
        }
        log.info("移除用户角色，userId: {}, roleId: {}, traceId: {}", userId, roleId, traceId);
        return roleService
                .removeRoleFromUser(userId, roleId)
                .map(ignore -> {
                    Result<Void> result = Result.success();
                    result.setTraceId(traceId);
                    return result;
                })
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }
}
