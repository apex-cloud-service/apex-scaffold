package com.apex.scaffold.fileservice.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.file.application.dto.FileUploadResult;
import com.apex.scaffold.fileservice.interfaces.dto.FileUploadRequestDoc;
import com.apex.scaffold.fileservice.interfaces.dto.GenericFileUploadRequestDoc;
import com.apex.scaffold.fileservice.interfaces.handler.FileHandler;
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
 * 文件管理路由配置类。
 *
 * <p>基于 Spring WebFlux 函数式编程模型，注册文件资源的 RESTful 路由规则。
 * 保留单应用时代文件接口的对外契约：前缀 {@code /api/v1/file} 与传统上传入口兼容，
 * 普通图片上传入口为 {@code /api/v1/files/generic}。</p>
 *
 * <p>路由规则：
 * <ul>
 *   <li>POST /api/v1/file/upload - 上传图片（已过时的兼容入口）</li>
 *   <li>GET /api/v1/file/content - 读取本地存储文件内容</li>
 *   <li>GET /api/v1/file/preview - 预览本地存储文件内容（inline）</li>
 *   <li>GET /api/v1/file/url - 获取文件预签名 URL</li>
 *   <li>DELETE /api/v1/file/delete - 删除文件</li>
 *   <li>GET /api/v1/file/exists - 检查文件是否存在</li>
 *   <li>POST /api/v1/files/generic - 上传普通业务图片</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.0.0
 * @see FileHandler
 */
@Configuration
public class FileRouter {

    /**
     * 配置文件管理路由函数。
     *
     * <p>统一注册文件的上传、下载、URL 获取、删除与存在性路由，业务处理委托给
     * {@link FileHandler}。</p>
     *
     * @param handler 文件业务处理器
     * @return 文件路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/file/upload",
                method = RequestMethod.POST,
                beanClass = FileHandler.class,
                beanMethod = "upload",
                operation =
                        @Operation(
                                operationId = "fileUpload",
                                summary = "上传图片（兼容入口）",
                                description = "已过时的兼容上传入口，普通图片请使用 /api/v1/files/generic",
                                tags = {"文件管理"},
                                requestBody =
                                        @RequestBody(
                                                description = "图片上传表单",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        FileUploadRequestDoc.class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "上传成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = FileUploadResult.class)))
                                })),
        @RouterOperation(
                path = "/api/v1/file/content",
                method = RequestMethod.GET,
                beanClass = FileHandler.class,
                beanMethod = "content",
                operation =
                        @Operation(
                                operationId = "getLocalFileContent",
                                summary = "读取本地存储文件",
                                description = "仅用于 local 存储模式，按服务端固定桶流式读取文件内容",
                                tags = {"文件管理"},
                                parameters = {
                                    @Parameter(
                                            name = "fileName",
                                            description = "上传后返回的对象名称",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "读取成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/file/preview",
                method = RequestMethod.GET,
                beanClass = FileHandler.class,
                beanMethod = "preview",
                operation =
                        @Operation(
                                operationId = "previewFile",
                                summary = "预览文件",
                                description = "仅用于 local 存储模式，按服务端固定桶流式读取文件内容并以 inline 方式返回，供浏览器直接预览",
                                tags = {"文件管理"},
                                parameters = {
                                    @Parameter(
                                            name = "fileName",
                                            description = "上传后返回的对象名称",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string")),
                                    @Parameter(
                                            name = "contentType",
                                            description = "可选，显式指定媒体类型；缺省时按文件扩展名推断",
                                            required = false,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "预览成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/file/url",
                method = RequestMethod.GET,
                beanClass = FileHandler.class,
                beanMethod = "getUrl",
                operation =
                        @Operation(
                                operationId = "getFileUrl",
                                summary = "获取文件URL",
                                description = "根据存储桶名称和文件名获取文件的预签名访问地址",
                                tags = {"文件管理"},
                                parameters = {
                                    @Parameter(
                                            name = "bucketName",
                                            description = "存储桶名称",
                                            required = true,
                                            in = ParameterIn.QUERY),
                                    @Parameter(
                                            name = "fileName",
                                            description = "文件名称",
                                            required = true,
                                            in = ParameterIn.QUERY)
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "获取成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/file/delete",
                method = RequestMethod.DELETE,
                beanClass = FileHandler.class,
                beanMethod = "delete",
                operation =
                        @Operation(
                                operationId = "deleteFile",
                                summary = "删除文件",
                                description = "从对象存储系统中删除指定的文件",
                                tags = {"文件管理"},
                                parameters = {
                                    @Parameter(
                                            name = "bucketName",
                                            description = "存储桶名称",
                                            required = true,
                                            in = ParameterIn.QUERY),
                                    @Parameter(
                                            name = "fileName",
                                            description = "文件名称",
                                            required = true,
                                            in = ParameterIn.QUERY)
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "删除成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/file/exists",
                method = RequestMethod.GET,
                beanClass = FileHandler.class,
                beanMethod = "exists",
                operation =
                        @Operation(
                                operationId = "fileExists",
                                summary = "检查文件是否存在",
                                description = "查询指定文件是否已存在于对象存储系统中",
                                tags = {"文件管理"},
                                parameters = {
                                    @Parameter(
                                            name = "bucketName",
                                            description = "存储桶名称",
                                            required = true,
                                            in = ParameterIn.QUERY),
                                    @Parameter(
                                            name = "fileName",
                                            description = "文件名称",
                                            required = true,
                                            in = ParameterIn.QUERY)
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/files/generic",
                method = RequestMethod.POST,
                beanClass = FileHandler.class,
                beanMethod = "uploadGenericFile",
                operation =
                        @Operation(
                                operationId = "uploadGenericFile",
                                summary = "上传普通图片",
                                description = "普通图片上传的独立接口，sex 必填：0=男（AM 目录），1=女（AF 目录）",
                                tags = {"文件管理"},
                                requestBody =
                                        @RequestBody(
                                                description = "普通图片上传表单",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        GenericFileUploadRequestDoc
                                                                                                .class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "上传成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = FileUploadResult.class)))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> fileRoutes(FileHandler handler) {
        return RouterFunctions.route()
                .path("/api/v1/file", builder -> builder.POST("/upload", handler::upload)
                        .GET("/content", handler::content)
                        .GET("/preview", handler::preview)
                        .GET("/url", handler::getUrl)
                        .DELETE("/delete", handler::delete)
                        .GET("/exists", handler::exists))
                .path(
                        "/api/v1/files",
                        builder -> builder.POST(
                                "/generic", contentType(MediaType.MULTIPART_FORM_DATA), handler::uploadGenericFile))
                .build();
    }
}
