package com.apex.scaffold.officeservice.interfaces.handler.office;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.office.application.OfficeDocumentService;
import com.apex.scaffold.office.domain.document.DocumentType;
import com.apex.scaffold.officeservice.interfaces.assembler.office.DocumentInfoAssembler;
import com.apex.scaffold.officeservice.interfaces.dto.office.document.CreateDocumentRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.document.DocumentInfoResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.document.UploadFileRequest;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 办公平台文档操作处理器
 *
 * <p>处理文档操作相关的HTTP请求，包括创建、查询、更新、删除、上传、下载文档等操作。
 * 使用函数式编程风格，每个方法对应一个具体的业务用例。
 *
 * <p>主要功能：
 * <ul>
 *   <li>创建文档 - 在指定办公平台创建新文档</li>
 *   <li>上传文件 - 上传文件到办公平台</li>
 *   <li>查询文档详情 - 根据文档ID查询详细信息</li>
 *   <li>查询文件夹下的文档 - 列出指定文件夹下的所有文档</li>
 *   <li>查询我的文档 - 查询当前用户的所有文档</li>
 *   <li>下载文档 - 下载指定文档</li>
 *   <li>删除文档 - 删除指定文档</li>
 *   <li>移动文档 - 移动文档到指定文件夹</li>
 *   <li>复制文档 - 复制文档</li>
 * </ul>
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "办公平台文档管理", description = "办公平台文档创建、上传、查询、下载、删除等相关接口")
public class OfficeDocumentHandler {

    /** 文档详情的统一响应结构，仅用于接口文档。 */
    @Schema(name = "OfficeDocumentResult")
    public static class DocumentResult extends Result<DocumentInfoResponse> {}

    /** 文档列表的统一响应结构，仅用于接口文档。 */
    @Schema(name = "OfficeDocumentListResult")
    public static class DocumentListResult extends Result<java.util.List<DocumentInfoResponse>> {}

    /** 文档写操作的布尔结果，仅用于接口文档。 */
    @Schema(name = "OfficeDocumentBooleanResult")
    public static class DocumentBooleanResult extends Result<Boolean> {}

    /** 文档下载地址的统一响应结构，仅用于接口文档。 */
    @Schema(name = "OfficeDocumentDownloadResult")
    public static class DocumentDownloadResult extends Result<String> {}

    private final OfficeDocumentService documentService;
    private final DocumentInfoAssembler assembler;
    private final RequestHandlerUtil requestHandlerUtil;

    /**
     * 创建文档
     *
     * <p>在指定的办公平台创建一个新文档。
     *
     * @param request ServerRequest对象，包含请求体和参数
     * @return Mono&lt;ServerResponse&gt; 包含创建成功的文档信息
     */
    @Operation(operationId = "createDocument", summary = "创建文档", description = "在指定办公平台创建新文档，支持多种文档类型")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "文档创建请求信息",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateDocumentRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = DocumentResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "创建失败",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeDocument",
            type = OperationLog.OperationType.INSERT,
            description = "createDocument",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> createDocument(ServerRequest request) {
        log.info("创建文档请求");

        return requestHandlerUtil
                .validateAndMap(request, CreateDocumentRequest.class)
                .flatMap(req -> {
                    DocumentType documentType = DocumentType.valueOf(req.getDocumentType());

                    return documentService.createDocument(
                            req.getPlatform(),
                            req.getFolderId(),
                            req.getTitle(),
                            documentType,
                            req.getOwnerId(),
                            req.getOwnerName());
                })
                .map(assembler::toResponse)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 上传文件
     *
     * <p>上传文件到指定的办公平台。
     *
     * @param request ServerRequest对象，包含文件内容和参数
     * @return Mono&lt;ServerResponse&gt; 包含上传成功的文档信息
     */
    @Operation(operationId = "uploadFile", summary = "上传文件", description = "上传文件到指定办公平台，文件内容需Base64编码")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "文件上传请求信息",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UploadFileRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "上传成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = DocumentResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "上传失败",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeDocument",
            type = OperationLog.OperationType.UPLOAD,
            description = "uploadFile",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> uploadFile(ServerRequest request) {
        log.info("上传文件请求");

        return requestHandlerUtil
                .validateAndMap(request, UploadFileRequest.class)
                .flatMap(req -> documentService.uploadFile(
                        req.getPlatform(),
                        req.getFolderId(),
                        req.getFileName(),
                        req.getFileContent(),
                        req.getOwnerId(),
                        req.getOwnerName()))
                .map(assembler::toResponse)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询文档详情
     *
     * <p>根据文档ID查询文档的详细信息。
     *
     * @param request ServerRequest对象，路径参数包含documentId
     * @return Mono&lt;ServerResponse&gt; 包含文档详细信息
     */
    @Operation(operationId = "getDocumentDetail", summary = "查询文档详情", description = "根据文档ID查询文档的详细信息")
    @Parameters({
        @Parameter(
                name = "documentId",
                in = ParameterIn.PATH,
                required = true,
                description = "文档ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = DocumentResult.class))),
        @ApiResponse(
                responseCode = "404",
                description = "文档不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeDocument",
            type = OperationLog.OperationType.SELECT,
            description = "getDocumentDetail",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getDocumentDetail(ServerRequest request) {
        String documentIdStr = request.pathVariable("documentId");
        Long documentId = Long.parseLong(documentIdStr);
        log.info("查询文档详情 - documentId: {}", documentId);

        return documentService
                .getDocumentDetail(documentId)
                .map(assembler::toResponse)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询文件夹下的文档
     *
     * <p>列出指定文件夹下的所有文档。
     *
     * @param request ServerRequest对象，查询参数包含platform和folderId
     * @return Mono&lt;ServerResponse&gt; 包含文档列表
     */
    @Operation(
            operationId = "getDocumentsInFolder",
            summary = "查询文件夹下的文档",
            description = "按folderId查询文档；platform仅用于请求日志，不参与筛选")
    @Parameters({
        @Parameter(
                name = "platform",
                in = ParameterIn.QUERY,
                required = true,
                description = "办公平台类型",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "folderId",
                in = ParameterIn.QUERY,
                required = true,
                description = "文件夹ID",
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = DocumentListResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeDocument",
            type = OperationLog.OperationType.SELECT,
            description = "getDocumentsInFolder",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getDocumentsInFolder(ServerRequest request) {
        String platform = request.queryParam("platform").orElse("");
        String folderId = request.queryParam("folderId").orElse("");

        log.info("查询文件夹下的文档 - platform: {}, folderId: {}", platform, folderId);

        return documentService
                .listDocuments(folderId)
                .map(assembler::toResponseList)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询我的文档
     *
     * <p>查询当前用户的所有文档。
     *
     * @param request ServerRequest对象，查询参数包含ownerId
     * @return Mono&lt;ServerResponse&gt; 包含文档列表
     */
    @Operation(operationId = "getMyDocuments", summary = "按所有者查询文档", description = "按请求中的ownerId查询文档，未传时使用空字符串")
    @Parameters({
        @Parameter(
                name = "ownerId",
                in = ParameterIn.QUERY,
                required = true,
                description = "文档所有者ID",
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = DocumentListResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeDocument",
            type = OperationLog.OperationType.SELECT,
            description = "getMyDocuments",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getMyDocuments(ServerRequest request) {
        String ownerId = request.queryParam("ownerId").orElse("");
        log.info("查询我的文档 - ownerId: {}", ownerId);

        return documentService
                .getMyDocuments(ownerId)
                .map(assembler::toResponseList)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 下载文档
     *
     * <p>下载指定的文档。
     *
     * @param request ServerRequest对象，路径参数包含documentId
     * @return 包含文档下载 URL 的统一响应
     */
    @Operation(operationId = "downloadDocument", summary = "获取文档下载地址", description = "按导出格式获取下载URL，data为地址字符串")
    @Parameters({
        @Parameter(
                name = "documentId",
                in = ParameterIn.PATH,
                required = true,
                description = "文档ID",
                schema = @Schema(type = "integer", format = "int64")),
        @Parameter(
                name = "format",
                in = ParameterIn.QUERY,
                required = false,
                description = "导出格式（默认pdf）",
                schema = @Schema(type = "string", defaultValue = "pdf"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "下载成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = DocumentDownloadResult.class))),
        @ApiResponse(
                responseCode = "404",
                description = "文档不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeDocument",
            type = OperationLog.OperationType.DOWNLOAD,
            description = "downloadDocument",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> downloadDocument(ServerRequest request) {
        String documentIdStr = request.pathVariable("documentId");
        Long documentId = Long.parseLong(documentIdStr);
        String exportFormat = request.queryParam("format").orElse("pdf");
        log.info("下载文档 - documentId: {}, exportFormat: ", documentId, exportFormat);

        return documentService
                .downloadDocument(documentId, exportFormat)
                .flatMap(downloadUrl -> requestHandlerUtil.ok(Result.success(downloadUrl)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 删除文档
     *
     * <p>删除指定的文档。
     *
     * @param request ServerRequest对象，路径参数包含documentId
     * @return Mono&lt;ServerResponse&gt; 包含删除结果
     */
    @Operation(operationId = "deleteDocument", summary = "删除文档", description = "删除指定的文档")
    @Parameters({
        @Parameter(
                name = "documentId",
                in = ParameterIn.PATH,
                required = true,
                description = "文档ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "删除成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = DocumentBooleanResult.class))),
        @ApiResponse(
                responseCode = "404",
                description = "文档不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeDocument",
            type = OperationLog.OperationType.DELETE,
            description = "deleteDocument",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> deleteDocument(ServerRequest request) {
        String documentIdStr = request.pathVariable("documentId");
        Long documentId = Long.parseLong(documentIdStr);
        log.info("删除文档 - documentId: {}", documentId);

        return documentService
                .deleteDocument(documentId)
                .flatMap(success -> requestHandlerUtil.ok(Result.success(success)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 移动文档
     *
     * <p>移动文档到指定的文件夹。
     *
     * @param request ServerRequest对象，路径参数包含documentId，查询参数包含targetFolderId
     * @return 包含移动操作布尔结果的统一响应
     */
    @Operation(operationId = "moveDocument", summary = "移动文档", description = "移动文档到指定的文件夹")
    @Parameters({
        @Parameter(
                name = "documentId",
                in = ParameterIn.PATH,
                required = true,
                description = "文档ID",
                schema = @Schema(type = "integer", format = "int64")),
        @Parameter(
                name = "targetFolderId",
                in = ParameterIn.QUERY,
                required = true,
                description = "目标文件夹ID",
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "移动成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = DocumentBooleanResult.class))),
        @ApiResponse(
                responseCode = "404",
                description = "文档或文件夹不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeDocument",
            type = OperationLog.OperationType.OTHER,
            description = "moveDocument",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> moveDocument(ServerRequest request) {
        String documentIdStr = request.pathVariable("documentId");
        Long documentId = Long.parseLong(documentIdStr);
        String targetFolderId = request.queryParam("targetFolderId").orElse("");

        log.info("移动文档 - documentId: {}, targetFolderId: {}", documentId, targetFolderId);

        return documentService
                .moveDocument(documentId, targetFolderId)
                .flatMap(success -> requestHandlerUtil.ok(Result.success(success)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 复制文档
     *
     * <p>复制指定的文档。
     *
     * @param request ServerRequest对象，路径参数包含documentId，查询参数包含targetFolderId
     * @return Mono&lt;ServerResponse&gt; 包含复制后的文档信息
     */
    @Operation(operationId = "copyDocument", summary = "复制文档", description = "复制指定的文档到目标文件夹")
    @Parameters({
        @Parameter(
                name = "documentId",
                in = ParameterIn.PATH,
                required = true,
                description = "文档ID",
                schema = @Schema(type = "integer", format = "int64")),
        @Parameter(
                name = "targetFolderId",
                in = ParameterIn.QUERY,
                required = true,
                description = "目标文件夹ID",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "newTitle",
                in = ParameterIn.QUERY,
                required = false,
                description = "新文档标题（可选）",
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "复制成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = DocumentResult.class))),
        @ApiResponse(
                responseCode = "404",
                description = "文档或文件夹不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeDocument",
            type = OperationLog.OperationType.OTHER,
            description = "copyDocument",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> copyDocument(ServerRequest request) {
        String documentIdStr = request.pathVariable("documentId");
        Long documentId = Long.parseLong(documentIdStr);
        String targetFolderId = request.queryParam("targetFolderId").orElse("");
        String newTitle = request.queryParam("newTitle").orElse("");

        log.info("复制文档 - documentId: {}, targetFolderId: {}, newTitle: {}", documentId, targetFolderId, newTitle);

        return documentService
                .copyDocument(documentId, targetFolderId, newTitle)
                .map(assembler::toResponse)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }
}
