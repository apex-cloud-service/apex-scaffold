package com.apex.scaffold.officeservice.interfaces.off.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.off.application.dto.DepartmentDTO;
import com.apex.scaffold.office.off.application.service.OrgSyncService;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 本地组织数据管理处理器。
 *
 * <p>维护部门、查询员工与同步历史；这些端点不触发第三方同步。参数、请求体和泛型响应契约由
 * {@link com.apex.scaffold.officeservice.interfaces.off.router.OrgSyncRouter} 声明。
 * 业务处理失败返回 HTTP 400，具体原因由统一响应中的 code 和 msg 表达。
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "组织同步管理", description = "本地组织数据维护与同步历史查询")
public class OrgSyncHandler {

    private final OrgSyncService orgSyncService;
    private final RequestHandlerUtil requestHandlerUtil;

    @RequirePermission("office:organization:list")
    /**
     * 分页查询部门，支持按状态原值过滤。
     *
     * @param request 查询参数 pageNum、pageSize、status；分页缺失、非法或负数时分别默认 1、10
     * @return 包含部门分页数据的 Result 成功响应，处理失败返回 HTTP 400
     */
    @Operation(operationId = "getDepartmentList", summary = "获取部门列表", description = "分页获取部门列表")
    @OperationLog(
            module = "组织同步",
            type = OperationLog.OperationType.SELECT,
            description = "获取部门列表",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getDepartmentList(ServerRequest request) {
        Integer pageNum =
                RequestHandlerUtil.safeParseInt(request.queryParam("pageNum").orElse("1"), 1);
        Integer pageSize =
                RequestHandlerUtil.safeParseInt(request.queryParam("pageSize").orElse("10"), 10);
        String status = request.queryParam("status").orElse(null);

        return orgSyncService
                .getDepartmentPage(pageNum, pageSize, status)
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .onErrorResume(this::handleError);
    }

    @RequirePermission("office:organization:query")
    /**
     * 根据数值 ID 查询部门详情。
     *
     * @param request 路径参数 id 为部门 Long ID
     * @return 包含部门 DTO 的 Result；ID 非法、部门不存在或处理失败时返回 HTTP 400
     */
    @Operation(operationId = "getDepartmentDetail", summary = "获取部门详情", description = "根据ID获取部门详情")
    @OperationLog(
            module = "组织同步",
            type = OperationLog.OperationType.SELECT,
            description = "获取部门详情",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getDepartmentDetail(ServerRequest request) {
        Long id = RequestHandlerUtil.safeParseLong(request.pathVariable("id"));
        if (id == null) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "部门ID无效");
        }

        return orgSyncService
                .getDepartmentById(id)
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .switchIfEmpty(requestHandlerUtil.buildErrorResponse(ResultCode.NOT_FOUND, "部门不存在"))
                .onErrorResume(this::handleError);
    }

    @RequirePermission("office:organization:dept")
    /**
     * 解析并校验部门 DTO，调用本地部门创建用例。
     *
     * @param request JSON 请求体为 DepartmentDTO
     * @return 包含部门 DTO 的 Result；校验或处理失败返回 HTTP 400
     */
    @Operation(operationId = "createDepartment", summary = "创建部门", description = "创建本地部门")
    @OperationLog(
            module = "组织同步",
            type = OperationLog.OperationType.INSERT,
            description = "创建部门",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> createDepartment(ServerRequest request) {
        return requestHandlerUtil
                .validateAndMap(request, DepartmentDTO.class)
                .flatMap(orgSyncService::createDepartment)
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .onErrorResume(this::handleError);
    }

    @RequirePermission("office:organization:dept")
    /**
     * 按路径 ID 更新本地部门。
     *
     * @param request 路径参数 id 为 Long ID，JSON 请求体为 DepartmentDTO
     * @return 包含更新后 DTO 的 Result；ID 非法、部门不存在或处理失败返回 HTTP 400
     */
    @Operation(operationId = "updateDepartment", summary = "更新部门", description = "更新本地部门信息")
    @OperationLog(
            module = "组织同步",
            type = OperationLog.OperationType.UPDATE,
            description = "更新部门",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> updateDepartment(ServerRequest request) {
        Long id = RequestHandlerUtil.safeParseLong(request.pathVariable("id"));
        if (id == null) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "部门ID无效");
        }

        return requestHandlerUtil
                .validateAndMap(request, DepartmentDTO.class)
                .flatMap(dto -> orgSyncService.updateDepartment(id, dto))
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .switchIfEmpty(requestHandlerUtil.buildErrorResponse(ResultCode.NOT_FOUND, "部门不存在"))
                .onErrorResume(this::handleError);
    }

    @RequirePermission("office:organization:dept")
    /**
     * 按 ID 删除部门，不额外检查记录是否存在。
     *
     * @param request 路径参数 id 为部门 Long ID
     * @return data 为空的 Result；ID 非法或处理失败返回 HTTP 400
     */
    @Operation(operationId = "deleteDepartment", summary = "删除部门", description = "删除指定部门")
    @OperationLog(
            module = "组织同步",
            type = OperationLog.OperationType.DELETE,
            description = "删除部门",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> deleteDepartment(ServerRequest request) {
        Long id = RequestHandlerUtil.safeParseLong(request.pathVariable("id"));
        if (id == null) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "部门ID无效");
        }

        return orgSyncService
                .deleteDepartment(id)
                .then(Mono.defer(() -> requestHandlerUtil.buildSuccessResponse(null)))
                .onErrorResume(this::handleError);
    }

    @RequirePermission("office:organization:list")
    /**
     * 分页查询员工，支持按部门 ID 和状态过滤。
     *
     * @param request 查询参数 pageNum、pageSize 默认 1、10；deptId 无法解析时不按部门过滤；status 按原值匹配
     * @return 包含员工分页数据的 Result；处理失败返回 HTTP 400
     */
    @Operation(operationId = "getEmployeeList", summary = "获取员工列表", description = "分页获取员工列表")
    @OperationLog(
            module = "组织同步",
            type = OperationLog.OperationType.SELECT,
            description = "获取员工列表",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getEmployeeList(ServerRequest request) {
        Integer pageNum =
                RequestHandlerUtil.safeParseInt(request.queryParam("pageNum").orElse("1"), 1);
        Integer pageSize =
                RequestHandlerUtil.safeParseInt(request.queryParam("pageSize").orElse("10"), 10);
        Long deptId =
                RequestHandlerUtil.safeParseLong(request.queryParam("deptId").orElse(null));
        String status = request.queryParam("status").orElse(null);

        return orgSyncService
                .getEmployeePage(pageNum, pageSize, deptId, status)
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .onErrorResume(this::handleError);
    }

    @RequirePermission("office:organization:user")
    /**
     * 根据数值 ID 查询员工详情。
     *
     * @param request 路径参数 id 为员工 Long ID
     * @return 包含员工 DTO 的 Result；ID 非法、员工不存在或处理失败返回 HTTP 400
     */
    @Operation(operationId = "getEmployeeDetail", summary = "获取员工详情", description = "根据ID获取员工详情")
    @OperationLog(
            module = "组织同步",
            type = OperationLog.OperationType.SELECT,
            description = "获取员工详情",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getEmployeeDetail(ServerRequest request) {
        Long id = RequestHandlerUtil.safeParseLong(request.pathVariable("id"));
        if (id == null) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "员工ID无效");
        }

        return orgSyncService
                .getEmployeeById(id)
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .switchIfEmpty(requestHandlerUtil.buildErrorResponse(ResultCode.NOT_FOUND, "员工不存在"))
                .onErrorResume(this::handleError);
    }

    @RequirePermission("office:sync:read")
    /**
     * 分页查询组织同步历史，支持按来源和状态原值过滤。
     *
     * @param request 查询参数 pageNum、pageSize 默认 1、10，syncSource、syncStatus 为可选过滤条件
     * @return 包含同步历史分页数据的 Result；处理失败返回 HTTP 400
     */
    @Operation(operationId = "getSyncHistory", summary = "获取同步历史", description = "分页获取同步历史记录")
    @OperationLog(
            module = "组织同步",
            type = OperationLog.OperationType.SELECT,
            description = "获取同步历史",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getSyncHistory(ServerRequest request) {
        Integer pageNum =
                RequestHandlerUtil.safeParseInt(request.queryParam("pageNum").orElse("1"), 1);
        Integer pageSize =
                RequestHandlerUtil.safeParseInt(request.queryParam("pageSize").orElse("10"), 10);
        String syncSource = request.queryParam("syncSource").orElse(null);
        String syncStatus = request.queryParam("syncStatus").orElse(null);

        return orgSyncService
                .getSyncHistoryPage(pageNum, pageSize, syncSource, syncStatus)
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .onErrorResume(this::handleError);
    }

    /**
     * 记录异常并委托工具构建 HTTP 400 错误响应。
     *
     * @param throwable 异常
     * @return 统一错误响应
     */
    private Mono<ServerResponse> handleError(Throwable throwable) {
        log.error("组织同步处理失败: {}", throwable.getMessage(), throwable);
        return requestHandlerUtil.handleError(throwable);
    }
}
