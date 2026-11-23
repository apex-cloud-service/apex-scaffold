package com.apex.scaffold.fileservice.interfaces.handler;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.file.application.dto.FileUploadResult;
import com.apex.scaffold.file.application.service.FileService;
import com.apex.scaffold.fileservice.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 开放文件接口：只暴露上传与按对象键读取，主体账号由认证过滤器注入的
 * {@code FileAccessContext} 决定，属主校验继续复用 {@link FileService} 内部不变量。
 *
 * <p>管理端的删除、孤儿清理、分享等操作不在此处开放，避免第三方应用获得超出 scope
 * 语义的管理能力。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Component
@Tag(name = "开放平台-文件", description = "开放应用级文件上传与读取；只走网关 scope 断言与能力门禁，不叠加管理端权限")
public class OpenFileHandler {

    /** 开放上传的统一响应结构，仅用于接口文档。 */
    @Schema(name = "OpenFileUploadResult")
    public static class OpenFileUploadResult extends Result<FileUploadResult> {}

    private final FileService files;

    private final RequestHandlerUtil responses;

    private final String defaultBucket;

    /**
     * 构造处理器。
     *
     * @param files 文件应用服务
     * @param responses 统一响应工具
     * @param defaultBucket 未指定桶时的默认桶名
     */
    public OpenFileHandler(
            FileService files,
            RequestHandlerUtil responses,
            @org.springframework.beans.factory.annotation.Value("${storage.bucket-name:apex}") String defaultBucket) {
        this.files = files;
        this.responses = responses;
        this.defaultBucket = defaultBucket;
    }

    /**
     * 开放上传：普通文件（非图片）落默认桶，owner 取可信主体。
     *
     * @param request multipart 请求
     * @return 上传结果
     */
    @Operation(
            operationId = "openFileUpload",
            summary = "开放平台上传文件",
            description = "以 multipart/form-data 上传单个文件（字段名 file），落默认桶且 owner 取可信主体账号；"
                    + "仅通过网关 scope 断言与能力门禁鉴权，不叠加管理端用户权限切面")
    @RequestBody(
            required = true,
            description = "multipart 表单；file 字段必填且须为文件类型",
            content =
                    @Content(
                            mediaType = "multipart/form-data",
                            schema = @Schema(type = "object", description = "包含 file 文件字段的 multipart 表单")))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "上传成功，data 为对象定位与访问地址",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OpenFileUploadResult.class))),
        @ApiResponse(responseCode = "400", description = "缺少 file 字段或上传文件为空"),
        @ApiResponse(responseCode = "401", description = "未通过网关 scope 断言或能力门禁"),
        @ApiResponse(responseCode = "403", description = "开放应用无文件上传能力")
    })
    public Mono<ServerResponse> upload(ServerRequest request) {
        return request.multipartData().flatMap(parts -> {
            if (!(parts.getFirst("file") instanceof FilePart file)) {
                return responses.buildErrorResponse(ResultCode.PARAM_ERROR, "上传文件不能为空");
            }
            return DataBufferUtils.join(file.content())
                    .flatMap(buffer -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        try {
                            buffer.read(bytes);
                            return files.upload(
                                    file.filename(), bytes, MediaType.APPLICATION_OCTET_STREAM_VALUE, null, null);
                        } finally {
                            DataBufferUtils.release(buffer);
                        }
                    })
                    .flatMap(result -> responses.buildSuccessResponse(result));
        });
    }

    /**
     * 开放读取：按 {@code bucketName}/{@code fileName} 读取，owner 校验在服务层完成。
     *
     * @param request 查询参数 bucketName、fileName
     * @return 文件流
     */
    @Operation(
            operationId = "openFileContent",
            summary = "开放平台按对象键读取文件",
            description = "按 bucketName/fileName 读取对象内容，属主校验在服务层完成；" + "响应为二进制流而非 Result 包装。仅通过网关 scope 断言与能力门禁鉴权")
    @Parameter(
            name = "fileName",
            in = ParameterIn.QUERY,
            required = true,
            description = "对象名称（上传返回的 fileName）",
            schema = @Schema(type = "string", example = "0f12ab34cd56ef78.bin"))
    @Parameter(
            name = "bucketName",
            in = ParameterIn.QUERY,
            description = "存储桶名称；缺省取服务端配置的 storage.bucket-name",
            schema = @Schema(type = "string", example = "apex"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "文件二进制内容",
                content =
                        @Content(
                                mediaType = "application/octet-stream",
                                schema = @Schema(type = "string", format = "binary"))),
        @ApiResponse(responseCode = "400", description = "文件名称缺失"),
        @ApiResponse(responseCode = "401", description = "未通过网关 scope 断言或能力门禁"),
        @ApiResponse(responseCode = "403", description = "开放应用无读取能力或不属于该对象属主"),
        @ApiResponse(responseCode = "404", description = "对象不存在")
    })
    public Mono<ServerResponse> content(ServerRequest request) {
        String fileName = request.queryParam("fileName").orElse(null);
        if (fileName == null || fileName.isBlank()) {
            return responses.buildErrorResponse(ResultCode.PARAM_ERROR, "文件名称不能为空");
        }
        String bucket = request.queryParam("bucketName").orElse(defaultBucket);
        return files.download(bucket, fileName).flatMap(stream -> {
            Flux<DataBuffer> body = DataBufferUtils.readInputStream(
                            () -> stream, DefaultDataBufferFactory.sharedInstance, 8192)
                    .subscribeOn(BlockingIoSchedulers.storage())
                    .doFinally(signal -> closeQuietly(stream));
            return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(body, DataBuffer.class);
        });
    }

    private void closeQuietly(java.io.InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // 关闭失败不影响已发出的响应；连接资源由存储适配器负责最终释放。
        }
    }
}
