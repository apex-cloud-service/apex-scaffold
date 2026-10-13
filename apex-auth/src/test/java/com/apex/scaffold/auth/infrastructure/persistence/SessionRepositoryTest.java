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

class SessionRepositoryTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 1, 1, 0, 0);
    private final SessionMapper mapper = mock(SessionMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final TokenMapper tokens = mock(TokenMapper.class);
    private final SessionRepositoryImpl repository =
            new SessionRepositoryImpl(mapper, Mappers.getMapper(SessionConverter.class), users, tokens);

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
                    : SessionPO.builder().id(7L).tenantId(TENANT).build();
        });

        var result = repository.findById(7L);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectOne(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findBySessionIdScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "session_id", "=", "session");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? null
                    : SessionPO.builder().id(7L).tenantId(TENANT).build();
        });

        var result = repository.findBySessionId("session");
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectOne(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByTokenIdScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "token_id", "=", 9L);
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? null
                    : SessionPO.builder().id(7L).tenantId(TENANT).build();
        });

        var result = repository.findByTokenId(9L);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectOne(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findAllScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);

            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? List.of()
                    : List.of(SessionPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findAll();
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
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
                    : List.of(SessionPO.builder().id(7L).tenantId(TENANT).build());
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
            condition(query, "status", "=", "ACTIVE");
            assertThat(query.getSqlSegment()).contains("expires_at >");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? List.of()
                    : List.of(SessionPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findActiveByUserId(9L, TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findExpiredSessionsScopesQueryAndPropagatesResult(String outcome) {
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
                    : List.of(SessionPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findExpiredSessions();
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "zero", "error"})
    void saveSetsDefaultsReturnsGeneratedIdAndPropagatesWriteFailures(String outcome) {
        when(users.selectCount(any(Wrapper.class))).thenAnswer(invocation -> {
            scoped(invocation.getArgument(0));
            condition(invocation.getArgument(0), "id", "=", 9L);
            return 1L;
        });
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("insert failed");
        when(mapper.insert(any(SessionPO.class))).thenAnswer(invocation -> {
            SessionPO po = invocation.getArgument(0);
            assertThat(po.getTenantId()).isEqualTo(TENANT);
            assertThat(po.getCreatedAt()).isAfter(TIME);
            assertThat(po.getStatus()).isEqualTo("ACTIVE");
            assertThat(po.getVersion()).isZero();
            assertThat(po.getDeleted()).isZero();
            assertThat(po.getLastActiveAt()).isEqualTo(po.getCreatedAt());
            po.setId(7L);
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 1;
        });
        var result = repository.save(
                SessionEntity.builder().tenantId(TENANT).userId(9L).build());
        verifyNoInteractions(mapper);
        if (outcome.equals("zero")) {
            businessError(result, ResultCode.PARAM_ERROR);
        } else {
            entity(result, outcome, failure);
        }
        verify(mapper).insert(any(SessionPO.class));
    }

    @Test
    void saveRejectsForeignTenantBeforeDatabaseAccess() {
        businessError(
                repository.save(
                        SessionEntity.builder().tenantId("tenant-b").userId(9L).build()),
                ResultCode.FORBIDDEN);
        verifyNoInteractions(mapper, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "zero", "error"})
    void updateScopesWriteAndHandlesConflicts(String outcome) {
        when(users.selectCount(any(Wrapper.class))).thenAnswer(invocation -> {
            scoped(invocation.getArgument(0));
            condition(invocation.getArgument(0), "id", "=", 9L);
            return 1L;
        });
        when(mapper.selectOne(any(Wrapper.class)))
                .thenReturn(SessionPO.builder().id(7L).tenantId(TENANT).build());
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("update failed");
        when(mapper.update(any(SessionPO.class), any(Wrapper.class))).thenAnswer(invocation -> {
            SessionPO po = invocation.getArgument(0);
            Wrapper<?> query = invocation.getArgument(1);
            scoped(query);
            condition(query, "id", "=", 7L);
            assertThat(po.getTenantId()).isEqualTo(TENANT);
            assertThat(po.getCreatedAt()).isNull();
            condition(query, "version", "=", 3);
            assertThat(po.getVersion()).isEqualTo(4);
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 1;
        });
        var result = repository.update(SessionEntity.builder()
                .id(7L)
                .tenantId(TENANT)
                .createdAt(TIME)
                .userId(9L)
                .version(3)
                .build());
        if (outcome.equals("zero")) {
            businessError(result, ResultCode.PARAM_ERROR);
        } else {
            entity(result, outcome, failure);
        }
        verify(mapper).update(any(SessionPO.class), any(Wrapper.class));
    }

    @Test
    void updateRejectsMissingIdBeforeDatabaseAccess() {

        businessError(
                repository.update(SessionEntity.builder()
                        .tenantId(TENANT)
                        .userId(9L)
                        .version(3)
                        .build()),
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

    @Test
    void foreignUserCannotCreateOrUpdate() {
        when(users.selectCount(any(Wrapper.class))).thenAnswer(invocation -> {
            scoped(invocation.getArgument(0));
            condition(invocation.getArgument(0), "id", "=", 9L);
            return 0L;
        });
        SessionEntity input = SessionEntity.builder()
                .id(7L)
                .tenantId(TENANT)
                .userId(9L)
                .version(3)
                .build();
        businessError(repository.save(input), ResultCode.FORBIDDEN);
        businessError(repository.update(input), ResultCode.FORBIDDEN);
        verifyNoInteractions(mapper);
    }

    @Test
    void foreignTokenCannotCreateOrUpdateSession() {
        when(users.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(tokens.selectCount(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "id", "=", 11L);
            condition(query, "user_id", "=", 9L);
            return 0L;
        });
        SessionEntity input = SessionEntity.builder()
                .id(7L)
                .tenantId(TENANT)
                .userId(9L)
                .tokenId(11L)
                .version(3)
                .build();
        businessError(repository.save(input), ResultCode.FORBIDDEN);
        businessError(repository.update(input), ResultCode.FORBIDDEN);
        verifyNoInteractions(mapper);
    }

    @Test
    void updateRequiresVersion() {
        when(users.selectCount(any(Wrapper.class))).thenReturn(1L);

        businessError(
                repository.update(SessionEntity.builder()
                        .id(7L)
                        .tenantId(TENANT)
                        .userId(9L)
                        .build()),
                ResultCode.PARAM_ERROR);
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "zero", "error"})
    void terminateByUserIdScopesStateTransition(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("transition failed");
        when(mapper.update(isNull(), any(Wrapper.class))).thenAnswer(invocation -> {
            UpdateWrapper<?> query = invocation.getArgument(1);
            scoped(query);
            condition(query, "user_id", "=", 9L);
            condition(query, "status", "=", "ACTIVE");
            assertThat(query.getSqlSet()).contains("last_active_at", "version = version + 1");
            assertThat(query.getSqlSet()).contains("status");
            assertThat(query.getParamNameValuePairs().values()).contains("TERMINATED");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 1;
        });
        if (outcome.equals("error")) {
            entity(repository.terminateByUserId(9L, TENANT), outcome, failure);
        } else {
            value(repository.terminateByUserId(9L, TENANT), outcome.equals("success"));
        }
        verify(mapper).update(isNull(), any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "zero", "error"})
    void terminateBySessionIdScopesStateTransition(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("transition failed");
        when(mapper.update(isNull(), any(Wrapper.class))).thenAnswer(invocation -> {
            UpdateWrapper<?> query = invocation.getArgument(1);
            scoped(query);
            condition(query, "session_id", "=", "session");
            condition(query, "status", "=", "ACTIVE");
            assertThat(query.getSqlSet()).contains("last_active_at", "version = version + 1");
            assertThat(query.getSqlSet()).contains("status");
            assertThat(query.getParamNameValuePairs().values()).contains("TERMINATED");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 1;
        });
        if (outcome.equals("error")) {
            entity(repository.terminateBySessionId("session"), outcome, failure);
        } else {
            value(repository.terminateBySessionId("session"), outcome.equals("success"));
        }
        verify(mapper).update(isNull(), any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "zero", "error"})
    void updateLastActiveScopesStateTransition(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("transition failed");
        when(mapper.update(isNull(), any(Wrapper.class))).thenAnswer(invocation -> {
            UpdateWrapper<?> query = invocation.getArgument(1);
            scoped(query);
            condition(query, "session_id", "=", "session");
            condition(query, "status", "=", "ACTIVE");
            assertThat(query.getSqlSet()).contains("last_active_at", "version = version + 1");
            assertThat(query.getSqlSegment()).contains("expires_at >");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 1;
        });
        if (outcome.equals("error")) {
            entity(repository.updateLastActive("session"), outcome, failure);
        } else {
            value(repository.updateLastActive("session"), outcome.equals("success"));
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
        value(repository.cleanupExpiredSessions(), (long) count);
        verify(mapper).delete(any(Wrapper.class));
    }

    @Test
    void cleanupPropagatesDatabaseFailure() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("cleanup failed");
        when(mapper.delete(any(Wrapper.class))).thenThrow(failure);
        entity(repository.cleanupExpiredSessions(), "error", failure);
    }
}
