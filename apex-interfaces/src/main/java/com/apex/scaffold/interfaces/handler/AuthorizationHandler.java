package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.assembler.PermissionAssembler;
import com.apex.scaffold.application.assembler.RoleAssembler;
import com.apex.scaffold.application.dto.PermissionRequest;
import com.apex.scaffold.application.dto.RoleRequest;
import com.apex.scaffold.application.service.PermissionService;
import com.apex.scaffold.application.service.RoleService;
import com.apex.scaffold.application.service.UserService;
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
 * 当前用户的角色/权限查询以及权限检查。
 * 具体业务逻辑由 {@link com.apex.scaffold.application.service.RoleService}、
 * {@link com.apex.scaffold.application.service.PermissionService} 和
 * {@link com.apex.scaffold.application.service.UserService} 协作实现。
 *
 * <p>路由设计：
 * <ul>
 *   <li>GET /authorization/my-roles — 获取当前用户角色</li>
 *   <li>GET /authorization/my-permissions — 获取当前用户权限</li>
 *   <li>GET /authorization/check-permission — 检查权限</li>
 *   <li>GET /authorization/roles — 获取所有角色</li>
 *   <li>GET /authorization/permissions — 获取所有权限</li>
 *   <li>POST /authorization/roles — 创建角色</li>
 *   <li>PUT /authorization/roles — 更新角色</li>
 *   <li>DELETE /authorization/roles/{id} — 删除角色</li>
 *   <li>POST /authorization/permissions — 创建权限</li>
 *   <li>PUT /authorization/permissions — 更新权限</li>
 *   <li>DELETE /authorization/permissions/{id} — 删除权限</li>
 *   <li>POST /authorization/users/{userId}/roles/{roleId} — 分配角色</li>
 *   <li>DELETE /authorization/users/{userId}/roles/{roleId} — 移除角色</li>
 * </ul>
 *
 * <p>异常处理模式：路径参数中的数字字段通过 {@link #safeParseLong(String)}
 * 安全解析，解析失败时返回 400 错误响应。
 *
 * @author Apex Team
 * @version 2.0.0
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
    @Operation(summary = "获取当前用户角色", description = "获取当前登录用户的所有角色")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "未登录",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "服务器内部错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
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
    @Operation(summary = "获取当前用户权限", description = "获取当前登录用户的所有权限")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "未登录",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "服务器内部错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
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
    @Operation(summary = "获取所有角色", description = "查询系统中所有角色")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "未登录",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "服务器内部错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
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
    @Operation(summary = "获取所有权限", description = "查询系统中所有权限")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "未登录",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "服务器内部错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
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
    @Operation(summary = "创建角色", description = "创建新的角色")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "操作成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "未登录",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "服务器内部错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
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
    @Operation(summary = "更新角色", description = "更新已有角色信息")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "操作成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "未登录",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "服务器内部错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
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
    @Operation(summary = "删除角色", description = "删除指定的角色")
    @Parameters({@Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "角色ID")})
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "操作成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误（含路径参数无效）",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "未登录",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "服务器内部错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
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
    @Operation(summary = "创建权限", description = "创建新的权限")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "操作成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "未登录",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "服务器内部错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
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
    @Operation(summary = "更新权限", description = "更新已有权限信息")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "操作成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "未登录",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "服务器内部错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
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
    @Operation(summary = "删除权限", description = "删除指定的权限")
    @Parameters({@Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "权限ID")})
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "操作成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误（含路径参数无效）",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "未登录",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "服务器内部错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
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
    @Operation(summary = "分配角色", description = "为用户分配指定角色")
    @Parameters({
        @Parameter(name = "userId", in = ParameterIn.PATH, required = true, description = "用户ID"),
        @Parameter(name = "roleId", in = ParameterIn.PATH, required = true, description = "角色ID")
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "操作成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误（含路径参数无效）",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "未登录",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "服务器内部错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
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
    @Operation(summary = "移除角色", description = "移除用户的指定角色")
    @Parameters({
        @Parameter(name = "userId", in = ParameterIn.PATH, required = true, description = "用户ID"),
        @Parameter(name = "roleId", in = ParameterIn.PATH, required = true, description = "角色ID")
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "操作成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误（含路径参数无效）",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "未登录",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "服务器内部错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
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
