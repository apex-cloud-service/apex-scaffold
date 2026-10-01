package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.generator.GenDatasourceEntity;
import com.apex.scaffold.domain.repository.GenDatasourceRepository;
import com.apex.scaffold.infrastructure.persistence.converter.GenDatasourcePoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.GenDatasourceMapper;
import com.apex.scaffold.infrastructure.persistence.po.GenDatasourcePO;
import com.apex.scaffold.infrastructure.persistence.util.ConditionUtil;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link GenDatasourceRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责代码生成器数据源配置的持久化读写与租户隔离查询，通过
 * {@link GenDatasourcePoConverter} 在领域实体与 {@link GenDatasourcePO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池执行。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>数据源配置的增删改查</li>
 *   <li>按租户隔离查询数据源列表</li>
 *   <li>按名称和租户ID唯一性校验</li>
 *   <li>JDBC 阻塞操作隔离到专用调度器</li>
 * </ul>
 *
 * <h2>租户隔离保障</h2>
 * <ul>
 *   <li>所有查询自动过滤 {@code tenantId} 和 {@code deleted} 字段</li>
 *   <li>更新和删除操作依赖 MyBatis-Plus 租户拦截器校验</li>
 *   <li>名称唯一性校验限定在租户范围内</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenDatasourceRepository
 */
@Repository
@RequiredArgsConstructor
public class GenDatasourceRepositoryImpl implements GenDatasourceRepository {

    /**
     * 数据源配置 Mapper。
     */
    private final GenDatasourceMapper datasourceMapper;

    /**
     * 数据源配置实体与持久化对象转换器。
     */
    private final GenDatasourcePoConverter datasourcePoConverter;

    /**
     * 根据 ID 查询数据源配置。
     *
     * @param id 数据源 ID
     * @return 数据源实体，未找到时返回 {@link Mono#empty()}
     */
    @Override
    public Mono<GenDatasourceEntity> findById(Long id) {
        return Mono.fromCallable(() -> datasourceMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(datasourcePoConverter.toEntity(po)));
    }

    /**
     * 根据名称和租户ID查询数据源配置。
     *
     * <p>用于校验数据源名称在租户内的唯一性。
     *
     * @param name 数据源名称
     * @param tenantId 租户ID
     * @return 数据源实体，未找到时返回 {@code Optional.empty()}
     */
    @Override
    public Mono<Optional<GenDatasourceEntity>> findByNameAndTenantId(String name, String tenantId) {
        return Mono.fromCallable(() -> {
                    GenDatasourcePO po = datasourceMapper.selectOne(ConditionUtil.lambda(GenDatasourcePO.class)
                            .eq(GenDatasourcePO::getName, name)
                            .eq(GenDatasourcePO::getTenantId, tenantId));
                    return Optional.ofNullable(po).map(datasourcePoConverter::toEntity);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 根据租户ID查询数据源列表。
     *
     * <p>查询指定租户下的所有数据源配置，按创建时间倒序排列。
     *
     * @param tenantId 租户ID
     * @return 数据源实体列表
     */
    @Override
    public Mono<List<GenDatasourceEntity>> findByTenantId(String tenantId) {
        return Mono.fromCallable(() -> datasourceMapper.selectList(ConditionUtil.lambda(GenDatasourcePO.class)
                        .eq(GenDatasourcePO::getTenantId, tenantId)
                        .orderByDesc(GenDatasourcePO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(datasourcePoConverter::toEntityList);
    }

    /**
     * 新增数据源配置。
     *
     * @param entity 数据源实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> insert(GenDatasourceEntity entity) {
        return Mono.fromCallable(() -> datasourceMapper.insert(datasourcePoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 更新数据源配置。
     *
     * @param entity 数据源实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> updateById(GenDatasourceEntity entity) {
        return Mono.fromCallable(() -> datasourceMapper.updateById(datasourcePoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 删除数据源配置。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。</p>
     *
     * @param id 数据源 ID
     * @return 影响行数
     */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> datasourceMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
