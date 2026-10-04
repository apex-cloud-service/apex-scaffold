package com.apex.scaffold.authcenter.interfaces.handler;

import com.apex.scaffold.authcenter.application.assembler.RoleAssembler;
import com.apex.scaffold.authcenter.application.dto.AssignPermissionsRequest;
import com.apex.scaffold.authcenter.application.dto.RoleDTO;
import com.apex.scaffold.authcenter.application.service.RoleService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.OperationLog.OperationType;
import com.apex.scaffold.common.annotation.RequireAdminRole;
import com.apex.scaffold.common.annotation.RequireResourcePermission;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 角色管理处理器（函数式端点）
 *
 * <p>处理角色相关的HTTP请求，提供完整的RBAC角色管理功能，包括角色的增删改查、
 * 权限分配、权限查询等操作。支持多租户隔离和数据权限范围控制。</p>
 *
 * <h2>主要功能</h2>
 * <ul>
 *   <li>角色创建 - 创建新的角色并定义其权限范围和数据权限</li>
 *   <li>角色更新 - 修改现有角色的基本信息和权限配置</li>
 *   <li>角色删除 - 删除指定角色（检查是否有用户绑定）</li>
 *   <li>角色查询 - 根据ID查询角色详情，包含关联的权限信息</li>
 *   <li>角色列表 - 获取租户下所有角色列表</li>
 *   <li>权限分配 - 为角色批量分配或移除权限</li>
 *   <li>权限查询 - 查询角色当前拥有的所有权限ID</li>
 * </ul>
 *
 * <h2>权限模型说明</h2>
 * <ul>
 *   <li>角色编码 - 全局唯一标识，如 ROLE_ADMIN、ROLE_USER</li>
 *   <li>数据权限范围 - 0=全部数据 1=本部门及子部门 2=本部门 3=仅本人</li>
 *   <li>角色状态 - 0=禁用 1=启用，禁用后该角色下的用户权限失效</li>
 *   <li>多租户隔离 - 每个租户有独立的角色体系</li>
 * </ul>
 *
 * <h2>安全特性</h2>
 * <ul>
 *   <li>角色编码唯一性校验 - 同一租户下角色编码不能重复</li>
 *   <li>级联删除保护 - 删除角色前检查是否有用户绑定</li>
 *   <li>权限变更审计 - 记录角色权限的所有变更操作</li>
 *   <li>乐观锁 - 使用版本号防止并发修改冲突</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2024-01-01
 * @see RoleService
 * @see RoleAssembler
 */
@Component
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "RBAC角色管理相关接口,包括角色增删改查和权限分配")
public class RoleHandler {

    private final RoleService roleService;
    private final RoleAssembler roleAssembler;

    /**
     * 创建角色
     *
     * <p>创建新的角色并定义其权限范围、数据权限和启用状态。
     * 角色编码在同一租户下必须唯一，建议使用 ROLE_ 前缀命名规范。</p>
     *
     * <h3>创建流程</h3>
     * <ol>
     *   <li>验证角色编码的唯一性（租户级别）</li>
     *   <li>验证必填字段（code、name）</li>
     *   <li>保存角色基本信息</li>
     *   <li>记录创建审计日志</li>
     * </ol>
     *
     * <h3>字段说明</h3>
     * <ul>
     *   <li>code - 角色编码，建议格式 ROLE_XXX，如 ROLE_ADMIN</li>
     *   <li>name - 角色名称，用于界面展示</li>
     *   <li>description - 角色描述，可选</li>
     *   <li>dataScope - 数据权限范围（0=全部 1=本部门及子部门 2=本部门 3=本人）</li>
     *   <li>enabled - 启用状态（0=禁用 1=启用）</li>
     *   <li>tenantId - 租户ID，支持多租户隔离</li>
     * </ul>
     *
     * @param request 服务器请求对象，包含角色信息
     * @return 创建成功的角色信息，包含自动生成的ID和时间戳
     */
    @OperationLog(module = "角色管理", type = OperationType.INSERT, description = "创建角色")
    @Operation(operationId = "createRole", summary = "创建角色", description = "创建新的角色并定义其权限范围和数据权限。角色编码在同一租户下必须唯一。")
    @ApiResponse(
            responseCode = "200",
            description = "创建成功,返回角色详情",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoleDTO.class)))
    @ApiResponse(
            responseCode = "400",
            description = "参数校验失败 - 角色编码重复或必填字段缺失",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "500",
            description = "系统内部错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "角色信息,必填字段: code(角色编码)、name(角色名称)",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoleDTO.class)))
    public Mono<ServerResponse> createRole(ServerRequest request) {
        return request.bodyToMono(RoleDTO.class)
                .flatMap(dto -> roleService.createRole(roleAssembler.toEntity(dto)))
                .map(roleAssembler::toDto)
                .flatMap(this::buildSuccess)
                .onErrorResume(this::handleError);
    }

    /**
     * 更新角色
     *
     * <p>修改现有角色的基本信息和权限配置。支持部分更新，未提供的字段保持不变。
     * 使用乐观锁（version字段）防止并发修改冲突。</p>
     *
     * <h3>更新流程</h3>
     * <ol>
     *   <li>验证角色是否存在</li>
     *   <li>检查版本号（乐观锁）</li>
     *   <li>如果修改角色编码，验证新编码的唯一性</li>
     *   <li>更新角色信息</li>
     *   <li>自动递增版本号</li>
     *   <li>记录更新审计日志</li>
     * </ol>
     *
     * <h3>注意事项</h3>
     * <ul>
     *   <li>角色ID必须存在且属于当前租户</li>
     *   <li>角色编码修改后仍需保持租户内唯一</li>
     *   <li>禁用角色后，该角色下的用户权限立即失效</li>
     *   <li>如果版本号不匹配会返回409冲突错误</li>
     * </ul>
     *
     * @param request 服务器请求对象，路径参数包含角色ID，请求体包含更新信息
     * @return 更新成功的角色信息，包含新的版本号
     */
    @OperationLog(module = "角色管理", type = OperationType.UPDATE, description = "更新角色")
    @Operation(operationId = "updateRole", summary = "更新角色", description = "修改现有角色信息,使用乐观锁防止并发冲突。支持部分更新。")
    @Parameter(name = "id", description = "角色ID", required = true, in = ParameterIn.PATH, example = "1")
    @ApiResponse(
            responseCode = "200",
            description = "更新成功,返回更新后的角色详情",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoleDTO.class)))
    @ApiResponse(
            responseCode = "400",
            description = "参数校验失败",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "404",
            description = "角色不存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "409",
            description = "版本冲突 - 角色已被其他用户修改",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "角色更新信息,包含需要修改的字段和当前版本号",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoleDTO.class)))
    public Mono<ServerResponse> updateRole(ServerRequest request) {
        String id = request.pathVariable("id");
        return request.bodyToMono(RoleDTO.class)
                .flatMap(dto -> {
                    dto.setId(Long.parseLong(id));
                    return roleService.updateRole(roleAssembler.toEntity(dto));
                })
                .map(roleAssembler::toDto)
                .flatMap(this::buildSuccess)
                .onErrorResume(this::handleError);
    }

    /**
     * 删除角色
     *
     * <p>删除指定的角色。删除前会检查是否有用户绑定该角色，
     * 如果存在用户绑定则拒绝删除，需要先解除用户绑定关系。</p>
     *
     * <h3>删除流程</h3>
     * <ol>
     *   <li>验证角色是否存在</li>
     *   <li>检查是否有用户绑定该角色</li>
     *   <li>如果有用户绑定，返回错误（级联删除保护）</li>
     *   <li>删除角色权限关联关系</li>
     *   <li>删除角色记录</li>
     *   <li>记录删除审计日志</li>
     * </ol>
     *
     * <h3>安全保护</h3>
     * <ul>
     *   <li>系统预置角色（如 ROLE_ADMIN）不允许删除</li>
     *   <li>有用户绑定的角色不允许删除</li>
     *   <li>删除操作需要管理员权限</li>
     *   <li>软删除方式保留审计记录</li>
     * </ul>
     *
     * @param request 服务器请求对象，路径参数包含角色ID
     * @return 删除成功响应
     */
    @OperationLog(module = "角色管理", type = OperationType.DELETE, description = "删除角色")
    @Operation(operationId = "deleteRole", summary = "删除角色", description = "删除指定角色。如果有用户绑定该角色则拒绝删除,需先解除用户绑定。")
    @Parameter(name = "id", description = "角色ID", required = true, in = ParameterIn.PATH, example = "1")
    @ApiResponse(responseCode = "200", description = "删除成功")
    @ApiResponse(
            responseCode = "400",
            description = "角色正在使用中,无法删除",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "404",
            description = "角色不存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "403",
            description = "系统预置角色不允许删除",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> deleteRole(ServerRequest request) {
        String id = request.pathVariable("id");
        return roleService
                .deleteRole(Long.parseLong(id))
                .then(buildSuccess(null))
                .onErrorResume(this::handleError);
    }

    /**
     * 根据ID查询角色
     *
     * <p>查询指定ID的角色详细信息，包含角色基本信息和关联的权限ID集合。
     * 只能查询当前租户下的角色。</p>
     *
     * <h3>返回信息</h3>
     * <ul>
     *   <li>角色ID和基本信息（编码、名称、描述）</li>
     *   <li>数据权限范围配置</li>
     *   <li>启用状态</li>
     *   <li>关联的权限ID集合</li>
     *   <li>创建和更新时间</li>
     *   <li>版本号（用于乐观锁）</li>
     * </ul>
     *
     * @param request 服务器请求对象，路径参数包含角色ID
     * @return 角色详细信息
     */
    @OperationLog(module = "角色管理", type = OperationType.SELECT, description = "查询角色详情")
    @Operation(operationId = "getRoleById", summary = "查询角色详情", description = "根据ID查询角色详细信息,包含基本信息和关联的权限ID集合")
    @Parameter(name = "id", description = "角色ID", required = true, in = ParameterIn.PATH, example = "1")
    @ApiResponse(
            responseCode = "200",
            description = "查询成功,返回角色详情",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoleDTO.class)))
    @ApiResponse(
            responseCode = "404",
            description = "角色不存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> getRoleById(ServerRequest request) {
        String id = request.pathVariable("id");
        return roleService
                .getRoleById(Long.parseLong(id))
                .map(roleAssembler::toDto)
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildSuccess(null))
                .onErrorResume(this::handleError);
    }

    /**
     * 获取角色列表
     *
     * <p>获取当前租户下所有角色的列表。支持查询所有角色或仅查询启用状态的角色。
     * 返回的列表包含角色的基本信息，不包含详细的权限关联信息。</p>
     *
     * <h3>查询特性</h3>
     * <ul>
     *   <li>自动按租户过滤，只返回当前租户的角色</li>
     *   <li>默认按创建时间倒序排列</li>
     *   <li>包含角色的启用状态和数据权限范围</li>
     *   <li>不分页，返回全量数据（适合下拉选择场景）</li>
     * </ul>
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>角色管理界面的列表展示</li>
     *   <li>用户分配角色时的下拉选择</li>
     *   <li>权限配置界面的角色选择器</li>
     * </ul>
     *
     * @param request 服务器请求对象
     * @return 角色列表
     */
    @OperationLog(module = "角色管理", type = OperationType.SELECT, description = "获取角色列表")
    @Operation(operationId = "listRoles", summary = "获取角色列表", description = "获取当前租户下的所有角色列表,按创建时间倒序排列")
    @ApiResponse(
            responseCode = "200",
            description = "查询成功,返回角色列表",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "array", implementation = RoleDTO.class)))
    public Mono<ServerResponse> listRoles(ServerRequest request) {
        return roleService
                .listRoles()
                .map(roleAssembler::toDto)
                .collectList()
                .flatMap(this::buildSuccess)
                .onErrorResume(this::handleError);
    }

    /**
     * 为角色分配权限
     *
     * <p>批量为指定角色分配权限。此操作会覆盖角色原有的权限配置，
     * 即删除所有旧权限关联并建立新的权限关联。</p>
     *
     * <h3>分配流程</h3>
     * <ol>
     *   <li>验证角色是否存在且属于当前租户</li>
     *   <li>验证所有权限ID的有效性</li>
     *   <li>删除角色的所有现有权限关联</li>
     *   <li>批量创建新的角色-权限关联关系</li>
     *   <li>清除相关用户的权限缓存</li>
     *   <li>记录权限变更审计日志</li>
     * </ol>
     *
     * <h3>注意事项</h3>
     * <ul>
     *   <li>这是覆盖操作，不是追加操作</li>
     *   <li>如果传入空数组，表示移除该角色的所有权限</li>
     *   <li>权限ID必须存在且属于当前租户</li>
     *   <li>分配后立即生效，拥有该角色的用户权限即时更新</li>
     *   <li>系统预置角色的权限分配可能受到限制</li>
     * </ul>
     *
     * <h3>请求体示例</h3>
     * <pre>{@code
     * {
     *   "roleId": 1,
     *   "permissionIds": [1, 2, 3, 5, 8]
     * }
     * }</pre>
     *
     * @param request 服务器请求对象，请求体包含角色ID和权限ID集合
     * @return 分配成功响应
     */
    @OperationLog(module = "角色管理", type = OperationType.UPDATE, description = "为角色分配权限")
    @RequireAdminRole
    @RequireResourcePermission(resourceType = "role", resourceIdExpression = "#request.roleId", permission = "MANAGE", errorMessage = "无权修改该角色权限")
    @Operation(
            operationId = "assignPermissions",
            summary = "为角色分配权限",
            description = "批量为角色分配权限,覆盖原有权限配置。如果传入空数组则移除该角色的所有权限。")
    @ApiResponse(responseCode = "200", description = "权限分配成功,拥有该角色的用户权限立即生效")
    @ApiResponse(
            responseCode = "400",
            description = "参数校验失败 - 角色ID或权限ID无效",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "404",
            description = "角色不存在或权限不存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "权限分配请求,包含角色ID和权限ID集合",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AssignPermissionsRequest.class)))
    public Mono<ServerResponse> assignPermissions(AssignPermissionsRequest request) {
        return roleService.assignPermissions(request.getRoleId(), request.getPermissionIds())
                .then(buildSuccess(null))
                .onErrorResume(this::handleError);
    }

    /**
     * 查询角色的权限
     *
     * <p>查询指定角色当前拥有的所有权限ID集合。
     * 返回的是权限ID列表，不包含权限的详细信息。</p>
     *
     * <h3>返回数据</h3>
     * <ul>
     *   <li>权限ID集合（Set&lt;Long&gt;）</li>
     *   <li>去重且无序</li>
     *   <li>只包含启用状态的权限</li>
     *   <li>如果角色无权限则返回空集合</li>
     * </ul>
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>编辑角色时回显已选中的权限</li>
     *   <li>权限配置界面的数据回填</li>
     *   <li>权限对比和差异分析</li>
     * </ul>
     *
     * @param request 服务器请求对象，路径参数包含角色ID
     * @return 权限ID集合
     */
    @OperationLog(module = "角色管理", type = OperationType.SELECT, description = "查询角色的权限")
    @Operation(
            operationId = "getRolePermissions",
            summary = "查询角色的权限",
            description = "查询指定角色当前拥有的所有权限ID集合,只返回权限ID不包含详细信息")
    @Parameter(name = "id", description = "角色ID", required = true, in = ParameterIn.PATH, example = "1")
    @ApiResponse(
            responseCode = "200",
            description = "查询成功,返回权限ID集合",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "array", implementation = Long.class)))
    @ApiResponse(
            responseCode = "404",
            description = "角色不存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> getRolePermissions(ServerRequest request) {
        String id = request.pathVariable("id");
        return roleService
                .getRolePermissions(Long.parseLong(id))
                .flatMap(this::buildSuccess)
                .onErrorResume(this::handleError);
    }

    /**
     * 构建成功响应
     *
     * @param data 响应数据
     * @return ServerResponse
     */
    private Mono<ServerResponse> buildSuccess(Object data) {
        Result<Object> result = Result.success(data, TraceIdUtil.getTraceId());
        return ServerResponse.ok().bodyValue(result);
    }

    /**
     * 处理错误
     *
     * @param throwable 异常对象
     * @return ServerResponse
     */
    private Mono<ServerResponse> handleError(Throwable throwable) {
        Result<Object> result =
                Result.error(ResultCode.ERROR.getCode(), throwable.getMessage(), TraceIdUtil.getTraceId());
        return ServerResponse.ok().bodyValue(result);
    }
}
