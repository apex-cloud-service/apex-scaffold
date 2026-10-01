package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.DeptDTO;
import com.apex.scaffold.application.service.DeptService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.domain.model.entity.DeptEntity;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * 部门管理处理器（函数式端点）。
 *
 * <p>提供部门树查询与 CRUD API（C3 数据权限/部门体系），树形结构由
 * {@link DeptService} 内存组装，所有端点要求 {@code system:dept:*} 权限。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>部门树查询：查询完整的部门树形结构</li>
 *   <li>部门 CRUD：新增、查询详情、更新、逻辑删除部门</li>
 * </ul>
 *
 * <h2>接口列表</h2>
 * <ul>
 *   <li>GET /admin-api/v1/depts — 查询部门树</li>
 *   <li>GET /admin-api/v1/depts/{id} — 查询部门详情</li>
 *   <li>POST /admin-api/v1/depts — 新增部门</li>
 *   <li>PUT /admin-api/v1/depts/{id} — 更新部门</li>
 *   <li>DELETE /admin-api/v1/depts/{id} — 删除部门</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see DeptService
 */
@Component
@Tag(name = "后台-部门管理", description = "部门树与数据权限基础")
public class DeptHandler extends BaseHandler {

    /** 部门管理服务 */
    private final DeptService deptService;

    /**
     * 创建部门管理处理器。
     *
     * @param deptService 部门管理服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public DeptHandler(DeptService deptService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.deptService = deptService;
    }

    /**
     * 查询部门树。
     *
     * <p>查询完整的部门树形结构，包含所有层级的部门信息。</p>
     *
     * @param request ServerRequest
     * @return 部门树列表
     */
    @RequirePermission("system:dept:list")
    @Operation(operationId = "adminListDepts", summary = "查询部门树", description = "查询完整的部门树形结构，包含所有层级的部门信息，支持C3数据权限过滤")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = java.util.List.class))),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:dept:list权限")
    })
    public Mono<ServerResponse> listTree(ServerRequest request) {
        return deptService.listTree().flatMap(this::buildSuccess);
    }

    /**
     * 查询部门详情。
     *
     * <p>根据部门ID查询部门的详细信息。</p>
     *
     * @param request ServerRequest，路径参数id
     * @return 部门详细信息
     */
    @RequirePermission("system:dept:query")
    @Operation(operationId = "adminGetDept", summary = "查询部门详情", description = "根据部门ID查询部门的详细信息")
    @Parameter(
            name = "id",
            description = "部门ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(mediaType = "application/json", schema = @Schema(implementation = DeptEntity.class))),
        @ApiResponse(responseCode = "404", description = "部门不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:dept:query权限")
    })
    public Mono<ServerResponse> detail(ServerRequest request) {
        return deptService.get(requirePositiveId(request)).flatMap(this::buildSuccess);
    }

    /**
     * 新增部门。
     *
     * <p>创建一个新的部门，包括基本信息和层级关系。</p>
     *
     * @param request ServerRequest，请求体为部门创建信息
     * @return 创建成功的部门信息
     */
    @RequirePermission("system:dept:add")
    @Operation(operationId = "adminCreateDept", summary = "新增部门", description = "创建一个新的部门，包括基本信息和层级关系")
    @RequestBody(
            description = "部门创建请求信息",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeptDTO.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功",
                content =
                        @Content(mediaType = "application/json", schema = @Schema(implementation = DeptDTO.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败或部门名称已存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:dept:add权限")
    })
    @OperationLog(module = "部门管理", type = OperationLog.OperationType.INSERT, description = "新增部门")
    public Mono<ServerResponse> create(ServerRequest request) {
        return parseBody(request, DeptDTO.class).map(this::toEntity).flatMap(deptService::create).flatMap(this::buildSuccess);
    }

    /**
     * 更新部门。
     *
     * <p>更新指定部门的信息，支持部分字段更新。</p>
     *
     * @param request ServerRequest，路径参数id，请求体为更新信息
     * @return 更新后的部门信息
     */
    @RequirePermission("system:dept:edit")
    @Operation(operationId = "adminUpdateDept", summary = "更新部门", description = "更新指定部门的信息，支持部分字段更新")
    @Parameter(
            name = "id",
            description = "部门ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "部门更新请求信息",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeptDTO.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功",
                content =
                        @Content(mediaType = "application/json", schema = @Schema(implementation = DeptDTO.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "404", description = "部门不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:dept:edit权限")
    })
    @OperationLog(module = "部门管理", type = OperationLog.OperationType.UPDATE, description = "更新部门")
    public Mono<ServerResponse> update(ServerRequest request) {
        long id = requirePositiveId(request);
        return parseBody(request, DeptDTO.class)
                .map(this::toEntity)
                .flatMap(dept -> deptService.update(id, dept))
                .flatMap(this::buildSuccess);
    }

    /**
     * 删除部门。
     *
     * <p>逻辑删除指定的部门，部门数据不会真正删除。</p>
     *
     * @param request ServerRequest，路径参数id
     * @return 删除结果
     */
    @RequirePermission("system:dept:remove")
    @Operation(operationId = "adminDeleteDept", summary = "删除部门", description = "逻辑删除指定的部门，部门数据不会真正删除")
    @Parameter(
            name = "id",
            description = "部门ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "404", description = "部门不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:dept:remove权限"),
        @ApiResponse(responseCode = "409", description = "部门存在子部门或关联数据，无法删除")
    })
    @OperationLog(module = "部门管理", type = OperationLog.OperationType.DELETE, description = "删除部门")
    public Mono<ServerResponse> delete(ServerRequest request) {
        return deptService
                .delete(requirePositiveId(request))
                .flatMap(deleted -> buildSuccess(java.util.Map.of("deleted", deleted)));
    }

    /**
     * 将部门请求 DTO 转换为领域实体。
     *
     * @param dto 部门请求 DTO
     * @return 部门领域实体
     */
    private DeptEntity toEntity(DeptDTO dto) {
        DeptEntity entity = new DeptEntity();
        entity.setParentId(dto.getParentId());
        entity.setName(dto.getName());
        entity.setOrderNum(dto.getOrderNum());
        entity.setLeader(dto.getLeader());
        entity.setPhone(dto.getPhone());
        entity.setEmail(dto.getEmail());
        entity.setStatus(dto.getStatus());
        return entity;
    }
}
