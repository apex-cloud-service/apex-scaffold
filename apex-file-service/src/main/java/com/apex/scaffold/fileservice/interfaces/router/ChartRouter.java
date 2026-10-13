package com.apex.scaffold.fileservice.interfaces.router;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

import com.apex.scaffold.file.application.dto.data.ChartRequest;
import com.apex.scaffold.fileservice.interfaces.handler.ChartHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 图表管理路由配置类。
 *
 * <p>基于 Spring WebFlux 函数式编程模型，注册图表资源的 RESTful 路由规则。
 * 路由前缀为 {@code /admin-api/v1/charts}，支持图表的 CRUD 操作、状态变更和图表数据查询。
 *
 * <p>路由规则：
 * <ul>
 *   <li>GET /admin-api/v1/charts - 查询图表列表（支持分页、筛选）</li>
 *   <li>POST /admin-api/v1/charts - 创建新图表</li>
 *   <li>GET /admin-api/v1/charts/{id} - 获取图表元数据</li>
 *   <li>GET /admin-api/v1/charts/{id}/data - 获取图表数据（数据点、时间序列、统计值）</li>
 *   <li>PUT /admin-api/v1/charts/{id} - 更新图表配置</li>
 *   <li>DELETE /admin-api/v1/charts/{id} - 删除图表</li>
 *   <li>PUT /admin-api/v1/charts/{id}/status - 更新图表状态（启用、停用等）</li>
 * </ul>
 *
 * <p>所有路由要求请求头 {@code Accept: application/json}，实际业务处理委托给 {@link ChartHandler}。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ChartHandler
 * @see org.springframework.web.reactive.function.server.RouterFunction
 */
@Configuration
public class ChartRouter {

    /**
     * 配置图表管理路由函数。
     *
     * <p>注册图表资源的所有 HTTP 端点，统一路由前缀为 {@code /admin-api/v1/charts}。
     * 各路由的业务逻辑由注入的 {@link ChartHandler} 实现。
     *
     * @param handler 图表业务处理器
     * @return 图表路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/charts",
                method = RequestMethod.GET,
                beanClass = ChartHandler.class,
                beanMethod = "query",
                operation =
                        @Operation(
                                operationId = "queryCharts",
                                summary = "分页查询图表列表",
                                description = "按条件分页查询图表资源，支持类型与状态筛选",
                                tags = {"图表管理"},
                                parameters = {
                                    @Parameter(name = "keyword", description = "图表名称关键字", in = ParameterIn.QUERY),
                                    @Parameter(name = "chartType", description = "图表类型", in = ParameterIn.QUERY),
                                    @Parameter(name = "category", description = "图表分类", in = ParameterIn.QUERY),
                                    @Parameter(
                                            name = "status",
                                            description = "图表状态",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "integer")),
                                    @Parameter(
                                            name = "page",
                                            description = "页码，从 1 开始",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "integer", defaultValue = "1", minimum = "1")),
                                    @Parameter(
                                            name = "size",
                                            description = "每页数量",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "integer", defaultValue = "20", minimum = "1"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/charts",
                method = RequestMethod.POST,
                beanClass = ChartHandler.class,
                beanMethod = "create",
                operation =
                        @Operation(
                                operationId = "createChart",
                                summary = "创建图表",
                                requestBody =
                                        @RequestBody(
                                                description = "图表创建信息",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema = @Schema(implementation = ChartRequest.class))),
                                description = "根据请求参数创建新图表并返回图表标识",
                                tags = {"图表管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "创建成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/charts/{id}",
                method = RequestMethod.GET,
                beanClass = ChartHandler.class,
                beanMethod = "getById",
                operation =
                        @Operation(
                                operationId = "getChartById",
                                summary = "查询图表详情",
                                description = "根据图表 ID 查询图表元数据",
                                tags = {"图表管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "图表 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/charts/{id}/data",
                method = RequestMethod.GET,
                beanClass = ChartHandler.class,
                beanMethod = "getChartData",
                operation =
                        @Operation(
                                operationId = "getChartData",
                                summary = "查询图表数据",
                                description = "根据图表 ID 查询图表数据点、时间序列与统计值",
                                tags = {"图表管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "图表 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/charts/{id}",
                method = RequestMethod.PUT,
                beanClass = ChartHandler.class,
                beanMethod = "update",
                operation =
                        @Operation(
                                operationId = "updateChart",
                                summary = "更新图表配置",
                                requestBody =
                                        @RequestBody(
                                                description = "图表更新信息",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema = @Schema(implementation = ChartRequest.class))),
                                description = "根据图表 ID 更新图表配置信息",
                                tags = {"图表管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "图表 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "更新成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/charts/{id}",
                method = RequestMethod.DELETE,
                beanClass = ChartHandler.class,
                beanMethod = "delete",
                operation =
                        @Operation(
                                operationId = "deleteChart",
                                summary = "删除图表",
                                description = "根据图表 ID 删除图表",
                                tags = {"图表管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "图表 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "删除成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/charts/{id}/status",
                method = RequestMethod.PUT,
                beanClass = ChartHandler.class,
                beanMethod = "updateStatus",
                operation =
                        @Operation(
                                operationId = "updateChartStatus",
                                summary = "变更图表状态",
                                requestBody =
                                        @RequestBody(
                                                description = "图表状态更新信息",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        ChartHandler.UpdateStatusRequest
                                                                                                .class))),
                                description = "根据图表 ID 变更图表状态（启用、停用等）",
                                tags = {"图表管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "图表 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "变更成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> chartRoutes(ChartHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/charts", builder -> builder.GET("", accept(APPLICATION_JSON), handler::query)
                        .POST("", accept(APPLICATION_JSON), handler::create)
                        .GET("/{id}", accept(APPLICATION_JSON), handler::getById)
                        .GET("/{id}/data", accept(APPLICATION_JSON), handler::getChartData)
                        .PUT("/{id}", accept(APPLICATION_JSON), handler::update)
                        .DELETE("/{id}", accept(APPLICATION_JSON), handler::delete)
                        .PUT("/{id}/status", accept(APPLICATION_JSON), handler::updateStatus))
                .build();
    }
}
