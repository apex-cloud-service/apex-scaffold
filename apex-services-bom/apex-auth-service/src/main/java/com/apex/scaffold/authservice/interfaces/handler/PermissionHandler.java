package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.application.assembler.PermissionAssembler;
import com.apex.scaffold.auth.application.dto.PermissionDTO;
import com.apex.scaffold.auth.application.service.PermissionService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.OperationLog.OperationType;
import com.apex.scaffold.common.annotation.RequireAdminRole;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * 权限管理Handler
 *
 * <p>处理权限相关的HTTP请求，提供细粒度的权限控制和资源访问管理能力。
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li><b>权限CRUD</b>：创建、更新、删除、查询权限信息</li>
 *   <li><b>资源权限映射</b>：根据资源标识查询关联的权限列表</li>
 *   <li><b>权限编码规范</b>：支持 resource:action 格式（如 user:read, order:create）</li>
 *   <li><b>多租户隔离</b>：自动绑定租户ID，确保权限数据隔离</li>
 *   <li><b>乐观锁</b>：基于version字段防止并发更新冲突</li>
 * </ul>
 *
 * <p><b>权限模型说明：</b>
 * <ul>
 *   <li><b>code</b>：权限唯一编码，通常为 resource:action 格式</li>
 *   <li><b>resource</b>：资源标识（如 user, role, order）</li>
 *   <li><b>action</b>：操作类型（如 read, create, update, delete）</li>
 *   <li><b>enabled</b>：权限启用状态，0=禁用（不影响已分配的角色）1=启用</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>系统管理员配置权限资源和操作类型</li>
 *   <li>角色管理时选择可分配的权限列表</li>
 *   <li>按资源维度快速查询相关操作权限</li>
 *   <li>权限审计和报表生成</li>
 * </ul>
 *
 * <p><b>安全注意事项：</b>
 * <ul>
 *   <li>权限删除会级联影响角色关联关系（建议禁用而非删除）</li>
 *   <li>权限code变更需同步更新代码中的权限校验逻辑</li>
 *   <li>禁用权限不影响已分配权限的角色，但会在新的权限校验中失效</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Component
@RequiredArgsConstructor
@Tag(name = "权限管理", description = "权限相关API - 提供细粒度的资源权限控制")
public class PermissionHandler {

    private final PermissionService permissionService;
    private final PermissionAssembler permissionAssembler;

    /**
     * 创建权限
     *
     * <p>创建新的权限资源，用于后续分配给角色进行访问控制。
     *
     * <p><b>请求参数说明：</b>
     * <ul>
     *   <li><b>code</b>：权限唯一编码，建议使用 resource:action 格式（如 user:read）</li>
     *   <li><b>name</b>：权限显示名称，用于前端展示（如"查看用户"）</li>
     *   <li><b>resource</b>：资源标识，表示操作的对象（如 user, role, order）</li>
     *   <li><b>action</b>：操作类型，表示对资源的操作（如 read, create, update, delete）</li>
     *   <li><b>description</b>：权限描述，说明该权限的用途和范围</li>
     *   <li><b>enabled</b>：是否启用，默认为1（启用）</li>
     * </ul>
     *
     * <p><b>业务规则：</b>
     * <ul>
     *   <li>权限code必须唯一，重复创建会返回400错误</li>
     *   <li>tenantId会自动从上下文获取，无需传入</li>
     *   <li>创建后的权限可立即用于角色分配</li>
     * </ul>
     *
     * <p><b>请求示例：</b>
     * <pre>{@code
     * {
     *   "code": "user:read",
     *   "name": "查看用户",
     *   "resource": "user",
     *   "action": "read",
     *   "description": "允许查看用户列表和详情",
     *   "enabled": 1
     * }
     * }</pre>
     *
     * @param request 服务器请求对象，请求体包含PermissionDTO
     * @return 响应体包含创建成功的权限信息（包含自动生成的ID、创建时间等）
     */
    @OperationLog(
            module = "权限管理",
            type = OperationType.INSERT,
            description = "创建权限",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "createPermission",
            summary = "创建权限",
            description = "创建新的权限资源，支持 resource:action 格式的权限编码，自动绑定租户ID")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "权限信息",
            required = true,
            content = @Content(schema = @Schema(implementation = PermissionDTO.class)))
    @ApiResponse(
            responseCode = "200",
            description = "权限创建成功，返回包含ID、创建时间、版本号等完整信息的权限对象",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "400",
            description = "参数校验失败 - code/name/resource/action为空，或权限code已存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务器内部错误 - 数据库异常或其他未预期错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @RequireAdminRole
    public Mono<ServerResponse> createPermission(ServerRequest request) {
        return request.bodyToMono(PermissionDTO.class)
                .flatMap(dto -> permissionService.createPermission(permissionAssembler.toEntity(dto)))
                .map(permissionAssembler::toDto)
                .flatMap(this::buildSuccess)
                .onErrorResume(this::handleError);
    }

    /**
     * 更新权限
     *
     * <p>修改现有权限的信息，支持部分字段更新。
     *
     * <p><b>可更新字段：</b>
     * <ul>
     *   <li><b>code</b>：权限编码（修改后需同步更新代码中的权限校验逻辑）</li>
     *   <li><b>name</b>：权限名称</li>
     *   <li><b>resource</b>：资源标识</li>
     *   <li><b>action</b>：操作类型</li>
     *   <li><b>description</b>：权限描述</li>
     *   <li><b>enabled</b>：启用状态（0=禁用 1=启用）</li>
     * </ul>
     *
     * <p><b>乐观锁机制：</b>
     * <ul>
     *   <li>更新时必须传入正确的version字段</li>
     *   <li>如果version不匹配（其他用户已更新），会返回409冲突错误</li>
     *   <li>更新成功后version会自动+1</li>
     * </ul>
     *
     * <p><b>业务影响：</b>
     * <ul>
     *   <li>禁用权限：已分配该权限的角色，用户访问时会立即失效</li>
     *   <li>修改code：需同步更新所有依赖该权限编码的业务代码</li>
     *   <li>修改resource/action：可能影响权限查询和分组逻辑</li>
     * </ul>
     *
     * <p><b>请求示例：</b>
     * <pre>{@code
     * {
     *   "code": "user:write",
     *   "name": "编辑用户",
     *   "resource": "user",
     *   "action": "write",
     *   "description": "允许创建和修改用户信息",
     *   "enabled": 1,
     *   "version": 3
     * }
     * }</pre>
     *
     * @param request 服务器请求对象，路径参数包含权限ID，请求体包含更新的PermissionDTO
     * @return 响应体包含更新后的权限信息（version已自动+1）
     */
    @OperationLog(
            module = "权限管理",
            type = OperationType.UPDATE,
            description = "更新权限",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "updatePermission", summary = "更新权限", description = "修改现有权限信息，支持乐观锁防止并发冲突，禁用权限会立即影响用户访问")
    @Parameter(name = "id", description = "权限ID", required = true, example = "100")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "权限更新信息（必须包含version字段）",
            required = true,
            content = @Content(schema = @Schema(implementation = PermissionDTO.class)))
    @ApiResponse(
            responseCode = "200",
            description = "权限更新成功，返回更新后的权限对象（version已+1）",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "400",
            description = "参数校验失败 - 必填字段为空或格式错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "404",
            description = "权限不存在 - 指定ID的权限未找到",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "409",
            description = "版本冲突 - version不匹配，权限已被其他用户修改，请刷新后重试",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务器内部错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @RequireAdminRole
    public Mono<ServerResponse> updatePermission(ServerRequest request) {
        String id = request.pathVariable("id");
        return request.bodyToMono(PermissionDTO.class)
                .flatMap(dto -> {
                    dto.setId(Long.parseLong(id));
                    return permissionService.updatePermission(permissionAssembler.toEntity(dto));
                })
                .map(permissionAssembler::toDto)
                .flatMap(this::buildSuccess)
                .onErrorResume(this::handleError);
    }

    /**
     * 删除权限
     *
     * <p>物理删除指定的权限记录，同时会级联处理相关的角色-权限关联关系。
     *
     * <p><b>删除影响范围：</b>
     * <ul>
     *   <li>权限记录本身会被永久删除</li>
     *   <li>所有角色与该权限的关联关系会被移除</li>
     *   <li>拥有该权限的用户将立即失去对应的访问能力</li>
     * </ul>
     *
     * <p><b>最佳实践：</b>
     * <ul>
     *   <li><b>推荐做法</b>：使用"禁用"（enabled=0）而非删除，保留历史数据和审计记录</li>
     *   <li><b>适用场景</b>：测试环境清理、错误创建的权限、废弃的功能模块</li>
     *   <li><b>删除前检查</b>：确认该权限未被关键角色使用，避免影响业务</li>
     * </ul>
     *
     * <p><b>安全限制：</b>
     * <ul>
     *   <li>如果权限被系统核心角色使用，可能返回403禁止删除</li>
     *   <li>删除操作需要管理员权限，建议记录操作日志</li>
     * </ul>
     *
     * @param request 服务器请求对象，路径参数包含要删除的权限ID
     * @return 删除成功返回空响应体，响应码200
     */
    @OperationLog(
            module = "权限管理",
            type = OperationType.DELETE,
            description = "删除权限",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "deletePermission",
            summary = "删除权限",
            description = "物理删除权限及其关联关系，影响所有使用该权限的角色和用户，建议使用禁用代替")
    @Parameter(name = "id", description = "权限ID", required = true, example = "100")
    @ApiResponse(
            responseCode = "200",
            description = "权限删除成功，关联的角色-权限关系已同步清理",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "400",
            description = "参数错误 - 权限ID格式无效",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "404",
            description = "权限不存在 - 指定ID的权限未找到或已被删除",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "403",
            description = "禁止删除 - 权限被系统核心角色使用，不允许删除",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务器内部错误 - 数据库级联删除失败",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @RequireAdminRole
    public Mono<ServerResponse> deletePermission(ServerRequest request) {
        String id = request.pathVariable("id");
        return permissionService
                .deletePermission(Long.parseLong(id))
                .then(buildSuccess(null))
                .onErrorResume(this::handleError);
    }

    /**
     * 根据ID查询权限
     *
     * <p>查询单个权限的完整详细信息，包含所有字段和元数据。
     *
     * <p><b>返回字段说明：</b>
     * <ul>
     *   <li><b>id</b>：权限唯一标识</li>
     *   <li><b>code</b>：权限编码（如 user:read）</li>
     *   <li><b>name</b>：权限显示名称</li>
     *   <li><b>resource</b>：资源标识</li>
     *   <li><b>action</b>：操作类型</li>
     *   <li><b>description</b>：权限描述</li>
     *   <li><b>enabled</b>：启用状态（0=禁用 1=启用）</li>
     *   <li><b>tenantId</b>：租户标识</li>
     *   <li><b>createdAt</b>：创建时间</li>
     *   <li><b>updatedAt</b>：最后更新时间</li>
     *   <li><b>version</b>：版本号（用于乐观锁）</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>权限编辑页面回显数据</li>
     *   <li>权限详情查看</li>
     *   <li>权限配置导入导出</li>
     *   <li>权限审计和报表</li>
     * </ul>
     *
     * @param request 服务器请求对象，路径参数包含权限ID
     * @return 包含权限完整信息的响应体，如果权限不存在则返回null
     */
    @OperationLog(
            module = "权限管理",
            type = OperationType.SELECT,
            description = "查询权限详情",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "getPermissionById", summary = "查询权限详情", description = "根据ID查询单个权限的完整信息，包含所有字段和元数据")
    @Parameter(name = "id", description = "权限ID", required = true, example = "100")
    @ApiResponse(
            responseCode = "200",
            description = "查询成功，返回权限完整信息（包含创建时间、更新时间、版本号等）",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "404",
            description = "权限不存在 - 指定ID的权限未找到",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务器内部错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @RequireAdminRole
    public Mono<ServerResponse> getPermissionById(ServerRequest request) {
        String id = request.pathVariable("id");
        return permissionService
                .getPermissionById(Long.parseLong(id))
                .map(permissionAssembler::toDto)
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildSuccess(null))
                .onErrorResume(this::handleError);
    }

    /**
     * 列出所有权限
     *
     * <p>查询当前租户下的所有权限列表，返回完整的权限数组。
     *
     * <p><b>返回数据说明：</b>
     * <ul>
     *   <li>返回数组格式：List&lt;PermissionDTO&gt;</li>
     *   <li>包含所有状态的权限（已启用和已禁用）</li>
     *   <li>按创建时间倒序排列（最新创建的在前）</li>
     *   <li>自动进行租户隔离，仅返回当前租户的权限</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li><b>角色分配权限</b>：获取可选权限列表供管理员选择</li>
     *   <li><b>权限管理列表</b>：展示所有权限供查看、编辑、删除</li>
     *   <li><b>权限统计分析</b>：统计权限数量、启用率等指标</li>
     *   <li><b>资源权限树</b>：按resource分组构建权限树形结构</li>
     *   <li><b>权限导出</b>：导出全量权限配置用于备份或迁移</li>
     * </ul>
     *
     * <p><b>性能说明：</b>
     * <ul>
     *   <li>无分页限制，返回全量数据（权限数据通常不会太多）</li>
     *   <li>建议前端缓存结果，避免频繁请求</li>
     *   <li>如果权限数量超过1000条，建议联系技术支持优化</li>
     * </ul>
     *
     * @param request 服务器请求对象（无需额外参数）
     * @return 包含权限列表的响应体，数组格式
     */
    @OperationLog(
            module = "权限管理",
            type = OperationType.SELECT,
            description = "列出所有权限",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "listPermissions", summary = "列出所有权限", description = "获取当前租户下的所有权限列表，包含启用和禁用的权限，自动租户隔离")
    @ApiResponse(
            responseCode = "200",
            description = "查询成功，返回权限列表数组，如果没有权限则返回空数组[]",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation = Result.class,
                                            description = "data字段为 List<PermissionDTO> 数组")))
    @ApiResponse(
            responseCode = "500",
            description = "服务器内部错误 - 数据库查询失败",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @RequireAdminRole
    public Mono<ServerResponse> listPermissions(ServerRequest request) {
        return permissionService
                .listPermissions()
                .map(permissionAssembler::toDto)
                .collectList()
                .flatMap(this::buildSuccess)
                .onErrorResume(this::handleError);
    }

    /**
     * 根据资源标识查询权限
     *
     * <p>根据资源标识（resource）查询该资源下的所有操作权限，用于按资源维度组织权限。
     *
     * <p><b>查询逻辑：</b>
     * <ul>
     *   <li>精确匹配resource字段（如查询"user"，只返回resource="user"的权限）</li>
     *   <li>返回该资源下的所有action操作权限（如read、create、update、delete）</li>
     *   <li>包含已启用和已禁用的权限</li>
     *   <li>自动租户隔离，仅返回当前租户的权限</li>
     * </ul>
     *
     * <p><b>返回数据示例：</b>
     * <pre>{@code
     * // 查询 resource=user 的权限
     * [
     *   { "id": 1, "code": "user:read", "name": "查看用户", "resource": "user", "action": "read" },
     *   { "id": 2, "code": "user:create", "name": "创建用户", "resource": "user", "action": "create" },
     *   { "id": 3, "code": "user:update", "name": "编辑用户", "resource": "user", "action": "update" },
     *   { "id": 4, "code": "user:delete", "name": "删除用户", "resource": "user", "action": "delete" }
     * ]
     * }</pre>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li><b>按资源分组展示</b>：在权限配置页面按资源分组显示（用户管理、角色管理等）</li>
     *   <li><b>资源权限选择器</b>：选择某个资源时，展示该资源的所有可选操作</li>
     *   <li><b>权限矩阵构建</b>：生成"资源-操作"权限矩阵表格</li>
     *   <li><b>权限快速配置</b>：为角色批量分配某个资源的全部操作权限</li>
     *   <li><b>权限审计</b>：检查某个资源的权限配置完整性</li>
     * </ul>
     *
     * <p><b>常见资源标识：</b>
     * <ul>
     *   <li>user - 用户管理</li>
     *   <li>role - 角色管理</li>
     *   <li>permission - 权限管理</li>
     *   <li>department - 部门管理</li>
     *   <li>order - 订单管理</li>
     * </ul>
     *
     * @param request 服务器请求对象，路径参数包含资源标识（resource）
     * @return 包含该资源下所有权限的列表，如果资源不存在或无权限则返回空数组
     */
    @OperationLog(
            module = "权限管理",
            type = OperationType.SELECT,
            description = "按资源查询权限",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "getPermissionsByResource",
            summary = "按资源查询权限",
            description = "根据资源标识查询该资源下的所有操作权限，用于资源维度的权限分组和管理")
    @Parameter(name = "resource", description = "资源标识（如 user、role、order）", required = true, example = "user")
    @ApiResponse(
            responseCode = "200",
            description = "查询成功，返回该资源下的权限列表数组，如果无匹配权限则返回空数组[]",
            content =
                    @Content(
                            schema =
                                    @Schema(
                                            implementation = Result.class,
                                            description = "data字段为 List<PermissionDTO> 数组，包含该资源的所有操作权限")))
    @ApiResponse(
            responseCode = "500",
            description = "服务器内部错误 - 数据库查询失败",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @RequireAdminRole
    public Mono<ServerResponse> getPermissionsByResource(ServerRequest request) {
        String resource = request.pathVariable("resource");
        return permissionService
                .getPermissionsByResource(resource)
                .map(permissionAssembler::toDto)
                .collectList()
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
