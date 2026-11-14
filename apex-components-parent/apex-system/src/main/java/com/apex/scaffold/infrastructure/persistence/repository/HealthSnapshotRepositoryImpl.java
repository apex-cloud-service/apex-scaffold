package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.monitor.HealthSnapshotEntity;
import com.apex.scaffold.domain.repository.HealthSnapshotRepository;
import com.apex.scaffold.infrastructure.persistence.converter.HealthSnapshotPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.HealthSnapshotMapper;
import com.apex.scaffold.infrastructure.persistence.po.HealthSnapshotPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link HealthSnapshotRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>所有 JDBC 阻塞操作均调度到 {@link BlockingIoSchedulers#jdbc()}；
 * 租户条件由持久化拦截器自动追加，此处不手写 {@code tenant_id} 过滤。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see HealthSnapshotRepository
 */
@Repository
@RequiredArgsConstructor
public class HealthSnapshotRepositoryImpl implements HealthSnapshotRepository {

    private final HealthSnapshotMapper snapshotMapper;

    private final HealthSnapshotPoConverter snapshotPoConverter;

    @Override
    public Mono<HealthSnapshotEntity> insert(HealthSnapshotEntity entity) {
        return Mono.fromCallable(() -> {
                    HealthSnapshotPO po = snapshotPoConverter.toPo(entity);
                    snapshotMapper.insert(po);
                    entity.setId(po.getId());
                    return entity;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<PageResponse<HealthSnapshotEntity>> search(
            LocalDateTime from, LocalDateTime to, String overallStatus, int pageNum, int pageSize) {
        return Mono.fromCallable(() -> {
                    Page<HealthSnapshotPO> page = new Page<>(pageNum, pageSize);
                    snapshotMapper.selectPage(page, rangeWrapper(from, to, overallStatus));
                    return PageResponse.of(
                            snapshotPoConverter.toEntityList(page.getRecords()), page.getTotal(), pageNum, pageSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<List<HealthSnapshotEntity>> findForTrend(LocalDateTime from, LocalDateTime to, int limit) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<HealthSnapshotPO> wrapper = rangeWrapper(from, to, null)
                            .orderByAsc(HealthSnapshotPO::getGeneratedAt)
                            .last("LIMIT " + limit);
                    return snapshotPoConverter.toEntityList(snapshotMapper.selectList(wrapper));
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Long> countByRange(LocalDateTime from, LocalDateTime to) {
        return Mono.fromCallable(() -> snapshotMapper.selectCount(rangeWrapper(from, to, null)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<HealthSnapshotEntity> findById(Long id) {
        return Mono.fromCallable(() -> snapshotPoConverter.toEntity(snapshotMapper.selectById(id)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> purgeGeneratedBefore(LocalDateTime before, int limit) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<HealthSnapshotPO> wrapper = new LambdaQueryWrapper<HealthSnapshotPO>()
                            .lt(HealthSnapshotPO::getGeneratedAt, before)
                            .orderByAsc(HealthSnapshotPO::getGeneratedAt)
                            .last("LIMIT " + limit);
                    // 逐条物理删除：LambdaQueryWrapper.delete 会走 @TableLogic 转为更新，无法回收容量
                    List<HealthSnapshotPO> expired = snapshotMapper.selectList(wrapper);
                    int purged = 0;
                    for (HealthSnapshotPO po : expired) {
                        purged += snapshotMapper.deleteByIdPhysically(po.getId());
                    }
                    return purged;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 构造生成时间区间与总体结论过滤条件。
     *
     * @param from 生成时间下界，可为 {@code null}
     * @param to 生成时间上界，可为 {@code null}
     * @param overallStatus 总体结论过滤，可为 {@code null}
     * @return 查询包装器
     */
    private LambdaQueryWrapper<HealthSnapshotPO> rangeWrapper(
            LocalDateTime from, LocalDateTime to, String overallStatus) {
        return ConditionUtil.builder(HealthSnapshotPO.class)
                .betweenIfPresent(from, to, HealthSnapshotPO::getGeneratedAt)
                .eqIfPresentStr(overallStatus, HealthSnapshotPO::getOverallStatus)
                .build();
    }
}
