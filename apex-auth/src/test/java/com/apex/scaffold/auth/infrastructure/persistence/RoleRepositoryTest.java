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
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RoleRepositoryTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 1, 1, 0, 0);
    private final RoleMapper mapper = mock(RoleMapper.class);
    private final PermissionMapper permissions = mock(PermissionMapper.class);
    private final RolePermissionMapper permissionLinks = mock(RolePermissionMapper.class);
    private final UserRoleMapper userLinks = mock(UserRoleMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    private final RoleRepositoryImpl repository = new RoleRepositoryImpl(
            mapper,
            Mappers.getMapper(RoleConverter.class),
            permissions,
            permissionLinks,
            userLinks,
            users,
            transactions);

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
                    : RolePO.builder().id(7L).tenantId(TENANT).build();
        });
        when(permissionLinks.selectList(any(Wrapper.class))).thenReturn(List.of());
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
            condition(query, "code", "=", "admin");
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? null
                    : RolePO.builder().id(7L).tenantId(TENANT).build();
        });
        when(permissionLinks.selectList(any(Wrapper.class))).thenReturn(List.of());
        var result = repository.findByCode("admin", TENANT);
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
                    : List.of(RolePO.builder().id(7L).tenantId(TENANT).build());
        });
        when(permissionLinks.selectList(any(Wrapper.class))).thenReturn(List.of());
        var result = repository.findAll();
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
                    : List.of(RolePO.builder().id(7L).tenantId(TENANT).build());
        });
        when(permissionLinks.selectList(any(Wrapper.class))).thenReturn(List.of());
        var result = repository.findByIds(List.of(7L, 7L));
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
                    : List.of(RolePO.builder().id(7L).tenantId(TENANT).build());
        });
        when(permissionLinks.selectList(any(Wrapper.class))).thenReturn(List.of());
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
                    : List.of(RolePO.builder().id(7L).tenantId(TENANT).build());
        });
        when(permissionLinks.selectList(any(Wrapper.class))).thenReturn(List.of());
        var result = repository.findEnabledByTenantId(TENANT);
        verifyNoInteractions(mapper);
        entity(result, outcome, failure);
        verify(mapper).selectList(any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "zero", "error"})
    void saveSetsDefaultsReturnsGeneratedIdAndPropagatesWriteFailures(String outcome) {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("insert failed");
        when(mapper.insert(any(RolePO.class))).thenAnswer(invocation -> {
            RolePO po = invocation.getArgument(0);
            assertThat(po.getTenantId()).isEqualTo(TENANT);
            assertThat(po.getCreatedAt()).isAfter(TIME);
            assertThat(po.getEnabled()).isEqualTo(1);
            assertThat(po.getVersion()).isZero();
            assertThat(po.getDeleted()).isZero();
            assertThat(po.getDataScope()).isEqualTo("0");
            assertThat(po.getSortOrder()).isZero();
            po.setId(7L);
            if (outcome.equals("error")) {
                throw failure;
            }
            return outcome.equals("zero") ? 0 : 1;
        });
        var result = repository.save(
                RoleEntity.builder().tenantId(TENANT).permissionIds(Set.of()).build());
        verifyNoInteractions(mapper);
        if (outcome.equals("zero")) {
            businessError(result, ResultCode.PARAM_ERROR);
        } else {
            entity(result, outcome, failure);
        }
        verify(mapper).insert(any(RolePO.class));
        if (outcome.equals("success")) {
            verify(transactions).commit(transaction);
        } else {
            verify(transactions).rollback(transaction);
            verifyNoInteractions(permissionLinks);
        }
    }

    @Test
    void saveRejectsForeignTenantBeforeDatabaseAccess() {
        businessError(
                repository.save(RoleEntity.builder()
                        .tenantId("tenant-b")
                        .permissionIds(Set.of())
                        .build()),
                ResultCode.FORBIDDEN);
        verifyNoInteractions(mapper, transactions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "zero", "error"})
    void updateScopesWriteAndHandlesConflicts(String outcome) {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        when(mapper.selectOne(any(Wrapper.class)))
                .thenReturn(RolePO.builder().id(7L).tenantId(TENANT).build());
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("update failed");
        when(mapper.update(any(RolePO.class), any(Wrapper.class))).thenAnswer(invocation -> {
            RolePO po = invocation.getArgument(0);
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
        var result = repository.update(RoleEntity.builder()
                .id(7L)
                .tenantId(TENANT)
                .createdAt(TIME)
                .permissionIds(Set.of())
                .version(3)
                .build());
        if (outcome.equals("zero")) {
            businessError(result, ResultCode.PARAM_ERROR);
        } else {
            entity(result, outcome, failure);
        }
        verify(mapper).update(any(RolePO.class), any(Wrapper.class));
        if (!outcome.equals("success")) {
            verifyNoInteractions(permissionLinks);
            verify(transactions).rollback(transaction);
        } else {
            verify(transactions).commit(transaction);
        }
    }

    @Test
    void updateRejectsMissingIdBeforeDatabaseAccess() {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        businessError(
                repository.update(RoleEntity.builder()
                        .tenantId(TENANT)
                        .permissionIds(Set.of())
                        .version(3)
                        .build()),
                ResultCode.PARAM_ERROR);
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void deleteScopesIdAndReportsAffectedRows(int count) {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        when(mapper.delete(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "id", "=", 7L);
            return count;
        });
        value(repository.deleteById(7L), count > 0);
        verify(mapper).delete(any(Wrapper.class));
        if (count == 0) {
            verifyNoInteractions(permissionLinks, userLinks);
        } else {
            verify(permissionLinks).delete(argThat((Wrapper<RolePermissionPO> q) -> {
                scoped(q);
                condition(q, "role_id", "=", 7L);
                return true;
            }));
            verify(userLinks).delete(argThat((Wrapper<UserRolePO> q) -> {
                scoped(q);
                condition(q, "role_id", "=", 7L);
                return true;
            }));
        }
        verify(transactions).commit(transaction);
    }

    @Test
    void deletePropagatesDatabaseFailure() {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("delete failed");
        when(mapper.delete(any(Wrapper.class))).thenThrow(failure);
        entity(repository.deleteById(7L), "error", failure);
        verifyNoInteractions(permissionLinks, userLinks);
        verify(transactions).rollback(transaction);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L})
    void existsByCodeScopesCount(long count) {
        when(mapper.selectCount(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "code", "=", "admin");
            return count;
        });
        value(repository.existsByCode("admin", TENANT), count > 0);
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

        when(transactions.getTransaction(any())).thenReturn(transaction);
        businessError(
                repository.update(RoleEntity.builder()
                        .id(7L)
                        .tenantId(TENANT)
                        .permissionIds(Set.of())
                        .build()),
                ResultCode.PARAM_ERROR);
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "no-links", "no-roles", "user-error", "link-error", "role-error"})
    void findByUserIdScopesAssociationsAndPropagatesFailures(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("association query failed");
        when(users.selectCount(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "id", "=", 9L);
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            if (outcome.equals("user-error")) {
                throw failure;
            }
            return 1L;
        });
        when(userLinks.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "user_id", "=", 9L);
            if (outcome.equals("link-error")) {
                throw failure;
            }
            return outcome.equals("no-links")
                    ? List.of()
                    : List.of(UserRolePO.builder().roleId(7L).build());
        });
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            assertIdsQuery(invocation.getArgument(0), "id", 7L);
            if (outcome.equals("role-error")) {
                throw failure;
            }
            return outcome.equals("no-roles")
                    ? List.of()
                    : List.of(RolePO.builder().id(7L).tenantId(TENANT).build());
        });
        when(permissionLinks.selectList(any(Wrapper.class))).thenReturn(List.of());
        var result = repository.findByUserId(9L, TENANT);
        verifyNoInteractions(users, userLinks, mapper, permissionLinks);
        entity(result, outcome.endsWith("error") ? "error" : outcome.equals("found") ? "found" : "empty", failure);
        if (outcome.equals("user-error")) {
            verifyNoInteractions(userLinks, mapper, permissionLinks);
        } else if (outcome.equals("no-links") || outcome.equals("link-error")) {
            verifyNoInteractions(mapper, permissionLinks);
        }
        verifyNoInteractions(transactions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"query", "remove"})
    void userAssociationsRejectForeignTenantAndInvalidUsers(String operation) {
        businessError(
                operation.equals("query")
                        ? repository.findByUserId(9L, "tenant-b")
                        : repository.removeRolesFromUser(9L, List.of(7L), "tenant-b"),
                ResultCode.FORBIDDEN);
        verifyNoInteractions(users, mapper, userLinks, transactions);
        when(transactions.getTransaction(any())).thenReturn(transaction);
        businessError(
                operation.equals("query")
                        ? repository.findByUserId(null, TENANT)
                        : repository.removeRolesFromUser(null, List.of(7L), TENANT),
                ResultCode.FORBIDDEN);
        verifyNoInteractions(users, mapper, userLinks);
        businessError(
                operation.equals("query")
                        ? repository.findByUserId(9L, TENANT)
                        : repository.removeRolesFromUser(9L, List.of(7L), TENANT),
                ResultCode.FORBIDDEN);
        verify(users).selectCount(argThat((Wrapper<UserPO> query) -> {
            scoped(query);
            condition(query, "id", "=", 9L);
            return true;
        }));
        verifyNoInteractions(mapper, userLinks);
        if (operation.equals("remove")) {
            verify(transactions, times(2)).rollback(transaction);
            verify(transactions, never()).commit(any());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void removeRolesFromUserScopesAndDeduplicatesIds(int affected) {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        when(users.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(mapper.selectCount(any(Wrapper.class))).thenAnswer(invocation -> {
            assertIdsQuery(invocation.getArgument(0), "id", 7L, 8L);
            return 2L;
        });
        when(userLinks.delete(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "user_id", "=", 9L);
            assertThat(query.getSqlSegment()).contains("role_id IN");
            assertThat(((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) query)
                            .getParamNameValuePairs()
                            .values())
                    .containsExactlyInAnyOrder(TENANT, 9L, 7L, 8L);
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            return affected;
        });
        var result = repository.removeRolesFromUser(9L, List.of(7L, 8L, 7L), TENANT);
        verifyNoInteractions(users, mapper, userLinks, transactions);
        value(result, true);
        verify(userLinks).delete(any(Wrapper.class));
        verify(transactions).commit(transaction);
        verify(transactions, never()).rollback(any());
    }

    @Test
    void removeEmptyRolesValidatesUserWithoutDeletingLinks() {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        when(users.selectCount(any(Wrapper.class))).thenReturn(1L);
        value(repository.removeRolesFromUser(9L, List.of(), TENANT), true);
        verify(users).selectCount(any(Wrapper.class));
        verifyNoInteractions(mapper, userLinks);
        verify(transactions).commit(transaction);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null-list", "null-id", "missing-role", "user-error", "role-error", "delete-error"})
    void removeRolesRejectsInvalidIdsAndRollsBackFailures(String outcome) {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("remove failed");
        when(users.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(outcome.equals("missing-role") ? 0L : 1L);
        if (outcome.equals("user-error")) {
            when(users.selectCount(any(Wrapper.class))).thenThrow(failure);
        } else if (outcome.equals("role-error")) {
            when(mapper.selectCount(any(Wrapper.class))).thenThrow(failure);
        } else if (outcome.equals("delete-error")) {
            when(userLinks.delete(any(Wrapper.class))).thenThrow(failure);
        }
        List<Long> ids = outcome.equals("null-list")
                ? null
                : outcome.equals("null-id") ? java.util.Arrays.asList(7L, null) : List.of(7L);
        var result = repository.removeRolesFromUser(9L, ids, TENANT);
        if (outcome.endsWith("error")) {
            entity(result, "error", failure);
        } else {
            businessError(result, outcome.equals("missing-role") ? ResultCode.FORBIDDEN : ResultCode.PARAM_ERROR);
        }
        if (!outcome.equals("delete-error")) {
            verifyNoInteractions(userLinks);
        }
        if (outcome.startsWith("null") || outcome.equals("user-error")) {
            verifyNoInteractions(mapper);
        }
        verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "update"})
    void writesReplacePermissionsAndReloadTenantScopedIds(String operation) {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        when(permissions.selectCount(any(Wrapper.class))).thenAnswer(invocation -> {
            assertIdsQuery(invocation.getArgument(0), "id", 11L, 12L);
            return 2L;
        });
        when(mapper.insert(any(RolePO.class))).thenAnswer(invocation -> {
            RolePO po = invocation.getArgument(0);
            po.setId(7L);
            return 1;
        });
        when(mapper.update(any(RolePO.class), any(Wrapper.class))).thenReturn(1);
        when(mapper.selectOne(any(Wrapper.class)))
                .thenReturn(RolePO.builder().id(7L).tenantId(TENANT).build());
        when(permissionLinks.delete(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "role_id", "=", 7L);
            return 1;
        });
        when(permissionLinks.insert(any(RolePermissionPO.class))).thenAnswer(invocation -> {
            RolePermissionPO po = invocation.getArgument(0);
            assertThat(po.getTenantId()).isEqualTo(TENANT);
            assertThat(po.getRoleId()).isEqualTo(7L);
            assertThat(po.getPermissionId()).isIn(11L, 12L);
            assertThat(po.getCreatedAt()).isAfter(TIME);
            assertThat(TenantContext.currentTenantId()).contains(TENANT);
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            return 1;
        });
        when(permissionLinks.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "role_id", "=", 7L);
            return List.of(
                    RolePermissionPO.builder().permissionId(11L).build(),
                    RolePermissionPO.builder().permissionId(12L).build());
        });
        when(permissions.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            assertIdsQuery(invocation.getArgument(0), "id", 11L, 12L);
            return List.of(
                    PermissionPO.builder().id(11L).tenantId(TENANT).build(),
                    PermissionPO.builder().id(12L).tenantId(TENANT).build());
        });
        RoleEntity role = RoleEntity.builder()
                .id(7L)
                .tenantId(TENANT)
                .version(3)
                .permissionIds(Set.of(11L, 12L))
                .build();
        var result = operation.equals("save") ? repository.save(role) : repository.update(role);
        verifyNoInteractions(mapper, permissions, permissionLinks, transactions);
        value(result.map(RoleEntity::getPermissionIds), Set.of(11L, 12L));
        var order = inOrder(permissions, mapper, permissionLinks, transactions);
        order.verify(permissions).selectCount(any(Wrapper.class));
        if (operation.equals("save")) {
            order.verify(mapper).insert(any(RolePO.class));
        } else {
            order.verify(mapper).update(any(RolePO.class), any(Wrapper.class));
        }
        order.verify(permissionLinks).delete(any(Wrapper.class));
        order.verify(permissionLinks, times(2)).insert(any(RolePermissionPO.class));
        order.verify(permissionLinks).selectList(any(Wrapper.class));
        order.verify(permissions).selectList(any(Wrapper.class));
        order.verify(transactions).commit(transaction);
        var captor = org.mockito.ArgumentCaptor.forClass(RolePermissionPO.class);
        verify(permissionLinks, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(RolePermissionPO::getPermissionId)
                .containsExactlyInAnyOrder(11L, 12L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"found", "empty", "link-error", "permission-error"})
    void hydrationFiltersUnavailablePermissionsAndPropagatesFailures(String outcome) {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("hydrate failed");
        when(mapper.selectOne(any(Wrapper.class)))
                .thenReturn(RolePO.builder().id(7L).tenantId(TENANT).build());
        when(permissionLinks.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            scoped(query);
            condition(query, "role_id", "=", 7L);
            if (outcome.equals("link-error")) {
                throw failure;
            }
            return List.of(
                    RolePermissionPO.builder().permissionId(11L).build(),
                    RolePermissionPO.builder().permissionId(12L).build());
        });
        when(permissions.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            assertIdsQuery(invocation.getArgument(0), "id", 11L, 12L);
            if (outcome.equals("permission-error")) {
                throw failure;
            }
            return outcome.equals("empty")
                    ? List.of()
                    : List.of(PermissionPO.builder().id(11L).build());
        });
        var result = repository.findById(7L);
        if (outcome.endsWith("error")) {
            entity(result, "error", failure);
        } else {
            value(result.map(RoleEntity::getPermissionIds), outcome.equals("empty") ? Set.of() : Set.of(11L));
        }
        if (outcome.equals("link-error")) {
            verifyNoInteractions(permissions);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "update"})
    void writesRejectNullAndUnavailablePermissionsBeforeRoleMutation(String operation) {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        RoleEntity role =
                RoleEntity.builder().id(7L).tenantId(TENANT).version(3).build();
        role.setPermissionIds(null);
        businessError(
                operation.equals("save") ? repository.save(role) : repository.update(role), ResultCode.PARAM_ERROR);
        role.setPermissionIds(new java.util.HashSet<>(java.util.Arrays.asList(11L, null)));
        businessError(
                operation.equals("save") ? repository.save(role) : repository.update(role), ResultCode.PARAM_ERROR);
        verifyNoInteractions(mapper, permissions, permissionLinks);
        role.setPermissionIds(Set.of(11L, 12L));
        when(permissions.selectCount(any(Wrapper.class))).thenReturn(1L);
        businessError(operation.equals("save") ? repository.save(role) : repository.update(role), ResultCode.FORBIDDEN);
        verifyNoInteractions(mapper, permissionLinks);
        verify(transactions, times(3)).rollback(transaction);
        verify(transactions, never()).commit(any());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "save-validation",
                "update-validation",
                "save-delete",
                "update-delete",
                "save-zero",
                "update-zero",
                "save-insert",
                "update-insert",
                "save-hydrate",
                "update-hydrate"
            })
    void permissionReplacementFailuresRollBackRoleWrite(String outcome) {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("permission write failed");
        when(permissions.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(mapper.insert(any(RolePO.class))).thenReturn(1);
        when(mapper.update(any(RolePO.class), any(Wrapper.class))).thenReturn(1);
        when(mapper.selectOne(any(Wrapper.class)))
                .thenReturn(RolePO.builder().id(7L).tenantId(TENANT).build());
        when(permissionLinks.insert(any(RolePermissionPO.class))).thenReturn(1);
        if (outcome.endsWith("validation")) {
            when(permissions.selectCount(any(Wrapper.class))).thenThrow(failure);
        } else if (outcome.endsWith("delete")) {
            when(permissionLinks.delete(any(Wrapper.class))).thenThrow(failure);
        } else if (outcome.endsWith("zero")) {
            when(permissionLinks.insert(any(RolePermissionPO.class))).thenReturn(0);
        } else if (outcome.endsWith("insert")) {
            when(permissionLinks.insert(any(RolePermissionPO.class))).thenThrow(failure);
        } else {
            when(permissionLinks.selectList(any(Wrapper.class))).thenThrow(failure);
        }
        RoleEntity role = RoleEntity.builder()
                .id(7L)
                .tenantId(TENANT)
                .version(3)
                .permissionIds(Set.of(11L))
                .build();
        var result = outcome.startsWith("save") ? repository.save(role) : repository.update(role);
        if (outcome.endsWith("zero")) {
            businessError(result, ResultCode.PARAM_ERROR);
        } else {
            entity(result, "error", failure);
        }
        if (outcome.endsWith("validation")) {
            verifyNoInteractions(mapper, permissionLinks);
        } else if (outcome.endsWith("delete")) {
            verify(permissionLinks, never()).insert(any(RolePermissionPO.class));
        }
        if (!outcome.endsWith("hydrate")) {
            verify(permissionLinks, never()).selectList(any(Wrapper.class));
        }
        verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "update"})
    void emptyPermissionsClearExistingLinksWithoutLoadingPermissions(String operation) {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        when(mapper.insert(any(RolePO.class))).thenReturn(1);
        when(mapper.update(any(RolePO.class), any(Wrapper.class))).thenReturn(1);
        when(mapper.selectOne(any(Wrapper.class)))
                .thenReturn(RolePO.builder().id(7L).tenantId(TENANT).build());
        when(permissionLinks.selectList(any(Wrapper.class))).thenReturn(List.of());
        RoleEntity role = RoleEntity.builder()
                .id(7L)
                .tenantId(TENANT)
                .version(3)
                .permissionIds(Set.of())
                .build();
        value(
                (operation.equals("save") ? repository.save(role) : repository.update(role))
                        .map(RoleEntity::getPermissionIds),
                Set.of());
        verify(permissionLinks).delete(argThat((Wrapper<RolePermissionPO> query) -> {
            scoped(query);
            condition(query, "role_id", "=", 7L);
            return true;
        }));
        verify(permissionLinks, never()).insert(any(RolePermissionPO.class));
        verifyNoInteractions(permissions);
        verify(transactions).commit(transaction);
    }

    private void assertIdsQuery(Wrapper<?> query, String column, Long... ids) {
        scoped(query);
        assertThat(query.getSqlSegment()).contains(column + " IN");
        assertThat(((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) query)
                        .getParamNameValuePairs()
                        .values())
                .containsExactlyInAnyOrderElementsOf(
                        java.util.stream.Stream.concat(java.util.stream.Stream.of(TENANT), java.util.Arrays.stream(ids))
                                .toList());
    }
}
