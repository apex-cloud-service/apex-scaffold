package com.apex.scaffold.application.service.impl;

import com.apex.scaffold.application.assembler.DocumentAssembler;
import com.apex.scaffold.application.dto.PageResponse;
import com.apex.scaffold.application.dto.data.DocumentQueryRequest;
import com.apex.scaffold.application.dto.data.DocumentRequest;
import com.apex.scaffold.application.dto.data.DocumentResponse;
import com.apex.scaffold.application.service.DocumentService;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.entity.DocumentEntity;
import com.apex.scaffold.domain.repository.DocumentRepository;
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

    @Override
    public Mono<Void> delete(Long id) {
        return Mono.justOrEmpty(TenantContext.currentTenantId())
                .flatMap(tenantId -> documentRepository.deleteById(id, tenantId));
    }

    @Override
    public Mono<DocumentResponse> getById(Long id) {
        return documentRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("文档不存在")))
                .map(documentAssembler::toResponse);
    }

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
