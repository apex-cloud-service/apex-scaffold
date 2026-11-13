package com.apex.scaffold.fileservice.interfaces.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.file.application.dto.share.FileShareCreated;
import com.apex.scaffold.file.application.dto.share.FileShareExchangeResult;
import com.apex.scaffold.file.application.service.FileShareService;
import com.apex.scaffold.fileservice.interfaces.dto.FileShareCreateRequestDoc;
import com.apex.scaffold.fileservice.interfaces.dto.FileShareTokenRequestDoc;
import com.apex.scaffold.fileservice.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 文件分享处理器（函数式端点）。
 *
 * <p>创建与撤销要求已认证身份，匿名兑换与下载只暴露精确路径。兑换完全依赖服务端分享记录
 * 确定租户与文件，不读取匿名请求携带的任何身份头；所有端点都不记录令牌与正文。</p>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 * @see FileShareService
 */
@Slf4j
@Component
@Tag(name = "文件分享", description = "限时只读文件分享的创建、撤销与匿名兑换")
public class FileShareHandler {

    private static final int STREAM_BUFFER_SIZE = 8192;

    private final FileShareService fileShareService;
    private final RequestHandlerUtil requestHandlerUtil;

    /**
     * 构造分享处理器。
     *
     * @param fileShareService 分享应用服务
     * @param requestHandlerUtil 请求处理工具
     */
    public FileShareHandler(FileShareService fileShareService, RequestHandlerUtil requestHandlerUtil) {
        this.fileShareService = fileShareService;
        this.requestHandlerUtil = requestHandlerUtil;
    }

    /**
     * 为指定文件创建限时只读分享。
     *
     * @param request 含 fileId 路径变量与可选有效期的请求
     * @return 含明文令牌与失效时间的响应
     */
    @Operation(operationId = "createFileShare", summary = "创建文件分享", description = "仅文件所有者可创建限时只读分享，令牌只在本次响应返回一次")
    @Parameter(
            name = "fileId",
            description = "文件编号",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "分享创建结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileShareCreatedResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误、无权分享或文件不可分享",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileShareErrorResponse.class)))
    })
    @OperationLog(
            module = "FileShare",
            type = OperationLog.OperationType.INSERT,
            description = "createFileShare",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> create(ServerRequest request) {
        return Mono.fromCallable(() -> Long.parseLong(request.pathVariable("fileId")))
                .flatMap(fileId -> requestHandlerUtil
                        .handleRequestBody(request, FileShareCreateRequestDoc.class)
                        .defaultIfEmpty(new FileShareCreateRequestDoc())
                        .flatMap(body -> fileShareService.create(fileId, body.getExpireSeconds())))
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 分页查询指定文件的分享列表（仅所有者，不返回令牌）。
     *
     * @param request 含 fileId 路径变量与 page/size/status 查询参数的请求
     * @return 分享列表分页结果
     */
    @Operation(operationId = "listFileShares", summary = "查询文件分享列表", description = "仅文件所有者可查询；响应只含分享元数据，不返回令牌或摘要")
    @Parameter(
            name = "fileId",
            description = "文件编号",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "分享列表",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileSharePageResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误或无权查询",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileShareErrorResponse.class)))
    })
    @OperationLog(
            module = "FileShare",
            type = OperationLog.OperationType.SELECT,
            description = "listFileShares",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> list(ServerRequest request) {
        return Mono.defer(() -> {
                    final long fileId;
                    try {
                        fileId = Long.parseLong(request.pathVariable("fileId"));
                    } catch (NumberFormatException error) {
                        return requestHandlerUtil.buildErrorResponse(
                                com.apex.scaffold.common.result.ResultCode.PARAM_ERROR, "fileId 必须为有效数字");
                    }
                    int page = parseInt(request.queryParam("page").orElse("1"), 1);
                    int size = parseInt(request.queryParam("size").orElse("20"), 20);
                    String status = request.queryParam("status").orElse(null);
                    return fileShareService
                            .list(fileId, status, page, size)
                            .flatMap(requestHandlerUtil::buildSuccessResponse);
                })
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /** 解析整数查询参数，非法时回退默认值。 */
    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    /**
     * 撤销分享。
     *
     * @param request 含 shareId 路径变量的请求
     * @return 撤销结果
     */
    @Operation(operationId = "revokeFileShare", summary = "撤销文件分享", description = "仅同一租户的创建者可撤销，重复撤销视为成功")
    @Parameter(
            name = "shareId",
            description = "分享记录编号",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "撤销结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileShareBooleanResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "分享不存在或无权撤销",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileShareErrorResponse.class)))
    })
    @OperationLog(
            module = "FileShare",
            type = OperationLog.OperationType.DELETE,
            description = "revokeFileShare",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> revoke(ServerRequest request) {
        return Mono.fromCallable(() -> Long.parseLong(request.pathVariable("shareId")))
                .flatMap(fileShareService::revoke)
                .then(Mono.defer(() -> requestHandlerUtil.buildSuccessResponse(Boolean.TRUE)))
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 匿名兑换分享，返回文件元数据。
     *
     * @param request 含令牌的请求
     * @return 兑换结果
     */
    @Operation(operationId = "exchangeFileShare", summary = "兑换文件分享", description = "匿名兑换，租户与文件完全由服务端分享记录确定")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "兑换结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileShareExchangeResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "令牌无效、分享过期或已撤销、文件不可访问",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileShareErrorResponse.class)))
    })
    @OperationLog(
            module = "FileShare",
            type = OperationLog.OperationType.SELECT,
            description = "exchangeFileShare",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> exchange(ServerRequest request) {
        return requestHandlerUtil
                .handleRequestBody(request, FileShareTokenRequestDoc.class)
                .flatMap(body -> fileShareService.exchange(body.getToken()))
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 匿名兑换并下载分享文件内容，由服务端代理读取。
     *
     * @param request 含令牌的请求
     * @return 文件流响应
     */
    @Operation(operationId = "downloadFileShare", summary = "下载分享文件", description = "匿名兑换并代理下载，不签发可直接访问的存储地址")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "文件内容流",
                content = @Content(mediaType = "*/*", schema = @Schema(type = "string", format = "binary"))),
        @ApiResponse(
                responseCode = "400",
                description = "令牌无效、分享过期或已撤销、文件不可访问",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileShareErrorResponse.class)))
    })
    @OperationLog(
            module = "FileShare",
            type = OperationLog.OperationType.DOWNLOAD,
            description = "downloadFileShare",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> download(ServerRequest request) {
        return requestHandlerUtil
                .handleRequestBody(request, FileShareTokenRequestDoc.class)
                .flatMap(body -> fileShareService.download(body.getToken()))
                .flatMap(content -> {
                    Flux<DataBuffer> stream = DataBufferUtils.readInputStream(
                                    () -> content.content(),
                                    DefaultDataBufferFactory.sharedInstance,
                                    STREAM_BUFFER_SIZE)
                            .subscribeOn(BlockingIoSchedulers.storage())
                            .doFinally(signal -> closeQuietly(content.content()));
                    ServerResponse.BodyBuilder builder = ServerResponse.ok()
                            .contentType(resolveMediaType(content.contentType()))
                            .header(
                                    HttpHeaders.CONTENT_DISPOSITION,
                                    ContentDisposition.attachment()
                                            .filename(
                                                    content.fileName() == null ? "download" : content.fileName(),
                                                    StandardCharsets.UTF_8)
                                            .build()
                                            .toString());
                    if (content.fileSize() != null) {
                        builder = builder.contentLength(content.fileSize());
                    }
                    return builder.body(stream, DataBuffer.class);
                })
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /** 关闭文件流并吞掉关闭异常，避免影响响应信号。 */
    private void closeQuietly(java.io.InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException exception) {
            log.warn("关闭分享文件读取流失败", exception);
        }
    }

    /** 解析响应媒体类型，非法或缺省时回退为二进制流。 */
    private MediaType resolveMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /** 分享创建结果的文档模型。 */
    @Schema(name = "FileShareCreatedResponse")
    public static class FileShareCreatedResponse extends Result<FileShareCreated> {}

    /** 分享列表分页结果的文档模型。 */
    @Schema(name = "FileSharePageResponse")
    public static class FileSharePageResponse
            extends Result<
                    com.apex.scaffold.file.application.dto.PageResponse<
                            com.apex.scaffold.file.application.dto.share.FileShareItem>> {}

    /** 分享兑换结果的文档模型。 */
    @Schema(name = "FileShareExchangeResponse")
    public static class FileShareExchangeResponse extends Result<FileShareExchangeResult> {}

    /** 分享布尔结果的文档模型。 */
    @Schema(name = "FileShareBooleanResponse")
    public static class FileShareBooleanResponse extends Result<Boolean> {}

    /** 分享错误统一响应的文档模型。 */
    @Schema(name = "FileShareErrorResponse")
    public static class FileShareErrorResponse extends Result<Void> {}
}
