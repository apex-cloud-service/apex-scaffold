package com.apex.scaffold.authcenter.infrastructure.persistence;

import static com.apex.scaffold.authcenter.infrastructure.persistence.RepositoryTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.authcenter.domain.model.entity.*;
import com.apex.scaffold.authcenter.infrastructure.persistence.converter.*;
import com.apex.scaffold.authcenter.infrastructure.persistence.mapper.*;
import com.apex.scaffold.authcenter.infrastructure.persistence.po.*;
import com.apex.scaffold.authcenter.infrastructure.persistence.repository.*;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.result.ResultCode;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataAccessResourceFailureException;

class UserRepositoryTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 1, 1, 0, 0);
    private final UserMapper mapper = mock(UserMapper.class);

    private final UserRepositoryImpl repository =
            new UserRepositoryImpl(mapper, Mappers.getMapper(UserConverter.class));

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
                    : UserPO.builder().id(7L).tenantId(TENANT).build();
        });

        var result = repository.findById(7L);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectOne(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByUsernameScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "username", "=", "alice");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? null
                    : UserPO.builder().id(7L).tenantId(TENANT).build();
        });

        var result = repository.findByUsername("alice", TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectOne(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByEmailScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "email", "=", "a@example.test");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? null
                    : UserPO.builder().id(7L).tenantId(TENANT).build();
        });

        var result = repository.findByEmail("a@example.test", TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectOne(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByPhoneScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "phone", "=", "123");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? null
                    : UserPO.builder().id(7L).tenantId(TENANT).build();
        });

        var result = repository.findByPhone("123", TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectOne(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByTenantIdScopesQueryAndPropagatesResult(String outcome) {
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
                    : List.of(UserPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findByTenantId(TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "zero", "error"})
    void saveSetsDefaultsReturnsGeneratedIdAndPropagatesWriteFailures(String outcome) {

        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("insert failed");
        when(mapper.insert(any(UserPO.class))).thenAnswer(invocation -> {
            UserPO po = invocation.getArgument(0);
            assertThat(po.getTenantId()).isEqualTo(TENANT);
            assertThat(po.getCreatedAt()).isAfter(TIME);
            assertThat(po.getStatus()).isEqualTo(1);
            assertThat(po.getFailedLoginAttempts()).isZero();
            assertThat(po.getDeleted()).isZero();
            assertThat(po.getUpdatedAt()).isEqualTo(po.getCreatedAt());
            po.setId(7L);
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 1;
        });
        var result = repository.save(UserEntity.builder().tenantId(TENANT).build());
        verifyNoInteractions(mapper);
        if (outcome.equals("zero")) {
            businessError(result, ResultCode.PARAM_ERROR);
        } else {
            entity(result, outcome, failure);
        }
        verify(mapper).insert(any(UserPO.class));
    }

    @Test
    void saveRejectsForeignTenantBeforeDatabaseAccess() {
        businessError(repository.save(UserEntity.builder().tenantId("tenant-b").build()), ResultCode.FORBIDDEN);
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "zero", "error"})
    void updateScopesWriteAndHandlesConflicts(String outcome) {
        when(mapper.selectOne(any(Wrapper.class)))
                .thenReturn(UserPO.builder().id(7L).tenantId(TENANT).build());
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("update failed");
        when(mapper.update(any(UserPO.class), any(Wrapper.class))).thenAnswer(invocation -> {
            UserPO po = invocation.getArgument(0);
            Wrapper<?> query = invocation.getArgument(1);
            scoped(query);
            condition(query, "id", "=", 7L);
            assertThat(po.getTenantId()).isEqualTo(TENANT);
            assertThat(po.getCreatedAt()).isNull();

            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 1;
        });
        var result = repository.update(
                UserEntity.builder().id(7L).tenantId(TENANT).createdAt(TIME).build());
        if (outcome.equals("zero")) {
            businessError(result, ResultCode.PARAM_ERROR);
        } else {
            entity(result, outcome, failure);
        }
        verify(mapper).update(any(UserPO.class), any(Wrapper.class));
    }

    @Test
    void updateRejectsMissingIdBeforeDatabaseAccess() {

        businessError(repository.update(UserEntity.builder().tenantId(TENANT).build()), ResultCode.PARAM_ERROR);
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
    void existsByUsernameScopesCount(long count) {
        when(mapper.selectCount(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "username", "=", "alice");
            return count;
        });
        value(repository.existsByUsername("alice", TENANT), count > 0);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L})
    void existsByEmailScopesCount(long count) {
        when(mapper.selectCount(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "email", "=", "a@example.test");
            return count;
        });
        value(repository.existsByEmail("a@example.test", TENANT), count > 0);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L})
    void existsByPhoneScopesCount(long count) {
        when(mapper.selectCount(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "phone", "=", "123");
            return count;
        });
        value(repository.existsByPhone("123", TENANT), count > 0);
    }
}
