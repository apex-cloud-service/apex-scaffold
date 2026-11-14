package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.interfaces.handler.OperationsHealthHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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
 * 运维路由配置（/api/v2/operations）。
 *
 * <p>提供平台级运维体检报告与体检历史能力；经 {@code RequirePermissionAspect} 校验
 * {@code system:operations:health:read}（V67 迁移预置）及快照读取、采集、导出、
 * 租户摘要权限（V69 迁移预置）。</p>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
public class OperationsRouter {

    /**
     * 运维体检路由（/api/v2/operations）。
     *
     * @param handler 运维体检处理器
     * @return 运维路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v2/operations/health-report",
                method = RequestMethod.GET,
                beanClass = OperationsHealthHandler.class,
                beanMethod = "healthReport",
                operation =
                        @Operation(
                                summary = "生成管理员体检报告",
                                description = "按应用存活、依赖就绪、业务风险、容量分组输出可解释体检结论（单实例范围）",
                                tags = {"运维体检"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v2/operations/health-snapshots",
                method = RequestMethod.POST,
                beanClass = OperationsHealthHandler.class,
                beanMethod = "captureSnapshot",
                operation =
                        @Operation(
                                summary = "采集体检快照",
                                description = "执行一次实例内体检并把结论级结果固化到历史快照",
                                tags = {"运维体检"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v2/operations/health-snapshots",
                method = RequestMethod.GET,
                beanClass = OperationsHealthHandler.class,
                beanMethod = "listSnapshots",
                operation =
                        @Operation(
                                summary = "查询体检快照历史",
                                description = "按生成时间范围与总体结论分页查询体检快照",
                                tags = {"运维体检"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v2/operations/health-snapshots/export",
                method = RequestMethod.GET,
                beanClass = OperationsHealthHandler.class,
                beanMethod = "exportSnapshots",
                operation =
                        @Operation(
                                summary = "导出体检快照",
                                description = "按时间范围与总体结论导出体检快照 CSV",
                                tags = {"运维体检"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = "text/csv"))
                                })),
        @RouterOperation(
                path = "/api/v2/operations/health-snapshots/export-tasks",
                method = RequestMethod.POST,
                beanClass = OperationsHealthHandler.class,
                beanMethod = "submitExportTask",
                operation =
                        @Operation(
                                summary = "提交体检快照异步导出任务",
                                description = "记录数超过异步导出上限时拒绝；任务由后台生成 CSV",
                                tags = {"运维体检"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v2/operations/health-snapshots/export-tasks/{id}",
                method = RequestMethod.GET,
                beanClass = OperationsHealthHandler.class,
                beanMethod = "getExportTask",
                operation =
                        @Operation(
                                summary = "查询体检快照异步导出任务",
                                description = "返回任务状态、尝试次数、失败原因与导出记录数",
                                tags = {"运维体检"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v2/operations/health-snapshots/export-tasks/{id}/download",
                method = RequestMethod.GET,
                beanClass = OperationsHealthHandler.class,
                beanMethod = "downloadExportTask",
                operation =
                        @Operation(
                                summary = "下载体检快照异步导出结果",
                                description = "仅已完成任务可下载",
                                tags = {"运维体检"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = "text/csv"))
                                })),
        @RouterOperation(
                path = "/api/v2/operations/health-trend",
                method = RequestMethod.GET,
                beanClass = OperationsHealthHandler.class,
                beanMethod = "trend",
                operation =
                        @Operation(
                                summary = "查询体检趋势",
                                description = "给出结论分布、首尾状态与是否收敛",
                                tags = {"运维体检"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v2/operations/tenant-health-summary",
                method = RequestMethod.GET,
                beanClass = OperationsHealthHandler.class,
                beanMethod = "tenantSummary",
                operation =
                        @Operation(
                                summary = "查询租户体检摘要",
                                description = "只返回当前租户最近一次体检的总体结论、风险计数与近期趋势",
                                tags = {"运维体检"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> operationsHealthRoutes(OperationsHealthHandler handler) {
        return RouterFunctions.route()
                .path("/api/v2/operations", routes -> routes.GET("/health-report", handler::healthReport)
                        .POST("/health-snapshots", handler::captureSnapshot)
                        .GET("/health-snapshots", handler::listSnapshots)
                        .GET("/health-snapshots/export", handler::exportSnapshots)
                        .POST("/health-snapshots/export-tasks", handler::submitExportTask)
                        .GET("/health-snapshots/export-tasks/{id}", handler::getExportTask)
                        .GET("/health-snapshots/export-tasks/{id}/download", handler::downloadExportTask)
                        .GET("/health-trend", handler::trend)
                        .GET("/tenant-health-summary", handler::tenantSummary))
                .build();
    }
}
