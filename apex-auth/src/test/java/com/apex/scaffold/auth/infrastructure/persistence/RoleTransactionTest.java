package com.apex.scaffold.auth.infrastructure.persistence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.apex.scaffold.auth.infrastructure.persistence.converter.RoleConverter;
import com.apex.scaffold.auth.infrastructure.persistence.mapper.*;
import com.apex.scaffold.auth.infrastructure.persistence.po.UserRolePO;
import com.apex.scaffold.auth.infrastructure.persistence.repository.RoleRepositoryImpl;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import reactor.test.StepVerifier;

class RoleTransactionTest {
    private final RoleMapper roles = mock(RoleMapper.class);
    private final PermissionMapper permissions = mock(PermissionMapper.class);
    private final RolePermissionMapper permissionLinks = mock(RolePermissionMapper.class);
    private final UserRoleMapper userLinks = mock(UserRoleMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    private final RoleRepositoryImpl repository = new RoleRepositoryImpl(
            roles,
            Mappers.getMapper(RoleConverter.class),
            permissions,
            permissionLinks,
            userLinks,
            users,
            transactions);

    @Test
    void rejectsForeignRolesBeforeAnyLinkIsWritten() {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        when(users.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(roles.selectCount(any(Wrapper.class))).thenReturn(1L);
        StepVerifier.create(repository
                        .assignRolesToUser(1L, List.of(2L, 3L), "tenant-a")
                        .contextWrite(TenantContext.withTenant("tenant-a")))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(userLinks);
        verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
    }

    @Test
    void rollsBackAllAssignmentsWhenLaterInsertFails() {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        when(users.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(roles.selectCount(any(Wrapper.class))).thenReturn(2L);
        when(userLinks.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(userLinks.insert(any(UserRolePO.class)))
                .thenReturn(1)
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        StepVerifier.create(repository
                        .assignRolesToUser(1L, List.of(2L, 3L), "tenant-a")
                        .contextWrite(TenantContext.withTenant("tenant-a")))
                .expectError(DataIntegrityViolationException.class)
                .verify();
        verify(userLinks, times(2)).insert(any(UserRolePO.class));
        verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
    }

    @Test
    void doesNotStartTransactionForTenantMismatch() {
        StepVerifier.create(repository
                        .assignRolesToUser(1L, List.of(2L), "tenant-b")
                        .contextWrite(TenantContext.withTenant("tenant-a")))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(transactions, roles, users, userLinks);
    }

    @Test
    void commitsDeduplicatedAssignmentsOnJdbcThread() {
        when(transactions.getTransaction(any())).thenAnswer(invocation -> {
            org.assertj.core.api.Assertions.assertThat(TenantContext.currentTenantId())
                    .contains("tenant-a");
            org.assertj.core.api.Assertions.assertThat(Thread.currentThread().getName())
                    .contains("jdbc");
            return transaction;
        });
        when(users.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(roles.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(userLinks.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(userLinks.insert(any(UserRolePO.class))).thenReturn(1);
        StepVerifier.create(repository
                        .assignRolesToUser(1L, List.of(2L, 2L), "tenant-a")
                        .contextWrite(TenantContext.withTenant("tenant-a")))
                .expectNext(true)
                .verifyComplete();
        verify(userLinks, times(1)).insert(any(UserRolePO.class));
        verify(transactions).commit(transaction);
        verify(transactions, never()).rollback(any());
    }

    @Test
    void staleVersionDoesNotReplacePermissionLinks() {
        when(transactions.getTransaction(any())).thenReturn(transaction);
        when(roles.update(any(com.apex.scaffold.auth.infrastructure.persistence.po.RolePO.class), any(Wrapper.class)))
                .thenReturn(0);
        com.apex.scaffold.auth.domain.model.entity.RoleEntity role =
                com.apex.scaffold.auth.domain.model.entity.RoleEntity.builder()
                        .id(1L)
                        .tenantId("tenant-a")
                        .version(1)
                        .permissionIds(java.util.Set.of())
                        .build();
        StepVerifier.create(repository.update(role).contextWrite(TenantContext.withTenant("tenant-a")))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(permissionLinks);
        verify(transactions).rollback(transaction);
    }
}
