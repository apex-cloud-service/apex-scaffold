package com.apex.scaffold.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.interfaces.handler.JobHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * 定时任务路由配置（/admin-api/v1/jobs）。
 *
 * <p>由原 {@code ApiRouter} 按子域拆分而来。</p>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
public class JobRouter {

    /**
     * 系统管理-定时任务路由（/admin-api/v1/jobs）。
     *
     * <p>提供定时任务的查询、创建、更新、删除、启停、手动触发与
     * 执行日志端点，所有端点经 {@code RequirePermissionAspect} 校验
     * {@code system:job:*} 权限。</p>
     *
     * @param handler 定时任务处理器
     * @return 定时任务管理路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/jobs",
                method = RequestMethod.GET,
                beanClass = JobHandler.class,
                beanMethod = "list",
                operation =
                        @Operation(
                                summary = "查询定时任务列表",
                                description = "查询全部定时任务",
                                tags = {"定时任务"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/jobs/page",
                method = RequestMethod.GET,
                beanClass = JobHandler.class,
                beanMethod = "page",
                operation =
                        @Operation(
                                summary = "分页查询定时任务",
                                description = "分页查询定时任务列表，支持条件筛选",
                                tags = {"定时任务"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}",
                method = RequestMethod.GET,
                beanClass = JobHandler.class,
                beanMethod = "detail",
                operation =
                        @Operation(
                                summary = "查询定时任务详情",
                                description = "根据任务 ID 查询定时任务详细信息",
                                tags = {"定时任务"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "任务 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/jobs",
                method = RequestMethod.POST,
                beanClass = JobHandler.class,
                beanMethod = "create",
                operation =
                        @Operation(
                                summary = "创建定时任务",
                                description = "创建新的定时任务",
                                tags = {"定时任务"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}",
                method = RequestMethod.PUT,
                beanClass = JobHandler.class,
                beanMethod = "update",
                operation =
                        @Operation(
                                summary = "更新定时任务",
                                description = "根据任务 ID 更新定时任务信息",
                                tags = {"定时任务"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "任务 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}",
                method = RequestMethod.DELETE,
                beanClass = JobHandler.class,
                beanMethod = "delete",
                operation =
                        @Operation(
                                summary = "删除定时任务",
                                description = "根据任务 ID 删除定时任务",
                                tags = {"定时任务"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "任务 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}/status",
                method = RequestMethod.PUT,
                beanClass = JobHandler.class,
                beanMethod = "updateStatus",
                operation =
                        @Operation(
                                summary = "修改定时任务启停状态",
                                description = "启用或禁用指定定时任务",
                                tags = {"定时任务"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "任务 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}/run",
                method = RequestMethod.POST,
                beanClass = JobHandler.class,
                beanMethod = "runOnce",
                operation =
                        @Operation(
                                summary = "手动触发定时任务",
                                description = "立即触发指定定时任务执行一次",
                                tags = {"定时任务"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "任务 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}/logs",
                method = RequestMethod.GET,
                beanClass = JobHandler.class,
                beanMethod = "logs",
                operation =
                        @Operation(
                                summary = "查询任务执行日志",
                                description = "分页查询指定定时任务的执行日志",
                                tags = {"定时任务"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "任务 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    /**
     * 注册后台任务管理路由。
     *
     * <p>覆盖 {@code /admin-api/v1/jobs} 的列表、分页、详情、创建、更新、删除、状态切换、
     * 手动执行与执行日志查询；写操作要求 JSON 请求体。
     *
     * @param handler 任务管理处理器
     * @return 任务管理路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> adminJobRoutes(JobHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/jobs", routes -> routes.GET("", handler::list)
                        .GET("/page", handler::page)
                        .GET("/{id}", handler::detail)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::create)
                        .PUT("/{id}", contentType(MediaType.APPLICATION_JSON), handler::update)
                        .DELETE("/{id}", handler::delete)
                        .PUT("/{id}/status", contentType(MediaType.APPLICATION_JSON), handler::updateStatus)
                        .POST("/{id}/run", handler::runOnce)
                        .GET("/{id}/logs", handler::logs))
                .build();
    }
}
