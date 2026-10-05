package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.SysSsoProviderEntity;
import com.apex.scaffold.domain.repository.SysSsoProviderRepository;
import com.apex.scaffold.infrastructure.persistence.converter.SysSsoProviderPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.SysSsoProviderMapper;
import com.apex.scaffold.infrastructure.persistence.po.SysSsoProviderPO;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SSO提供商仓储实现
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class SysSsoProviderRepositoryImpl implements SysSsoProviderRepository {

    private final SysSsoProviderMapper ssoProviderMapper;

    private final SysSsoProviderPoConverter ssoProviderPoConverter;

    @Override
    public Mono<SysSsoProviderEntity> findById(Long id) {
        return Mono.fromCallable(() -> ssoProviderMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(ssoProviderPoConverter.toEntity(po)));
    }

    @Override
    public Mono<SysSsoProviderEntity> findByNameAndTenantId(String name, String tenantId) {
        return Mono.fromCallable(() -> ssoProviderMapper.selectOne(ConditionUtil.lambda(SysSsoProviderPO.class)
                        .eq(SysSsoProviderPO::getProviderName, name)
                        .eq(SysSsoProviderPO::getTenantId, tenantId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(ssoProviderPoConverter.toEntity(po)));
    }

    @Override
    public Flux<SysSsoProviderEntity> findByTenantId(String tenantId) {
        return Mono.fromCallable(() -> ssoProviderMapper.selectList(ConditionUtil.lambda(SysSsoProviderPO.class)
                        .eq(SysSsoProviderPO::getTenantId, tenantId)
                        .orderByAsc(SysSsoProviderPO::getPriority)
                        .orderByDesc(SysSsoProviderPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(list -> Flux.fromIterable((List<SysSsoProviderPO>) list))
                .map(ssoProviderPoConverter::toEntity);
    }

    @Override
    public Mono<Integer> insert(SysSsoProviderEntity entity) {
        return Mono.fromCallable(() -> ssoProviderMapper.insert(ssoProviderPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> updateById(SysSsoProviderEntity entity) {
        return Mono.fromCallable(() -> ssoProviderMapper.updateById(ssoProviderPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> ssoProviderMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
