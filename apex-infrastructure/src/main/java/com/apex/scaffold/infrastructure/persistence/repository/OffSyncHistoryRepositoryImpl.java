package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.OffSyncHistoryEntity;
import com.apex.scaffold.domain.repository.OffSyncHistoryRepository;
import com.apex.scaffold.infrastructure.persistence.converter.OffSyncHistoryPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.OffSyncHistoryMapper;
import com.apex.scaffold.infrastructure.persistence.po.OffSyncHistoryPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 同步历史Repository实现
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class OffSyncHistoryRepositoryImpl implements OffSyncHistoryRepository {

    private final OffSyncHistoryMapper mapper;
    private final OffSyncHistoryPoConverter converter;

    @Override
    public Mono<OffSyncHistoryEntity> findById(Long id) {
        return Mono.fromCallable(() -> mapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toDomain(po)));
    }

    @Override
    public Flux<OffSyncHistoryEntity> findBySyncSource(String syncSource) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OffSyncHistoryPO>()
                        .eq(OffSyncHistoryPO::getSyncSource, syncSource)
                        .orderByDesc(OffSyncHistoryPO::getStartTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }

    @Override
    public Mono<OffSyncHistoryEntity> findLatestBySource(String syncSource) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<OffSyncHistoryPO>()
                        .eq(OffSyncHistoryPO::getSyncSource, syncSource)
                        .orderByDesc(OffSyncHistoryPO::getStartTime)
                        .last("LIMIT 1")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toDomain(po)));
    }

    @Override
    public Mono<Integer> insert(OffSyncHistoryEntity entity) {
        return Mono.fromCallable(() -> {
                    OffSyncHistoryPO po = converter.toPo(entity);
                    mapper.insert(po);
                    entity.setId(po.getId());
                    return 1;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> update(OffSyncHistoryEntity entity) {
        return Mono.fromCallable(() -> mapper.updateById(converter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<PageResponse<OffSyncHistoryEntity>> findPage(
            Integer pageNum, Integer pageSize, String syncSource, String syncStatus) {
        return Mono.fromCallable(() -> {
                    Page<OffSyncHistoryPO> page = new Page<>(pageNum, pageSize);
                    LambdaQueryWrapper<OffSyncHistoryPO> wrapper = new LambdaQueryWrapper<>();
                    if (syncSource != null) {
                        wrapper.eq(OffSyncHistoryPO::getSyncSource, syncSource);
                    }
                    if (syncStatus != null) {
                        wrapper.eq(OffSyncHistoryPO::getSyncStatus, syncStatus);
                    }
                    wrapper.orderByDesc(OffSyncHistoryPO::getStartTime);
                    mapper.selectPage(page, wrapper);

                    PageResponse<OffSyncHistoryEntity> response = new PageResponse<>();
                    response.setTotal(page.getTotal());
                    response.setRecords(
                            page.getRecords().stream().map(converter::toDomain).toList());
                    return response;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
