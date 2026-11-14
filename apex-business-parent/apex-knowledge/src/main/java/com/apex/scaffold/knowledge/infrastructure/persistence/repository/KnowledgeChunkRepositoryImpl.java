package com.apex.scaffold.knowledge.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
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
 * <p>{@code replaceByDocument} 的删除与写入两步在同一线程顺序执行；
 * 不在同一事务内提交，重摄取失败后旧分块已删除、文档状态标记为失败，
 * 由校准 Job 或手动重建兜底。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeChunkRepositoryImpl implements KnowledgeChunkRepository {

    private final KnowledgeChunkMapper mapper;

    @Override
    public Mono<Integer> replaceByDocument(Long documentId, List<KnowledgeChunk> chunks) {
        return Mono.fromCallable(() -> {
                    mapper.delete(
                            new LambdaQueryWrapper<KnowledgeChunkPO>().eq(KnowledgeChunkPO::getDocumentId, documentId));
                    if (chunks.isEmpty()) {
                        return 0;
                    }
                    String tenantId = TenantContext.currentTenantId().orElse(null);
                    int written = 0;
                    for (KnowledgeChunk chunk : chunks) {
                        KnowledgeChunkPO po = new KnowledgeChunkPO();
                        po.setDocumentId(documentId);
                        po.setSeq(chunk.getSeq());
                        po.setContent(chunk.getContent());
                        po.setContentHash(chunk.getContentHash());
                        po.setTokenEstimate(chunk.getTokenEstimate());
                        po.setTenantId(tenantId);
                        written += mapper.insert(po);
                    }
                    return written;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Boolean> deleteByDocument(Long documentId) {
        return Mono.fromCallable(() -> mapper.delete(new LambdaQueryWrapper<KnowledgeChunkPO>()
                                .eq(KnowledgeChunkPO::getDocumentId, documentId))
                        > 0)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<List<KnowledgeChunk>> findByDocument(Long documentId) {
        return Mono.fromCallable(() -> mapper
                        .selectList(new LambdaQueryWrapper<KnowledgeChunkPO>()
                                .eq(KnowledgeChunkPO::getDocumentId, documentId)
                                .orderByAsc(KnowledgeChunkPO::getSeq))
                        .stream()
                        .map(this::toModel)
                        .toList())
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<List<KnowledgeChunk>> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Mono.just(List.of());
        }
        return Mono.fromCallable(
                        () -> mapper
                                .selectList(new LambdaQueryWrapper<KnowledgeChunkPO>().in(KnowledgeChunkPO::getId, ids))
                                .stream()
                                .map(this::toModel)
                                .toList())
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Long> countByDocument(Long documentId) {
        return Mono.fromCallable(() -> mapper.selectCount(
                        new LambdaQueryWrapper<KnowledgeChunkPO>().eq(KnowledgeChunkPO::getDocumentId, documentId)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
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
