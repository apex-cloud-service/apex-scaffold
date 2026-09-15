package com.apex.scaffold.interfaces.handler.office;

import com.apex.scaffold.application.office.OfficeDocumentService;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.domain.office.document.DocumentOperationType;
import com.apex.scaffold.domain.office.document.DocumentType;
import com.apex.scaffold.interfaces.assembler.office.DocumentInfoAssembler;
import com.apex.scaffold.interfaces.dto.office.document.CreateDocumentRequest;
import com.apex.scaffold.interfaces.dto.office.document.DocumentInfoResponse;
import com.apex.scaffold.interfaces.dto.office.document.UploadFileRequest;
import com.apex.scaffold.interfaces.util.RequestHandlerUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Base64;

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
public class OfficeDocumentHandler {

    private final OfficeDocumentService documentService;
    private final DocumentInfoAssembler assembler;

    /**
     * 创建文档
     *
     * <p>在指定的办公平台创建一个新文档。
     *
     * @param request ServerRequest对象，包含请求体和参数
     * @return Mono&lt;ServerResponse&gt; 包含创建成功的文档信息
     */
    public Mono<ServerResponse> createDocument(ServerRequest request) {
        log.info("创建文档请求");
        
        return RequestHandlerUtil.validateAndMap(request, CreateDocumentRequest.class)
                .flatMap(req -> {
                    DocumentType documentType = DocumentType.valueOf(req.getDocumentType());
                    
                    return documentService.createDocument(
                            req.getPlatform(),
                            req.getFolderId(),
                            req.getTitle(),
                            documentType,
                            req.getOwnerId(),
                            req.getOwnerName()
                    );
                })
                .map(assembler::toResponse)
                .flatMap(response -> RequestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 上传文件
     *
     * <p>上传文件到指定的办公平台。
     *
     * @param request ServerRequest对象，包含文件内容和参数
     * @return Mono&lt;ServerResponse&gt; 包含上传成功的文档信息
     */
    public Mono<ServerResponse> uploadFile(ServerRequest request) {
        log.info("上传文件请求");
        
        return RequestHandlerUtil.validateAndMap(request, UploadFileRequest.class)
                .flatMap(req -> {
                    byte[] fileContent = Base64.getDecoder().decode(req.getFileContent());
                    
                    return documentService.uploadFile(
                            req.getPlatform(),
                            req.getFolderId(),
                            req.getFileName(),
                            fileContent,
                            req.getOwnerId(),
                            req.getOwnerName()
                    );
                })
                .map(assembler::toResponse)
                .flatMap(response -> RequestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 查询文档详情
     *
     * <p>根据文档ID查询文档的详细信息。
     *
     * @param request ServerRequest对象，路径参数包含documentId
     * @return Mono&lt;ServerResponse&gt; 包含文档详细信息
     */
    public Mono<ServerResponse> getDocumentDetail(ServerRequest request) {
        String documentIdStr = request.pathVariable("documentId");
        Long documentId = Long.parseLong(documentIdStr);
        log.info("查询文档详情 - documentId: {}", documentId);

        return documentService.getDocumentDetail(documentId)
                .map(assembler::toResponse)
                .flatMap(response -> RequestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 查询文件夹下的文档
     *
     * <p>列出指定文件夹下的所有文档。
     *
     * @param request ServerRequest对象，查询参数包含platform和folderId
     * @return Mono&lt;ServerResponse&gt; 包含文档列表
     */
    public Mono<ServerResponse> getDocumentsInFolder(ServerRequest request) {
        String platform = request.queryParam("platform").orElse("");
        String folderId = request.queryParam("folderId").orElse("");
        
        log.info("查询文件夹下的文档 - platform: {}, folderId: {}", platform, folderId);

        return documentService.listDocumentsInFolder(platform, folderId)
                .map(assembler::toResponseList)
                .flatMap(response -> RequestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 查询我的文档
     *
     * <p>查询当前用户的所有文档。
     *
     * @param request ServerRequest对象，查询参数包含ownerId
     * @return Mono&lt;ServerResponse&gt; 包含文档列表
     */
    public Mono<ServerResponse> getMyDocuments(ServerRequest request) {
        String ownerId = request.queryParam("ownerId").orElse("");
        log.info("查询我的文档 - ownerId: {}", ownerId);

        return documentService.getMyDocuments(ownerId)
                .map(assembler::toResponseList)
                .flatMap(response -> RequestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 下载文档
     *
     * <p>下载指定的文档。
     *
     * @param request ServerRequest对象，路径参数包含documentId
     * @return Mono&lt;ServerResponse&gt; 包含文档内容（Base64编码）
     */
    public Mono<ServerResponse> downloadDocument(ServerRequest request) {
        String documentIdStr = request.pathVariable("documentId");
        Long documentId = Long.parseLong(documentIdStr);
        log.info("下载文档 - documentId: {}", documentId);

        return documentService.downloadDocument(documentId)
                .map(content -> Base64.getEncoder().encodeToString(content))
                .flatMap(base64Content -> RequestHandlerUtil.ok(Result.success(base64Content)))
                .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 删除文档
     *
     * <p>删除指定的文档。
     *
     * @param request ServerRequest对象，路径参数包含documentId
     * @return Mono&lt;ServerResponse&gt; 包含删除结果
     */
    public Mono<ServerResponse> deleteDocument(ServerRequest request) {
        String documentIdStr = request.pathVariable("documentId");
        Long documentId = Long.parseLong(documentIdStr);
        log.info("删除文档 - documentId: {}", documentId);

        return documentService.deleteDocument(documentId)
                .flatMap(success -> RequestHandlerUtil.ok(Result.success(success)))
                .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 移动文档
     *
     * <p>移动文档到指定的文件夹。
     *
     * @param request ServerRequest对象，路径参数包含documentId，查询参数包含targetFolderId
     * @return Mono&lt;ServerResponse&gt; 包含移动后的文档信息
     */
    public Mono<ServerResponse> moveDocument(ServerRequest request) {
        String documentIdStr = request.pathVariable("documentId");
        Long documentId = Long.parseLong(documentIdStr);
        String targetFolderId = request.queryParam("targetFolderId").orElse("");
        
        log.info("移动文档 - documentId: {}, targetFolderId: {}", documentId, targetFolderId);

        return documentService.moveDocument(documentId, targetFolderId)
                .map(assembler::toResponse)
                .flatMap(response -> RequestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 复制文档
     *
     * <p>复制指定的文档。
     *
     * @param request ServerRequest对象，路径参数包含documentId，查询参数包含targetFolderId
     * @return Mono&lt;ServerResponse&gt; 包含复制后的文档信息
     */
    public Mono<ServerResponse> copyDocument(ServerRequest request) {
        String documentIdStr = request.pathVariable("documentId");
        Long documentId = Long.parseLong(documentIdStr);
        String targetFolderId = request.queryParam("targetFolderId").orElse("");
        
        log.info("复制文档 - documentId: {}, targetFolderId: {}", documentId, targetFolderId);

        return documentService.copyDocument(documentId, targetFolderId)
                .map(assembler::toResponse)
                .flatMap(response -> RequestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(RequestHandlerUtil::handleError);
    }
}
