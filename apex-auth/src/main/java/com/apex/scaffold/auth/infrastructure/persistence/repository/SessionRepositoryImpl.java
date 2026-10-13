package com.apex.scaffold.auth.infrastructure.persistence.repository;

import com.apex.scaffold.auth.domain.model.entity.SessionEntity;
import com.apex.scaffold.auth.domain.repository.SessionRepository;
import com.apex.scaffold.auth.infrastructure.persistence.converter.SessionConverter;
import com.apex.scaffold.auth.infrastructure.persistence.mapper.*;
import com.apex.scaffold.auth.infrastructure.persistence.po.*;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
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
public class SessionRepositoryImpl implements SessionRepository {
    private final SessionMapper mapper;
    private final SessionConverter converter;
    private final UserMapper users;
    private final TokenMapper tokens;

    @Override
    public Mono<SessionEntity> save(SessionEntity entity) {
        return JdbcTenantScope.call(entity.getTenantId(), t -> {
            SessionPO po = converter.toPo(entity);
            po.setTenantId(t);
            if (po.getUserId() == null
                    || users.selectCount(JdbcTenantScope.<UserPO>query(t).eq("id", po.getUserId())) != 1) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
            if (po.getTokenId() != null
                    && tokens.selectCount(JdbcTenantScope.<TokenPO>query(t)
                                    .eq("id", po.getTokenId())
                                    .eq("user_id", po.getUserId()))
                            != 1) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }

            po.setCreatedAt(LocalDateTime.now());
            po.setDeleted(0);
            po.setVersion(0);
            if (po.getStatus() == null) {
                po.setStatus("ACTIVE");
            }
            po.setLastActiveAt(po.getCreatedAt());
            JdbcTenantScope.changed(mapper.insert(po));
            return converter.toEntity(po);
        });
    }

    @Override
    public Mono<SessionEntity> findById(Long id) {
        return JdbcTenantScope.call(
                null,
                t -> converter.toEntity(
                        mapper.selectOne(JdbcTenantScope.<SessionPO>query(t).eq("id", id))));
    }

    @Override
    public Flux<SessionEntity> findAll() {
        return JdbcTenantScope.call(null, t -> mapper.selectList(JdbcTenantScope.<SessionPO>query(t)))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Mono<SessionEntity> update(SessionEntity entity) {
        return JdbcTenantScope.call(entity.getTenantId(), t -> {
            if (entity.getId() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR);
            }
            SessionPO po = converter.toPo(entity);
            po.setTenantId(t);
            if (po.getUserId() == null
                    || users.selectCount(JdbcTenantScope.<UserPO>query(t).eq("id", po.getUserId())) != 1) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
            if (po.getTokenId() != null
                    && tokens.selectCount(JdbcTenantScope.<TokenPO>query(t)
                                    .eq("id", po.getTokenId())
                                    .eq("user_id", po.getUserId()))
                            != 1) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }

            po.setCreatedAt(null);
            var query = JdbcTenantScope.<SessionPO>update(t).eq("id", po.getId());
            if (po.getVersion() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR);
            }
            query.eq("version", po.getVersion());
            po.setVersion(po.getVersion() + 1);
            JdbcTenantScope.changed(mapper.update(po, query));
            return converter.toEntity(
                    mapper.selectOne(JdbcTenantScope.<SessionPO>query(t).eq("id", po.getId())));
        });
    }

    @Override
    public Mono<Boolean> deleteById(Long id) {
        return JdbcTenantScope.call(
                null, t -> mapper.delete(JdbcTenantScope.<SessionPO>query(t).eq("id", id)) > 0);
    }

    @Override
    public Flux<SessionEntity> findByUserId(Long userId, String tenantId) {
        return JdbcTenantScope.call(
                        tenantId,
                        t -> mapper.selectList(
                                JdbcTenantScope.<SessionPO>query(t).eq("user_id", userId)))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Flux<SessionEntity> findActiveByUserId(Long userId, String tenantId) {
        return JdbcTenantScope.call(
                        tenantId,
                        t -> mapper.selectList(JdbcTenantScope.<SessionPO>query(t)
                                .eq("user_id", userId)
                                .eq("status", "ACTIVE")
                                .gt("expires_at", LocalDateTime.now())))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Mono<SessionEntity> findBySessionId(String sessionId) {
        return JdbcTenantScope.call(
                null,
                t -> converter.toEntity(
                        mapper.selectOne(JdbcTenantScope.<SessionPO>query(t).eq("session_id", sessionId))));
    }

    @Override
    public Mono<SessionEntity> findByTokenId(Long tokenId) {
        return JdbcTenantScope.call(
                null,
                t -> converter.toEntity(
                        mapper.selectOne(JdbcTenantScope.<SessionPO>query(t).eq("token_id", tokenId))));
    }

    @Override
    public Mono<Boolean> terminateByUserId(Long userId, String tenantId) {
        return JdbcTenantScope.call(
                tenantId,
                t -> mapper.update(
                                null,
                                JdbcTenantScope.<SessionPO>update(t)
                                        .eq("user_id", userId)
                                        .eq("status", "ACTIVE")
                                        .set("status", "TERMINATED")
                                        .set("last_active_at", LocalDateTime.now())
                                        .setSql("version = version + 1"))
                        > 0);
    }

    @Override
    public Mono<Boolean> terminateBySessionId(String sessionId) {
        return JdbcTenantScope.call(
                null,
                t -> mapper.update(
                                null,
                                JdbcTenantScope.<SessionPO>update(t)
                                        .eq("session_id", sessionId)
                                        .eq("status", "ACTIVE")
                                        .set("status", "TERMINATED")
                                        .set("last_active_at", LocalDateTime.now())
                                        .setSql("version = version + 1"))
                        > 0);
    }

    @Override
    public Mono<Boolean> updateLastActive(String sessionId) {
        return JdbcTenantScope.call(
                null,
                t -> mapper.update(
                                null,
                                JdbcTenantScope.<SessionPO>update(t)
                                        .eq("session_id", sessionId)
                                        .eq("status", "ACTIVE")
                                        .gt("expires_at", LocalDateTime.now())
                                        .set("last_active_at", LocalDateTime.now())
                                        .setSql("version = version + 1"))
                        > 0);
    }

    @Override
    public Flux<SessionEntity> findExpiredSessions() {
        return JdbcTenantScope.call(
                        null,
                        t -> mapper.selectList(
                                JdbcTenantScope.<SessionPO>query(t).le("expires_at", LocalDateTime.now())))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Mono<Long> cleanupExpiredSessions() {
        return JdbcTenantScope.call(null, t ->
                (long) mapper.delete(JdbcTenantScope.<SessionPO>query(t).le("expires_at", LocalDateTime.now())));
    }
}
