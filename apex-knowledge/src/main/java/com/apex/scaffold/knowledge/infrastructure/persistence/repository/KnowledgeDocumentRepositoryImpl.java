package com.apex.scaffold.knowledge.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentRepository;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocument;
import com.apex.scaffold.knowledge.infrastructure.persistence.mapper.KnowledgeDocumentMapper;
import com.apex.scaffold.knowledge.infrastructure.persistence.po.KnowledgeDocumentPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 知识文档仓储实现：MyBatis-Plus 访问统一隔离到 jdbc 调度器。
 *
 * <p>状态机字符串与领域枚举在实现层互转，调用方始终面向领域模型。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeDocumentRepositoryImpl implements KnowledgeDocumentRepository {

    private final KnowledgeDocumentMapper mapper;

    @Override
    public Mono<KnowledgeDocument> insert(KnowledgeDocument document) {
        return Mono.fromCallable(() -> {
                    KnowledgeDocumentPO po = KnowledgeDocumentPO.builder()
                            .spaceId(document.getSpaceId())
                            .title(document.getTitle())
                            .sourceType(document.getSourceType().name())
                            .contentType(document.getContentType())
                            .objectKey(document.getObjectKey())
                            .fileSize(document.getFileSize())
                            .contentText(document.getContentText())
                            .parseStatus(document.getParseStatus().name())
                            .parseError(document.getParseError())
                            .chunkCount(document.getChunkCount())
                            .indexStatus(document.getIndexStatus().name())
                            .tenantId(TenantContext.currentTenantId().orElse(null))
                            .build();
                    mapper.insert(po);
                    return toModel(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<KnowledgeDocument> update(KnowledgeDocument document) {
        return Mono.fromCallable(() -> {
                    KnowledgeDocumentPO po = mapper.selectById(document.getId());
                    if (po == null) {
                        throw new IllegalStateException("知识文档不存在: " + document.getId());
                    }
                    po.setTitle(document.getTitle());
                    po.setContentText(document.getContentText());
                    po.setParseStatus(document.getParseStatus().name());
                    po.setParseError(document.getParseError());
                    po.setChunkCount(document.getChunkCount());
                    po.setIndexStatus(document.getIndexStatus().name());
                    mapper.updateById(po);
                    return toModel(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<KnowledgeDocument> findById(Long id) {
        return Mono.fromCallable(() -> {
                    KnowledgeDocumentPO po = mapper.selectById(id);
                    return po == null ? null : toModel(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<PageResponse<KnowledgeDocument>> findPage(
            Long spaceId, String parseStatus, String keyword, int pageNum, int pageSize) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<KnowledgeDocumentPO> wrapper = new LambdaQueryWrapper<KnowledgeDocumentPO>()
                            .eq(spaceId != null, KnowledgeDocumentPO::getSpaceId, spaceId)
                            .eq(
                                    parseStatus != null && !parseStatus.isBlank(),
                                    KnowledgeDocumentPO::getParseStatus,
                                    parseStatus)
                            .like(keyword != null && !keyword.isBlank(), KnowledgeDocumentPO::getTitle, keyword)
                            .orderByDesc(KnowledgeDocumentPO::getId);
                    Page<KnowledgeDocumentPO> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
                    List<KnowledgeDocument> items =
                            page.getRecords().stream().map(this::toModel).toList();
                    return PageResponse.of(items, page.getTotal(), pageNum, pageSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<List<Long>> findIdsByStatusAndUpdatedBefore(
            List<String> statuses, LocalDateTime updatedBefore, int limit) {
        return Mono.fromCallable(() -> mapper
                        .selectList(new LambdaQueryWrapper<KnowledgeDocumentPO>()
                                .select(KnowledgeDocumentPO::getId)
                                .in(KnowledgeDocumentPO::getParseStatus, statuses)
                                .le(KnowledgeDocumentPO::getUpdateTime, updatedBefore)
                                .last("LIMIT " + Math.max(1, limit)))
                        .stream()
                        .map(KnowledgeDocumentPO::getId)
                        .toList())
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<List<Long>> findIdsByIndexStatusAndUpdatedBefore(
            List<String> statuses, LocalDateTime updatedBefore, int limit) {
        return Mono.fromCallable(() -> mapper
                        .selectList(new LambdaQueryWrapper<KnowledgeDocumentPO>()
                                .select(KnowledgeDocumentPO::getId)
                                .in(KnowledgeDocumentPO::getIndexStatus, statuses)
                                .le(KnowledgeDocumentPO::getUpdateTime, updatedBefore)
                                .last("LIMIT " + Math.max(1, limit)))
                        .stream()
                        .map(KnowledgeDocumentPO::getId)
                        .toList())
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Boolean> deleteById(Long id) {
        return Mono.fromCallable(() -> mapper.deleteById(id) > 0).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    private KnowledgeDocument toModel(KnowledgeDocumentPO po) {
        return KnowledgeDocument.builder()
                .id(po.getId())
                .spaceId(po.getSpaceId())
                .title(po.getTitle())
                .sourceType(KnowledgeDocument.SourceType.valueOf(po.getSourceType()))
                .contentType(po.getContentType())
                .objectKey(po.getObjectKey())
                .fileSize(po.getFileSize())
                .contentText(po.getContentText())
                .parseStatus(KnowledgeDocument.ParseStatus.valueOf(po.getParseStatus()))
                .parseError(po.getParseError())
                .chunkCount(po.getChunkCount())
                .indexStatus(KnowledgeDocument.IndexStatus.valueOf(po.getIndexStatus()))
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }
}
