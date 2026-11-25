package com.apex.scaffold.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.interfaces.handler.DatabaseConfigHandler;
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
 * 数据库配置路由配置（/admin-api/v1/database-configs）。
 *
 * <p>由原 {@code ApiRouter} 按子域拆分而来。</p>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
public class DatabaseConfigRouter {

    /**
     * 系统管理-数据库配置路由（/admin-api/v1/database）。
     *
     * <p>提供数据库连接测试、部署配置生成、当前数据库信息查询，以及数据源
     * 管理的完整 CRUD 与连接池监控端点。所有端点经 {@code RequirePermissionAspect}
     * 校验 {@code system:database:*} 权限；数据源管理端点要求
     * {@code system:database:datasource:*} 细粒度权限。</p>
     *
     * <h2>职责范围</h2>
     * <ul>
     *   <li>GET /admin-api/v1/database/datasources - 分页查询数据源列表</li>
     *   <li>POST /admin-api/v1/database/datasources - 创建数据源</li>
     *   <li>PUT /admin-api/v1/database/datasources/{id} - 更新数据源</li>
     *   <li>DELETE /admin-api/v1/database/datasources/{id} - 删除数据源</li>
     *   <li>GET /admin-api/v1/database/datasources/{id}/metrics - 查询连接池监控指标</li>
     * </ul>
     *
     * @param handler 数据库配置处理器
     * @return 数据库配置管理路由函数
     * @since 1.2.0
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/database/test",
                method = RequestMethod.POST,
                beanClass = DatabaseConfigHandler.class,
                beanMethod = "testConnection",
                operation =
                        @Operation(
                                summary = "测试数据库连接",
                                description = "使用给定连接信息测试数据库连通性",
                                tags = {"数据库配置"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/database/generate-config",
                method = RequestMethod.POST,
                beanClass = DatabaseConfigHandler.class,
                beanMethod = "generateConfig",
                operation =
                        @Operation(
                                summary = "生成部署配置",
                                description = "根据连接信息生成数据源部署配置",
                                tags = {"数据库配置"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/database/current",
                method = RequestMethod.GET,
                beanClass = DatabaseConfigHandler.class,
                beanMethod = "getCurrentInfo",
                operation =
                        @Operation(
                                summary = "查询当前数据库信息",
                                description = "查询当前应用所连接数据库的信息",
                                tags = {"数据库配置"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/database/datasources",
                method = RequestMethod.GET,
                beanClass = DatabaseConfigHandler.class,
                beanMethod = "listDataSources",
                operation =
                        @Operation(
                                summary = "分页查询数据源列表",
                                description = "分页查询已登记的数据源列表",
                                tags = {"数据库配置"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/database/datasources",
                method = RequestMethod.POST,
                beanClass = DatabaseConfigHandler.class,
                beanMethod = "createDataSource",
                operation =
                        @Operation(
                                summary = "创建数据源",
                                description = "登记新的数据源配置",
                                tags = {"数据库配置"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/database/datasources/{id}",
                method = RequestMethod.PUT,
                beanClass = DatabaseConfigHandler.class,
                beanMethod = "updateDataSource",
                operation =
                        @Operation(
                                summary = "更新数据源",
                                description = "根据 ID 更新数据源配置信息",
                                tags = {"数据库配置"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "数据源 ID",
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
                path = "/admin-api/v1/database/datasources/{id}",
                method = RequestMethod.DELETE,
                beanClass = DatabaseConfigHandler.class,
                beanMethod = "deleteDataSource",
                operation =
                        @Operation(
                                summary = "删除数据源",
                                description = "根据 ID 删除数据源配置",
                                tags = {"数据库配置"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "数据源 ID",
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
                path = "/admin-api/v1/database/datasources/{id}/metrics",
                method = RequestMethod.GET,
                beanClass = DatabaseConfigHandler.class,
                beanMethod = "getDataSourceMetrics",
                operation =
                        @Operation(
                                summary = "查询数据源连接池指标",
                                description = "查询指定数据源的连接池监控指标",
                                tags = {"数据库配置"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "数据源 ID",
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
     * 注册数据库配置管理路由。
     *
     * <p>覆盖连接测试、配置生成、当前数据源信息以及多数据源增删改查；写操作要求 JSON 请求体。
     *
     * @param handler 数据库配置处理器
     * @return 数据库配置管理路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> adminDatabaseConfigRoutes(DatabaseConfigHandler handler) {
        return RouterFunctions.route()
                .POST("/admin-api/v1/database/test", contentType(MediaType.APPLICATION_JSON), handler::testConnection)
                .POST(
                        "/admin-api/v1/database/generate-config",
                        contentType(MediaType.APPLICATION_JSON),
                        handler::generateConfig)
                .GET("/admin-api/v1/database/current", handler::getCurrentInfo)
                .path("/admin-api/v1/database/datasources", routes -> routes.GET("", handler::listDataSources)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::createDataSource)
                        .PUT("/{id}", contentType(MediaType.APPLICATION_JSON), handler::updateDataSource)
                        .DELETE("/{id}", handler::deleteDataSource)
                        .GET("/{id}/metrics", handler::getDataSourceMetrics))
                .build();
    }
}
