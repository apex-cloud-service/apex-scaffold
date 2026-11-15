package com.apex.scaffold.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.auth.domain.model.entity.TokenEntity;
import com.apex.scaffold.auth.domain.model.vo.TokenEntityPair;
import com.apex.scaffold.auth.domain.model.vo.TokenRotationResult;
import com.apex.scaffold.auth.infrastructure.persistence.converter.TokenConverter;
import com.apex.scaffold.auth.infrastructure.persistence.mapper.TokenMapper;
import com.apex.scaffold.auth.infrastructure.persistence.mapper.UserMapper;
import com.apex.scaffold.auth.infrastructure.persistence.po.TokenPO;
import com.apex.scaffold.auth.infrastructure.persistence.repository.TokenRepositoryImpl;
import com.apex.scaffold.common.context.TenantContext;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.test.StepVerifier;

class TokenRepositoryTransactionTest {
    private static final String TENANT = "tenant-a";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private TokenRepositoryImpl first;
    private TokenRepositoryImpl second;
    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
    private WriteHook firstHook;
    private WriteHook secondHook;
    private TokenEntity oldRefresh;

    @BeforeEach
    void setup() throws Exception {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute(
                """
                CREATE TABLE auth_user (
                    id BIGINT PRIMARY KEY, tenant_id VARCHAR(64), username VARCHAR(128), email VARCHAR(128),
                    phone VARCHAR(32), password_hash VARCHAR(256), open_id VARCHAR(128), account_id BIGINT,
                    status INT, last_login_at TIMESTAMP, last_login_ip VARCHAR(64), failed_login_attempts INT,
                    locked_until TIMESTAMP, created_at TIMESTAMP, updated_at TIMESTAMP, deleted INT DEFAULT 0)
                """);
        jdbc.execute(
                """
                CREATE TABLE auth_token (
                    id BIGINT PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, user_id BIGINT NOT NULL,
                    token_type VARCHAR(16), token_value VARCHAR(1024) CHECK (token_value <> 'fail'),
                    jti VARCHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMP(9), revoked INT DEFAULT 0,
                    revoked_at TIMESTAMP, revoked_by VARCHAR(64), revoke_reason VARCHAR(64),
                    last_used_at TIMESTAMP, use_count INT, device_id VARCHAR(64), client_ip VARCHAR(64),
                    user_agent VARCHAR(256), created_at TIMESTAMP, token_hash VARCHAR(128), token_iv VARCHAR(128),
                    token_family VARCHAR(64), previous_jti VARCHAR(64), rotation_count INT)
                """);
        jdbc.execute("CREATE INDEX idx_token_user ON auth_token(tenant_id, user_id, revoked)");
        jdbc.update(
                "INSERT INTO auth_user(id, tenant_id, status, deleted) VALUES (9, ?, 1, 0), (10, ?, 1, 0)",
                TENANT,
                "tenant-b");
        firstHook = new WriteHook(connections);
        first = repository(firstHook);
        secondHook = new WriteHook(connections);
        second = repository(secondHook);
        TokenEntityPair original = pair("old", TENANT, 9L);
        StepVerifier.create(scoped(first.savePair(original)))
                .assertNext(saved -> oldRefresh = saved.refreshToken())
                .verifyComplete();
        connections.clear();
    }

    @AfterEach
    void closeDatabase() {
        jdbc.execute("SHUTDOWN");
        TenantContext.clear();
    }

    private TokenRepositoryImpl repository(WriteHook hook) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(TokenMapper.class);
        configuration.addMapper(UserMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setPlugins(hook);
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        return new TokenRepositoryImpl(
                session.getMapper(TokenMapper.class),
                Mappers.getMapper(TokenConverter.class),
                session.getMapper(UserMapper.class),
                dataSource);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCESS", "REFRESH"})
    void insertFailureRollsBackConsumptionAndBothNewRows(String failingType) {
        TokenEntityPair prepared = pair("failed", TENANT, 9L);
        (failingType.equals("ACCESS") ? prepared.accessToken() : prepared.refreshToken()).setTokenValue("fail");
        StepVerifier.create(scoped(first.rotateRefreshToken(oldRefresh, prepared)))
                .expectError(DataAccessException.class)
                .verify(TIMEOUT);
        assertThat(revoked("old-refresh")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_token", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT revoke_reason FROM auth_token WHERE jti='old-refresh'", String.class))
                .isNull();
        assertThat(oldRefresh.isRevoked()).isFalse();
    }

    @Test
    void initialPairRefreshInsertFailureRollsBackAccessInsert() {
        TokenEntityPair pair = pair("initial-failed", TENANT, 9L);
        pair.refreshToken().setTokenValue("fail");
        StepVerifier.create(scoped(first.savePair(pair)))
                .expectError(DataAccessException.class)
                .verify(TIMEOUT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_token", Integer.class))
                .isEqualTo(2);
        assertThat(pair.accessToken().getId()).isNull();
    }

    @Test
    void simultaneousConsumptionCommitsOnePairThenReplayRevokesIt() {
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        firstHook.afterWrite = parameter -> {
            if (parameter instanceof TokenPO po && "winner-access".equals(po.getJti())) {
                inserted.countDown();
                await(release);
            }
        };
        Mono<Signal<TokenRotationResult>> winner = scoped(
                        first.rotateRefreshToken(oldRefresh, pair("winner", TENANT, 9L)))
                .materialize();
        Mono<Signal<TokenRotationResult>> replay = Mono.fromRunnable(() -> await(inserted))
                .then(scoped(second.rotateRefreshToken(oldRefresh, pair("loser", TENANT, 9L))))
                .materialize();
        var winning = winner.toFuture();
        await(inserted);
        CountDownLatch competing = new CountDownLatch(1);
        secondHook.beforeLock = competing::countDown;
        var losing = replay.toFuture();
        await(competing);
        assertThat(losing.isDone()).isFalse();
        release.countDown();
        StepVerifier.create(Mono.zip(Mono.fromFuture(winning), Mono.fromFuture(losing)))
                .assertNext(results -> {
                    assertThat(results.getT1().get().status()).isEqualTo(TokenRotationResult.Status.ROTATED);
                    assertThat(results.getT2().get().status()).isEqualTo(TokenRotationResult.Status.REPLAY);
                })
                .verifyComplete();
        assertThat(connections).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_token", Integer.class))
                .isEqualTo(4);
        assertThat(activeCount(TENANT)).isZero();
        assertThat(revoked("old-refresh")).isEqualTo(1);
    }

    @Test
    void failedFirstTransactionAllowsIndependentWaiterToConsumeOldRefresh() {
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        firstHook.afterWrite = parameter -> {
            if (parameter instanceof TokenPO po && "failed-access".equals(po.getJti())) {
                inserted.countDown();
                await(release);
            }
        };
        TokenEntityPair failing = pair("failed", TENANT, 9L);
        failing.refreshToken().setTokenValue("fail");
        var failed = scoped(first.rotateRefreshToken(oldRefresh, failing))
                .materialize()
                .toFuture();
        await(inserted);
        CountDownLatch competing = new CountDownLatch(1);
        secondHook.beforeLock = competing::countDown;
        var succeeds = scoped(second.rotateRefreshToken(oldRefresh, pair("survivor", TENANT, 9L)))
                .toFuture();
        await(competing);
        assertThat(succeeds.isDone()).isFalse();
        release.countDown();
        StepVerifier.create(Mono.zip(Mono.fromFuture(failed), Mono.fromFuture(succeeds)))
                .assertNext(results -> {
                    assertThat(results.getT1().getThrowable()).isInstanceOf(DataAccessException.class);
                    assertThat(results.getT2().status()).isEqualTo(TokenRotationResult.Status.ROTATED);
                })
                .verifyComplete();
        assertThat(connections).hasSize(2);
        assertThat(activeCount(TENANT)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_token WHERE jti LIKE 'failed-%'", Integer.class))
                .isZero();
        assertThat(revoked("old-refresh")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ancestorReplayAndDescendantRotationCannotLeaveActiveDescendants(boolean replayFirst) {
        TokenEntity descendant = rotate(first, oldRefresh, "child").tokens().refreshToken();
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        firstHook.afterWrite = parameter -> {
            if (replayFirst || parameter instanceof TokenPO po && "grandchild-access".equals(po.getJti())) {
                written.countDown();
                await(release);
            }
        };
        var firstOperation = scoped(
                        replayFirst
                                ? first.checkRefreshReplay(oldRefresh)
                                : first.rotateRefreshToken(descendant, pair("grandchild", TENANT, 9L)))
                .toFuture();
        await(written);
        CountDownLatch competing = new CountDownLatch(1);
        secondHook.beforeLock = competing::countDown;
        var secondOperation = scoped(
                        replayFirst
                                ? second.rotateRefreshToken(descendant, pair("blocked", TENANT, 9L))
                                : second.checkRefreshReplay(oldRefresh))
                .toFuture();
        await(competing);
        assertThat(secondOperation.isDone()).isFalse();
        release.countDown();
        StepVerifier.create(Mono.zip(Mono.fromFuture(firstOperation), Mono.fromFuture(secondOperation)))
                .assertNext(results -> {
                    assertThat(results.getT1().status())
                            .isEqualTo(
                                    replayFirst
                                            ? TokenRotationResult.Status.REPLAY
                                            : TokenRotationResult.Status.ROTATED);
                    assertThat(results.getT2().status())
                            .isEqualTo(
                                    replayFirst
                                            ? TokenRotationResult.Status.REJECTED
                                            : TokenRotationResult.Status.REPLAY);
                })
                .verifyComplete();
        assertThat(connections.size()).isGreaterThanOrEqualTo(2);
        assertThat(activeCount(TENANT)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_token WHERE jti LIKE 'blocked-%'", Integer.class))
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"manual", "logout", "password_changed"})
    void ordinaryRevocationRejectsRefreshWithoutRevokingOtherSessions(String reason) {
        StepVerifier.create(scoped(first.revokeByJti(oldRefresh.getJti(), "user:9", reason)))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(scoped(second.rotateRefreshToken(oldRefresh, pair("rejected", TENANT, 9L))))
                .assertNext(result -> assertThat(result.status()).isEqualTo(TokenRotationResult.Status.REJECTED))
                .verifyComplete();
        assertThat(activeCount(TENANT)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_token", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void replayCommitsTenantUserRevocationWithoutTouchingOtherTenantAndNewLoginCanCreateNewFamily() {
        TokenEntityPair other = pair("other", "tenant-b", 10L);
        StepVerifier.create(second.savePair(other).contextWrite(TenantContext.withTenant("tenant-b")))
                .expectNextCount(1)
                .verifyComplete();
        rotate(first, oldRefresh, "rotated");
        StepVerifier.create(scoped(second.checkRefreshReplay(oldRefresh)))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(TokenRotationResult.Status.REPLAY);
                    assertThat(activeCount(TENANT)).isZero();
                    assertThat(activeCount("tenant-b")).isEqualTo(2);
                })
                .verifyComplete();
        StepVerifier.create(scoped(first.savePair(pair("new-login", TENANT, 9L))))
                .expectNextCount(1)
                .verifyComplete();
        assertThat(activeCount(TENANT)).isEqualTo(2);
    }

    @Test
    void legacyFamilyIsPersistedOnConsumedRefreshAndInheritedByBothChildren() {
        jdbc.update("UPDATE auth_token SET token_family=NULL, rotation_count=NULL WHERE jti=?", oldRefresh.getJti());
        oldRefresh.setTokenFamily(null);
        oldRefresh.setRotationCount(null);
        TokenRotationResult result = rotate(first, oldRefresh, "legacy");
        assertThat(result.tokens().accessToken().getTokenFamily())
                .isEqualTo(result.tokens().refreshToken().getTokenFamily());
        assertThat(result.tokens().refreshToken().getPreviousJti()).isEqualTo(oldRefresh.getJti());
        assertThat(result.tokens().refreshToken().getRotationCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT token_family FROM auth_token WHERE jti=?", String.class, oldRefresh.getJti()))
                .isEqualTo(result.tokens().refreshToken().getTokenFamily());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "hash", "jti", "type", "expiry", "user", "tenant"})
    void expectedRefreshIdentityMismatchCannotConsumeOrRevoke(String field) {
        switch (field) {
            case "id" -> oldRefresh.setId(-1L);
            case "hash" -> oldRefresh.setTokenHash("wrong");
            case "jti" -> oldRefresh.setJti("wrong");
            case "type" -> oldRefresh.setTokenType("ACCESS");
            case "expiry" -> oldRefresh.setExpiresAt(oldRefresh.getExpiresAt().plusSeconds(1));
            case "user" -> oldRefresh.setUserId(10L);
            case "tenant" -> oldRefresh.setTenantId("tenant-b");
            default -> throw new IllegalArgumentException(field);
        }
        StepVerifier.create(scoped(first.checkRefreshReplay(oldRefresh)).materialize())
                .assertNext(signal -> {
                    if (field.equals("tenant") || field.equals("user")) {
                        assertThat(signal.isOnError()).isTrue();
                    } else {
                        assertThat(signal.get().status()).isEqualTo(TokenRotationResult.Status.REJECTED);
                    }
                })
                .verifyComplete();
        assertThat(revoked("old-refresh")).isZero();
        assertThat(activeCount(TENANT)).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jti", "jti-reason", "token", "family", "user", "user-reason", "update"})
    void allRevocationPathsWaitForSameUserLockBeforeWriting(String path) {
        TokenEntity child = rotate(first, oldRefresh, "child").tokens().refreshToken();
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        firstHook.afterWrite = parameter -> {
            if (parameter instanceof TokenPO po && "next-access".equals(po.getJti())) {
                inserted.countDown();
                await(release);
            }
        };
        var rotating = scoped(first.rotateRefreshToken(child, pair("next", TENANT, 9L)))
                .toFuture();
        await(inserted);
        CountDownLatch competing = new CountDownLatch(1);
        secondHook.beforeLock = competing::countDown;
        Mono<?> revoke =
                switch (path) {
                    case "jti" -> second.revokeByJti(child.getJti());
                    case "jti-reason" -> second.revokeByJti(child.getJti(), "admin", "manual");
                    case "token" -> second.revokeByToken(child.getTokenValue());
                    case "family" -> second.revokeByTokenFamily(child.getTokenFamily(), "system", "security");
                    case "user" -> second.revokeByUserId(9L, TENANT);
                    case "user-reason" -> second.revokeByUserId(9L, TENANT, "system", "security");
                    case "update" -> {
                        child.setRevoked(1);
                        child.setRevokeReason("manual");
                        yield second.update(child);
                    }
                    default -> throw new IllegalArgumentException(path);
                };
        var revoking = scoped(revoke).materialize().toFuture();
        await(competing);
        assertThat(revoking.isDone()).isFalse();
        release.countDown();
        StepVerifier.create(Mono.zip(Mono.fromFuture(rotating), Mono.fromFuture(revoking)))
                .assertNext(results -> {
                    assertThat(results.getT1().status()).isEqualTo(TokenRotationResult.Status.ROTATED);
                    assertThat(results.getT2().isOnError()).isEqualTo(path.equals("update"));
                })
                .verifyComplete();
        assertThat(revoked("child-refresh")).isEqualTo(1);
        if (path.equals("family") || path.equals("token") || path.startsWith("user")) {
            assertThat(activeCount(TENANT)).isZero();
        }
        assertThat(connections.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void expiredRefreshCannotConsumeOrCauseSecurityRevocation() {
        jdbc.update(
                "UPDATE auth_token SET expires_at=? WHERE jti=?",
                LocalDateTime.of(2020, 1, 1, 0, 0),
                oldRefresh.getJti());
        oldRefresh.setExpiresAt(LocalDateTime.of(2020, 1, 1, 0, 0));
        StepVerifier.create(scoped(first.rotateRefreshToken(oldRefresh, pair("expired", TENANT, 9L))))
                .assertNext(result -> assertThat(result.status()).isEqualTo(TokenRotationResult.Status.REJECTED))
                .verifyComplete();
        assertThat(revoked("old-refresh")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_token", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void disabledUserCannotCommitPreparedPairOrConsumeRefresh() {
        jdbc.update("UPDATE auth_user SET status=0 WHERE id=9");
        StepVerifier.create(scoped(first.savePair(pair("disabled", TENANT, 9L))))
                .expectError()
                .verify(TIMEOUT);
        StepVerifier.create(scoped(first.rotateRefreshToken(oldRefresh, pair("disabled-rotate", TENANT, 9L))))
                .expectError()
                .verify(TIMEOUT);
        assertThat(revoked("old-refresh")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_token", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void serviceReplayCommitsSecurityRevocationBeforeFailedAuditAndUnauthorizedSignal() {
        var users = org.mockito.Mockito.mock(com.apex.scaffold.auth.domain.repository.UserRepository.class);
        var encryption = org.mockito.Mockito.mock(com.apex.scaffold.auth.domain.service.TokenEncryptionService.class);
        var audit = org.mockito.Mockito.mock(com.apex.scaffold.common.audit.SecurityAuditService.class);
        var jwt = org.mockito.Mockito.mock(com.apex.scaffold.auth.domain.service.JwtKeyMaterialService.class);
        var user = com.apex.scaffold.auth.domain.model.entity.UserEntity.builder()
                .id(9L)
                .tenantId(TENANT)
                .username("user")
                .status(1)
                .build();
        org.mockito.Mockito.when(users.findById(9L)).thenReturn(Mono.just(user));
        org.mockito.Mockito.when(encryption.hashToken(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> "hash-" + invocation.getArgument(0));
        org.mockito.Mockito.when(encryption.encrypt(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new com.apex.scaffold.auth.domain.service.TokenEncryptionService.EncryptedToken(
                        "cipher", "iv"));
        org.mockito.Mockito.when(jwt.parse("old-refresh"))
                .thenReturn(Mono.just(new io.jsonwebtoken.impl.DefaultClaims(java.util.Map.of(
                        "jti", "old-refresh", "tenantId", TENANT, "userId", 9L, "tokenType", "REFRESH"))));
        org.mockito.Mockito.when(jwt.sign(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyMap(),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(invocation ->
                        Mono.just("signed-" + ((java.util.Map<?, ?>) invocation.getArgument(1)).get("jti")));
        org.mockito.Mockito.when(audit.logTokenRefreshed(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.error(new IllegalStateException("audit unavailable")));
        org.mockito.Mockito.when(audit.logTokenReplayAttack(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
                                    .isActualTransactionActive())
                            .isFalse();
                    assertThat(activeCount(TENANT)).isZero();
                    return Mono.error(new IllegalStateException("audit unavailable"));
                });
        var service =
                new com.apex.scaffold.auth.application.service.TokenServiceImpl(first, users, encryption, audit, jwt);
        StepVerifier.create(service.refreshToken("old-refresh"))
                .assertNext(pair -> {
                    assertThat(pair.getAccessToken()).startsWith("signed-");
                    assertThat(revoked("old-refresh")).isEqualTo(1);
                    assertThat(activeCount(TENANT)).isEqualTo(3);
                })
                .verifyComplete();
        StepVerifier.create(service.refreshToken("old-refresh"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(com.apex.scaffold.common.exception.BusinessException.class);
                    assertThat(((com.apex.scaffold.common.exception.BusinessException) error).getCode())
                            .isEqualTo(401);
                    assertThat(activeCount(TENANT)).isZero();
                })
                .verify(TIMEOUT);
        assertThat(revoked("old-refresh")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_token", Integer.class))
                .isEqualTo(4);
    }

    @Test
    void committedConsumedTokenCannotBeSavedOrUpdatedBackToActive() {
        rotate(first, oldRefresh, "commit");
        oldRefresh.setRevoked(0);
        StepVerifier.create(scoped(first.update(oldRefresh))).expectError().verify(TIMEOUT);
        StepVerifier.create(scoped(first.save(oldRefresh))).expectError().verify(TIMEOUT);
        assertThat(revoked("old-refresh")).isEqualTo(1);
    }

    private TokenRotationResult rotate(TokenRepositoryImpl repository, TokenEntity old, String prefix) {
        TokenRotationResult[] result = new TokenRotationResult[1];
        StepVerifier.create(scoped(repository.rotateRefreshToken(old, pair(prefix, TENANT, 9L))))
                .assertNext(value -> result[0] = value)
                .verifyComplete();
        return result[0];
    }

    private static TokenEntityPair pair(String prefix, String tenant, Long user) {
        return new TokenEntityPair(
                token(prefix + "-access", "ACCESS", tenant, user), token(prefix + "-refresh", "REFRESH", tenant, user));
    }

    private static TokenEntity token(String jti, String type, String tenant, Long user) {
        return TokenEntity.builder()
                .tenantId(tenant)
                .userId(user)
                .tokenType(type)
                .jti(jti)
                .tokenValue("encrypted")
                .tokenIv("iv")
                .tokenHash("hash-" + jti)
                .tokenFamily("family-" + jti.substring(0, jti.lastIndexOf('-')))
                .expiresAt(LocalDateTime.of(2030, 1, 1, 0, 0))
                .rotationCount(0)
                .build();
    }

    private int activeCount(String tenant) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth_token WHERE tenant_id=? AND revoked=0", Integer.class, tenant);
    }

    private int revoked(String jti) {
        return jdbc.queryForObject("SELECT revoked FROM auth_token WHERE jti=?", Integer.class, jti);
    }

    private static <T> Mono<T> scoped(Mono<T> action) {
        return action.contextWrite(TenantContext.withTenant(TENANT));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @Intercepts({
        @Signature(
                type = Executor.class,
                method = "update",
                args = {MappedStatement.class, Object.class}),
        @Signature(
                type = Executor.class,
                method = "query",
                args = {
                    MappedStatement.class,
                    Object.class,
                    org.apache.ibatis.session.RowBounds.class,
                    org.apache.ibatis.session.ResultHandler.class
                })
    })
    private static class WriteHook implements Interceptor {
        private final Set<Connection> connections;
        private volatile Consumer<Object> afterWrite = ignored -> {};
        private volatile Runnable beforeLock = () -> {};

        WriteHook(Set<Connection> connections) {
            this.connections = connections;
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            Executor executor = (Executor) invocation.getTarget();
            connections.add(executor.getTransaction().getConnection());
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            if (invocation.getMethod().getName().equals("query")
                    && statement.getId().contains("UserMapper")) {
                beforeLock.run();
            }
            Object result = invocation.proceed();
            if (invocation.getMethod().getName().equals("update")) {
                afterWrite.accept(invocation.getArgs()[1]);
            }
            return result;
        }
    }
}
