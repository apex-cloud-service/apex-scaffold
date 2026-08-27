package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.DeptService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.domain.model.entity.DeptEntity;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
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
 * {@link DeptService} 内存组装，所有端点要求 {@code system:dept:*} 权限。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /admin-api/v1/depts - 查询部门树</li>
 *   <li>GET /admin-api/v1/depts/{id} - 查询部门详情</li>
 *   <li>POST /admin-api/v1/depts - 新增部门</li>
 *   <li>PUT /admin-api/v1/depts/{id} - 更新部门</li>
 *   <li>DELETE /admin-api/v1/depts/{id} - 删除部门</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
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

    /** 查询部门树。 */
    @RequirePermission("system:dept:list")
    @Operation(operationId = "adminListDepts", summary = "查询部门树")
    public Mono<ServerResponse> listTree(ServerRequest request) {
        return deptService.listTree().flatMap(this::buildSuccess);
    }

    /** 查询部门详情。 */
    @RequirePermission("system:dept:query")
    @Operation(operationId = "adminGetDept", summary = "查询部门详情")
    public Mono<ServerResponse> detail(ServerRequest request) {
        return deptService.get(requirePositiveId(request)).flatMap(this::buildSuccess);
    }

    /** 新增部门。 */
    @RequirePermission("system:dept:add")
    @Operation(operationId = "adminCreateDept", summary = "新增部门")
    @OperationLog(module = "部门管理", type = OperationLog.OperationType.INSERT, description = "新增部门")
    public Mono<ServerResponse> create(ServerRequest request) {
        return parseBody(request, DeptEntity.class).flatMap(deptService::create).flatMap(this::buildSuccess);
    }

    /** 更新部门。 */
    @RequirePermission("system:dept:edit")
    @Operation(operationId = "adminUpdateDept", summary = "更新部门")
    @OperationLog(module = "部门管理", type = OperationLog.OperationType.UPDATE, description = "更新部门")
    public Mono<ServerResponse> update(ServerRequest request) {
        long id = requirePositiveId(request);
        return parseBody(request, DeptEntity.class)
                .flatMap(dept -> deptService.update(id, dept))
                .flatMap(this::buildSuccess);
    }

    /** 删除部门。 */
    @RequirePermission("system:dept:remove")
    @Operation(operationId = "adminDeleteDept", summary = "删除部门")
    @OperationLog(module = "部门管理", type = OperationLog.OperationType.DELETE, description = "删除部门")
    public Mono<ServerResponse> delete(ServerRequest request) {
        return deptService
                .delete(requirePositiveId(request))
                .flatMap(deleted -> buildSuccess(java.util.Map.of("deleted", deleted)));
    }
}
