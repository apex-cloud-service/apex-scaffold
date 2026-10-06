package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.SysSsoAuditLogEntity;
import com.apex.scaffold.domain.repository.SysSsoAuditLogRepository;
import com.apex.scaffold.infrastructure.persistence.converter.SysSsoAuditLogPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.SysSsoAuditLogMapper;
import com.apex.scaffold.infrastructure.persistence.po.SysSsoAuditLogPO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SSO审计日志仓储实现
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class SysSsoAuditLogRepositoryImpl implements SysSsoAuditLogRepository {

    private final SysSsoAuditLogMapper ssoAuditLogMapper;

    private final SysSsoAuditLogPoConverter ssoAuditLogPoConverter;

    @Override
    public Mono<SysSsoAuditLogEntity> findById(Long id) {
        return Mono.fromCallable(() -> ssoAuditLogMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(ssoAuditLogPoConverter.toEntity(po)));
    }

    @Override
    public Flux<SysSsoAuditLogEntity> findByProviderId(Long providerId) {
        return Mono.fromCallable(() -> ssoAuditLogMapper.selectList(ConditionUtil.lambda(SysSsoAuditLogPO.class)
                        .eq(SysSsoAuditLogPO::getProviderId, providerId)
                        .orderByDesc(SysSsoAuditLogPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(list -> Flux.fromIterable((List<SysSsoAuditLogPO>) list))
                .map(ssoAuditLogPoConverter::toEntity);
    }

    @Override
    public Flux<SysSsoAuditLogEntity> findByUserId(Long userId) {
        return Mono.fromCallable(() -> ssoAuditLogMapper.selectList(ConditionUtil.lambda(SysSsoAuditLogPO.class)
                        .eq(SysSsoAuditLogPO::getUserId, userId)
                        .orderByDesc(SysSsoAuditLogPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(list -> Flux.fromIterable((List<SysSsoAuditLogPO>) list))
                .map(ssoAuditLogPoConverter::toEntity);
    }

    @Override
    public Flux<SysSsoAuditLogEntity> findByTenantId(String tenantId) {
        return Mono.fromCallable(() -> ssoAuditLogMapper.selectList(ConditionUtil.lambda(SysSsoAuditLogPO.class)
                        .eq(SysSsoAuditLogPO::getTenantId, tenantId)
                        .orderByDesc(SysSsoAuditLogPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(list -> Flux.fromIterable((List<SysSsoAuditLogPO>) list))
                .map(ssoAuditLogPoConverter::toEntity);
    }

    @Override
    public Mono<Integer> insert(SysSsoAuditLogEntity entity) {
        SysSsoAuditLogPO po = ssoAuditLogPoConverter.toPo(entity);
        return Mono.fromCallable(() -> ssoAuditLogMapper.insert(po)).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> ssoAuditLogMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
