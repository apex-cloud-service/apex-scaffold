package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.ConfigEntity;
import com.apex.scaffold.domain.repository.ConfigRepository;
import com.apex.scaffold.infrastructure.persistence.converter.ConfigPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.ConfigMapper;
import com.apex.scaffold.infrastructure.persistence.po.ConfigPO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link ConfigRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link ConfigEntity} 的持久化读写与键名唯一性校验，通过
 * {@link ConfigPoConverter} 在领域实体与 {@link ConfigPO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池执行。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ConfigRepository
 */
@Repository
@RequiredArgsConstructor
public class ConfigRepositoryImpl implements ConfigRepository {

    /**
     * 参数配置 Mapper。
     */
    private final ConfigMapper configMapper;

    /**
     * 参数配置实体与持久化对象转换器。
     */
    private final ConfigPoConverter configPoConverter;

    /**
     * 根据 ID 查询参数配置。
     *
     * @param id 参数配置 ID
     * @return 参数配置实体，未找到时返回 {@link Mono#empty()}
     */
    @Override
    public Mono<ConfigEntity> findById(Long id) {
        return Mono.fromCallable(() -> configMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(configPoConverter.toEntity(po)));
    }

    /**
     * 根据键名查询参数配置。
     *
     * @param configKey 参数键名
     * @return 参数配置实体，未找到时返回 {@link Mono#empty()}
     */
    @Override
    public Mono<ConfigEntity> findByKey(String configKey) {
        return Mono.fromCallable(() -> configMapper.selectOne(
                        ConditionUtil.lambda(ConfigPO.class).eq(ConfigPO::getConfigKey, configKey)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(configPoConverter::toEntity);
    }

    /**
     * 查询全部参数配置。
     *
     * @return 参数配置实体列表
     */
    @Override
    public Mono<List<ConfigEntity>> findAll() {
        return Mono.fromCallable(() -> configMapper.selectList(
                        ConditionUtil.lambda(ConfigPO.class).orderByAsc(ConfigPO::getConfigKey)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(configPoConverter::toEntityList);
    }

    /**
     * 检查参数键名是否已存在。
     *
     * @param configKey 参数键名
     * @param excludeId 需要排除的参数 ID（更新场景），可为 {@code null}
     * @return true-已存在，false-不存在
     */
    @Override
    public Mono<Boolean> existsByKey(String configKey, Long excludeId) {
        return Mono.fromCallable(() -> {
                    Long count = configMapper.selectCount(ConditionUtil.lambda(ConfigPO.class)
                            .eqIfPresent(ConfigPO::getConfigKey, configKey)
                            .neIfPresent(ConfigPO::getId, excludeId));
                    return count != null && count > 0;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 新增参数配置。
     *
     * @param entity 参数配置实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> insert(ConfigEntity entity) {
        return Mono.fromCallable(() -> configMapper.insert(configPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 更新参数配置。
     *
     * @param entity 参数配置实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> updateById(ConfigEntity entity) {
        return Mono.fromCallable(() -> configMapper.updateById(configPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 删除参数配置。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。</p>
     *
     * @param id 参数配置 ID
     * @return 影响行数
     */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> configMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
