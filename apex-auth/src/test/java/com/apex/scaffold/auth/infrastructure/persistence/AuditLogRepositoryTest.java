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
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataAccessResourceFailureException;

class AuditLogRepositoryTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 1, 1, 0, 0);
    private final AuditLogMapper mapper = mock(AuditLogMapper.class);

    private final AuditLogRepositoryImpl repository =
            new AuditLogRepositoryImpl(mapper, Mappers.getMapper(AuditLogConverter.class));

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
                    : AuditLogPO.builder().id(7L).tenantId(TENANT).build();
        });

        var result = repository.findById(7L);
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
                    : List.of(AuditLogPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findByUserId(9L, TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByEventTypeScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "event_type", "=", "LOGIN");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? List.of()
                    : List.of(AuditLogPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findByEventType("LOGIN", TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByUserIdAndEventTypeScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "user_id", "=", 9L);
            condition(query, "event_type", "=", "LOGIN");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? List.of()
                    : List.of(AuditLogPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findByUserIdAndEventType(9L, "LOGIN", TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByIpAddressScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "client_ip", "=", "127.0.0.1");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? List.of()
                    : List.of(AuditLogPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findByIpAddress("127.0.0.1", TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByCreatedAtBeforeScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "created_at", "<", TIME);
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? List.of()
                    : List.of(AuditLogPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findByCreatedAtBefore(TIME, TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByTimeRangeScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "event_time", ">=", TIME);
            condition(query, "event_time", "<=", TIME.plusDays(1));
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? List.of()
                    : List.of(AuditLogPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findByTimeRange(TIME, TIME.plusDays(1), TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findFailedEventsScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "event_status", "=", "FAILURE");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? List.of()
                    : List.of(AuditLogPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findFailedEvents(TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findHighRiskEventsScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            assertThat(query.getSqlSegment()).contains("risk_level IN");
            assertThat(((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) query)
                            .getParamNameValuePairs()
                            .values())
                    .contains("HIGH", "CRITICAL");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? List.of()
                    : List.of(AuditLogPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findHighRiskEvents(TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "zero", "error"})
    void saveSetsDefaultsReturnsGeneratedIdAndPropagatesWriteFailures(String outcome) {

        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("insert failed");
        when(mapper.insert(any(AuditLogPO.class))).thenAnswer(invocation -> {
            AuditLogPO po = invocation.getArgument(0);
            assertThat(po.getTenantId()).isEqualTo(TENANT);
            assertThat(po.getCreatedAt()).isAfter(TIME);
            assertThat(po.getEventTime()).isEqualTo(po.getCreatedAt());
            po.setId(7L);
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 1;
        });
        var result = repository.save(AuditLogEntity.builder().tenantId(TENANT).build());
        verifyNoInteractions(mapper);
        if (outcome.equals("zero")) {
            businessError(result, ResultCode.PARAM_ERROR);
        } else {
            entity(result, outcome, failure);
        }
        verify(mapper).insert(any(AuditLogPO.class));
    }

    @Test
    void saveRejectsForeignTenantBeforeDatabaseAccess() {
        businessError(
                repository.save(AuditLogEntity.builder().tenantId("tenant-b").build()), ResultCode.FORBIDDEN);
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3})
    void cleanupScopesTenantAndReturnsActualCount(int count) {
        when(mapper.delete(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "created_at", "<", TIME);
            return count;
        });
        value(repository.deleteByCreatedAtBefore(TIME, TENANT), (long) count);
        verify(mapper).delete(any(Wrapper.class));
    }

    @Test
    void cleanupPropagatesDatabaseFailure() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("cleanup failed");
        when(mapper.delete(any(Wrapper.class))).thenThrow(failure);
        entity(repository.deleteByCreatedAtBefore(TIME, TENANT), "error", failure);
    }

    @Test
    void savePreservesProvidedEventTime() {
        when(mapper.insert(any(AuditLogPO.class))).thenAnswer(invocation -> {
            AuditLogPO po = invocation.getArgument(0);
            assertThat(po.getEventTime()).isEqualTo(TIME);
            po.setId(7L);
            return 1;
        });
        entity(
                repository.save(AuditLogEntity.builder()
                        .tenantId(TENANT)
                        .eventTime(TIME)
                        .build()),
                "found",
                null);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 4L})
    void countScopesInclusiveTimeRange(long count) {
        when(mapper.selectCount(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "event_time", ">=", TIME);
            condition(query, "event_time", "<=", TIME.plusDays(1));
            return count;
        });
        value(repository.countByTimeRange(TIME, TIME.plusDays(1), TENANT), count);
    }
}
