package com.apex.scaffold.authcenter.infrastructure.persistence.repository;

import com.apex.scaffold.authcenter.domain.model.entity.TokenEntity;
import com.apex.scaffold.authcenter.domain.repository.TokenRepository;
import com.apex.scaffold.authcenter.infrastructure.persistence.converter.TokenConverter;
import com.apex.scaffold.authcenter.infrastructure.persistence.mapper.*;
import com.apex.scaffold.authcenter.infrastructure.persistence.po.*;
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
                t ->
                        mapper.update(
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
}
