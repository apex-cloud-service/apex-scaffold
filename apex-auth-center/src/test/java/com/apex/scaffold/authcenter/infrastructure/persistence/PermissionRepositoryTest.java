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

class PermissionRepositoryTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 1, 1, 0, 0);
    private final PermissionMapper mapper = mock(PermissionMapper.class);
    private final RoleMapper roles = mock(RoleMapper.class);
    private final RolePermissionMapper links = mock(RolePermissionMapper.class);
    private final PermissionRepositoryImpl repository =
            new PermissionRepositoryImpl(mapper, Mappers.getMapper(PermissionConverter.class), roles, links);

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
                    : PermissionPO.builder().id(7L).tenantId(TENANT).build();
        });

        var result = repository.findById(7L);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectOne(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByCodeScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "code", "=", "read");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? null
                    : PermissionPO.builder().id(7L).tenantId(TENANT).build();
        });

        var result = repository.findByCode("read", TENANT);
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
                    : List.of(PermissionPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findAll();
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByResourceScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "resource", "=", "users");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? List.of()
                    : List.of(PermissionPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findByResource("users");
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
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
                    : List.of(PermissionPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findByTenantId(TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findEnabledByTenantIdScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "enabled", "=", 1);
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? List.of()
                    : List.of(PermissionPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findEnabledByTenantId(TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByResourceTypeScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "resource_type", "=", "API");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? List.of()
                    : List.of(PermissionPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findByResourceType("API", TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "error"})
    void findByIdsScopesQueryAndPropagatesResult(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            assertThat(query.getSqlSegment()).contains("id IN");
            assertThat(((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) query)
                            .getParamNameValuePairs()
                            .values())
                    .containsExactlyInAnyOrder(TENANT, 7L);
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? List.of()
                    : List.of(PermissionPO.builder().id(7L).tenantId(TENANT).build());
        });

        var result = repository.findByIds(List.of(7L, 7L));
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "zero", "error"})
    void saveSetsDefaultsReturnsGeneratedIdAndPropagatesWriteFailures(String outcome) {

        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("insert failed");
        when(mapper.insert(any(PermissionPO.class))).thenAnswer(invocation -> {
            PermissionPO po = invocation.getArgument(0);
            assertThat(po.getTenantId()).isEqualTo(TENANT);
            assertThat(po.getCreatedAt()).isAfter(TIME);
            assertThat(po.getEnabled()).isEqualTo(1);
            assertThat(po.getVersion()).isZero();
            assertThat(po.getDeleted()).isZero();
            po.setId(7L);
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 1;
        });
        var result = repository.save(PermissionEntity.builder().tenantId(TENANT).build());
        verifyNoInteractions(mapper);
        if (outcome.equals("zero")) {
            businessError(result, ResultCode.PARAM_ERROR);
        } else {
            entity(result, outcome, failure);
        }
        verify(mapper).insert(any(PermissionPO.class));
    }

    @Test
    void saveRejectsForeignTenantBeforeDatabaseAccess() {
        businessError(
                repository.save(PermissionEntity.builder().tenantId("tenant-b").build()), ResultCode.FORBIDDEN);
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "zero", "error"})
    void updateScopesWriteAndHandlesConflicts(String outcome) {
        when(mapper.selectOne(any(Wrapper.class)))
                .thenReturn(PermissionPO.builder().id(7L).tenantId(TENANT).build());
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("update failed");
        when(mapper.update(any(PermissionPO.class), any(Wrapper.class))).thenAnswer(invocation -> {
            PermissionPO po = invocation.getArgument(0);
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
        var result = repository.update(PermissionEntity.builder()
                .id(7L)
                .tenantId(TENANT)
                .createdAt(TIME)
                .version(3)
                .build());
        if (outcome.equals("zero")) {
            businessError(result, ResultCode.PARAM_ERROR);
        } else {
            entity(result, outcome, failure);
        }
        verify(mapper).update(any(PermissionPO.class), any(Wrapper.class));
    }

    @Test
    void updateRejectsMissingIdBeforeDatabaseAccess() {

        businessError(
                repository.update(
                        PermissionEntity.builder().tenantId(TENANT).version(3).build()),
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
    void existsByCodeScopesCount(long count) {
        when(mapper.selectCount(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "code", "=", "read");
            return count;
        });
        value(repository.existsByCode("read", TENANT), count > 0);
    }

    @Test
    void emptyIdsAvoidDatabaseAccess() {
        entity(repository.findByIds(List.of()), "empty", null);
        verifyNoInteractions(mapper);
    }

    @Test
    void nullIdsAreRejectedBeforeDatabaseAccess() {
        businessError(repository.findByIds(null), ResultCode.PARAM_ERROR);
        businessError(repository.findByIds(java.util.Arrays.asList(7L, null)), ResultCode.PARAM_ERROR);
        verifyNoInteractions(mapper);
    }

    @Test
    void updateRequiresVersion() {

        businessError(
                repository.update(
                        PermissionEntity.builder().id(7L).tenantId(TENANT).build()),
                ResultCode.PARAM_ERROR);
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "found",
                "missing-role",
                "no-links",
                "no-permissions",
                "role-error",
                "link-error",
                "permission-error"
            })
    void findByRoleIdScopesEveryQueryAndPropagatesFailures(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("role permissions failed");
        when(roles.selectCount(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "id", "=", 9L);
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("role-error")) {
                throw failure;
            }
            return outcome.equals("missing-role") ? 0L : 1L;
        });
        when(links.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "role_id", "=", 9L);
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("link-error")) {
                throw failure;
            }
            return outcome.equals("no-links")
                    ? List.of()
                    : List.of(
                            RolePermissionPO.builder().permissionId(7L).build(),
                            RolePermissionPO.builder().permissionId(8L).build());
        });
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            assertThat(query.getSqlSegment()).contains("id IN");
            assertThat(((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) query)
                            .getParamNameValuePairs()
                            .values())
                    .containsExactlyInAnyOrder(TENANT, 7L, 8L);
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("permission-error")) {
                throw failure;
            }
            return outcome.equals("no-permissions")
                    ? List.of()
                    : List.of(PermissionPO.builder()
                            .id(7L)
                            .tenantId(TENANT)
                            .code("read")
                            .build());
        });
        var result = repository.findByRoleId(9L);
        verifyNoInteractions(roles, links, mapper);
        if (outcome.equals("found")) {
            value(result.map(PermissionEntity::getCode), "read");
        } else {
            entity(result, outcome.endsWith("error") ? "error" : "empty", failure);
        }
        verify(roles).selectCount(any(Wrapper.class));
        if (outcome.equals("missing-role") || outcome.equals("role-error")) {
            verifyNoInteractions(links, mapper);
        } else {
            verify(links).selectList(any(Wrapper.class));
            if (outcome.equals("no-links") || outcome.equals("link-error")) {
                verifyNoInteractions(mapper);
            } else {
                verify(mapper).selectList(any(Wrapper.class));
            }
        }
    }

    @Test
    void findByRoleIdWithoutTenantRejectsBeforeDatabaseAccess() {
        TenantContext.clear();
        reactor.test.StepVerifier.create(repository.findByRoleId(9L))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(com.apex.scaffold.common.exception.BusinessException.class);
                    assertThat(((com.apex.scaffold.common.exception.BusinessException) error).getCode())
                            .isEqualTo(ResultCode.FORBIDDEN.getCode());
                })
                .verify(java.time.Duration.ofSeconds(5));
        verifyNoInteractions(roles, links, mapper);
    }

    @Test
    void findByNullRoleIdReturnsEmptyWhenRoleIsUnavailable() {
        when(roles.selectCount(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "id", "=", null);
            return 0L;
        });
        entity(repository.findByRoleId(null), "empty", null);
        verify(roles).selectCount(any(Wrapper.class));
        verifyNoInteractions(links, mapper);
    }
}
