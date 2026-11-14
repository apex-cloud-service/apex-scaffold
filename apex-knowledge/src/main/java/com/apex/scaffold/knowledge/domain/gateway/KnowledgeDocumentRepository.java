package com.apex.scaffold.knowledge.domain.gateway;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocument;
import java.time.LocalDateTime;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 知识文档仓储契约。
 *
 * @author Apex Team
 * @since 1.3.2
 */
public interface KnowledgeDocumentRepository {

    /**
     * 新增文档主档。
     *
     * @param document 文档模型
     * @return 已写入模型（含生成 ID）
     */
    Mono<KnowledgeDocument> insert(KnowledgeDocument document);

    /**
     * 更新文档主档（含状态机字段）。
     *
     * @param document 文档模型
     * @return 更新后的模型
     */
    Mono<KnowledgeDocument> update(KnowledgeDocument document);

    /**
     * 按 ID 查询文档。
     *
     * @param id 文档 ID
     * @return 文档；不存在或跨租户时为空
     */
    Mono<KnowledgeDocument> findById(Long id);

    /**
     * 分页查询文档。
     *
     * @param spaceId 空间过滤（可空）
     * @param parseStatus 解析状态过滤（可空）
     * @param keyword 标题模糊匹配（可空）
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 文档分页
     */
    Mono<PageResponse<KnowledgeDocument>> findPage(
            Long spaceId, String parseStatus, String keyword, int pageNum, int pageSize);

    /**
     * 查询解析状态命中且更新时间早于阈值的文档 ID（校准 Job 重试扫描）。
     *
     * @param statuses 关注解析状态集合
     * @param updatedBefore 更新时间上限（含）
     * @param limit 最大返回条数
     * @return 文档 ID 列表
     */
    Mono<List<Long>> findIdsByStatusAndUpdatedBefore(List<String> statuses, LocalDateTime updatedBefore, int limit);

    /**
     * 查询索引状态命中且更新时间早于阈值的文档 ID（校准 Job 重试扫描）。
     *
     * @param statuses 关注索引状态集合
     * @param updatedBefore 更新时间上限（含）
     * @param limit 最大返回条数
     * @return 文档 ID 列表
     */
    Mono<List<Long>> findIdsByIndexStatusAndUpdatedBefore(
            List<String> statuses, LocalDateTime updatedBefore, int limit);

    /**
     * 按 ID 删除文档（逻辑删除）。
     *
     * @param id 文档 ID
     * @return 是否删除成功
     */
    Mono<Boolean> deleteById(Long id);
}
