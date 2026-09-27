package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.DepartmentDTO;
import com.apex.scaffold.application.service.OrgSyncService;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.util.MessageUtils;
import com.apex.scaffold.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 组织同步管理处理器（函数式端点）
 *
 * <p>提供组织架构数据同步功能，支持从第三方办公平台（如飞书、钉钉、企业微信等）
 * 同步部门和员工信息，并管理同步历史记录。确保本地组织架构数据与外部系统保持一致。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>部门同步：从外部系统同步部门结构和信息</li>
 *   <li>员工同步：同步员工基本信息、部门归属和职位信息</li>
 *   <li>增量同步：支持定时增量同步，减少数据传输量</li>
 *   <li>同步历史：记录每次同步的结果和变更详情</li>
 *   <li>冲突处理：智能处理本地修改与外部变更的冲突</li>
 * </ul>
 *
 * <h2>同步策略</h2>
 * <ul>
 *   <li>全量同步：首次同步或定期全量刷新</li>
 *   <li>增量同步：基于时间戳或版本号的增量更新</li>
 *   <li>双向同步：支持本地修改同步回外部系统（可选）</li>
 * </ul>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /api/v1/org-sync/departments - 获取部门列表</li>
 *   <li>GET /api/v1/org-sync/departments/{id} - 获取部门详情</li>
 *   <li>GET /api/v1/org-sync/employees - 获取员工列表</li>
 *   <li>POST /api/v1/org-sync/sync - 执行组织架构同步</li>
 *   <li>GET /api/v1/org-sync/history - 获取同步历史</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see com.apex.scaffold.application.service.OrgSyncService
 */
@Slf4j
@Component
@Tag(name = "组织同步管理", description = "组织架构同步相关接口")
public class OrgSyncHandler extends BaseHandler {

    private final OrgSyncService orgSyncService;
    private final UserService userService;
    private final RequestHandlerUtil requestHandlerUtil;

    public OrgSyncHandler(
            OrgSyncService orgSyncService,
            UserService userService,
            RequestHandlerUtil requestHandlerUtil,
            MessageUtils messageUtils) {
        super(messageUtils);
        this.orgSyncService = orgSyncService;
        this.userService = userService;
        this.requestHandlerUtil = requestHandlerUtil;
    }

    /**
     * 处理获取部门列表请求。
     *
     * <p>分页查询部门数据，支持按状态过滤，查询结果以统一响应格式返回。
     *
     * @param request HTTP 请求对象，查询参数包含 pageNum、pageSize、status
     * @return 包含部门分页数据的统一成功响应，失败时返回错误响应
     */
    @Operation(summary = "获取部门列表", description = "分页获取部门列表")
    @Parameters({
        @Parameter(
                name = "pageNum",
                in = ParameterIn.QUERY,
                description = "页码",
                schema = @Schema(type = "integer", defaultValue = "1")),
        @Parameter(
                name = "pageSize",
                in = ParameterIn.QUERY,
                description = "每页数量",
                schema = @Schema(type = "integer", defaultValue = "10")),
        @Parameter(name = "status", in = ParameterIn.QUERY, description = "状态")
    })
    @OperationLog(module = "组织同步", type = OperationLog.OperationType.SELECT, description = "获取部门列表")
    public Mono<ServerResponse> getDepartmentList(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Integer pageNum = safeParseInt(request.queryParam("pageNum").orElse("1"), 1);
        Integer pageSize = safeParseInt(request.queryParam("pageSize").orElse("10"), 10);
        String status = request.queryParam("status").orElse(null);

        return orgSyncService
                .getDepartmentPage(pageNum, pageSize, status)
                .flatMap(this::buildSuccess)
                .onErrorResume(e -> {
                    log.error("获取部门列表失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 处理获取部门详情请求。
     *
     * <p>根据路径中的部门 ID 查询部门详情，ID 缺失或非法时返回参数错误，
     * 部门不存在时返回 404 响应。
     *
     * @param request HTTP 请求对象，路径参数包含部门 ID
     * @return 包含部门详情的统一成功响应，不存在或失败时返回对应错误响应
     */
    @Parameters({@Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "部门ID")})
    @Operation(summary = "获取部门详情", description = "根据ID获取部门详情")
    @OperationLog(module = "组织同步", type = OperationLog.OperationType.SELECT, description = "获取部门详情")
    public Mono<ServerResponse> getDepartmentDetail(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long id = safeParseLong(request.pathVariable("id"));
        if (id == null) {
            return buildError(ResultCode.PARAM_ERROR, traceId);
        }

        return orgSyncService
                .getDepartmentById(id)
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildError(ResultCode.NOT_FOUND, traceId));
    }

    /**
     * 处理创建部门请求。
     *
     * <p>解析并校验请求体为 {@link DepartmentDTO}，校验通过后调用应用服务创建新部门。
     *
     * @param request HTTP 请求对象，请求体为部门信息 JSON
     * @return 包含新建部门信息的统一成功响应，校验失败或创建失败时返回错误响应
     */
    @Operation(summary = "创建部门", description = "创建新部门")
    @OperationLog(module = "组织同步", type = OperationLog.OperationType.INSERT, description = "创建部门")
    public Mono<ServerResponse> createDepartment(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return requestHandlerUtil
                .validateAndMap(request, DepartmentDTO.class)
                .flatMap(orgSyncService::createDepartment)
                .flatMap(this::buildSuccess)
                .onErrorResume(e -> {
                    log.error("创建部门失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 处理更新部门请求。
     *
     * <p>根据路径中的部门 ID 更新部门信息，ID 缺失或非法时返回参数错误，
     * 请求体校验通过后调用应用服务执行更新，部门不存在时返回 404 响应。
     *
     * @param request HTTP 请求对象，路径参数包含部门 ID，请求体为部门信息 JSON
     * @return 包含更新后部门信息的统一成功响应，不存在或失败时返回对应错误响应
     */
    @Parameters({@Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "部门ID")})
    @Operation(summary = "更新部门", description = "更新部门信息")
    @OperationLog(module = "组织同步", type = OperationLog.OperationType.UPDATE, description = "更新部门")
    public Mono<ServerResponse> updateDepartment(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long id = safeParseLong(request.pathVariable("id"));
        if (id == null) {
            return buildError(ResultCode.PARAM_ERROR, traceId);
        }

        return requestHandlerUtil
                .validateAndMap(request, DepartmentDTO.class)
                .flatMap(dto -> orgSyncService.updateDepartment(id, dto))
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildError(ResultCode.NOT_FOUND, traceId))
                .onErrorResume(e -> {
                    log.error("更新部门失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 处理删除部门请求。
     *
     * <p>根据路径中的部门 ID 删除指定部门，ID 缺失或非法时返回参数错误。
     *
     * @param request HTTP 请求对象，路径参数包含部门 ID
     * @return 删除成功的统一成功响应，失败时返回错误响应
     */
    @Parameters({@Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "部门ID")})
    @Operation(summary = "删除部门", description = "删除指定部门")
    @OperationLog(module = "组织同步", type = OperationLog.OperationType.DELETE, description = "删除部门")
    public Mono<ServerResponse> deleteDepartment(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long id = safeParseLong(request.pathVariable("id"));
        if (id == null) {
            return buildError(ResultCode.PARAM_ERROR, traceId);
        }

        return orgSyncService.deleteDepartment(id).then(buildSuccess(null)).onErrorResume(e -> {
            log.error("删除部门失败: {}", e.getMessage(), e);
            return buildError(ResultCode.ERROR, traceId);
        });
    }

    /**
     * 处理获取员工列表请求。
     *
     * <p>分页查询员工数据，支持按部门 ID 和状态过滤，查询结果以统一响应格式返回。
     *
     * @param request HTTP 请求对象，查询参数包含 pageNum、pageSize、deptId、status
     * @return 包含员工分页数据的统一成功响应，失败时返回错误响应
     */
    @Operation(summary = "获取员工列表", description = "分页获取员工列表")
    @Parameters({
        @Parameter(
                name = "pageNum",
                in = ParameterIn.QUERY,
                description = "页码",
                schema = @Schema(type = "integer", defaultValue = "1")),
        @Parameter(
                name = "pageSize",
                in = ParameterIn.QUERY,
                description = "每页数量",
                schema = @Schema(type = "integer", defaultValue = "10")),
        @Parameter(name = "deptId", in = ParameterIn.QUERY, description = "部门ID"),
        @Parameter(name = "status", in = ParameterIn.QUERY, description = "状态")
    })
    @OperationLog(module = "组织同步", type = OperationLog.OperationType.SELECT, description = "获取员工列表")
    public Mono<ServerResponse> getEmployeeList(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Integer pageNum = safeParseInt(request.queryParam("pageNum").orElse("1"), 1);
        Integer pageSize = safeParseInt(request.queryParam("pageSize").orElse("10"), 10);
        Long deptId = request.queryParam("deptId").map(Long::parseLong).orElse(null);
        String status = request.queryParam("status").orElse(null);

        return orgSyncService
                .getEmployeePage(pageNum, pageSize, deptId, status)
                .flatMap(this::buildSuccess)
                .onErrorResume(e -> {
                    log.error("获取员工列表失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 处理获取员工详情请求。
     *
     * <p>根据路径中的员工 ID 查询员工详情，ID 缺失或非法时返回参数错误，
     * 员工不存在时返回 404 响应。
     *
     * @param request HTTP 请求对象，路径参数包含员工 ID
     * @return 包含员工详情的统一成功响应，不存在或失败时返回对应错误响应
     */
    @Parameters({@Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "员工ID")})
    @Operation(summary = "获取员工详情", description = "根据ID获取员工详情")
    @OperationLog(module = "组织同步", type = OperationLog.OperationType.SELECT, description = "获取员工详情")
    public Mono<ServerResponse> getEmployeeDetail(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long id = safeParseLong(request.pathVariable("id"));
        if (id == null) {
            return buildError(ResultCode.PARAM_ERROR, traceId);
        }

        return orgSyncService
                .getEmployeeById(id)
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildError(ResultCode.NOT_FOUND, traceId));
    }

    /**
     * 处理获取同步历史请求。
     *
     * <p>分页查询组织架构同步的历史记录，支持按同步来源和同步状态过滤。
     *
     * @param request HTTP 请求对象，查询参数包含 pageNum、pageSize、syncSource、syncStatus
     * @return 包含同步历史分页数据的统一成功响应，失败时返回错误响应
     */
    @Operation(summary = "获取同步历史", description = "分页获取同步历史记录")
    @Parameters({
        @Parameter(
                name = "pageNum",
                in = ParameterIn.QUERY,
                description = "页码",
                schema = @Schema(type = "integer", defaultValue = "1")),
        @Parameter(
                name = "pageSize",
                in = ParameterIn.QUERY,
                description = "每页数量",
                schema = @Schema(type = "integer", defaultValue = "10")),
        @Parameter(name = "syncSource", in = ParameterIn.QUERY, description = "同步来源"),
        @Parameter(name = "syncStatus", in = ParameterIn.QUERY, description = "同步状态")
    })
    @OperationLog(module = "组织同步", type = OperationLog.OperationType.SELECT, description = "获取同步历史")
    public Mono<ServerResponse> getSyncHistory(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Integer pageNum = safeParseInt(request.queryParam("pageNum").orElse("1"), 1);
        Integer pageSize = safeParseInt(request.queryParam("pageSize").orElse("10"), 10);
        String syncSource = request.queryParam("syncSource").orElse(null);
        String syncStatus = request.queryParam("syncStatus").orElse(null);

        return orgSyncService
                .getSyncHistoryPage(pageNum, pageSize, syncSource, syncStatus)
                .flatMap(this::buildSuccess)
                .onErrorResume(e -> {
                    log.error("获取同步历史失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }
}
