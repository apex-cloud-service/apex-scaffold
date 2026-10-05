package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.SysSsoSessionEntity;
import com.apex.scaffold.domain.repository.SysSsoSessionRepository;
import com.apex.scaffold.infrastructure.persistence.converter.SysSsoSessionPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.SysSsoSessionMapper;
import com.apex.scaffold.infrastructure.persistence.po.SysSsoSessionPO;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SSO会话仓储实现
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class SysSsoSessionRepositoryImpl implements SysSsoSessionRepository {

    private final SysSsoSessionMapper ssoSessionMapper;

    private final SysSsoSessionPoConverter ssoSessionPoConverter;

    @Override
    public Mono<SysSsoSessionEntity> findById(Long id) {
        return Mono.fromCallable(() -> ssoSessionMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(ssoSessionPoConverter.toEntity(po)));
    }

    @Override
    public Mono<SysSsoSessionEntity> findBySessionIndex(String sessionIndex) {
        return Mono.fromCallable(() -> ssoSessionMapper.selectOne(
                        ConditionUtil.lambda(SysSsoSessionPO.class).eq(SysSsoSessionPO::getSessionIndex, sessionIndex)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(ssoSessionPoConverter.toEntity(po)));
    }

    @Override
    public Flux<SysSsoSessionEntity> findByUserId(Long userId) {
        return Mono.fromCallable(() -> ssoSessionMapper.selectList(ConditionUtil.lambda(SysSsoSessionPO.class)
                        .eq(SysSsoSessionPO::getUserId, userId)
                        .orderByDesc(SysSsoSessionPO::getLoginTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(list -> Flux.fromIterable((List<SysSsoSessionPO>) list))
                .map(ssoSessionPoConverter::toEntity);
    }

    @Override
    public Flux<SysSsoSessionEntity> findByTenantId(String tenantId) {
        return Mono.fromCallable(() -> ssoSessionMapper.selectList(ConditionUtil.lambda(SysSsoSessionPO.class)
                        .eq(SysSsoSessionPO::getTenantId, tenantId)
                        .orderByDesc(SysSsoSessionPO::getLoginTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(list -> Flux.fromIterable((List<SysSsoSessionPO>) list))
                .map(ssoSessionPoConverter::toEntity);
    }

    @Override
    public Flux<SysSsoSessionEntity> findExpiredSessions(LocalDateTime before) {
        return Mono.fromCallable(() -> ssoSessionMapper.selectList(ConditionUtil.lambda(SysSsoSessionPO.class)
                        .lt(SysSsoSessionPO::getExpireTime, before)
                        .eq(SysSsoSessionPO::getStatus, "ACTIVE")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(list -> Flux.fromIterable((List<SysSsoSessionPO>) list))
                .map(ssoSessionPoConverter::toEntity);
    }

    @Override
    public Mono<Integer> insert(SysSsoSessionEntity entity) {
        return Mono.fromCallable(() -> ssoSessionMapper.insert(ssoSessionPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> updateById(SysSsoSessionEntity entity) {
        return Mono.fromCallable(() -> ssoSessionMapper.updateById(ssoSessionPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> ssoSessionMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
