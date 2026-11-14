package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.ConfigEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 参数配置仓储接口。
 *
 * <p>定义参数配置的持久化读写操作，由基础设施层提供 MyBatis-Plus 实现。
 * 参数键名全局唯一，仓储提供按键查询与存在性校验能力。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface ConfigRepository {

    /**
     * 根据 ID 查询参数配置。
     *
     * @param id 参数配置 ID
     * @return 参数配置实体，未找到时返回 {@link Mono#empty()}
     */
    Mono<ConfigEntity> findById(Long id);

    /**
     * 根据键名查询参数配置。
     *
     * @param configKey 参数键名
     * @return 参数配置实体，未找到时返回 {@link Mono#empty()}
     */
    Mono<ConfigEntity> findByKey(String configKey);

    /**
     * 查询全部参数配置。
     *
     * @return 参数配置实体列表
     */
    Mono<List<ConfigEntity>> findAll();

    /**
     * 检查参数键名是否已存在。
     *
     * @param configKey 参数键名
     * @param excludeId 需要排除的参数 ID（更新场景），可为 {@code null}
     * @return true-已存在，false-不存在
     */
    Mono<Boolean> existsByKey(String configKey, Long excludeId);

    /**
     * 新增参数配置。
     *
     * @param entity 参数配置实体
     * @return 影响行数
     */
    Mono<Integer> insert(ConfigEntity entity);

    /**
     * 更新参数配置。
     *
     * @param entity 参数配置实体
     * @return 影响行数
     */
    Mono<Integer> updateById(ConfigEntity entity);

    /**
     * 删除参数配置。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。</p>
     *
     * @param id 参数配置 ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
