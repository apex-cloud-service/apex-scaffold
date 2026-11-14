package com.apex.scaffold.knowledge.domain.gateway;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.knowledge.domain.model.KnowledgeSpace;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 知识空间仓储契约。
 *
 * <p>租户作用域由持久化层租户拦截器注入；跨租户访问在实现层二次校验（fail-closed）。
 *
 * @author Apex Team
 * @since 1.3.2
 */
public interface KnowledgeSpaceRepository {

    /**
     * 新增空间。
     *
     * @param space 空间模型（含租户）
     * @return 已写入模型（含生成 ID）
     */
    Mono<KnowledgeSpace> insert(KnowledgeSpace space);

    /**
     * 更新空间。
     *
     * @param space 空间模型（含乐观锁版本前置值由实现方维护）
     * @return 更新后的模型
     */
    Mono<KnowledgeSpace> update(KnowledgeSpace space);

    /**
     * 按 ID 查询空间。
     *
     * @param id 空间 ID
     * @return 空间；不存在或跨租户时为空
     */
    Mono<KnowledgeSpace> findById(Long id);

    /**
     * 分页查询空间。
     *
     * @param keyword 名称模糊匹配（可空）
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 页大小
     * @return 空间分页
     */
    Mono<PageResponse<KnowledgeSpace>> findPage(String keyword, int pageNum, int pageSize);

    /**
     * 查询租户内全部正常空间（检索可用范围）。
     *
     * @return 空间列表
     */
    Mono<List<KnowledgeSpace>> findAllActive();

    /**
     * 按 ID 删除空间（逻辑删除）。
     *
     * @param id 空间 ID
     * @return 是否删除成功
     */
    Mono<Boolean> deleteById(Long id);

    /**
     * 空间名称在租户内是否已存在。
     *
     * @param name 空间名称
     * @param excludeId 排除的空间 ID（更新场景，可为空）
     * @return true 表示已存在
     */
    Mono<Boolean> existsByName(String name, Long excludeId);
}
