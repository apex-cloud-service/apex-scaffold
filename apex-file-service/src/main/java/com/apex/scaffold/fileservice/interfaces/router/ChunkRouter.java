package com.apex.scaffold.fileservice.interfaces.router;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.file.application.dto.chunk.ChunkInitRequest;
import com.apex.scaffold.file.application.dto.chunk.ChunkMergeRequest;
import com.apex.scaffold.fileservice.interfaces.handler.ChunkHandler;
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
 * 分片上传路由配置类。
 *
 * <p>基于 Spring WebFlux 函数式编程模型，注册分片上传的 RESTful 路由规则。保留单应用时代
 * 的对外契约前缀 {@code /api/v1/file/chunk}，覆盖初始化、逐片上传、合并与进度查询四类端点。</p>
 *
 * <p>路由规则：
 * <ul>
 *   <li>POST /api/v1/file/chunk/init - 初始化分片上传</li>
 *   <li>POST /api/v1/file/chunk/upload - 上传单个分片（二进制请求体）</li>
 *   <li>POST /api/v1/file/chunk/merge - 合并分片</li>
 *   <li>GET /api/v1/file/chunk/status/{fileId} - 查询分片上传进度</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ChunkHandler
 */
@Configuration
public class ChunkRouter {

    /**
     * 配置分片上传路由函数。
     *
     * <p>注册分片上传的初始化、上传、合并与进度查询端点，业务处理委托给 {@link ChunkHandler}。
     * 分片上传端点以 {@code application/octet-stream} 接收二进制分片内容。</p>
     *
     * @param handler 分片上传业务处理器
     * @return 分片上传路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/file/chunk/init",
                method = RequestMethod.POST,
                beanClass = ChunkHandler.class,
                beanMethod = "init",
                operation =
                        @Operation(
                                operationId = "initChunkUpload",
                                summary = "初始化分片上传",
                                description = "登记文件元数据并创建上传中记录，返回 fileId 与建议分片大小",
                                tags = {"分片上传"},
                                requestBody =
                                        @RequestBody(
                                                description = "分片上传初始化信息",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        ChunkInitRequest.class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "初始化成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/file/chunk/upload",
                method = RequestMethod.POST,
                beanClass = ChunkHandler.class,
                beanMethod = "uploadChunk",
                operation =
                        @Operation(
                                operationId = "uploadChunk",
                                summary = "上传分片",
                                description = "上传单个分片，二进制内容走请求体，元数据经查询参数传递；重复上传幂等",
                                tags = {"分片上传"},
                                parameters = {
                                    @Parameter(
                                            name = "fileId",
                                            description = "文件记录主键",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "integer", format = "int64")),
                                    @Parameter(
                                            name = "chunkIndex",
                                            description = "分片序号（从 0 开始）",
                                            required = true,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "integer", minimum = "0")),
                                    @Parameter(
                                            name = "totalChunks",
                                            description = "分片总数",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "integer")),
                                    @Parameter(
                                            name = "md5",
                                            description = "分片 MD5",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string"))
                                },
                                requestBody =
                                        @RequestBody(
                                                description = "分片二进制内容",
                                                required = true,
                                                content =
                                                        @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "上传成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/file/chunk/merge",
                method = RequestMethod.POST,
                beanClass = ChunkHandler.class,
                beanMethod = "merge",
                operation =
                        @Operation(
                                operationId = "mergeChunks",
                                summary = "合并分片",
                                description = "按序拼接全部分片为完整文件并生成访问 URL",
                                tags = {"分片上传"},
                                requestBody =
                                        @RequestBody(
                                                description = "分片合并信息",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        ChunkMergeRequest.class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "合并成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/file/chunk/status/{fileId}",
                method = RequestMethod.GET,
                beanClass = ChunkHandler.class,
                beanMethod = "status",
                operation =
                        @Operation(
                                operationId = "getChunkStatus",
                                summary = "查询分片上传进度",
                                description = "返回已上传分片序号列表，用于断点续传",
                                tags = {"分片上传"},
                                parameters = {
                                    @Parameter(
                                            name = "fileId",
                                            description = "文件记录主键",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> chunkRoutes(ChunkHandler handler) {
        return RouterFunctions.route()
                .path("/api/v1/file/chunk", builder -> builder.POST(
                                "/init", contentType(APPLICATION_JSON).and(accept(APPLICATION_JSON)), handler::init)
                        .POST(
                                "/upload",
                                contentType(MediaType.APPLICATION_OCTET_STREAM).and(accept(APPLICATION_JSON)),
                                handler::uploadChunk)
                        .POST("/merge", contentType(APPLICATION_JSON).and(accept(APPLICATION_JSON)), handler::merge)
                        .GET("/status/{fileId}", accept(APPLICATION_JSON), handler::status))
                .build();
    }
}
