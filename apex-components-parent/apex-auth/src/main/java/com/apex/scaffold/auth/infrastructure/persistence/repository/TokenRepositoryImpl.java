package com.apex.scaffold.auth.infrastructure.persistence.repository;

import com.apex.scaffold.auth.domain.model.entity.TokenEntity;
import com.apex.scaffold.auth.domain.model.vo.TokenEntityPair;
import com.apex.scaffold.auth.domain.model.vo.TokenRotationResult;
import com.apex.scaffold.auth.domain.repository.TokenRepository;
import com.apex.scaffold.auth.infrastructure.persistence.converter.TokenConverter;
import com.apex.scaffold.auth.infrastructure.persistence.mapper.*;
import com.apex.scaffold.auth.infrastructure.persistence.po.*;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final DataSource dataSource;

    @Override
    public Mono<TokenEntity> save(TokenEntity entity) {
        return transactional(entity.getTenantId(), t -> {
            if (entity.getId() != null || entity.isRevoked()) {
                throw new BusinessException(ResultCode.FORBIDDEN, "已持久化或撤销的Token不能重新保存");
            }
            lockUser(t, entity.getUserId());
            return insertToken(t, entity);
        });
    }

    @Override
    public Mono<TokenEntityPair> savePair(TokenEntityPair tokens) {
        return transactional(tokens.accessToken().getTenantId(), t -> {
            validatePair(t, tokens);
            validateActiveUser(lockUser(t, tokens.accessToken().getUserId()));
            return insertPair(t, tokens);
        });
    }

    @Override
    public Mono<TokenRotationResult> rotateRefreshToken(TokenEntity expectedRefresh, TokenEntityPair preparedTokens) {
        return transactional(expectedRefresh.getTenantId(), t -> {
            validatePair(t, preparedTokens);
            if (!Objects.equals(
                    expectedRefresh.getUserId(), preparedTokens.accessToken().getUserId())) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
            UserPO user = lockUser(t, expectedRefresh.getUserId());
            TokenPO current = currentRefresh(t, expectedRefresh);
            if (current == null) {
                return TokenRotationResult.rejected();
            }
            if (Integer.valueOf(1).equals(current.getRevoked())) {
                return handleRevokedRefresh(t, current);
            }
            validateActiveUser(user);
            String family =
                    current.getTokenFamily() == null || current.getTokenFamily().isBlank()
                            ? preparedTokens.refreshToken().getTokenFamily()
                            : current.getTokenFamily();
            int rotationCount = Math.addExact(current.getRotationCount() == null ? 0 : current.getRotationCount(), 1);
            if (Objects.equals(current.getJti(), preparedTokens.accessToken().getJti())
                    || Objects.equals(
                            current.getJti(), preparedTokens.refreshToken().getJti())) {
                throw new BusinessException(ResultCode.PARAM_ERROR);
            }
            for (TokenEntity token : List.of(preparedTokens.accessToken(), preparedTokens.refreshToken())) {
                token.setTokenFamily(family);
                token.setPreviousJti(current.getJti());
                token.setRotationCount(rotationCount);
            }
            int consumed = mapper.update(
                    null,
                    JdbcTenantScope.<TokenPO>update(t)
                            .eq("user_id", expectedRefresh.getUserId())
                            .eq("id", expectedRefresh.getId())
                            .eq("jti", expectedRefresh.getJti())
                            .eq(expectedRefresh.getTokenHash() != null, "token_hash", expectedRefresh.getTokenHash())
                            .eq("expires_at", expectedRefresh.getExpiresAt())
                            .eq("token_type", "REFRESH")
                            .eq("revoked", 0)
                            .gt("expires_at", LocalDateTime.now())
                            .set("revoked", 1)
                            .set("revoked_at", LocalDateTime.now())
                            .set("revoked_by", "system")
                            .set("revoke_reason", "token_refresh")
                            .set("token_family", family));
            if (consumed > 1) {
                throw new BusinessException(ResultCode.ERROR, "刷新令牌状态异常");
            }
            if (consumed == 0) {
                TokenPO latest = currentRefresh(t, expectedRefresh);
                return latest == null ? TokenRotationResult.rejected() : handleRevokedRefresh(t, latest);
            }
            return TokenRotationResult.rotated(insertPair(t, preparedTokens));
        });
    }

    @Override
    public Mono<TokenRotationResult> checkRefreshReplay(TokenEntity expectedRefresh) {
        return transactional(expectedRefresh.getTenantId(), t -> {
            lockUser(t, expectedRefresh.getUserId());
            TokenPO current = currentRefresh(t, expectedRefresh);
            return current == null ? TokenRotationResult.rejected() : handleRevokedRefresh(t, current);
        });
    }

    private TokenPO currentRefresh(String tenant, TokenEntity expected) {
        TokenPO current = mapper.selectOne(JdbcTenantScope.<TokenPO>query(tenant)
                .eq("jti", expected.getJti())
                .eq("user_id", expected.getUserId())
                .eq("token_type", "REFRESH")
                .last("FOR UPDATE"));
        if (current == null
                || !Objects.equals(current.getId(), expected.getId())
                || !Objects.equals(current.getTokenHash(), expected.getTokenHash())
                || !Objects.equals(current.getTenantId(), expected.getTenantId())
                || !Objects.equals(current.getUserId(), expected.getUserId())
                || !Objects.equals(current.getJti(), expected.getJti())
                || !Objects.equals(current.getTokenType(), expected.getTokenType())
                || !Objects.equals(current.getExpiresAt(), expected.getExpiresAt())
                || current.getExpiresAt() == null
                || !current.getExpiresAt().isAfter(LocalDateTime.now())) {
            return null;
        }
        return current;
    }

    private TokenRotationResult handleRevokedRefresh(String tenant, TokenPO current) {
        if (isRefreshTokenReplay(current)) {
            revokeUserTokens(tenant, current.getUserId(), "system", "token_reuse_detected");
            return TokenRotationResult.replay();
        }
        return TokenRotationResult.rejected();
    }

    private boolean isRefreshTokenReplay(TokenPO token) {
        if (!Integer.valueOf(1).equals(token.getRevoked())) {
            return false;
        }
        return "token_refresh".equals(token.getRevokeReason()) || "token_rotated".equals(token.getRevokeReason());
    }

    private void validatePair(String tenant, TokenEntityPair tokens) {
        TokenEntity access = tokens.accessToken();
        TokenEntity refresh = tokens.refreshToken();
        if (!Objects.equals(tenant, access.getTenantId())
                || !Objects.equals(tenant, refresh.getTenantId())
                || access.getUserId() == null
                || !Objects.equals(access.getUserId(), refresh.getUserId())
                || !"ACCESS".equals(access.getTokenType())
                || !"REFRESH".equals(refresh.getTokenType())
                || access.getId() != null
                || refresh.getId() != null
                || access.isRevoked()
                || refresh.isRevoked()
                || access.getExpiresAt() == null
                || !access.getExpiresAt().isAfter(LocalDateTime.now())
                || refresh.getExpiresAt() == null
                || !refresh.getExpiresAt().isAfter(LocalDateTime.now())
                || refresh.getTokenFamily() == null
                || refresh.getTokenFamily().isBlank()
                || !Objects.equals(access.getTokenFamily(), refresh.getTokenFamily())
                || access.getJti() == null
                || refresh.getJti() == null
                || Objects.equals(access.getJti(), refresh.getJti())) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
    }

    private TokenEntityPair insertPair(String tenant, TokenEntityPair tokens) {
        TokenEntity access = insertToken(tenant, tokens.accessToken());
        TokenEntity refresh = insertToken(tenant, tokens.refreshToken());
        return new TokenEntityPair(access, refresh);
    }

    private TokenEntity insertToken(String tenant, TokenEntity entity) {
        TokenPO po = converter.toPo(entity);
        po.setTenantId(tenant);
        po.setCreatedAt(LocalDateTime.now());
        po.setRevoked(0);
        po.setRevokedAt(null);
        po.setRevokedBy(null);
        po.setRevokeReason(null);
        JdbcTenantScope.changed(mapper.insert(po));
        return converter.toEntity(po);
    }

    private UserPO lockUser(String tenant, Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        UserPO user = users.selectOne(
                JdbcTenantScope.<UserPO>query(tenant).eq("id", userId).last("FOR UPDATE"));
        if (user == null) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return user;
    }

    private void validateActiveUser(UserPO user) {
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());
        boolean active = Integer.valueOf(1).equals(user.getStatus())
                || (Integer.valueOf(2).equals(user.getStatus()) && user.getLockedUntil() != null && !locked);
        if (!active || locked) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账户已禁用或锁定");
        }
    }

    /**
     * 整个回调在 JDBC 调度器、可信租户及同一业务数据源连接上同步执行。
     * 内部管理器不注册为全局 Bean，避免与 R2DBC 或既有事务管理器发生候选冲突。
     * @param tenant 期望租户
     * @param action 同步数据库操作，不得返回 Publisher 或包含远程调用
     * @param <T> 事务结果类型
     * @return 事务提交后的结果信号
     */
    private <T> Mono<T> transactional(String tenant, Function<String, T> action) {
        return JdbcTenantScope.call(tenant, t -> new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                .execute(status -> action.apply(t)));
    }

    private int revokeUserTokens(String tenant, Long userId, String revokedBy, String reason) {
        return mapper.update(
                null,
                JdbcTenantScope.<TokenPO>update(tenant)
                        .eq("user_id", userId)
                        .eq("revoked", 0)
                        .set("revoked", 1)
                        .set("revoked_at", LocalDateTime.now())
                        .set("revoked_by", revokedBy)
                        .set("revoke_reason", reason));
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
        return transactional(entity.getTenantId(), t -> {
            if (entity.getId() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR);
            }
            TokenPO po = converter.toPo(entity);
            po.setTenantId(t);
            lockUser(t, po.getUserId());

            TokenPO existing = mapper.selectOne(
                    JdbcTenantScope.<TokenPO>query(t).eq("id", po.getId()).last("FOR UPDATE"));
            if (existing == null) {
                throw new BusinessException(ResultCode.NOT_FOUND);
            }
            if (!Objects.equals(existing.getUserId(), po.getUserId())) {
                throw new BusinessException(ResultCode.FORBIDDEN);
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
        return revokeByUserId(userId, tenantId, "system", "logout").map(count -> count > 0);
    }

    @Override
    public Mono<Boolean> revokeByToken(String token) {
        return revokeMatching("token_value", token, "system", "manual").map(count -> count > 0);
    }

    @Override
    public Mono<Boolean> revokeByJti(String jti) {
        return revokeByJti(jti, "system", "manual");
    }

    @Override
    public Mono<Boolean> revokeByJti(String jti, String revokedBy, String reason) {
        return revokeMatching("jti", jti, revokedBy, reason).map(count -> count > 0);
    }

    @Override
    public Mono<Integer> revokeByUserId(Long userId, String tenantId, String revokedBy, String reason) {
        return transactional(tenantId, t -> {
            lockUser(t, userId);
            return revokeUserTokens(t, userId, revokedBy, reason);
        });
    }

    private Mono<Integer> revokeMatching(String column, String value, String revokedBy, String reason) {
        return transactional(null, t -> {
            List<Long> userIds =
                    mapper
                            .selectList(JdbcTenantScope.<TokenPO>query(t)
                                    .select("user_id")
                                    .eq(column, value))
                            .stream()
                            .map(TokenPO::getUserId)
                            .distinct()
                            .sorted()
                            .toList();
            if (userIds.isEmpty()) {
                return 0;
            }
            List<UserPO> lockedUsers = users.selectList(JdbcTenantScope.<UserPO>query(t)
                    .in("id", userIds)
                    .orderByAsc("id")
                    .last("FOR UPDATE"));
            if (lockedUsers.size() != userIds.size()) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
            return mapper.update(
                    null,
                    JdbcTenantScope.<TokenPO>update(t)
                            .eq(column, value)
                            .in("user_id", userIds)
                            .eq("revoked", 0)
                            .set("revoked", 1)
                            .set("revoked_at", LocalDateTime.now())
                            .set("revoked_by", revokedBy)
                            .set("revoke_reason", reason));
        });
    }

    @Override
    public Mono<Boolean> existsByToken(String token) {
        return JdbcTenantScope.call(
                null, t -> mapper.selectCount(JdbcTenantScope.<TokenPO>query(t).eq("token_value", token)) > 0);
    }

    @Override
    public Mono<Integer> revokeByTokenFamily(String tokenFamily, String revokedBy, String reason) {
        return revokeMatching("token_family", tokenFamily, revokedBy, reason);
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
