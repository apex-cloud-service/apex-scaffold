package com.apex.scaffold.authcenter.infrastructure.persistence.repository;

import com.apex.scaffold.authcenter.domain.model.entity.AuditLogEntity;
import com.apex.scaffold.authcenter.domain.repository.AuditLogRepository;
import com.apex.scaffold.authcenter.infrastructure.persistence.converter.AuditLogConverter;
import com.apex.scaffold.authcenter.infrastructure.persistence.mapper.*;
import com.apex.scaffold.authcenter.infrastructure.persistence.po.*;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 租户隔离的认证仓储实现。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class AuditLogRepositoryImpl implements AuditLogRepository {
    private final AuditLogMapper mapper;
    private final AuditLogConverter converter;

    @Override
    public Mono<AuditLogEntity> save(AuditLogEntity entity) {
        return JdbcTenantScope.call(entity.getTenantId(), t -> {
            AuditLogPO po = converter.toPo(entity);
            po.setTenantId(t);
            po.setCreatedAt(LocalDateTime.now());
            if (po.getEventTime() == null) {
                po.setEventTime(po.getCreatedAt());
            }
            JdbcTenantScope.changed(mapper.insert(po));
            return converter.toEntity(po);
        });
    }

    @Override
    public Mono<AuditLogEntity> findById(Long id) {
        return JdbcTenantScope.call(
                null,
                t -> converter.toEntity(
                        mapper.selectOne(JdbcTenantScope.<AuditLogPO>query(t).eq("id", id))));
    }

    @Override
    public Flux<AuditLogEntity> findByUserId(Long userId, String tenantId) {
        return JdbcTenantScope.call(
                        tenantId,
                        t -> mapper.selectList(
                                JdbcTenantScope.<AuditLogPO>query(t).eq("user_id", userId)))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Flux<AuditLogEntity> findByEventType(String eventType, String tenantId) {
        return JdbcTenantScope.call(
                        tenantId,
                        t -> mapper.selectList(
                                JdbcTenantScope.<AuditLogPO>query(t).eq("event_type", eventType)))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Flux<AuditLogEntity> findByUserIdAndEventType(Long userId, String eventType, String tenantId) {
        return JdbcTenantScope.call(
                        tenantId,
                        t -> mapper.selectList(JdbcTenantScope.<AuditLogPO>query(t)
                                .eq("user_id", userId)
                                .eq("event_type", eventType)))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Flux<AuditLogEntity> findByIpAddress(String ipAddress, String tenantId) {
        return JdbcTenantScope.call(
                        tenantId,
                        t -> mapper.selectList(
                                JdbcTenantScope.<AuditLogPO>query(t).eq("client_ip", ipAddress)))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Flux<AuditLogEntity> findByCreatedAtBefore(LocalDateTime beforeTime, String tenantId) {
        return JdbcTenantScope.call(
                        tenantId,
                        t -> mapper.selectList(
                                JdbcTenantScope.<AuditLogPO>query(t).lt("created_at", beforeTime)))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Flux<AuditLogEntity> findByTimeRange(LocalDateTime startTime, LocalDateTime endTime, String tenantId) {
        return JdbcTenantScope.call(
                        tenantId,
                        t -> mapper.selectList(JdbcTenantScope.<AuditLogPO>query(t)
                                .ge("event_time", startTime)
                                .le("event_time", endTime)))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Flux<AuditLogEntity> findFailedEvents(String tenantId) {
        return JdbcTenantScope.call(
                        tenantId,
                        t -> mapper.selectList(
                                JdbcTenantScope.<AuditLogPO>query(t).eq("event_status", "FAILURE")))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Flux<AuditLogEntity> findHighRiskEvents(String tenantId) {
        return JdbcTenantScope.call(
                        tenantId,
                        t -> mapper.selectList(
                                JdbcTenantScope.<AuditLogPO>query(t).in("risk_level", "HIGH", "CRITICAL")))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Mono<Long> deleteByCreatedAtBefore(LocalDateTime beforeTime, String tenantId) {
        return JdbcTenantScope.call(tenantId, t ->
                (long) mapper.delete(JdbcTenantScope.<AuditLogPO>query(t).lt("created_at", beforeTime)));
    }

    @Override
    public Mono<Long> countByTimeRange(LocalDateTime startTime, LocalDateTime endTime, String tenantId) {
        return JdbcTenantScope.call(
                tenantId,
                t -> mapper.selectCount(JdbcTenantScope.<AuditLogPO>query(t)
                        .ge("event_time", startTime)
                        .le("event_time", endTime)));
    }
}
