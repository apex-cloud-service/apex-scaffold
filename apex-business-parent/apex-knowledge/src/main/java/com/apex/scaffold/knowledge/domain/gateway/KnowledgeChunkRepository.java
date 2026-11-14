package com.apex.scaffold.knowledge.domain.gateway;

import com.apex.scaffold.knowledge.domain.model.KnowledgeChunk;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 知识分块仓储契约。
 *
 * @author Apex Team
 * @since 1.3.2
 */
public interface KnowledgeChunkRepository {

    /**
     * 全量替换文档分块：先删除旧分块再批量写入（摄取重跑幂等）。
     *
     * @param documentId 文档 ID
     * @param chunks 新分块列表
     * @return 写入数量
     */
    Mono<Integer> replaceByDocument(Long documentId, List<KnowledgeChunk> chunks);

    /**
     * 删除文档全部分块（文档删除时调用）。
     *
     * @param documentId 文档 ID
     * @return 是否有分块被删除
     */
    Mono<Boolean> deleteByDocument(Long documentId);

    /**
     * 查询文档全部分块（预览与索引重建）。
     *
     * @param documentId 文档 ID
     * @return 分块列表（按 seq 升序）
     */
    Mono<List<KnowledgeChunk>> findByDocument(Long documentId);

    /**
     * 批量按 ID 查询分块。
     *
     * @param ids 分块 ID 列表
     * @return 分块列表
     */
    Mono<List<KnowledgeChunk>> findByIds(List<Long> ids);

    /**
     * 查询文档分块数量。
     *
     * @param documentId 文档 ID
     * @return 数量
     */
    Mono<Long> countByDocument(Long documentId);
}
