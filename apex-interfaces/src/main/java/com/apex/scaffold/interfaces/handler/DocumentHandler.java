package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.data.DocumentQueryRequest;
import com.apex.scaffold.application.dto.data.DocumentRequest;
import com.apex.scaffold.application.service.DocumentService;
import com.apex.scaffold.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 文档管理处理器（函数式端点）。
 *
 * <p>提供文档管理的完整 CRUD API，包括文档创建、更新、删除、查询、
 * 状态管理、下载以及浏览/下载计数等功能，支持分页查询与多维度过滤。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>POST /api/documents - 创建文档（需要 data:document:create 权限）</li>
 *   <li>PUT /api/documents/{id} - 更新文档（需要 data:document:update 权限）</li>
 *   <li>DELETE /api/documents/{id} - 删除文档（需要 data:document:delete 权限）</li>
 *   <li>GET /api/documents/{id} - 查询文档详情（需要 data:document:query 权限）</li>
 *   <li>GET /api/documents - 分页查询文档列表（需要 data:document:query 权限）</li>
 *   <li>PUT /api/documents/{id}/status - 更新文档状态（需要 data:document:update 权限）</li>
 *   <li>GET /api/documents/{id}/download - 下载文档（需要 data:document:download 权限）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DocumentService
 */
@Tag(name = "文档管理")
@Component
@RequiredArgsConstructor
public class DocumentHandler {

    private final DocumentService documentService;

    /**
     * 处理创建文档请求。
     *
     * <p>解析请求体为 {@link DocumentRequest}，校验通过后调用应用服务创建文档。
     *
     * @param request HTTP 请求对象，请求体为文档信息 JSON
     * @return 包含新建文档信息的统一成功响应
     */
    @Operation(summary = "创建文档")
    @PreAuthorize("hasAuthority('data:document:create')")
    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(DocumentRequest.class)
                .flatMap(documentService::create)
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    /**
     * 处理更新文档请求。
     *
     * <p>根据路径中的文档 ID 解析请求体并更新文档信息。
     *
     * @param request HTTP 请求对象，路径参数包含文档 ID，请求体为文档信息 JSON
     * @return 包含更新后文档信息的统一成功响应
     */
    @Operation(summary = "更新文档")
    @PreAuthorize("hasAuthority('data:document:update')")
    public Mono<ServerResponse> update(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return request.bodyToMono(DocumentRequest.class)
                .flatMap(req -> documentService.update(id, req))
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    /**
     * 处理删除文档请求。
     *
     * <p>根据路径中的文档 ID 删除指定文档。
     *
     * @param request HTTP 请求对象，路径参数包含文档 ID
     * @return 删除成功的统一成功响应
     */
    @Operation(summary = "删除文档")
    @PreAuthorize("hasAuthority('data:document:delete')")
    public Mono<ServerResponse> delete(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return documentService.delete(id).then(ServerResponse.ok().bodyValue(Result.success()));
    }

    /**
     * 处理查询文档详情请求。
     *
     * <p>先对指定文档累加浏览计数，再查询并返回文档详情。
     *
     * @param request HTTP 请求对象，路径参数包含文档 ID
     * @return 包含文档详情的统一成功响应
     */
    @Operation(summary = "查询文档详情")
    @PreAuthorize("hasAuthority('data:document:query')")
    public Mono<ServerResponse> getById(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return documentService
                .incrementViewCount(id)
                .then(documentService.getById(id))
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    /**
     * 处理分页查询文档列表请求。
     *
     * <p>从查询参数构建过滤条件（关键字、文档类型、分类、状态、页码、每页数量），
     * 调用应用服务分页查询文档列表。
     *
     * @param request HTTP 请求对象，查询参数包含 keyword、docType、category、status、page、size
     * @return 包含文档分页结果的统一成功响应
     */
    @Operation(summary = "分页查询文档列表")
    @PreAuthorize("hasAuthority('data:document:query')")
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
     * 处理更新文档状态请求。
     *
     * <p>解析请求体中的目标状态，并更新指定文档的状态。
     *
     * @param request HTTP 请求对象，路径参数包含文档 ID，请求体包含状态值
     * @return 更新成功的统一成功响应
     */
    @Operation(summary = "更新文档状态")
    @PreAuthorize("hasAuthority('data:document:update')")
    public Mono<ServerResponse> updateStatus(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return request.bodyToMono(UpdateStatusRequest.class)
                .flatMap(req -> documentService.updateStatus(id, req.getStatus()))
                .then(ServerResponse.ok().bodyValue(Result.success()));
    }

    /**
     * 处理下载文档请求。
     *
     * <p>对指定文档累加下载计数并返回附件下载地址；文档没有附件时返回 400 错误响应。
     *
     * @param request HTTP 请求对象，路径参数包含文档 ID
     * @return 包含附件下载地址的统一成功响应，无附件时返回 400 错误响应
     */
    @Operation(summary = "下载文档")
    @PreAuthorize("hasAuthority('data:document:download')")
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

    @Data
    private static class UpdateStatusRequest {
        private Integer status;
    }
}
