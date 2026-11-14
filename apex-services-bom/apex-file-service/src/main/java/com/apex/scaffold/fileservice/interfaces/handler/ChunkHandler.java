package com.apex.scaffold.fileservice.interfaces.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.file.application.dto.chunk.ChunkInitRequest;
import com.apex.scaffold.file.application.dto.chunk.ChunkInitResult;
import com.apex.scaffold.file.application.dto.chunk.ChunkMergeRequest;
import com.apex.scaffold.file.application.dto.chunk.ChunkMergeResult;
import com.apex.scaffold.file.application.dto.chunk.ChunkUploadRequest;
import com.apex.scaffold.file.application.dto.chunk.ChunkUploadResult;
import com.apex.scaffold.file.application.service.ChunkUploadService;
import com.apex.scaffold.fileservice.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 分片上传处理器（函数式端点）。
 *
 * <p>为超大文件提供分片上传、断点续传与合片能力的 RESTful API，覆盖初始化、逐片上传、
 * 合并、进度查询与清理重试。分片二进制内容通过请求体直接传递，元数据经查询参数或 JSON
 * 请求体提交。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>POST /api/v1/file/chunk/init - 初始化分片上传，登记文件元数据并返回 fileId</li>
 *   <li>POST /api/v1/file/chunk/upload - 上传单个分片（二进制请求体 + 元数据查询参数）</li>
 *   <li>POST /api/v1/file/chunk/merge - 合并全部分片为完整文件</li>
 *   <li>POST /api/v1/file/chunk/cleanup/{fileId}/retry - 重试已完成会话的分片清理</li>
 *   <li>GET /api/v1/file/chunk/status/{fileId} - 查询分片上传进度（断点续传）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ChunkUploadService
 * @see RequestHandlerUtil
 */
@Slf4j
@Component
@Tag(name = "分片上传", description = "超大文件分片上传、断点续传与合片接口")
public class ChunkHandler {

    private final ChunkUploadService chunkUploadService;

    private final RequestHandlerUtil requestHandlerUtil;

    /**
     * 构造分片上传处理器。
     *
     * @param chunkUploadService 分片上传应用服务
     * @param requestHandlerUtil 请求处理工具
     */
    public ChunkHandler(ChunkUploadService chunkUploadService, RequestHandlerUtil requestHandlerUtil) {
        this.chunkUploadService = chunkUploadService;
        this.requestHandlerUtil = requestHandlerUtil;
    }

    /**
     * 初始化分片上传。
     *
     * <p>解析 JSON 请求体为 {@link ChunkInitRequest}，校验通过后登记文件元数据，返回
     * 文件主键与建议分片大小供客户端开始逐片上传。</p>
     *
     * @param request 包含初始化元数据 JSON 的服务器请求
     * @return 包含 {@link ChunkInitResult} 的统一成功响应
     */
    @Operation(operationId = "initChunkUpload", summary = "初始化分片上传", description = "登记文件元数据并创建上传中记录，返回 fileId 与建议分片大小")
    @RequestBody(
            required = true,
            content =
                    @Content(mediaType = "application/json", schema = @Schema(implementation = ChunkInitRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "初始化信息",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ChunkSessionResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数或业务处理失败",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ChunkErrorResult.class)))
    })
    @OperationLog(
            module = "Chunk",
            type = OperationLog.OperationType.OTHER,
            description = "init",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> init(ServerRequest request) {
        return requestHandlerUtil
                .handleRequestBody(request, ChunkInitRequest.class)
                .flatMap(chunkUploadService::init)
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 上传单个分片。
     *
     * <p>分片二进制内容从请求体读取，文件 ID、分片序号等元数据从查询参数读取并组装为
     * {@link ChunkUploadRequest}。同一分片重复上传时服务端幂等处理。</p>
     *
     * @param request 携带分片二进制请求体与元数据查询参数的服务器请求
     * @return 包含 {@link ChunkUploadResult} 的统一成功响应
     */
    @Operation(operationId = "uploadChunk", summary = "上传分片", description = "上传单个分片，二进制内容走请求体，元数据经查询参数传递；重复上传幂等")
    @Parameters({
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
        @Parameter(name = "md5", description = "分片 MD5", in = ParameterIn.QUERY, schema = @Schema(type = "string"))
    })
    @RequestBody(
            required = true,
            content =
                    @Content(
                            mediaType = "application/octet-stream",
                            schema = @Schema(type = "string", format = "binary")))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "分片上传结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ChunkPartResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数或业务处理失败",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ChunkErrorResult.class)))
    })
    @OperationLog(
            module = "Chunk",
            type = OperationLog.OperationType.UPLOAD,
            description = "uploadChunk",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> uploadChunk(ServerRequest request) {
        return Mono.defer(() -> {
                    ChunkUploadRequest chunkRequest;
                    try {
                        chunkRequest = buildChunkUploadRequest(request);
                    } catch (NumberFormatException error) {
                        return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "分片参数必须为有效数字");
                    }
                    if (chunkRequest.getFileId() == null || chunkRequest.getChunkIndex() == null) {
                        return requestHandlerUtil.buildErrorResponse(
                                ResultCode.PARAM_ERROR, "fileId 与 chunkIndex 不能为空");
                    }
                    return request.bodyToMono(byte[].class)
                            .switchIfEmpty(Mono.error(new com.apex.scaffold.common.exception.BusinessException(
                                    ResultCode.PARAM_ERROR, "分片内容不能为空")))
                            .flatMap(content -> chunkUploadService.uploadChunk(chunkRequest, content))
                            .flatMap(requestHandlerUtil::buildSuccessResponse);
                })
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 从查询参数组装分片上传元数据请求。
     *
     * @param request 服务器请求对象
     * @return 组装后的分片上传请求
     */
    private ChunkUploadRequest buildChunkUploadRequest(ServerRequest request) {
        return ChunkUploadRequest.builder()
                .fileId(request.queryParam("fileId").map(Long::valueOf).orElse(null))
                .chunkIndex(
                        request.queryParam("chunkIndex").map(Integer::valueOf).orElse(null))
                .totalChunks(
                        request.queryParam("totalChunks").map(Integer::valueOf).orElse(null))
                .md5(request.queryParam("md5").orElse(null))
                .build();
    }

    /**
     * 合并分片为完整文件。
     *
     * <p>解析 JSON 请求体为 {@link ChunkMergeRequest}，校验分片完整性后按序拼接并上传为完整
     * 文件，清理已用分片后返回对象定位信息与访问 URL。云签名 TTL 由配置决定；Local 内容地址每次访问仍须认证授权。</p>
     *
     * @param request 包含合并元数据 JSON 的服务器请求
     * @return 包含 {@link ChunkMergeResult} 的统一成功响应
     */
    @Operation(operationId = "mergeChunks", summary = "合并分片", description = "按序拼接全部分片为完整文件并生成访问 URL")
    @RequestBody(
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ChunkMergeRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "合并文件定位信息",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ChunkMergedResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数或业务处理失败",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ChunkErrorResult.class)))
    })
    @OperationLog(
            module = "Chunk",
            type = OperationLog.OperationType.OTHER,
            description = "merge",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> merge(ServerRequest request) {
        return requestHandlerUtil
                .handleRequestBody(request, ChunkMergeRequest.class)
                .flatMap(chunkUploadService::merge)
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 重试所有者已完成会话的分片清理，未删对象保留元数据供后续重试。
     * @param request 仅包含文件记录编号的请求
     * @return 本次清理尝试已执行的响应
     */
    @Operation(operationId = "retryChunkCleanup", summary = "重试分片清理", description = "仅文件所有者可重试已完成会话的清理；未删除分片保留元数据")
    @Parameter(
            name = "fileId",
            description = "文件记录主键",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "清理尝试已执行，data 为 true",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ChunkCleanupResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数或业务处理失败",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ChunkErrorResult.class)))
    })
    @OperationLog(
            module = "Chunk",
            type = OperationLog.OperationType.OTHER,
            description = "retryCleanup",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> retryCleanup(ServerRequest request) {
        return Mono.defer(() -> {
                    final long fileId;
                    try {
                        fileId = Long.parseLong(request.pathVariable("fileId"));
                    } catch (NumberFormatException error) {
                        return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "fileId 必须为有效数字");
                    }
                    return chunkUploadService
                            .retryCleanup(fileId)
                            .then(Mono.defer(() -> requestHandlerUtil.buildSuccessResponse(true)));
                })
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 查询分片上传进度。
     *
     * <p>根据路径中的 fileId 返回已成功上传的分片序号列表与分片大小信息，客户端据此跳过
     * 已完成分片，实现断点续传。</p>
     *
     * @param request 路径参数包含 fileId 的服务器请求
     * @return 包含 {@link ChunkInitResult} 的统一成功响应
     */
    @Operation(operationId = "getChunkStatus", summary = "查询分片上传进度", description = "返回已上传分片序号列表，用于断点续传")
    @Parameters({
        @Parameter(
                name = "fileId",
                description = "文件记录主键",
                required = true,
                in = ParameterIn.PATH,
                schema = @Schema(type = "integer", format = "int64"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "上传进度",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ChunkSessionResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数或业务处理失败",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ChunkErrorResult.class)))
    })
    @OperationLog(
            module = "Chunk",
            type = OperationLog.OperationType.SELECT,
            description = "status",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> status(ServerRequest request) {
        return Mono.defer(() -> {
                    final long fileId;
                    try {
                        fileId = Long.parseLong(request.pathVariable("fileId"));
                    } catch (NumberFormatException error) {
                        return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "fileId 必须为有效数字");
                    }
                    return chunkUploadService.status(fileId).flatMap(requestHandlerUtil::buildSuccessResponse);
                })
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /** 分片会话统一响应的文档模型。 */
    @Schema(name = "ChunkSessionResult")
    public static class ChunkSessionResult extends Result<ChunkInitResult> {}

    /** 单片上传统一响应的文档模型。 */
    @Schema(name = "ChunkPartResult")
    public static class ChunkPartResult extends Result<ChunkUploadResult> {}

    /** 合并文件统一响应的文档模型。 */
    @Schema(name = "ChunkMergedResult")
    public static class ChunkMergedResult extends Result<ChunkMergeResult> {}

    /** 清理尝试统一响应的文档模型。 */
    @Schema(name = "ChunkCleanupResult")
    public static class ChunkCleanupResult extends Result<Boolean> {}

    /** 分片错误统一响应的文档模型。 */
    @Schema(name = "ChunkErrorResult")
    public static class ChunkErrorResult extends Result<Void> {}
}
