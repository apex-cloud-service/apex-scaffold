package com.apex.scaffold.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.interfaces.handler.ConfigHandler;
import com.apex.scaffold.interfaces.handler.DictHandler;
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
 * 配置与字典路由配置（/admin-api/v1/dict、/admin-api/v1/configs）。
 *
 * <p>由原 {@code ApiRouter} 按子域拆分而来。</p>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
public class ConfigRouter {

    /**
     * 系统管理-字典路由（/admin-api/v1/dict）。
     *
     * <p>提供字典类型与字典数据的查询、创建、更新、删除与启停管理端点，
     * 所有端点经 {@code RequirePermissionAspect} 校验 {@code system:dict:*} 权限。</p>
     *
     * @param handler 系统字典处理器
     * @return 字典管理路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/dict/types",
                method = RequestMethod.GET,
                beanClass = DictHandler.class,
                beanMethod = "listTypes",
                operation =
                        @Operation(
                                summary = "分页查询字典类型",
                                description = "分页查询字典类型列表，支持条件筛选",
                                tags = {"字典管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/dict/types/{id}",
                method = RequestMethod.GET,
                beanClass = DictHandler.class,
                beanMethod = "getType",
                operation =
                        @Operation(
                                summary = "查询字典类型详情",
                                description = "根据 ID 查询字典类型详细信息",
                                tags = {"字典管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "字典类型 ID",
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
                path = "/admin-api/v1/dict/types",
                method = RequestMethod.POST,
                beanClass = DictHandler.class,
                beanMethod = "createType",
                operation =
                        @Operation(
                                summary = "创建字典类型",
                                description = "创建新的字典类型",
                                tags = {"字典管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/dict/types/{id}",
                method = RequestMethod.PUT,
                beanClass = DictHandler.class,
                beanMethod = "updateType",
                operation =
                        @Operation(
                                summary = "更新字典类型",
                                description = "根据 ID 更新字典类型信息",
                                tags = {"字典管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "字典类型 ID",
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
                path = "/admin-api/v1/dict/types/{id}",
                method = RequestMethod.DELETE,
                beanClass = DictHandler.class,
                beanMethod = "deleteType",
                operation =
                        @Operation(
                                summary = "删除字典类型",
                                description = "根据 ID 删除字典类型",
                                tags = {"字典管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "字典类型 ID",
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
                path = "/admin-api/v1/dict/types/{id}/status",
                method = RequestMethod.PUT,
                beanClass = DictHandler.class,
                beanMethod = "updateTypeStatus",
                operation =
                        @Operation(
                                summary = "修改字典类型启停状态",
                                description = "启用或禁用指定字典类型",
                                tags = {"字典管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "字典类型 ID",
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
                path = "/admin-api/v1/dict/data",
                method = RequestMethod.GET,
                beanClass = DictHandler.class,
                beanMethod = "listData",
                operation =
                        @Operation(
                                summary = "分页查询字典数据",
                                description = "分页查询指定字典类型下的字典数据列表",
                                tags = {"字典管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/dict/data/{id}",
                method = RequestMethod.GET,
                beanClass = DictHandler.class,
                beanMethod = "getData",
                operation =
                        @Operation(
                                summary = "查询字典数据详情",
                                description = "根据 ID 查询字典数据详细信息",
                                tags = {"字典管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "字典数据 ID",
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
                path = "/admin-api/v1/dict/data",
                method = RequestMethod.POST,
                beanClass = DictHandler.class,
                beanMethod = "createData",
                operation =
                        @Operation(
                                summary = "创建字典数据",
                                description = "在指定字典类型下创建新的字典数据",
                                tags = {"字典管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/dict/data/{id}",
                method = RequestMethod.PUT,
                beanClass = DictHandler.class,
                beanMethod = "updateData",
                operation =
                        @Operation(
                                summary = "更新字典数据",
                                description = "根据 ID 更新字典数据信息",
                                tags = {"字典管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "字典数据 ID",
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
                path = "/admin-api/v1/dict/data/{id}",
                method = RequestMethod.DELETE,
                beanClass = DictHandler.class,
                beanMethod = "deleteData",
                operation =
                        @Operation(
                                summary = "删除字典数据",
                                description = "根据 ID 删除字典数据",
                                tags = {"字典管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "字典数据 ID",
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
                path = "/admin-api/v1/dict/data/{id}/status",
                method = RequestMethod.PUT,
                beanClass = DictHandler.class,
                beanMethod = "updateDataStatus",
                operation =
                        @Operation(
                                summary = "修改字典数据启停状态",
                                description = "启用或禁用指定字典数据",
                                tags = {"字典管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "字典数据 ID",
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
    @Bean
    public RouterFunction<ServerResponse> adminDictRoutes(DictHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/dict/types", types -> types.GET("", handler::listTypes)
                        .GET("/{id}", handler::getType)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::createType)
                        .PUT("/{id}", contentType(MediaType.APPLICATION_JSON), handler::updateType)
                        .DELETE("/{id}", handler::deleteType)
                        .PUT("/{id}/status", contentType(MediaType.APPLICATION_JSON), handler::updateTypeStatus))
                .path("/admin-api/v1/dict/data", data -> data.GET("", handler::listData)
                        .GET("/{id}", handler::getData)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::createData)
                        .PUT("/{id}", contentType(MediaType.APPLICATION_JSON), handler::updateData)
                        .DELETE("/{id}", handler::deleteData)
                        .PUT("/{id}/status", contentType(MediaType.APPLICATION_JSON), handler::updateDataStatus))
                .build();
    }

    /**
     * 系统管理-参数配置路由（/admin-api/v1/configs）。
     *
     * <p>提供参数配置的查询、详情、创建、更新、删除与启停管理端点，
     * 所有端点经 {@code RequirePermissionAspect} 校验 {@code system:config:*} 权限。</p>
     *
     * @param handler 系统参数配置处理器
     * @return 参数配置管理路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/configs",
                method = RequestMethod.GET,
                beanClass = ConfigHandler.class,
                beanMethod = "list",
                operation =
                        @Operation(
                                summary = "分页查询参数配置",
                                description = "分页查询系统参数配置列表，支持条件筛选",
                                tags = {"参数配置"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/configs/{id}",
                method = RequestMethod.GET,
                beanClass = ConfigHandler.class,
                beanMethod = "detail",
                operation =
                        @Operation(
                                summary = "查询参数配置详情",
                                description = "根据 ID 查询参数配置详细信息",
                                tags = {"参数配置"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "配置 ID",
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
                path = "/admin-api/v1/configs",
                method = RequestMethod.POST,
                beanClass = ConfigHandler.class,
                beanMethod = "create",
                operation =
                        @Operation(
                                summary = "创建参数配置",
                                description = "创建新的系统参数配置",
                                tags = {"参数配置"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/configs/{id}",
                method = RequestMethod.PUT,
                beanClass = ConfigHandler.class,
                beanMethod = "update",
                operation =
                        @Operation(
                                summary = "更新参数配置",
                                description = "根据 ID 更新参数配置信息",
                                tags = {"参数配置"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "配置 ID",
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
                path = "/admin-api/v1/configs/{id}",
                method = RequestMethod.DELETE,
                beanClass = ConfigHandler.class,
                beanMethod = "delete",
                operation =
                        @Operation(
                                summary = "删除参数配置",
                                description = "根据 ID 删除参数配置",
                                tags = {"参数配置"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "配置 ID",
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
                path = "/admin-api/v1/configs/{id}/status",
                method = RequestMethod.PUT,
                beanClass = ConfigHandler.class,
                beanMethod = "updateStatus",
                operation =
                        @Operation(
                                summary = "修改参数配置启停状态",
                                description = "启用或禁用指定参数配置",
                                tags = {"参数配置"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "配置 ID",
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
    @Bean
    public RouterFunction<ServerResponse> adminConfigRoutes(ConfigHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/configs", routes -> routes.GET("", handler::list)
                        .GET("/{id}", handler::detail)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::create)
                        .PUT("/{id}", contentType(MediaType.APPLICATION_JSON), handler::update)
                        .DELETE("/{id}", handler::delete)
                        .PUT("/{id}/status", contentType(MediaType.APPLICATION_JSON), handler::updateStatus))
                .build();
    }
}
