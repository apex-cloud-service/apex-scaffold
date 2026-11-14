package com.apex.scaffold.fileservice.interfaces.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.file.application.dto.PageResponse;
import com.apex.scaffold.file.application.dto.data.ChartDataResponse;
import com.apex.scaffold.file.application.dto.data.ChartQueryRequest;
import com.apex.scaffold.file.application.dto.data.ChartRequest;
import com.apex.scaffold.file.application.dto.data.ChartResponse;
import com.apex.scaffold.file.application.service.ChartService;
import com.apex.scaffold.fileservice.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 图表管理处理器，提供图表配置、分页查询、状态管理和渲染数据查询。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "图表管理")
@Component
public class ChartHandler {

    private final ChartService chartService;
    private final RequestHandlerUtil requestHandlerUtil;

    /**
     * 构造图表处理器。
     * @param chartService 图表服务
     * @param requestHandlerUtil 请求处理工具
     */
    public ChartHandler(ChartService chartService, RequestHandlerUtil requestHandlerUtil) {
        this.chartService = chartService;
        this.requestHandlerUtil = requestHandlerUtil;
    }

    /**
     * 校验 JSON 图表配置并创建图表。
     * @param request 包含 ChartRequest 的请求
     * @return 包含图表详情的统一响应
     */
    @Operation(
            operationId = "createChart",
            summary = "创建图表",
            requestBody =
                    @RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ChartRequest.class))),
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "图表详情",
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ChartResult.class))))
    @OperationLog(
            module = "Chart",
            type = OperationLog.OperationType.INSERT,
            description = "create",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> create(ServerRequest request) {
        return requestHandlerUtil
                .handleRequestBody(request, ChartRequest.class)
                .flatMap(chartService::create)
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    /**
     * 校验 JSON 图表配置并更新路径指定的图表。
     * @param request 包含 id 路径参数和 ChartRequest 的请求
     * @return 包含更新后图表详情的统一响应
     */
    @Operation(
            operationId = "updateChart",
            summary = "更新图表",
            parameters =
                    @Parameter(
                            name = "id",
                            description = "图表 ID",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "integer", format = "int64")),
            requestBody =
                    @RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ChartRequest.class))),
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "图表详情",
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ChartResult.class))))
    @OperationLog(
            module = "Chart",
            type = OperationLog.OperationType.UPDATE,
            description = "update",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> update(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return requestHandlerUtil
                .handleRequestBody(request, ChartRequest.class)
                .flatMap(req -> chartService.update(id, req))
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    /**
     * 删除路径指定的图表。
     * @param request 包含 id 路径参数的请求
     * @return data 为空的统一成功响应
     */
    @Operation(
            operationId = "deleteChart",
            summary = "删除图表",
            parameters =
                    @Parameter(
                            name = "id",
                            description = "图表 ID",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "integer", format = "int64")),
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "删除成功",
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = EmptyChartResult.class))))
    @OperationLog(
            module = "Chart",
            type = OperationLog.OperationType.DELETE,
            description = "delete",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> delete(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return chartService.delete(id).then(ServerResponse.ok().bodyValue(Result.success()));
    }

    /**
     * 查询路径指定的图表详情。
     * @param request 包含 id 路径参数的请求
     * @return 包含图表详情的统一响应
     */
    @Operation(
            operationId = "getChartById",
            summary = "查询图表详情",
            parameters =
                    @Parameter(
                            name = "id",
                            description = "图表 ID",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "integer", format = "int64")),
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "图表详情",
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ChartResult.class))))
    @OperationLog(
            module = "Chart",
            type = OperationLog.OperationType.SELECT,
            description = "getById",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getById(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return chartService.getById(id).flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    /**
     * 累加浏览次数后查询图表渲染数据。
     * @param request 包含 id 路径参数的请求
     * @return 包含坐标轴和序列数据的统一响应
     */
    @Operation(
            operationId = "getChartData",
            summary = "查询图表数据",
            description = "先累加浏览次数，再返回渲染数据",
            parameters =
                    @Parameter(
                            name = "id",
                            description = "图表 ID",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "integer", format = "int64")),
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "图表渲染数据",
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ChartDataResult.class))))
    @OperationLog(
            module = "Chart",
            type = OperationLog.OperationType.SELECT,
            description = "getChartData",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getChartData(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return chartService
                .incrementViewCount(id)
                .then(chartService.getChartData(id))
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    /**
     * 按关键字、类型、分类和状态分页查询图表。
     * @param request 查询参数包含 keyword、chartType、category、status、page 和 size
     * @return 包含图表分页数据的统一响应
     */
    @Operation(
            operationId = "queryCharts",
            summary = "分页查询图表列表",
            parameters = {
                @Parameter(
                        name = "keyword",
                        in = ParameterIn.QUERY,
                        description = "关键字",
                        schema = @Schema(type = "string")),
                @Parameter(
                        name = "chartType",
                        in = ParameterIn.QUERY,
                        description = "图表类型",
                        schema = @Schema(type = "string")),
                @Parameter(
                        name = "category",
                        in = ParameterIn.QUERY,
                        description = "分类",
                        schema = @Schema(type = "string")),
                @Parameter(
                        name = "status",
                        in = ParameterIn.QUERY,
                        description = "状态",
                        schema = @Schema(type = "integer")),
                @Parameter(
                        name = "page",
                        in = ParameterIn.QUERY,
                        description = "页码",
                        schema = @Schema(type = "integer", defaultValue = "1")),
                @Parameter(
                        name = "size",
                        in = ParameterIn.QUERY,
                        description = "每页数量",
                        schema = @Schema(type = "integer", defaultValue = "20"))
            },
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "图表分页结果",
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ChartPageResult.class))))
    @OperationLog(
            module = "Chart",
            type = OperationLog.OperationType.SELECT,
            description = "query",
            saveRequest = false,
            saveResponse = false)
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
     * 更新路径指定图表的状态。
     * @param request 包含 id 路径参数和状态 JSON 的请求
     * @return data 为空的统一成功响应
     */
    @Operation(
            operationId = "updateChartStatus",
            summary = "更新图表状态",
            parameters =
                    @Parameter(
                            name = "id",
                            description = "图表 ID",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "integer", format = "int64")),
            requestBody =
                    @RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = UpdateStatusRequest.class))),
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "更新成功",
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = EmptyChartResult.class))))
    @OperationLog(
            module = "Chart",
            type = OperationLog.OperationType.UPDATE,
            description = "updateStatus",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> updateStatus(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return requestHandlerUtil
                .handleRequestBody(request, UpdateStatusRequest.class)
                .flatMap(req -> chartService.updateStatus(id, req.getStatus()))
                .then(ServerResponse.ok().bodyValue(Result.success()));
    }

    /** 图表状态更新请求。 */
    @Data
    @Schema(name = "ChartStatusRequest", description = "图表状态更新请求")
    public static class UpdateStatusRequest {
        @Schema(description = "图表状态：0-草稿，1-正常", example = "1")
        private Integer status;
    }

    /** 图表详情统一响应的文档模型。 */
    @Schema(name = "ChartResult")
    public static class ChartResult extends Result<ChartResponse> {}

    /** 图表渲染数据统一响应的文档模型。 */
    @Schema(name = "ChartDataResult")
    public static class ChartDataResult extends Result<ChartDataResponse> {}

    /** 图表分页统一响应的文档模型。 */
    @Schema(name = "ChartPageResult")
    public static class ChartPageResult extends Result<PageResponse<ChartResponse>> {}

    /** 图表无数据统一响应的文档模型。 */
    @Schema(name = "EmptyChartResult")
    public static class EmptyChartResult extends Result<Void> {}
}
