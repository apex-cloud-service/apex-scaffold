package com.apex.scaffold.fileservice.interfaces.handler;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.file.application.dto.data.ChartQueryRequest;
import com.apex.scaffold.file.application.dto.data.ChartRequest;
import com.apex.scaffold.file.application.service.ChartService;
import com.apex.scaffold.fileservice.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
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
 *   <li>POST /admin-api/v1/charts - 创建图表</li>
 *   <li>PUT /admin-api/v1/charts/{id} - 更新图表</li>
 *   <li>DELETE /admin-api/v1/charts/{id} - 删除图表</li>
 *   <li>GET /admin-api/v1/charts/{id} - 查询图表详情</li>
 *   <li>GET /admin-api/v1/charts/{id}/data - 查询图表数据</li>
 *   <li>GET /admin-api/v1/charts - 分页查询图表列表</li>
 *   <li>PUT /admin-api/v1/charts/{id}/status - 更新图表状态</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ChartService
 */
@Slf4j
@Tag(name = "图表管理")
@Component
public class ChartHandler {

    private final ChartService chartService;

    private final RequestHandlerUtil requestHandlerUtil;

    /**
     * 构造图表处理器。
     *
     * @param chartService       图表服务
     * @param requestHandlerUtil 请求处理工具
     */
    public ChartHandler(ChartService chartService, RequestHandlerUtil requestHandlerUtil) {
        this.chartService = chartService;
        this.requestHandlerUtil = requestHandlerUtil;
    }

    /**
     * 处理创建图表请求。
     *
     * <p>解析请求体为 {@link ChartRequest}，调用应用服务创建图表。
     *
     * @param request HTTP 请求对象，请求体为图表配置信息 JSON
     * @return 包含新建图表信息的统一成功响应
     */
    @Operation(summary = "创建图表")
    public Mono<ServerResponse> create(ServerRequest request) {
        return requestHandlerUtil
                .handleRequestBody(request, ChartRequest.class)
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
    @Operation(summary = "更新图表")
    public Mono<ServerResponse> update(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return requestHandlerUtil
                .handleRequestBody(request, ChartRequest.class)
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
    @Operation(summary = "删除图表")
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
    @Operation(summary = "更新图表状态")
    public Mono<ServerResponse> updateStatus(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return requestHandlerUtil
                .handleRequestBody(request, UpdateStatusRequest.class)
                .flatMap(req -> chartService.updateStatus(id, req.getStatus()))
                .then(ServerResponse.ok().bodyValue(Result.success()));
    }

    @Data
    @Schema(description = "图表状态更新请求")
    public static class UpdateStatusRequest {
        @Schema(description = "图表状态：0-草稿，1-正常", example = "1")
        private Integer status;
    }
}
