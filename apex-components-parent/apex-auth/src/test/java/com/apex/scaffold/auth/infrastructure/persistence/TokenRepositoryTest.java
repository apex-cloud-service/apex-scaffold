package com.apex.scaffold.auth.infrastructure.persistence;

import static com.apex.scaffold.auth.infrastructure.persistence.RepositoryTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.auth.domain.model.entity.*;
import com.apex.scaffold.auth.infrastructure.persistence.converter.*;
import com.apex.scaffold.auth.infrastructure.persistence.mapper.*;
import com.apex.scaffold.auth.infrastructure.persistence.po.*;
import com.apex.scaffold.auth.infrastructure.persistence.repository.*;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.result.ResultCode;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataAccessResourceFailureException;

class TokenRepositoryTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 1, 1, 0, 0);
    private final TokenMapper mapper = mock(TokenMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final javax.sql.DataSource dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
            "jdbc:h2:mem:token_mock_transactions", "sa", "");
    private final TokenRepositoryImpl repository =
            new TokenRepositoryImpl(mapper, Mappers.getMapper(TokenConverter.class), users, dataSource);

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByIdScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "id", "=", 7L);
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? null
                    : TokenPO.builder().id(7L).tenantId(TENANT).build();
        });

        var result = repository.findById(7L);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectOne(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByTokenScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "token_value", "=", "access");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? null
                    : TokenPO.builder().id(7L).tenantId(TENANT).build();
        });

        var result = repository.findByToken("access");
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectOne(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByRefreshTokenScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "token_value", "=", "refresh");
            condition(query, "token_type", "=", "REFRESH");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? null
                    : TokenPO.builder().id(7L).tenantId(TENANT).build();
        });

        var result = repository.findByRefreshToken("refresh");
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectOne(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByUserIdScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "user_id", "=", 9L);
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? List.of()
                    : List.of(TokenPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findByUserId(9L, TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findActiveByUserIdScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "user_id", "=", 9L);
            condition(query, "revoked", "=", 0);
            assertThat(query.getSqlSegment()).contains("expires_at >");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? List.of()
                    : List.of(TokenPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findActiveByUserId(9L, TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findExpiredTokensScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            assertThat(query.getSqlSegment()).contains("expires_at <=");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? List.of()
                    : List.of(TokenPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findExpiredTokens();
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "zero", "error"})
    void saveSetsDefaultsReturnsGeneratedIdAndPropagatesWriteFailures(String outcome) {
        when(users.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            scoped(invocation.getArgument(0));
            condition(invocation.getArgument(0), "id", "=", 9L);
            assertThat(((Wrapper<?>) invocation.getArgument(0)).getSqlSegment()).contains("FOR UPDATE");
            return UserPO.builder().id(9L).tenantId(TENANT).status(1).build();
        });
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("insert failed");
        when(mapper.insert(any(TokenPO.class))).thenAnswer(invocation -> {
            TokenPO po = invocation.getArgument(0);
            assertThat(po.getTenantId()).isEqualTo(TENANT);
            assertThat(po.getCreatedAt()).isAfter(TIME);
            assertThat(po.getRevoked()).isZero();
            po.setId(7L);
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 1;
        });
        var result = repository.save(
                TokenEntity.builder().tenantId(TENANT).userId(9L).build());
        verifyNoInteractions(mapper);
        if (outcome.equals("zero")) {
            businessError(result, ResultCode.PARAM_ERROR);
        } else {
            entity(result, outcome, failure);
        }
        verify(mapper).insert(any(TokenPO.class));
    }

    @Test
    void saveRejectsForeignTenantBeforeDatabaseAccess() {
        businessError(
                repository.save(
                        TokenEntity.builder().tenantId("tenant-b").userId(9L).build()),
                ResultCode.FORBIDDEN);
        verifyNoInteractions(mapper, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "zero", "error"})
    void updateScopesWriteAndHandlesConflicts(String outcome) {
        when(users.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            scoped(invocation.getArgument(0));
            condition(invocation.getArgument(0), "id", "=", 9L);
            assertThat(((Wrapper<?>) invocation.getArgument(0)).getSqlSegment()).contains("FOR UPDATE");
            return UserPO.builder().id(9L).tenantId(TENANT).status(1).build();
        });
        when(mapper.selectOne(any(Wrapper.class)))
                .thenReturn(TokenPO.builder()
                        .id(7L)
                        .tenantId(TENANT)
                        .userId(9L)
                        .revoked(0)
                        .build());
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("update failed");
        when(mapper.update(any(TokenPO.class), any(Wrapper.class))).thenAnswer(invocation -> {
            TokenPO po = invocation.getArgument(0);
            Wrapper<?> query = invocation.getArgument(1);
            scoped(query);
            condition(query, "id", "=", 7L);
            assertThat(po.getTenantId()).isEqualTo(TENANT);
            assertThat(po.getCreatedAt()).isNull();
            condition(query, "revoked", "=", 0);
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 1;
        });
        var result = repository.update(TokenEntity.builder()
                .id(7L)
                .tenantId(TENANT)
                .createdAt(TIME)
                .userId(9L)
                .build());
        if (outcome.equals("zero")) {
            businessError(result, ResultCode.PARAM_ERROR);
        } else {
            entity(result, outcome, failure);
        }
        verify(mapper).update(any(TokenPO.class), any(Wrapper.class));
    }

    @Test
    void updateRejectsMissingIdBeforeDatabaseAccess() {

        businessError(
                repository.update(
                        TokenEntity.builder().tenantId(TENANT).userId(9L).build()),
                ResultCode.PARAM_ERROR);
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void deleteScopesIdAndReportsAffectedRows(int count) {

        when(mapper.delete(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "id", "=", 7L);
            return count;
        });
        value(repository.deleteById(7L), count > 0);
        verify(mapper).delete(any(Wrapper.class));
    }

    @Test
    void deletePropagatesDatabaseFailure() {

        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("delete failed");
        when(mapper.delete(any(Wrapper.class))).thenThrow(failure);
        entity(repository.deleteById(7L), "error", failure);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L})
    void existsByTokenScopesCount(long count) {
        when(mapper.selectCount(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "token_value", "=", "access");
            return count;
        });
        value(repository.existsByToken("access"), count > 0);
    }

    @Test
    void foreignUserCannotCreateOrUpdate() {
        when(users.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            scoped(invocation.getArgument(0));
            condition(invocation.getArgument(0), "id", "=", 9L);
            return null;
        });
        TokenEntity input = TokenEntity.builder().tenantId(TENANT).userId(9L).build();
        businessError(repository.save(input), ResultCode.FORBIDDEN);
        input.setId(7L);
        businessError(repository.update(input), ResultCode.FORBIDDEN);
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "revoked"})
    void updateRejectsMissingOrRevokedToken(String state) {
        when(users.selectOne(any(Wrapper.class)))
                .thenReturn(UserPO.builder().id(9L).tenantId(TENANT).status(1).build());
        when(mapper.selectOne(any(Wrapper.class)))
                .thenReturn(
                        state.equals("missing")
                                ? null
                                : TokenPO.builder().id(7L).userId(9L).revoked(1).build());
        businessError(
                repository.update(
                        TokenEntity.builder().id(7L).tenantId(TENANT).userId(9L).build()),
                state.equals("missing") ? ResultCode.NOT_FOUND : ResultCode.FORBIDDEN);
        verify(mapper, never()).update(any(TokenPO.class), any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "zero", "error"})
    void revokeByUserIdScopesStateTransition(String outcome) {
        when(users.selectOne(any(Wrapper.class)))
                .thenReturn(UserPO.builder().id(9L).tenantId(TENANT).build());
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("transition failed");
        when(mapper.update(isNull(), any(Wrapper.class))).thenAnswer(invocation -> {
            UpdateWrapper<?> query = invocation.getArgument(1);
            scoped(query);
            condition(query, "user_id", "=", 9L);
            condition(query, "revoked", "=", 0);
            assertThat(query.getSqlSet()).contains("revoked", "revoked_at");
            assertThat(query.getParamNameValuePairs().values()).contains(1);
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 1;
        });
        if (outcome.equals("error")) {
            entity(repository.revokeByUserId(9L, TENANT), outcome, failure);
        } else {
            value(repository.revokeByUserId(9L, TENANT), outcome.equals("success"));
        }
        verify(mapper).update(isNull(), any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "zero", "error"})
    void revokeByTokenScopesStateTransition(String outcome) {
        when(mapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(TokenPO.builder().userId(9L).build()));
        when(users.selectList(any(Wrapper.class)))
                .thenReturn(List.of(UserPO.builder().id(9L).tenantId(TENANT).build()));
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("transition failed");
        when(mapper.update(isNull(), any(Wrapper.class))).thenAnswer(invocation -> {
            UpdateWrapper<?> query = invocation.getArgument(1);
            scoped(query);
            condition(query, "token_value", "=", "access");
            condition(query, "revoked", "=", 0);
            assertThat(query.getSqlSet()).contains("revoked", "revoked_at");
            assertThat(query.getParamNameValuePairs().values()).contains(1);
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 1;
        });
        if (outcome.equals("error")) {
            entity(repository.revokeByToken("access"), outcome, failure);
        } else {
            value(repository.revokeByToken("access"), outcome.equals("success"));
        }
        verify(mapper).update(isNull(), any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3})
    void cleanupScopesTenantAndReturnsActualCount(int count) {
        when(mapper.delete(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            assertThat(query.getSqlSegment()).contains("expires_at <=");
            return count;
        });
        value(repository.deleteExpiredTokens(), (long) count);
        verify(mapper).delete(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"rotated", "replay", "manual", "cas-zero"})
    void rotationScopesCasAndReturnsCommittedClassification(String state) {
        var expiry = LocalDateTime.of(2030, 1, 1, 0, 0);
        TokenEntity expected = TokenEntity.builder()
                .id(7L)
                .tenantId(TENANT)
                .userId(9L)
                .jti("old")
                .tokenHash("old-hash")
                .tokenType("REFRESH")
                .expiresAt(expiry)
                .build();
        TokenPO current = TokenPO.builder()
                .id(7L)
                .tenantId(TENANT)
                .userId(9L)
                .jti("old")
                .tokenType("REFRESH")
                .tokenHash("old-hash")
                .expiresAt(expiry)
                .revoked(state.equals("replay") || state.equals("manual") ? 1 : 0)
                .revokeReason(state.equals("replay") ? "token_refresh" : "manual")
                .tokenFamily("family")
                .build();
        when(users.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "id", "=", 9L);
            assertThat(query.getSqlSegment()).contains("FOR UPDATE");
            return UserPO.builder().id(9L).status(1).tenantId(TENANT).build();
        });
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "user_id", "=", 9L);
            condition(query, "jti", "=", "old");
            condition(query, "token_type", "=", "REFRESH");
            assertThat(query.getSqlSegment()).contains("FOR UPDATE");
            return current;
        });
        when(mapper.update(isNull(), any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(1);
            scoped(query);
            condition(query, "user_id", "=", 9L);
            condition(query, "revoked", "=", 0);
            if (!state.equals("replay")) {
                condition(query, "id", "=", 7L);
                condition(query, "jti", "=", "old");
                condition(query, "token_hash", "=", "old-hash");
                condition(query, "expires_at", "=", expiry);
                condition(query, "token_type", "=", "REFRESH");
                assertThat(query.getSqlSegment()).contains("expires_at >");
            }
            return state.equals("cas-zero") ? 0 : 1;
        });
        when(mapper.insert(any(TokenPO.class))).thenAnswer(invocation -> {
            TokenPO po = invocation.getArgument(0);
            po.setId(po.getTokenType().equals("ACCESS") ? 8L : 9L);
            return 1;
        });
        TokenEntity access = TokenEntity.builder()
                .tenantId(TENANT)
                .userId(9L)
                .tokenType("ACCESS")
                .jti("new-access")
                .expiresAt(expiry)
                .tokenFamily("prepared")
                .build();
        TokenEntity refresh = TokenEntity.builder()
                .tenantId(TENANT)
                .userId(9L)
                .tokenType("REFRESH")
                .jti("new-refresh")
                .expiresAt(expiry)
                .tokenFamily("prepared")
                .build();
        reactor.test.StepVerifier.create(repository
                        .rotateRefreshToken(
                                expected, new com.apex.scaffold.auth.domain.model.vo.TokenEntityPair(access, refresh))
                        .contextWrite(TenantContext.withTenant(TENANT)))
                .assertNext(result -> {
                    assertThat(result.status().name())
                            .isEqualTo(
                                    state.equals("rotated")
                                            ? "ROTATED"
                                            : state.equals("replay") ? "REPLAY" : "REJECTED");
                    if (state.equals("rotated")) {
                        assertThat(result.tokens().accessToken().getId()).isEqualTo(8L);
                        assertThat(result.tokens().refreshToken().getPreviousJti())
                                .isEqualTo("old");
                        assertThat(result.tokens().refreshToken().getTokenFamily())
                                .isEqualTo("family");
                    }
                })
                .verifyComplete();
        org.mockito.InOrder order = inOrder(users, mapper);
        order.verify(users).selectOne(any(Wrapper.class));
        order.verify(mapper).selectOne(any(Wrapper.class));
        verify(mapper, times(state.equals("rotated") ? 2 : 0)).insert(any(TokenPO.class));
    }

    @Test
    void cleanupPropagatesDatabaseFailure() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("cleanup failed");
        when(mapper.delete(any(Wrapper.class))).thenThrow(failure);
        entity(repository.deleteExpiredTokens(), "error", failure);
    }
}
