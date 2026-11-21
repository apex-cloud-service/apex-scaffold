package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.DictDataEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 字典数据仓储接口。
 *
 * <p>定义字典数据的持久化读写操作，由基础设施层提供 MyBatis-Plus 实现。
 * 字典数据按 {@code dictType} 编码归属字典类型，支持按类型批量读取。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface DictDataRepository {

    /**
     * 根据 ID 查询字典数据。
     *
     * @param id 字典数据 ID
     * @return 字典数据实体，未找到时返回 {@link Mono#empty()}
     */
    Mono<DictDataEntity> findById(Long id);

    /**
     * 根据字典类型编码查询全部数据项。
     *
     * <p>管理列表包含启用和停用项，按字典排序升序排列。</p>
     *
     * @param dictType 字典类型编码
     * @return 字典数据实体列表
     */
    Mono<List<DictDataEntity>> findByType(String dictType);

    /**
     * 检查字典类型下是否存在数据项。
     *
     * <p>用于删除字典类型前的存在性校验。</p>
     *
     * @param dictType 字典类型编码
     * @return true-存在数据项，false-不存在
     */
    Mono<Boolean> existsByType(String dictType);

    /**
     * 新增字典数据。
     *
     * @param entity 字典数据实体
     * @return 影响行数
     */
    Mono<Integer> insert(DictDataEntity entity);

    /**
     * 更新字典数据。
     *
     * @param entity 字典数据实体
     * @return 影响行数
     */
    Mono<Integer> updateById(DictDataEntity entity);

    /**
     * 删除字典数据。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。</p>
     *
     * @param id 字典数据 ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);

    /**
     * 将指定类型下的全部数据项取消默认标记。
     *
     * <p>用于设置新的默认项前清空旧默认项，保证同一类型内默认项唯一。</p>
     *
     * @param dictType 字典类型编码
     * @return 影响行数
     */
    Mono<Integer> clearDefaultByType(String dictType);
}
