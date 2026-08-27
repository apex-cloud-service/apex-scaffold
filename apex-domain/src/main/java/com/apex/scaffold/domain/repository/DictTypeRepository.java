package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.DictTypeEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 字典类型仓储接口。
 *
 * <p>定义字典类型的持久化读写操作，由基础设施层提供 MyBatis-Plus 实现。
 * 字典类型编码全局唯一，仓储提供按编码查询与存在性校验能力。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface DictTypeRepository {

    /**
     * 根据 ID 查询字典类型。
     *
     * @param id 字典类型 ID
     * @return 字典类型实体，未找到时返回 {@link Mono#empty()}
     */
    Mono<DictTypeEntity> findById(Long id);

    /**
     * 根据编码查询字典类型。
     *
     * @param dictType 字典类型编码
     * @return 字典类型实体，未找到时返回 {@link Mono#empty()}
     */
    Mono<DictTypeEntity> findByType(String dictType);

    /**
     * 查询全部字典类型。
     *
     * @return 字典类型实体列表
     */
    Mono<List<DictTypeEntity>> findAll();

    /**
     * 检查字典类型编码是否已存在。
     *
     * @param dictType 字典类型编码
     * @param excludeId 需要排除的字典类型 ID（更新场景），可为 {@code null}
     * @return true-已存在，false-不存在
     */
    Mono<Boolean> existsByType(String dictType, Long excludeId);

    /**
     * 新增字典类型。
     *
     * @param entity 字典类型实体
     * @return 影响行数
     */
    Mono<Integer> insert(DictTypeEntity entity);

    /**
     * 更新字典类型。
     *
     * @param entity 字典类型实体
     * @return 影响行数
     */
    Mono<Integer> updateById(DictTypeEntity entity);

    /**
     * 删除字典类型。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。</p>
     *
     * @param id 字典类型 ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
