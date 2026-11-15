package com.apex.scaffold.knowledge.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeChunkRepository;
import com.apex.scaffold.knowledge.domain.model.KnowledgeChunk;
import com.apex.scaffold.knowledge.infrastructure.persistence.mapper.KnowledgeChunkMapper;
import com.apex.scaffold.knowledge.infrastructure.persistence.po.KnowledgeChunkPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 知识分块仓储实现：MyBatis-Plus 访问统一隔离到 jdbc 调度器。
 *
 * <p>读取从可信租户上下文显式桥接到jdbc线程；写入必须通过生命周期端口在同事务锁定
 * work认领与未删除主档。旧无认领写方法显式拒绝，防止绕过fencing规则。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeChunkRepositoryImpl implements KnowledgeChunkRepository {

    private final KnowledgeChunkMapper mapper;

    private <T> Mono<T> scoped(java.util.function.Supplier<T> action) {
        return TenantContext.currentTenantIdReactive()
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "可信租户上下文缺失")))
                .flatMap(tenant -> Mono.fromCallable(() -> TenantContext.callWithTenant(tenant, action))
                        .subscribeOn(BlockingIoSchedulers.jdbc()));
    }

    @Override
    public Mono<Integer> replaceByDocument(Long documentId, List<KnowledgeChunk> chunks) {
        return Mono.error(new IllegalStateException("分块替换必须持有生命周期认领凭证"));
    }

    @Override
    public Mono<Boolean> deleteByDocument(Long documentId) {
        return Mono.error(new IllegalStateException("分块删除必须持有生命周期清理认领凭证"));
    }

    @Override
    public Mono<List<KnowledgeChunk>> findByDocument(Long documentId) {
        return scoped(() -> mapper
                .selectList(new LambdaQueryWrapper<KnowledgeChunkPO>()
                        .eq(
                                KnowledgeChunkPO::getTenantId,
                                TenantContext.currentTenantId().orElseThrow())
                        .eq(KnowledgeChunkPO::getDocumentId, documentId)
                        .orderByAsc(KnowledgeChunkPO::getSeq))
                .stream()
                .map(this::toModel)
                .toList());
    }

    @Override
    public Mono<List<KnowledgeChunk>> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Mono.just(List.of());
        }
        return scoped(() -> mapper
                .selectList(new LambdaQueryWrapper<KnowledgeChunkPO>()
                        .eq(
                                KnowledgeChunkPO::getTenantId,
                                TenantContext.currentTenantId().orElseThrow())
                        .in(KnowledgeChunkPO::getId, ids))
                .stream()
                .map(this::toModel)
                .toList());
    }

    @Override
    public Mono<Long> countByDocument(Long documentId) {
        return scoped(() -> mapper.selectCount(new LambdaQueryWrapper<KnowledgeChunkPO>()
                .eq(
                        KnowledgeChunkPO::getTenantId,
                        TenantContext.currentTenantId().orElseThrow())
                .eq(KnowledgeChunkPO::getDocumentId, documentId)));
    }

    private KnowledgeChunk toModel(KnowledgeChunkPO po) {
        return KnowledgeChunk.builder()
                .id(po.getId())
                .documentId(po.getDocumentId())
                .seq(po.getSeq())
                .content(po.getContent())
                .contentHash(po.getContentHash())
                .tokenEstimate(po.getTokenEstimate())
                .createTime(po.getCreateTime())
                .build();
    }
}
