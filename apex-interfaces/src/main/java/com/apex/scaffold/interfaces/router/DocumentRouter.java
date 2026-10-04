package com.apex.scaffold.interfaces.router;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

import com.apex.scaffold.application.dto.data.DocumentRequest;
import com.apex.scaffold.interfaces.handler.DocumentHandler;
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
 * 文档管理路由配置类。
 *
 * <p>基于 Spring WebFlux 函数式编程模型，注册文档资源的 RESTful 路由规则。
 * 路由前缀为 {@code /admin-api/v1/documents}，支持文档的 CRUD 操作、状态变更和文件下载。
 *
 * <p>路由规则：
 * <ul>
 *   <li>GET /admin-api/v1/documents - 查询文档列表（支持分页、筛选）</li>
 *   <li>POST /admin-api/v1/documents - 创建新文档</li>
 *   <li>GET /admin-api/v1/documents/{id} - 获取文档详情</li>
 *   <li>PUT /admin-api/v1/documents/{id} - 更新文档信息</li>
 *   <li>DELETE /admin-api/v1/documents/{id} - 删除文档</li>
 *   <li>PUT /admin-api/v1/documents/{id}/status - 更新文档状态（发布、归档等）</li>
 *   <li>GET /admin-api/v1/documents/{id}/download - 下载文档文件</li>
 * </ul>
 *
 * <p>所有路由要求请求头 {@code Accept: application/json}，实际业务处理委托给 {@link DocumentHandler}。
 *
 * @author Apex Team
 * @since 1.2.0
 * @see DocumentHandler
 * @see org.springframework.web.reactive.function.server.RouterFunction
 */
@Configuration
public class DocumentRouter {

    /**
     * 配置文档管理路由函数。
     *
     * <p>注册文档资源的所有 HTTP 端点，统一路由前缀为 {@code /admin-api/v1/documents}。
     * 各路由的业务逻辑由注入的 {@link DocumentHandler} 实现。
     *
     * @param handler 文档业务处理器
     * @return 文档路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/documents",
                method = RequestMethod.GET,
                beanClass = DocumentHandler.class,
                beanMethod = "query",
                operation =
                        @Operation(
                                operationId = "queryDocuments",
                                summary = "分页查询文档列表",
                                description = "按条件分页查询文档资源，支持关键字筛选与状态过滤",
                                tags = {"文档管理"},
                                parameters = {
                                    @Parameter(name = "keyword", description = "文档标题关键字", in = ParameterIn.QUERY),
                                    @Parameter(name = "docType", description = "文档类型", in = ParameterIn.QUERY),
                                    @Parameter(name = "category", description = "文档分类", in = ParameterIn.QUERY),
                                    @Parameter(
                                            name = "status",
                                            description = "文档状态",
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
                path = "/admin-api/v1/documents",
                method = RequestMethod.POST,
                beanClass = DocumentHandler.class,
                beanMethod = "create",
                operation =
                        @Operation(
                                operationId = "createDocument",
                                summary = "创建文档",
                                requestBody =
                                        @RequestBody(
                                                description = "文档创建信息",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        DocumentRequest.class))),
                                description = "根据请求参数创建新文档并返回文档标识",
                                tags = {"文档管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "创建成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/documents/{id}",
                method = RequestMethod.GET,
                beanClass = DocumentHandler.class,
                beanMethod = "getById",
                operation =
                        @Operation(
                                operationId = "getDocumentById",
                                summary = "查询文档详情",
                                description = "根据文档 ID 查询文档元数据与内容概要",
                                tags = {"文档管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "文档 ID",
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
                path = "/admin-api/v1/documents/{id}",
                method = RequestMethod.PUT,
                beanClass = DocumentHandler.class,
                beanMethod = "update",
                operation =
                        @Operation(
                                operationId = "updateDocument",
                                summary = "更新文档信息",
                                requestBody =
                                        @RequestBody(
                                                description = "文档更新信息",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        DocumentRequest.class))),
                                description = "根据文档 ID 更新文档基础信息",
                                tags = {"文档管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "文档 ID",
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
                path = "/admin-api/v1/documents/{id}",
                method = RequestMethod.DELETE,
                beanClass = DocumentHandler.class,
                beanMethod = "delete",
                operation =
                        @Operation(
                                operationId = "deleteDocument",
                                summary = "删除文档",
                                description = "根据文档 ID 删除文档",
                                tags = {"文档管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "文档 ID",
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
                path = "/admin-api/v1/documents/{id}/status",
                method = RequestMethod.PUT,
                beanClass = DocumentHandler.class,
                beanMethod = "updateStatus",
                operation =
                        @Operation(
                                operationId = "updateDocumentStatus",
                                summary = "变更文档状态",
                                requestBody =
                                        @RequestBody(
                                                description = "文档状态更新信息",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        DocumentHandler
                                                                                                .UpdateStatusRequest
                                                                                                .class))),
                                description = "根据文档 ID 变更文档状态（如发布、归档）",
                                tags = {"文档管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "文档 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "变更成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/documents/{id}/download",
                method = RequestMethod.GET,
                beanClass = DocumentHandler.class,
                beanMethod = "download",
                operation =
                        @Operation(
                                operationId = "getDocumentDownloadUrl",
                                summary = "下载文档文件",
                                description = "根据文档 ID 获取附件下载地址，并累加下载次数",
                                tags = {"文档管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "文档 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "下载成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> documentRoutes(DocumentHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/documents", builder -> builder.GET("", accept(APPLICATION_JSON), handler::query)
                        .POST("", accept(APPLICATION_JSON), handler::create)
                        .GET("/{id}", accept(APPLICATION_JSON), handler::getById)
                        .PUT("/{id}", accept(APPLICATION_JSON), handler::update)
                        .DELETE("/{id}", accept(APPLICATION_JSON), handler::delete)
                        .PUT("/{id}/status", accept(APPLICATION_JSON), handler::updateStatus)
                        .GET("/{id}/download", accept(APPLICATION_JSON), handler::download))
                .build();
    }
}
