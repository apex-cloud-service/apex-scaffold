package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.generator.GenConfigEntity;
import com.apex.scaffold.domain.repository.GenConfigRepository;
import com.apex.scaffold.infrastructure.persistence.converter.GenConfigPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.GenConfigMapper;
import com.apex.scaffold.infrastructure.persistence.po.GenConfigPO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link GenConfigRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责代码生成器生成配置的持久化读写与关联查询，通过
 * {@link GenConfigPoConverter} 在领域实体与 {@link GenConfigPO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池执行。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>生成配置的增删改查</li>
 *   <li>按数据源ID查询关联的配置列表</li>
 *   <li>支持配置的批量操作和分页查询</li>
 *   <li>JDBC 阻塞操作隔离到专用调度器</li>
 * </ul>
 *
 * <h2>关联关系</h2>
 * <ul>
 *   <li>多对一：多个配置可引用同一个数据源</li>
 *   <li>查询时自动过滤租户和逻辑删除标记</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenConfigRepository
 */
@Repository
@RequiredArgsConstructor
public class GenConfigRepositoryImpl implements GenConfigRepository {

    /**
     * 生成配置 Mapper。
     */
    private final GenConfigMapper configMapper;

    /**
     * 生成配置实体与持久化对象转换器。
     */
    private final GenConfigPoConverter configPoConverter;

    /**
     * 根据 ID 查询生成配置。
     *
     * @param id 配置 ID
     * @return 配置实体，未找到时返回 {@link Mono#empty()}
     */
    @Override
    public Mono<GenConfigEntity> findById(Long id) {
        return Mono.fromCallable(() -> configMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(configPoConverter.toEntity(po)));
    }

    /**
     * 根据数据源ID查询生成配置列表。
     *
     * <p>查询关联到指定数据源的所有生成配置，按创建时间倒序排列。
     * 用于数据源删除前的依赖检查和配置管理。
     *
     * @param datasourceId 数据源ID
     * @return 配置实体列表
     */
    @Override
    public Mono<List<GenConfigEntity>> findByDatasourceId(Long datasourceId) {
        return Mono.fromCallable(() -> configMapper.selectList(ConditionUtil.lambda(GenConfigPO.class)
                        .eq(GenConfigPO::getDatasourceId, datasourceId)
                        .orderByDesc(GenConfigPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(configPoConverter::toEntityList);
    }

    /**
     * 查询全部生成配置。
     *
     * <p>查询当前租户下的所有生成配置，按创建时间倒序排列。
     *
     * @return 配置实体列表
     */
    @Override
    public Mono<List<GenConfigEntity>> findAll() {
        return Mono.fromCallable(() -> configMapper.selectList(
                        ConditionUtil.lambda(GenConfigPO.class).orderByDesc(GenConfigPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(configPoConverter::toEntityList);
    }

    /**
     * 分页查询生成配置。
     *
     * <p>过滤与分页下推到数据库，按创建时间倒序排列；租户范围由持久化拦截器追加。
     *
     * @param configName 配置名称模糊匹配条件，可为 {@code null}
     * @param datasourceId 数据源 ID 精确匹配条件，可为 {@code null}
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页大小
     * @return 分页结果
     */
    @Override
    public Mono<PageResponse<GenConfigEntity>> search(String configName, Long datasourceId, int pageNum, int pageSize) {
        return Mono.fromCallable(() -> {
                    Page<GenConfigPO> page = new Page<>(pageNum, pageSize);
                    configMapper.selectPage(
                            page,
                            ConditionUtil.lambda(GenConfigPO.class)
                                    .likeIfPresent(GenConfigPO::getConfigName, configName)
                                    .eqIfPresent(GenConfigPO::getDatasourceId, datasourceId)
                                    .orderByDesc(GenConfigPO::getCreateTime));
                    return PageResponse.of(
                            configPoConverter.toEntityList(page.getRecords()), page.getTotal(), pageNum, pageSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 新增生成配置。
     *
     * @param entity 配置实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> insert(GenConfigEntity entity) {
        return Mono.fromCallable(() -> configMapper.insert(configPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 更新生成配置。
     *
     * @param entity 配置实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> updateById(GenConfigEntity entity) {
        return Mono.fromCallable(() -> configMapper.updateById(configPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 删除生成配置。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。
     * 删除配置不影响已产生的生成历史记录。</p>
     *
     * @param id 配置 ID
     * @return 影响行数
     */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> configMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
