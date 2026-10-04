package com.apex.scaffold.interfaces.router.office;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.interfaces.handler.office.OfficeDocumentHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
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
 * 办公平台文档操作路由配置
 *
 * <p>配置文档操作相关的HTTP路由，使用函数式路由定义所有端点。
 * 每个路由都配置了完整的SpringDoc注解，用于生成在线API文档。
 *
 * <p>路由列表：
 * <ul>
 *   <li>POST /api/office/documents - 创建文档</li>
 *   <li>POST /api/office/documents/upload - 上传文件</li>
 *   <li>GET /api/office/documents/{documentId} - 查询文档详情</li>
 *   <li>GET /api/office/documents/folder - 查询文件夹下的文档</li>
 *   <li>GET /api/office/documents/my - 查询我的文档</li>
 *   <li>GET /api/office/documents/{documentId}/download - 下载文档</li>
 *   <li>DELETE /api/office/documents/{documentId} - 删除文档</li>
 *   <li>PUT /api/office/documents/{documentId}/move - 移动文档</li>
 *   <li>POST /api/office/documents/{documentId}/copy - 复制文档</li>
 * </ul>
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Configuration
@RequiredArgsConstructor
public class OfficeDocumentRouter {

    private static final String BASE_PATH = "/api/office/documents";

    private final OfficeDocumentHandler handler;

    /**
     * 配置文档操作路由
     *
     * @return RouterFunction 路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = BASE_PATH,
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "createDocument",
                                summary = "创建文档",
                                description = "在指定办公平台创建新文档，支持飞书、钉钉、企业微信",
                                tags = {"办公平台文档"},
                                requestBody =
                                        @RequestBody(
                                                description = "文档创建请求",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.interfaces.dto
                                                                                                .office.document
                                                                                                .CreateDocumentRequest
                                                                                                .class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "创建成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.interfaces.dto
                                                                                            .office.document
                                                                                            .DocumentInfoResponse
                                                                                            .class))),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/upload",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "uploadFile",
                                summary = "上传文件",
                                description = "上传文件到指定办公平台",
                                tags = {"办公平台文档"},
                                requestBody =
                                        @RequestBody(
                                                description = "文件上传请求",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.interfaces.dto
                                                                                                .office.document
                                                                                                .UploadFileRequest
                                                                                                .class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "上传成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.interfaces.dto
                                                                                            .office.document
                                                                                            .DocumentInfoResponse
                                                                                            .class))),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/{documentId}",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getDocumentDetail",
                                summary = "查询文档详情",
                                description = "根据文档ID查询文档的详细信息",
                                tags = {"办公平台文档"},
                                parameters = {
                                    @Parameter(
                                            name = "documentId",
                                            description = "文档ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.interfaces.dto
                                                                                            .office.document
                                                                                            .DocumentInfoResponse
                                                                                            .class))),
                                    @ApiResponse(responseCode = "404", description = "文档不存在"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/folder",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getDocumentsInFolder",
                                summary = "查询文件夹下的文档",
                                description = "列出指定文件夹下的所有文档",
                                tags = {"办公平台文档"},
                                parameters = {
                                    @Parameter(
                                            name = "platform",
                                            description = "办公平台类型",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string")),
                                    @Parameter(
                                            name = "folderId",
                                            description = "文件夹ID",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/my",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getMyDocuments",
                                summary = "查询我的文档",
                                description = "查询当前用户的所有文档",
                                tags = {"办公平台文档"},
                                parameters = {
                                    @Parameter(
                                            name = "ownerId",
                                            description = "所有者ID",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/{documentId}/download",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "downloadDocument",
                                summary = "下载文档",
                                description = "下载指定的文档",
                                tags = {"办公平台文档"},
                                parameters = {
                                    @Parameter(
                                            name = "documentId",
                                            description = "文档ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "下载成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(responseCode = "404", description = "文档不存在"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/{documentId}",
                method = RequestMethod.DELETE,
                operation =
                        @Operation(
                                operationId = "deleteDocument",
                                summary = "删除文档",
                                description = "删除指定的文档",
                                tags = {"办公平台文档"},
                                parameters = {
                                    @Parameter(
                                            name = "documentId",
                                            description = "文档ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "删除成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(responseCode = "404", description = "文档不存在"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/{documentId}/move",
                method = RequestMethod.PUT,
                operation =
                        @Operation(
                                operationId = "moveDocument",
                                summary = "移动文档",
                                description = "移动文档到指定的文件夹",
                                tags = {"办公平台文档"},
                                parameters = {
                                    @Parameter(
                                            name = "documentId",
                                            description = "文档ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64")),
                                    @Parameter(
                                            name = "targetFolderId",
                                            description = "目标文件夹ID",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "移动成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.interfaces.dto
                                                                                            .office.document
                                                                                            .DocumentInfoResponse
                                                                                            .class))),
                                    @ApiResponse(responseCode = "404", description = "文档不存在"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = BASE_PATH + "/{documentId}/copy",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "copyDocument",
                                summary = "复制文档",
                                description = "复制指定的文档",
                                tags = {"办公平台文档"},
                                parameters = {
                                    @Parameter(
                                            name = "documentId",
                                            description = "文档ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64")),
                                    @Parameter(
                                            name = "targetFolderId",
                                            description = "目标文件夹ID",
                                            required = false,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "复制成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.interfaces.dto
                                                                                            .office.document
                                                                                            .DocumentInfoResponse
                                                                                            .class))),
                                    @ApiResponse(responseCode = "404", description = "文档不存在"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> officeDocumentRoutes() {
        return RouterFunctions.route(POST(BASE_PATH).and(accept(MediaType.APPLICATION_JSON)), handler::createDocument)
                .andRoute(POST(BASE_PATH + "/upload").and(accept(MediaType.APPLICATION_JSON)), handler::uploadFile)
                .andRoute(GET(BASE_PATH + "/{documentId}"), handler::getDocumentDetail)
                .andRoute(GET(BASE_PATH + "/folder"), handler::getDocumentsInFolder)
                .andRoute(GET(BASE_PATH + "/my"), handler::getMyDocuments)
                .andRoute(GET(BASE_PATH + "/{documentId}/download"), handler::downloadDocument)
                .andRoute(DELETE(BASE_PATH + "/{documentId}"), handler::deleteDocument)
                .andRoute(PUT(BASE_PATH + "/{documentId}/move"), handler::moveDocument)
                .andRoute(POST(BASE_PATH + "/{documentId}/copy"), handler::copyDocument);
    }
}
