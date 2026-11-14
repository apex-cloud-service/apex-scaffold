package com.apex.scaffold.fileservice.interfaces.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.file.application.dto.PageResponse;
import com.apex.scaffold.file.application.dto.data.DocumentQueryRequest;
import com.apex.scaffold.file.application.dto.data.DocumentRequest;
import com.apex.scaffold.file.application.dto.data.DocumentResponse;
import com.apex.scaffold.file.application.service.DocumentService;
import com.apex.scaffold.fileservice.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 文档管理处理器，提供文档增删改查、状态变更及附件地址查询。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "文档管理")
@Component
public class DocumentHandler {

    private final DocumentService documentService;
    private final RequestHandlerUtil requestHandlerUtil;

    /**
     * 构造文档处理器。
     *
     * @param documentService 文档服务
     * @param requestHandlerUtil 请求校验工具
     */
    public DocumentHandler(DocumentService documentService, RequestHandlerUtil requestHandlerUtil) {
        this.documentService = documentService;
        this.requestHandlerUtil = requestHandlerUtil;
    }

    /**
     * 校验文档信息并创建文档。
     *
     * @param request 请求体为文档创建信息的请求
     * @return HTTP 200，数据为新建文档详情的统一响应
     */
    @Operation(
            operationId = "createDocument",
            summary = "创建文档",
            requestBody =
                    @RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = DocumentRequest.class))),
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "创建成功，返回文档详情",
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = DocumentResult.class))))
    @OperationLog(
            module = "Document",
            type = OperationLog.OperationType.INSERT,
            description = "create",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> create(ServerRequest request) {
        return requestHandlerUtil
                .handleRequestBody(request, DocumentRequest.class)
                .flatMap(documentService::create)
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    /**
     * 根据路径 ID 校验并更新文档信息。
     *
     * @param request 包含文档 ID 和文档更新信息的请求
     * @return HTTP 200，数据为更新后文档详情的统一响应
     */
    @Operation(
            operationId = "updateDocument",
            summary = "更新文档",
            parameters =
                    @Parameter(
                            name = "id",
                            description = "文档 ID",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "integer", format = "int64")),
            requestBody =
                    @RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = DocumentRequest.class))),
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "更新成功，返回文档详情",
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = DocumentResult.class))))
    @OperationLog(
            module = "Document",
            type = OperationLog.OperationType.UPDATE,
            description = "update",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> update(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return requestHandlerUtil
                .handleRequestBody(request, DocumentRequest.class)
                .flatMap(req -> documentService.update(id, req))
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    /**
     * 根据路径 ID 删除文档。
     *
     * @param request 包含文档 ID 的请求
     * @return HTTP 200，data 为空的统一成功响应
     */
    @Operation(
            operationId = "deleteDocument",
            summary = "删除文档",
            parameters =
                    @Parameter(
                            name = "id",
                            description = "文档 ID",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "integer", format = "int64")),
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "删除成功，data 为空",
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = EmptyDocumentResult.class))))
    @OperationLog(
            module = "Document",
            type = OperationLog.OperationType.DELETE,
            description = "delete",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> delete(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return documentService.delete(id).then(ServerResponse.ok().bodyValue(Result.success()));
    }

    /**
     * 累加浏览次数后查询文档详情。
     *
     * @param request 包含文档 ID 的请求
     * @return HTTP 200，数据为文档详情的统一响应
     */
    @Operation(
            operationId = "getDocumentById",
            summary = "查询文档详情",
            description = "先累加浏览次数，再读取文档详情",
            parameters =
                    @Parameter(
                            name = "id",
                            description = "文档 ID",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "integer", format = "int64")),
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "查询成功",
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = DocumentResult.class))))
    @OperationLog(
            module = "Document",
            type = OperationLog.OperationType.SELECT,
            description = "getById",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getById(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return documentService
                .incrementViewCount(id)
                .then(documentService.getById(id))
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    /**
     * 按关键字、文档类型、分类和状态分页查询文档。
     *
     * @param request 包含筛选条件与 page、size 的请求，默认第 1 页、每页 20 条
     * @return HTTP 200，数据为文件域分页结构的统一响应
     */
    @Operation(
            operationId = "queryDocuments",
            summary = "分页查询文档列表",
            parameters = {
                @Parameter(
                        name = "keyword",
                        description = "文档标题关键字",
                        in = ParameterIn.QUERY,
                        schema = @Schema(type = "string")),
                @Parameter(
                        name = "docType",
                        description = "文档类型",
                        in = ParameterIn.QUERY,
                        schema = @Schema(type = "string")),
                @Parameter(
                        name = "category",
                        description = "文档分类",
                        in = ParameterIn.QUERY,
                        schema = @Schema(type = "string")),
                @Parameter(
                        name = "status",
                        description = "文档状态",
                        in = ParameterIn.QUERY,
                        schema = @Schema(type = "integer")),
                @Parameter(
                        name = "page",
                        description = "页码",
                        in = ParameterIn.QUERY,
                        schema = @Schema(type = "integer", defaultValue = "1")),
                @Parameter(
                        name = "size",
                        description = "每页数量",
                        in = ParameterIn.QUERY,
                        schema = @Schema(type = "integer", defaultValue = "20"))
            },
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "查询成功",
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = DocumentPageResult.class))))
    @OperationLog(
            module = "Document",
            type = OperationLog.OperationType.SELECT,
            description = "query",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> query(ServerRequest request) {
        DocumentQueryRequest queryRequest = DocumentQueryRequest.builder()
                .keyword(request.queryParam("keyword").orElse(null))
                .docType(request.queryParam("docType").orElse(null))
                .category(request.queryParam("category").orElse(null))
                .status(request.queryParam("status").map(Integer::parseInt).orElse(null))
                .pageNum(request.queryParam("page").map(Integer::parseInt).orElse(1))
                .pageSize(request.queryParam("size").map(Integer::parseInt).orElse(20))
                .build();

        return documentService.query(queryRequest).flatMap(result -> ServerResponse.ok()
                .bodyValue(Result.success(result)));
    }

    /**
     * 读取请求中的状态并更新指定文档。
     *
     * @param request 包含文档 ID 和状态 JSON 的请求
     * @return HTTP 200，data 为空的统一成功响应
     */
    @Operation(
            operationId = "updateDocumentStatus",
            summary = "更新文档状态",
            parameters =
                    @Parameter(
                            name = "id",
                            description = "文档 ID",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "integer", format = "int64")),
            requestBody =
                    @RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = UpdateStatusRequest.class))),
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "更新成功，data 为空",
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = EmptyDocumentResult.class))))
    @OperationLog(
            module = "Document",
            type = OperationLog.OperationType.UPDATE,
            description = "updateStatus",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> updateStatus(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return requestHandlerUtil
                .handleRequestBody(request, UpdateStatusRequest.class)
                .flatMap(req -> documentService.updateStatus(id, req.getStatus()))
                .then(ServerResponse.ok().bodyValue(Result.success()));
    }

    /**
     * 累加下载次数后返回附件地址，不直接传输附件内容。
     *
     * @param request 包含文档 ID 的请求
     * @return HTTP 200 的附件地址统一响应；无附件时返回 HTTP 400 的错误响应
     */
    @Operation(
            operationId = "getDocumentDownloadUrl",
            summary = "获取文档附件地址",
            description = "先累加下载次数，再返回附件 URL；不返回二进制文件流",
            parameters =
                    @Parameter(
                            name = "id",
                            description = "文档 ID",
                            required = true,
                            in = ParameterIn.PATH,
                            schema = @Schema(type = "integer", format = "int64")),
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "附件地址",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = DocumentUrlResult.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "文档无附件",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = EmptyDocumentResult.class)))
            })
    @OperationLog(
            module = "Document",
            type = OperationLog.OperationType.DOWNLOAD,
            description = "download",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> download(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return documentService
                .incrementDownloadCount(id)
                .then(documentService.getById(id))
                .flatMap(doc -> {
                    if (doc.getFileUrl() == null || doc.getFileUrl().isBlank()) {
                        return ServerResponse.badRequest().bodyValue(Result.error("文档无附件"));
                    }
                    return ServerResponse.ok().bodyValue(Result.success(doc.getFileUrl()));
                });
    }

    /** 文档状态更新请求。 */
    @Data
    @Schema(name = "DocumentStatusRequest", description = "文档状态更新请求")
    public static class UpdateStatusRequest {
        @Schema(description = "文档状态：0-草稿，1-正常，2-归档", example = "1")
        private Integer status;
    }

    /** 文档详情统一响应的文档模型。 */
    @Schema(name = "DocumentResult")
    public static class DocumentResult extends Result<DocumentResponse> {}

    /** 文档分页统一响应的文档模型。 */
    @Schema(name = "DocumentPageResult")
    public static class DocumentPageResult extends Result<PageResponse<DocumentResponse>> {}

    /** 文档附件地址统一响应的文档模型。 */
    @Schema(name = "DocumentUrlResult")
    public static class DocumentUrlResult extends Result<String> {}

    /** 文档无数据统一响应的文档模型。 */
    @Schema(name = "EmptyDocumentResult")
    public static class EmptyDocumentResult extends Result<Void> {}
}
