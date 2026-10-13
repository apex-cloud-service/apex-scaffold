package com.apex.scaffold.file.application.service.impl;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.file.application.assembler.DocumentAssembler;
import com.apex.scaffold.file.application.dto.PageResponse;
import com.apex.scaffold.file.application.dto.data.DocumentQueryRequest;
import com.apex.scaffold.file.application.dto.data.DocumentRequest;
import com.apex.scaffold.file.application.dto.data.DocumentResponse;
import com.apex.scaffold.file.application.service.DocumentService;
import com.apex.scaffold.file.domain.model.entity.DocumentEntity;
import com.apex.scaffold.file.domain.repository.DocumentRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 文档应用服务实现。
 *
 * <p>提供文档的创建、更新、删除、查询与计数管理能力。文档支持富文本内容、
 * 文件附件、标签分类与浏览/下载统计。所有操作强制租户隔离。</p>
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>文档 CRUD：创建、更新、删除、按 ID 查询</li>
 *   <li>分页查询：支持关键词、类型、分类、状态过滤</li>
 *   <li>状态管理：启用/停用文档</li>
 *   <li>统计计数：浏览量与下载量增量更新</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentAssembler documentAssembler;

    /**
     * 创建文档
     *
     * <p>文档归属当前租户（取自 {@link TenantContext}），创建人固定为 system，
     * 可选附件、标签与初始状态。
     *
     * @param request 文档创建请求（含标题、文档类型、分类、内容、附件、标签与状态）
     * @return 新建文档的响应
     */
    @Override
    public Mono<DocumentResponse> create(DocumentRequest request) {
        return Mono.justOrEmpty(TenantContext.currentTenantId())
                .map(tenantId -> DocumentEntity.create(
                        request.getTitle(),
                        request.getDocType(),
                        request.getCategory(),
                        request.getContent(),
                        1L,
                        "system",
                        tenantId))
                .doOnNext(entity -> {
                    if (request.getFileUrl() != null && !request.getFileUrl().isBlank()) {
                        entity.attachFile(request.getFileUrl(), request.getFileSize());
                    }
                    if (request.getTags() != null) {
                        entity.setTags(request.getTags());
                    }
                    if (request.getStatus() != null) {
                        entity.setStatus(request.getStatus());
                    }
                })
                .flatMap(documentRepository::save)
                .map(documentAssembler::toResponse);
    }

    /**
     * 更新文档信息
     *
     * <p>校验文档归属当前租户后方可更新，请求中非空字段生效。
     *
     * @param id 文档 ID
     * @param request 文档更新请求
     * @return 更新后的文档响应；文档不存在或无租户权限时 Mono 以 BusinessException 错误终止
     */
    @Override
    public Mono<DocumentResponse> update(Long id, DocumentRequest request) {
        return Mono.justOrEmpty(TenantContext.currentTenantId()).flatMap(tenantId -> documentRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("文档不存在")))
                .flatMap(entity -> {
                    if (!entity.getTenantId().equals(tenantId)) {
                        return Mono.error(new BusinessException("无权限操作此文档"));
                    }
                    entity.updateInfo(
                            request.getTitle(),
                            request.getDocType(),
                            request.getCategory(),
                            request.getContent(),
                            request.getTags());
                    if (request.getFileUrl() != null) {
                        entity.attachFile(request.getFileUrl(), request.getFileSize());
                    }
                    if (request.getStatus() != null) {
                        entity.setStatus(request.getStatus());
                    }
                    return documentRepository.save(entity);
                })
                .map(documentAssembler::toResponse));
    }

    /**
     * 删除文档
     *
     * <p>按当前租户隔离删除指定文档。
     *
     * @param id 文档 ID
     * @return 完成信号
     */
    @Override
    public Mono<Void> delete(Long id) {
        return Mono.justOrEmpty(TenantContext.currentTenantId())
                .flatMap(tenantId -> documentRepository.deleteById(id, tenantId));
    }

    /**
     * 按 ID 查询文档详情
     *
     * @param id 文档 ID
     * @return 文档响应；文档不存在时 Mono 以 BusinessException 错误终止
     */
    @Override
    public Mono<DocumentResponse> getById(Long id) {
        return documentRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("文档不存在")))
                .map(documentAssembler::toResponse);
    }

    /**
     * 分页查询文档列表
     *
     * <p>在当前租户范围内按关键词、文档类型、分类、状态条件过滤后内存分页。
     *
     * @param request 文档查询请求（含关键词、类型、分类、状态与分页参数）
     * @return 分页结果
     */
    @Override
    public Mono<PageResponse<DocumentResponse>> query(DocumentQueryRequest request) {
        return Mono.justOrEmpty(TenantContext.currentTenantId()).flatMap(tenantId -> documentRepository
                .findAll(tenantId)
                .filter(entity -> matchesFilter(entity, request))
                .collectList()
                .map(list -> {
                    int start = (request.getPageNum() - 1) * request.getPageSize();
                    int end = Math.min(start + request.getPageSize(), list.size());
                    List<DocumentResponse> pageList = list.subList(start, end).stream()
                            .map(documentAssembler::toResponse)
                            .collect(Collectors.toList());
                    return PageResponse.of(pageList, (long) list.size(), request.getPageNum(), request.getPageSize());
                }));
    }

    /**
     * 更新文档状态
     *
     * <p>校验文档归属当前租户后方可更新状态。
     *
     * @param id 文档 ID
     * @param status 目标状态
     * @return 完成信号；文档不存在或无租户权限时 Mono 以 BusinessException 错误终止
     */
    @Override
    public Mono<Void> updateStatus(Long id, Integer status) {
        return Mono.justOrEmpty(TenantContext.currentTenantId()).flatMap(tenantId -> documentRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("文档不存在")))
                .flatMap(entity -> {
                    if (!entity.getTenantId().equals(tenantId)) {
                        return Mono.error(new BusinessException("无权限操作此文档"));
                    }
                    entity.setStatus(status);
                    return documentRepository.save(entity);
                })
                .then());
    }

    /**
     * 增加文档浏览计数
     *
     * <p>将指定文档的浏览计数加一并保存，文档不存在时直接完成。
     *
     * @param id 文档 ID
     * @return 完成信号
     */
    @Override
    public Mono<Void> incrementViewCount(Long id) {
        return documentRepository
                .findById(id)
                .flatMap(entity -> {
                    entity.incrementViewCount();
                    return documentRepository.save(entity);
                })
                .then();
    }

    /**
     * 增加文档下载计数
     *
     * <p>将指定文档的下载计数加一并保存，文档不存在时直接完成。
     *
     * @param id 文档 ID
     * @return 完成信号
     */
    @Override
    public Mono<Void> incrementDownloadCount(Long id) {
        return documentRepository
                .findById(id)
                .flatMap(entity -> {
                    entity.incrementDownloadCount();
                    return documentRepository.save(entity);
                })
                .then();
    }

    private boolean matchesFilter(DocumentEntity entity, DocumentQueryRequest request) {
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            String keyword = request.getKeyword().toLowerCase();
            boolean titleMatch =
                    entity.getTitle() != null && entity.getTitle().toLowerCase().contains(keyword);
            boolean contentMatch = entity.getContent() != null
                    && entity.getContent().toLowerCase().contains(keyword);
            if (!titleMatch && !contentMatch) {
                return false;
            }
        }
        if (request.getDocType() != null && !request.getDocType().equals(entity.getDocType())) {
            return false;
        }
        if (request.getCategory() != null && !request.getCategory().equals(entity.getCategory())) {
            return false;
        }
        if (request.getStatus() != null && !request.getStatus().equals(entity.getStatus())) {
            return false;
        }
        return true;
    }
}
