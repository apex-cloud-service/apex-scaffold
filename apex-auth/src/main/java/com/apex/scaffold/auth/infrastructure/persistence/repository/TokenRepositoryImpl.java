package com.apex.scaffold.auth.infrastructure.persistence.repository;

import com.apex.scaffold.auth.domain.model.entity.TokenEntity;
import com.apex.scaffold.auth.domain.repository.TokenRepository;
import com.apex.scaffold.auth.infrastructure.persistence.converter.TokenConverter;
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
public class TokenRepositoryImpl implements TokenRepository {
    private final TokenMapper mapper;
    private final TokenConverter converter;
    private final UserMapper users;

    @Override
    public Mono<TokenEntity> save(TokenEntity entity) {
        return JdbcTenantScope.call(entity.getTenantId(), t -> {
            TokenPO po = converter.toPo(entity);
            po.setTenantId(t);
            if (po.getUserId() == null
                    || users.selectCount(JdbcTenantScope.<UserPO>query(t).eq("id", po.getUserId())) != 1) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }

            po.setCreatedAt(LocalDateTime.now());
            po.setRevoked(0);
            JdbcTenantScope.changed(mapper.insert(po));
            return converter.toEntity(po);
        });
    }

    @Override
    public Mono<TokenEntity> findById(Long id) {
        return JdbcTenantScope.call(
                null,
                t -> converter.toEntity(
                        mapper.selectOne(JdbcTenantScope.<TokenPO>query(t).eq("id", id))));
    }

    @Override
    public Mono<TokenEntity> update(TokenEntity entity) {
        return JdbcTenantScope.call(entity.getTenantId(), t -> {
            if (entity.getId() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR);
            }
            TokenPO po = converter.toPo(entity);
            po.setTenantId(t);
            if (po.getUserId() == null
                    || users.selectCount(JdbcTenantScope.<UserPO>query(t).eq("id", po.getUserId())) != 1) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }

            TokenPO existing =
                    mapper.selectOne(JdbcTenantScope.<TokenPO>query(t).eq("id", po.getId()));
            if (existing == null) {
                throw new BusinessException(ResultCode.NOT_FOUND);
            }
            if (existing.getRevoked() != null && existing.getRevoked() == 1) {
                throw new BusinessException(ResultCode.FORBIDDEN, "已撤销的Token不能被更新");
            }

            po.setCreatedAt(null);
            var query = JdbcTenantScope.<TokenPO>update(t).eq("id", po.getId()).eq("revoked", 0);

            JdbcTenantScope.changed(mapper.update(po, query));
            return converter.toEntity(
                    mapper.selectOne(JdbcTenantScope.<TokenPO>query(t).eq("id", po.getId())));
        });
    }

    @Override
    public Mono<Boolean> deleteById(Long id) {
        return JdbcTenantScope.call(
                null, t -> mapper.delete(JdbcTenantScope.<TokenPO>query(t).eq("id", id)) > 0);
    }

    @Override
    public Flux<TokenEntity> findByUserId(Long userId, String tenantId) {
        return JdbcTenantScope.call(
                        tenantId,
                        t -> mapper.selectList(JdbcTenantScope.<TokenPO>query(t).eq("user_id", userId)))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Flux<TokenEntity> findActiveByUserId(Long userId, String tenantId) {
        return JdbcTenantScope.call(
                        tenantId,
                        t -> mapper.selectList(JdbcTenantScope.<TokenPO>query(t)
                                .eq("user_id", userId)
                                .eq("revoked", 0)
                                .gt("expires_at", LocalDateTime.now())))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Mono<TokenEntity> findByToken(String token) {
        return JdbcTenantScope.call(
                null,
                t -> converter.toEntity(
                        mapper.selectOne(JdbcTenantScope.<TokenPO>query(t).eq("token_value", token))));
    }

    @Override
    public Mono<TokenEntity> findByJti(String jti) {
        return JdbcTenantScope.call(
                null,
                t -> converter.toEntity(
                        mapper.selectOne(JdbcTenantScope.<TokenPO>query(t).eq("jti", jti))));
    }

    @Override
    public Mono<TokenEntity> findByRefreshToken(String refreshToken) {
        return JdbcTenantScope.call(
                null,
                t -> converter.toEntity(mapper.selectOne(JdbcTenantScope.<TokenPO>query(t)
                        .eq("token_value", refreshToken)
                        .eq("token_type", "REFRESH"))));
    }

    @Override
    public Mono<Boolean> revokeByUserId(Long userId, String tenantId) {
        return JdbcTenantScope.call(
                tenantId,
                t -> mapper.update(
                                null,
                                JdbcTenantScope.<TokenPO>update(t)
                                        .eq("user_id", userId)
                                        .eq("revoked", 0)
                                        .set("revoked", 1)
                                        .set("revoked_at", LocalDateTime.now()))
                        > 0);
    }

    @Override
    public Mono<Boolean> revokeByToken(String token) {
        return JdbcTenantScope.call(
                null,
                t -> mapper.update(
                                null,
                                JdbcTenantScope.<TokenPO>update(t)
                                        .eq("token_value", token)
                                        .eq("revoked", 0)
                                        .set("revoked", 1)
                                        .set("revoked_at", LocalDateTime.now()))
                        > 0);
    }

    @Override
    public Mono<Boolean> revokeByJti(String jti) {
        return JdbcTenantScope.call(
                null,
                t -> mapper.update(
                                null,
                                JdbcTenantScope.<TokenPO>update(t)
                                        .eq("jti", jti)
                                        .eq("revoked", 0)
                                        .set("revoked", 1)
                                        .set("revoked_at", LocalDateTime.now()))
                        > 0);
    }

    @Override
    public Mono<Boolean> revokeByJti(String jti, String revokedBy, String reason) {
        return JdbcTenantScope.call(
                null,
                t -> mapper.update(
                                null,
                                JdbcTenantScope.<TokenPO>update(t)
                                        .eq("jti", jti)
                                        .eq("revoked", 0)
                                        .set("revoked", 1)
                                        .set("revoked_at", LocalDateTime.now())
                                        .set("revoked_by", revokedBy)
                                        .set("revoke_reason", reason))
                        > 0);
    }

    @Override
    public Mono<Integer> revokeByUserId(Long userId, String tenantId, String revokedBy, String reason) {
        return JdbcTenantScope.call(
                tenantId,
                t -> mapper.update(
                        null,
                        JdbcTenantScope.<TokenPO>update(t)
                                .eq("user_id", userId)
                                .eq("revoked", 0)
                                .set("revoked", 1)
                                .set("revoked_at", LocalDateTime.now())
                                .set("revoked_by", revokedBy)
                                .set("revoke_reason", reason)));
    }

    @Override
    public Mono<Boolean> existsByToken(String token) {
        return JdbcTenantScope.call(
                null, t -> mapper.selectCount(JdbcTenantScope.<TokenPO>query(t).eq("token_value", token)) > 0);
    }

    @Override
    public Mono<Integer> revokeByTokenFamily(String tokenFamily, String revokedBy, String reason) {
        return JdbcTenantScope.call(
                null,
                t -> mapper.update(
                        null,
                        JdbcTenantScope.<TokenPO>update(t)
                                .eq("token_family", tokenFamily)
                                .eq("revoked", 0)
                                .set("revoked", 1)
                                .set("revoked_at", LocalDateTime.now())
                                .set("revoked_by", revokedBy)
                                .set("revoke_reason", reason)));
    }

    @Override
    public Flux<TokenEntity> findExpiredTokens() {
        return JdbcTenantScope.call(
                        null,
                        t -> mapper.selectList(JdbcTenantScope.<TokenPO>query(t).le("expires_at", LocalDateTime.now())))
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Mono<Long> deleteExpiredTokens() {
        return JdbcTenantScope.call(null, t ->
                (long) mapper.delete(JdbcTenantScope.<TokenPO>query(t).le("expires_at", LocalDateTime.now())));
    }

    /**
     * 平台级撤销增量查询（撤销流专用）：内部端点调用无租户上下文，且消费方（网关）
     * 需要跨租户撤销视图，故不经 {@link JdbcTenantScope}，直接在 jdbc 池执行。
     *
     * <p>受控口径：仅由 {@code /internal/v1/tokens/revocations} 撤销流端点消费，
     * 返回字段仅含 {@code jti} 与撤销时间，不含任何业务载荷。
     */
    @Override
    public reactor.core.publisher.Flux<com.apex.scaffold.auth.domain.model.TokenRevocation> findRevocationsSince(
            long sinceEpochMillis, int limit) {
        int bounded = Math.max(1, Math.min(limit, 1000));
        LocalDateTime since = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(sinceEpochMillis), java.time.ZoneId.systemDefault());
        return Mono.fromCallable(() -> {
                    com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TokenPO> query =
                            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TokenPO>()
                                    .select("jti", "revoked_at")
                                    .eq("revoked", 1)
                                    .isNotNull("jti")
                                    .gt("revoked_at", since)
                                    .orderByAsc("revoked_at")
                                    .last("LIMIT " + bounded);
                    return mapper.selectList(query).stream()
                            .filter(po -> po.getRevokedAt() != null)
                            .map(po -> new com.apex.scaffold.auth.domain.model.TokenRevocation(
                                    po.getJti(),
                                    po.getRevokedAt()
                                            .atZone(java.time.ZoneId.systemDefault())
                                            .toInstant()
                                            .toEpochMilli()))
                            .toList();
                })
                .subscribeOn(com.apex.scaffold.common.scheduler.BlockingIoSchedulers.jdbc())
                .flatMapMany(reactor.core.publisher.Flux::fromIterable);
    }
}
