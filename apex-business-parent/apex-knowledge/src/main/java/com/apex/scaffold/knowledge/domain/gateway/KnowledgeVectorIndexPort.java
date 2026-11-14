package com.apex.scaffold.knowledge.domain.gateway;

import com.apex.scaffold.knowledge.domain.model.IndexedChunk;
import com.apex.scaffold.knowledge.domain.model.VectorSearchQuery;
import com.apex.scaffold.knowledge.domain.model.VectorSearchResult;
import java.time.LocalDateTime;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 知识向量索引端口：面向 Meilisearch 的知识分块索引读写。
 *
 * <p>实现方负责租户过滤字段写入与强制过滤；未启用索引引擎时由降级装配提供
 * no-op 实现，语义为「功能未启用」而非静默吞错。
 *
 * @author Apex Team
 * @since 1.3.2
 */
public interface KnowledgeVectorIndexPort {

    /**
     * 确保索引存在且设置正确（幂等）。在启用 embedding 时同步配置 embedder。
     *
     * @param embeddingEnabled 是否启用向量检索
     * @param dimensions 向量维度（embeddingEnabled=true 时必须有效）
     * @return 完成信号
     */
    Mono<Void> ensureIndex(boolean embeddingEnabled, int dimensions);

    /**
     * 批量写入分块索引文档（带租户过滤字段；向量可为空表示仅词法）。
     *
     * @param chunks 待写入分块
     * @return 完成信号
     */
    Mono<Void> upsertChunks(List<IndexedChunk> chunks);

    /**
     * 按文档删除其全部分块索引文档（租户强制参与过滤）。
     *
     * @param tenantId 租户 ID
     * @param documentId 文档 ID
     * @return 完成信号
     */
    Mono<Void> deleteDocumentChunks(String tenantId, Long documentId);

    /**
     * 混合检索：携带查询向量时走 hybrid，否则词法；租户过滤强制生效。
     *
     * @param query 检索条件
     * @return 检索结果（含是否实际启用向量语义的标记）
     */
    Mono<VectorSearchResult> search(VectorSearchQuery query);

    /**
     * 按更新时间增量重建（校准 Job 使用）：拉取指定更新时间之后的分块重写索引。
     *
     * @param since 更新时间下限（含）
     * @param chunkLoader 分块加载函数（按 ID 批量取 IndexedChunk）
     * @param ids 需要重写的分块 ID 列表
     * @return 成功写入数量
     */
    Mono<Long> rebuild(List<Long> ids, java.util.function.Function<List<Long>, Mono<List<IndexedChunk>>> chunkLoader);

    /**
     * 校准扫描辅助：查询最近更新时间戳（索引侧无状态，固定返回 null 由调用方以 DB 时间驱动）。
     *
     * @return 无状态实现恒返回 null
     */
    default LocalDateTime lastSyncMarker() {
        return null;
    }
}
