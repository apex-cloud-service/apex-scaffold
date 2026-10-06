package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.ChartEntity;
import com.apex.scaffold.domain.repository.ChartRepository;
import com.apex.scaffold.infrastructure.persistence.converter.ChartPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.ChartMapper;
import com.apex.scaffold.infrastructure.persistence.po.ChartPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 图表仓储实现
 *
 * <p>提供图表数据的持久化操作
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class ChartRepositoryImpl implements ChartRepository {

    private final ChartMapper chartMapper;
    private final ChartPoConverter converter;

    @Override
    public Mono<ChartEntity> save(ChartEntity entity) {
        return Mono.fromCallable(() -> {
                    ChartPO po = converter.toPo(entity);
                    TenantContext.currentTenantId().ifPresent(po::setTenantId);

                    if (po.getId() == null) {
                        if (chartMapper.insert(po) != 1) {
                            throw new IllegalStateException("创建图表失败");
                        }
                        entity.setId(po.getId());
                    } else {
                        if (chartMapper.updateById(po) != 1) {
                            throw new OptimisticLockingFailureException("图表并发更新冲突, id=" + po.getId());
                        }
                    }
                    return entity;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<ChartEntity> findById(Long id) {
        return Mono.fromCallable(() -> chartMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(converter::toEntity);
    }

    @Override
    public Flux<ChartEntity> findAll(String tenantId) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<ChartPO> wrapper = ConditionUtil.lambda(ChartPO.class);
                    wrapper.eq(ChartPO::getTenantId, tenantId);
                    wrapper.orderByDesc(ChartPO::getCreateTime);

                    return chartMapper.selectList(wrapper).stream()
                            .map(converter::toEntity)
                            .collect(Collectors.toList());
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Void> deleteById(Long id, String tenantId) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<ChartPO> wrapper = ConditionUtil.lambda(ChartPO.class);
                    wrapper.eq(ChartPO::getId, id);
                    wrapper.eq(ChartPO::getTenantId, tenantId);

                    return chartMapper.delete(wrapper);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }

    @Override
    public Mono<Long> count(String tenantId) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<ChartPO> wrapper = ConditionUtil.lambda(ChartPO.class);
                    wrapper.eq(ChartPO::getTenantId, tenantId);
                    return chartMapper.selectCount(wrapper);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
