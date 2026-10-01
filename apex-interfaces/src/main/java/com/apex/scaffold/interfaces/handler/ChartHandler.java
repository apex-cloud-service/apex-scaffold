package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.data.ChartQueryRequest;
import com.apex.scaffold.application.dto.data.ChartRequest;
import com.apex.scaffold.application.service.ChartService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 图表管理处理器（函数式端点）。
 *
 * <p>提供图表管理的完整 CRUD API，包括图表创建、更新、删除、查询、
 * 状态管理、图表数据获取以及浏览计数等功能，支持分页查询与多维度过滤。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>POST /api/charts - 创建图表（需要 data:chart:create 权限）</li>
 *   <li>PUT /api/charts/{id} - 更新图表（需要 data:chart:update 权限）</li>
 *   <li>DELETE /api/charts/{id} - 删除图表（需要 data:chart:delete 权限）</li>
 *   <li>GET /api/charts/{id} - 查询图表详情（需要 data:chart:query 权限）</li>
 *   <li>GET /api/charts/{id}/data - 查询图表数据（需要 data:chart:query 权限）</li>
 *   <li>GET /api/charts - 分页查询图表列表（需要 data:chart:query 权限）</li>
 *   <li>PUT /api/charts/{id}/status - 更新图表状态（需要 data:chart:update 权限）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ChartService
 */
@Tag(name = "图表管理")
@Component
@RequiredArgsConstructor
public class ChartHandler {

    private final ChartService chartService;

    /**
     * 处理创建图表请求。
     *
     * <p>解析请求体为 {@link ChartRequest}，调用应用服务创建图表。
     *
     * @param request HTTP 请求对象，请求体为图表配置信息 JSON
     * @return 包含新建图表信息的统一成功响应
     */
    @OperationLog(module = "图表管理", type = OperationLog.OperationType.INSERT, description = "创建图表")
    @Operation(summary = "创建图表")
    @PreAuthorize("hasAuthority('data:chart:create')")
    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(ChartRequest.class)
                .flatMap(chartService::create)
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    /**
     * 处理更新图表请求。
     *
     * <p>根据路径中的图表 ID 解析请求体并更新图表配置信息。
     *
     * @param request HTTP 请求对象，路径参数包含图表 ID，请求体为图表配置信息 JSON
     * @return 包含更新后图表信息的统一成功响应
     */
    @OperationLog(module = "图表管理", type = OperationLog.OperationType.UPDATE, description = "更新图表")
    @Operation(summary = "更新图表")
    @PreAuthorize("hasAuthority('data:chart:update')")
    public Mono<ServerResponse> update(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return request.bodyToMono(ChartRequest.class)
                .flatMap(req -> chartService.update(id, req))
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    /**
     * 处理删除图表请求。
     *
     * <p>根据路径中的图表 ID 删除指定图表。
     *
     * @param request HTTP 请求对象，路径参数包含图表 ID
     * @return 删除成功的统一成功响应
     */
    @OperationLog(module = "图表管理", type = OperationLog.OperationType.DELETE, description = "删除图表")
    @Operation(summary = "删除图表")
    @PreAuthorize("hasAuthority('data:chart:delete')")
    public Mono<ServerResponse> delete(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return chartService.delete(id).then(ServerResponse.ok().bodyValue(Result.success()));
    }

    /**
     * 处理查询图表详情请求。
     *
     * <p>根据路径中的图表 ID 查询并返回图表详情。
     *
     * @param request HTTP 请求对象，路径参数包含图表 ID
     * @return 包含图表详情的统一成功响应
     */
    @Operation(summary = "查询图表详情")
    @PreAuthorize("hasAuthority('data:chart:query')")
    public Mono<ServerResponse> getById(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return chartService.getById(id).flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    /**
     * 处理查询图表数据请求。
     *
     * <p>先对指定图表累加浏览计数，再查询并返回图表的渲染数据。
     *
     * @param request HTTP 请求对象，路径参数包含图表 ID
     * @return 包含图表数据的统一成功响应
     */
    @Operation(summary = "查询图表数据")
    @PreAuthorize("hasAuthority('data:chart:query')")
    public Mono<ServerResponse> getChartData(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return chartService
                .incrementViewCount(id)
                .then(chartService.getChartData(id))
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    /**
     * 处理分页查询图表列表请求。
     *
     * <p>从查询参数构建过滤条件（关键字、图表类型、分类、状态、页码、每页数量），
     * 调用应用服务分页查询图表列表。
     *
     * @param request HTTP 请求对象，查询参数包含 keyword、chartType、category、status、page、size
     * @return 包含图表分页结果的统一成功响应
     */
    @Operation(summary = "分页查询图表列表")
    @PreAuthorize("hasAuthority('data:chart:query')")
    public Mono<ServerResponse> query(ServerRequest request) {
        ChartQueryRequest queryRequest = ChartQueryRequest.builder()
                .keyword(request.queryParam("keyword").orElse(null))
                .chartType(request.queryParam("chartType").orElse(null))
                .category(request.queryParam("category").orElse(null))
                .status(request.queryParam("status").map(Integer::parseInt).orElse(null))
                .pageNum(request.queryParam("page").map(Integer::parseInt).orElse(1))
                .pageSize(request.queryParam("size").map(Integer::parseInt).orElse(20))
                .build();

        return chartService.query(queryRequest).flatMap(result -> ServerResponse.ok()
                .bodyValue(Result.success(result)));
    }

    /**
     * 处理更新图表状态请求。
     *
     * <p>解析请求体中的目标状态，并更新指定图表的状态。
     *
     * @param request HTTP 请求对象，路径参数包含图表 ID，请求体包含状态值
     * @return 更新成功的统一成功响应
     */
    @OperationLog(module = "图表管理", type = OperationLog.OperationType.UPDATE, description = "更新图表状态")
    @Operation(summary = "更新图表状态")
    @PreAuthorize("hasAuthority('data:chart:update')")
    public Mono<ServerResponse> updateStatus(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return request.bodyToMono(UpdateStatusRequest.class)
                .flatMap(req -> chartService.updateStatus(id, req.getStatus()))
                .then(ServerResponse.ok().bodyValue(Result.success()));
    }

    @Data
    @io.swagger.v3.oas.annotations.media.Schema(description = "图表状态更新请求")
    public static class UpdateStatusRequest {
        @io.swagger.v3.oas.annotations.media.Schema(description = "图表状态：0-草稿，1-正常", example = "1")
        private Integer status;
    }
}
