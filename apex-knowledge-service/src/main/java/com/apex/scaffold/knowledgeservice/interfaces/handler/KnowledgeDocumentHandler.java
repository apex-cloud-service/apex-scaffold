package com.apex.scaffold.knowledgeservice.interfaces.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.knowledge.application.dto.KnowledgeManualDocumentRequest;
import com.apex.scaffold.knowledge.application.service.KnowledgeDocumentAppService;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledgeservice.interfaces.util.KnowledgeRequestHandlerUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 知识文档管理 Handler：文档上传、在线录入、查询、删除与重建索引。
 *
 * <p>上传为 multipart（字段 {@code file}），字节上限由
 * {@code apex.knowledge.ingest.max-upload-size-mb} 控制；超限显式报错。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentHandler {

    private final KnowledgeDocumentAppService documentAppService;

    private final KnowledgeRequestHandlerUtil requestUtil;

    private final KnowledgeProperties properties;

    /**
     * 分页查询文档。
     *
     * @param request 请求（spaceId/parseStatus/keyword/page/size 查询参数）
     * @return 文档分页响应
     */
    @RequirePermission("kb:doc:read")
    public Mono<ServerResponse> page(ServerRequest request) {
        Long spaceId = KnowledgeRequestHandlerUtil.safeParseLong(
                request.queryParam("spaceId").orElse(null));
        String parseStatus = request.queryParam("parseStatus").orElse(null);
        String keyword = request.queryParam("keyword").orElse(null);
        int pageNum = KnowledgeRequestHandlerUtil.safeParseInt(
                request.queryParam("page").orElse(null), 1);
        int pageSize = KnowledgeRequestHandlerUtil.safeParseInt(
                request.queryParam("size").orElse(null), 20);
        return documentAppService
                .findPage(spaceId, parseStatus, keyword, pageNum, pageSize)
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 文档详情（含分块预览）。
     *
     * @param request 请求（docId 路径变量）
     * @return 文档详情响应
     */
    @RequirePermission("kb:doc:read")
    public Mono<ServerResponse> detail(ServerRequest request) {
        Long docId = Long.valueOf(request.pathVariable("docId"));
        return documentAppService
                .findById(docId)
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 上传文档（multipart）。
     *
     * @param request 请求（spaceId 路径变量 + file 文件字段）
     * @return 登记后的文档 DTO
     */
    @OperationLog(module = "知识库", type = OperationLog.OperationType.UPLOAD, description = "上传知识文档")
    @RequirePermission("kb:doc:upload")
    public Mono<ServerResponse> upload(ServerRequest request) {
        Long spaceId = Long.valueOf(request.pathVariable("spaceId"));
        long maxBytes = (long) properties.getIngest().getMaxUploadSizeMb() * 1024 * 1024;
        return request.multipartData()
                .flatMap(parts -> {
                    Part part = parts.getFirst("file");
                    if (!(part instanceof FilePart filePart)) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "缺少文件字段 file"));
                    }
                    String contentType = filePart.headers().getContentType() == null
                            ? "application/octet-stream"
                            : filePart.headers().getContentType().toString();
                    return readBytes(filePart, maxBytes)
                            .map(bytes -> new UploadPayload(filePart.filename(), contentType, bytes));
                })
                .flatMap(payload -> documentAppService.registerUpload(
                        spaceId, payload.fileName(), payload.contentType(), payload.content()))
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 在线录入文档（JSON 正文直接入库）。
     *
     * @param request 请求（JSON body）
     * @return 登记后的文档 DTO
     */
    @OperationLog(module = "知识库", type = OperationLog.OperationType.INSERT, description = "在线录入知识文档")
    @RequirePermission("kb:doc:upload")
    public Mono<ServerResponse> registerManual(ServerRequest request) {
        return requestUtil
                .handleRequestBody(request, KnowledgeManualDocumentRequest.class)
                .flatMap(documentAppService::registerManual)
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 删除文档（同步清理分块与索引）。
     *
     * @param request 请求（docId 路径变量）
     * @return 删除结果
     */
    @OperationLog(module = "知识库", type = OperationLog.OperationType.DELETE, description = "删除知识文档")
    @RequirePermission("kb:doc:delete")
    public Mono<ServerResponse> delete(ServerRequest request) {
        Long docId = Long.valueOf(request.pathVariable("docId"));
        return documentAppService
                .delete(docId)
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 手动重建索引。
     *
     * @param request 请求（docId 路径变量）
     * @return 更新后的文档 DTO
     */
    @OperationLog(module = "知识库", type = OperationLog.OperationType.UPDATE, description = "重建知识文档索引")
    @RequirePermission("kb:doc:reindex")
    public Mono<ServerResponse> reindex(ServerRequest request) {
        Long docId = Long.valueOf(request.pathVariable("docId"));
        return documentAppService
                .reindex(docId)
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 读取文件字节（限流读取，超限显式报错）。
     *
     * @param filePart 文件分片
     * @param maxBytes 字节上限
     * @return 文件字节
     */
    private Mono<byte[]> readBytes(FilePart filePart, long maxBytes) {
        return DataBufferUtils.join(filePart.content(), (int) maxBytes)
                .map(dataBuffer -> {
                    try {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                })
                .onErrorMap(
                        DataBufferLimitException.class,
                        e -> new BusinessException(
                                ResultCode.PARAM_ERROR,
                                "文件大小超出限制: " + properties.getIngest().getMaxUploadSizeMb() + "MB"));
    }

    /** 上传载荷内部结构。 */
    private record UploadPayload(String fileName, String contentType, byte[] content) {}
}
