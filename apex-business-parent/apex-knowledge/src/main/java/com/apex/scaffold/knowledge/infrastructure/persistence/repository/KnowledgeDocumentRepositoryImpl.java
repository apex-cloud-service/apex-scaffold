package com.apex.scaffold.knowledge.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentRepository;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocument;
import com.apex.scaffold.knowledge.infrastructure.persistence.mapper.KnowledgeDocumentMapper;
import com.apex.scaffold.knowledge.infrastructure.persistence.po.KnowledgeDocumentPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final DataSource dataSource;

    private <T> Mono<T> scoped(java.util.function.Supplier<T> action) {
        return TenantContext.currentTenantIdReactive()
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "可信租户上下文缺失")))
                .flatMap(tenant -> Mono.fromCallable(() -> TenantContext.callWithTenant(tenant, action))
                        .subscribeOn(BlockingIoSchedulers.jdbc()));
    }

    @Override
    public Mono<KnowledgeDocument> insert(KnowledgeDocument document) {
        return scoped(() -> new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(status -> {
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
            if (mapper.insert(po) != 1) {
                throw new IllegalStateException("文档登记失败");
            }
            if (new JdbcTemplate(dataSource)
                            .update(
                                    "INSERT INTO kb_document_work (id,tenant_id,document_id) VALUES (?,?,?)",
                                    IdWorker.getId(),
                                    po.getTenantId(),
                                    po.getId())
                    != 1) {
                throw new IllegalStateException("文档工作登记失败");
            }
            return toModel(po);
        }));
    }

    @Override
    public Mono<KnowledgeDocument> update(KnowledgeDocument document) {
        return Mono.error(new IllegalStateException("文档更新必须持有生命周期认领凭证"));
    }

    @Override
    public Mono<KnowledgeDocument> findById(Long id) {
        return scoped(() -> {
            KnowledgeDocumentPO po = mapper.selectOne(new LambdaQueryWrapper<KnowledgeDocumentPO>()
                    .eq(KnowledgeDocumentPO::getId, id)
                    .eq(
                            KnowledgeDocumentPO::getTenantId,
                            TenantContext.currentTenantId().orElseThrow()));
            return po == null ? null : toModel(po);
        });
    }

    @Override
    public Mono<PageResponse<KnowledgeDocument>> findPage(
            Long spaceId, String parseStatus, String keyword, int pageNum, int pageSize) {
        return scoped(() -> {
            LambdaQueryWrapper<KnowledgeDocumentPO> wrapper = new LambdaQueryWrapper<KnowledgeDocumentPO>()
                    .eq(
                            KnowledgeDocumentPO::getTenantId,
                            TenantContext.currentTenantId().orElseThrow())
                    .eq(spaceId != null, KnowledgeDocumentPO::getSpaceId, spaceId)
                    .eq(parseStatus != null && !parseStatus.isBlank(), KnowledgeDocumentPO::getParseStatus, parseStatus)
                    .like(keyword != null && !keyword.isBlank(), KnowledgeDocumentPO::getTitle, keyword)
                    .orderByDesc(KnowledgeDocumentPO::getId);
            Page<KnowledgeDocumentPO> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
            List<KnowledgeDocument> items =
                    page.getRecords().stream().map(this::toModel).toList();
            return PageResponse.of(items, page.getTotal(), pageNum, pageSize);
        });
    }

    @Override
    public Mono<List<Long>> findIdsByStatusAndUpdatedBefore(
            List<String> statuses, LocalDateTime updatedBefore, int limit) {
        return scoped(() -> mapper
                .selectList(new LambdaQueryWrapper<KnowledgeDocumentPO>()
                        .eq(
                                KnowledgeDocumentPO::getTenantId,
                                TenantContext.currentTenantId().orElseThrow())
                        .select(KnowledgeDocumentPO::getId)
                        .in(KnowledgeDocumentPO::getParseStatus, statuses)
                        .le(KnowledgeDocumentPO::getUpdateTime, updatedBefore)
                        .last("LIMIT " + Math.max(1, limit)))
                .stream()
                .map(KnowledgeDocumentPO::getId)
                .toList());
    }

    @Override
    public Mono<List<Long>> findIdsByIndexStatusAndUpdatedBefore(
            List<String> statuses, LocalDateTime updatedBefore, int limit) {
        return scoped(() -> mapper
                .selectList(new LambdaQueryWrapper<KnowledgeDocumentPO>()
                        .eq(
                                KnowledgeDocumentPO::getTenantId,
                                TenantContext.currentTenantId().orElseThrow())
                        .select(KnowledgeDocumentPO::getId)
                        .in(KnowledgeDocumentPO::getIndexStatus, statuses)
                        .le(KnowledgeDocumentPO::getUpdateTime, updatedBefore)
                        .last("LIMIT " + Math.max(1, limit)))
                .stream()
                .map(KnowledgeDocumentPO::getId)
                .toList());
    }

    @Override
    public Mono<Boolean> deleteById(Long id) {
        return Mono.error(new IllegalStateException("文档删除必须原子登记持久清理意图"));
    }

    private KnowledgeDocument toModel(KnowledgeDocumentPO po) {
        return KnowledgeDocument.builder()
                .id(po.getId())
                .spaceId(po.getSpaceId())
                .title(po.getTitle())
                .sourceType(KnowledgeDocument.SourceType.valueOf(po.getSourceType()))
                .contentType(po.getContentType())
                .objectKey(po.getObjectKey())
                .storageProvider(po.getStorageProvider())
                .storageBucket(po.getStorageBucket())
                .locationSnapshot(Integer.valueOf(1).equals(po.getObjectOwned()))
                .indexEndpoint(po.getIndexEndpoint())
                .indexName(po.getIndexName())
                .indexRequired(Integer.valueOf(1).equals(po.getIndexRequired()))
                .deleted(Integer.valueOf(1).equals(po.getDeleted()))
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
