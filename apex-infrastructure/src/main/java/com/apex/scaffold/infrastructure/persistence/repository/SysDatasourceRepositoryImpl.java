package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.system.SysDatasource;
import com.apex.scaffold.domain.repository.SysDatasourceRepository;
import com.apex.scaffold.infrastructure.persistence.converter.SysDatasourcePoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.SysDatasourceMapper;
import com.apex.scaffold.infrastructure.persistence.po.SysDatasourcePO;
import com.apex.scaffold.infrastructure.persistence.util.ConditionUtil;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link SysDatasourceRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责系统数据源配置的持久化读写与租户隔离查询，通过
 * {@link SysDatasourcePoConverter} 在领域实体与 {@link SysDatasourcePO} 之间转换，
 * 所有阻塞 JDBC 操作调度到 {@code BlockingIoSchedulers.jdbc()} 专用线程池执行。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>数据源配置的增删改查（逻辑删除 + 乐观锁）</li>
 *   <li>按租户隔离查询数据源列表</li>
 *   <li>数据源名称在租户内的唯一性校验查询</li>
 *   <li>插入后回填雪花算法生成的主键 ID</li>
 * </ul>
 *
 * <h2>租户隔离保障</h2>
 * <ul>
 *   <li>所有查询显式携带 {@code tenantId} 条件</li>
 *   <li>逻辑删除记录由 MyBatis-Plus 插件自动过滤</li>
 *   <li>名称唯一性校验限定在租户范围内</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SysDatasourceRepository
 */
@Repository
@RequiredArgsConstructor
public class SysDatasourceRepositoryImpl implements SysDatasourceRepository {

    /**
     * 系统数据源配置 Mapper。
     */
    private final SysDatasourceMapper sysDatasourceMapper;

    /**
     * 系统数据源配置实体与持久化对象转换器。
     */
    private final SysDatasourcePoConverter sysDatasourcePoConverter;

    /**
     * 根据 ID 查询数据源配置。
     *
     * @param id 数据源 ID
     * @return 数据源实体，未找到时返回 {@link Mono#empty()}
     */
    @Override
    public Mono<SysDatasource> findById(Long id) {
        return Mono.fromCallable(() -> sysDatasourceMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(sysDatasourcePoConverter.toEntity(po)));
    }

    /**
     * 根据名称和租户 ID 查询数据源配置。
     *
     * <p>用于校验数据源名称在租户内的唯一性。
     *
     * @param name 数据源名称
     * @param tenantId 租户 ID
     * @return 数据源实体（{@code Optional} 包装，便于唯一性判断）
     */
    @Override
    public Mono<Optional<SysDatasource>> findByNameAndTenantId(String name, String tenantId) {
        return Mono.fromCallable(() -> {
                    SysDatasourcePO po = sysDatasourceMapper.selectOne(ConditionUtil.lambda(SysDatasourcePO.class)
                            .eq(SysDatasourcePO::getName, name)
                            .eq(SysDatasourcePO::getTenantId, tenantId));
                    return Optional.ofNullable(po).map(sysDatasourcePoConverter::toEntity);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 根据租户 ID 查询全部数据源配置。
     *
     * <p>按创建时间倒序排列。
     *
     * @param tenantId 租户 ID
     * @return 数据源实体列表
     */
    @Override
    public Mono<List<SysDatasource>> findByTenantId(String tenantId) {
        return Mono.fromCallable(() -> sysDatasourceMapper.selectList(ConditionUtil.lambda(SysDatasourcePO.class)
                        .eq(SysDatasourcePO::getTenantId, tenantId)
                        .orderByDesc(SysDatasourcePO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(sysDatasourcePoConverter::toEntityList);
    }

    /**
     * 新增数据源配置。
     *
     * <p>主键由 MyBatis-Plus 雪花算法（{@code IdType.ASSIGN_ID}）在插入时生成，
     * 生成后回填到 PO 并转换回实体返回，供上层获取新记录 ID。
     *
     * @param entity 数据源实体（password 必须已加密）
     * @return 含生成 ID 的数据源实体
     */
    @Override
    public Mono<SysDatasource> insert(SysDatasource entity) {
        return Mono.fromCallable(() -> {
                    SysDatasourcePO po = sysDatasourcePoConverter.toPo(entity);
                    sysDatasourceMapper.insert(po);
                    // 插入后回填雪花 ID 与自动填充的审计时间，保证上层拿到完整数据
                    SysDatasource saved = sysDatasourcePoConverter.toEntity(po);
                    saved.setId(po.getId());
                    return saved;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 更新数据源配置。
     *
     * <p>基于乐观锁（{@code version} 字段）更新，并发冲突时影响行数为 0。
     *
     * @param entity 数据源实体（须携带 ID 与 version）
     * @return 影响行数
     */
    @Override
    public Mono<Integer> update(SysDatasource entity) {
        return Mono.fromCallable(() -> sysDatasourceMapper.updateById(sysDatasourcePoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 删除数据源配置。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。
     *
     * @param id 数据源 ID
     * @return 影响行数
     */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> sysDatasourceMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
