package com.apex.scaffold.auth.application.service;

import static org.mockito.Mockito.*;

import com.apex.scaffold.auth.domain.model.entity.UserEntity;
import com.apex.scaffold.auth.domain.repository.PermissionRepository;
import com.apex.scaffold.auth.domain.repository.RoleRepository;
import com.apex.scaffold.auth.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UnifiedAuthorizationQueryServiceTest {
    @Test
    void deletedUserCannotReceiveAuthorization() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PermissionRepository permissions = mock(PermissionRepository.class);
        when(users.findById(99L))
                .thenReturn(Mono.just(UserEntity.builder()
                        .id(99L)
                        .tenantId("center")
                        .status(1)
                        .deleted(1)
                        .build()));
        StepVerifier.create(new UnifiedAuthorizationQueryService(users, roles, permissions).query("center", 99L))
                .expectError(AccessDeniedException.class)
                .verify();
        verifyNoInteractions(roles, permissions);
    }

    @Test
    void foreignTenantCannotReceiveAuthorization() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PermissionRepository permissions = mock(PermissionRepository.class);
        when(users.findById(99L))
                .thenReturn(Mono.just(UserEntity.builder()
                        .id(99L)
                        .tenantId("foreign")
                        .status(1)
                        .deleted(0)
                        .build()));
        StepVerifier.create(new UnifiedAuthorizationQueryService(users, roles, permissions).query("center", 99L))
                .expectError(AccessDeniedException.class)
                .verify();
        verifyNoInteractions(roles, permissions);
    }

    @Test
    void deletedReaderCannotQueryAuthorization() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PermissionRepository permissions = mock(PermissionRepository.class);
        when(users.findById(1L))
                .thenReturn(Mono.just(UserEntity.builder()
                        .id(1L)
                        .tenantId("center")
                        .status(1)
                        .deleted(1)
                        .build()));
        StepVerifier.create(new UnifiedAuthorizationQueryService(users, roles, permissions)
                        .queryForReader("center", 1L, 99L))
                .expectError(AccessDeniedException.class)
                .verify();
        verify(users, never()).findById(99L);
        verifyNoInteractions(roles, permissions);
    }

    @Test
    void disabledAndLockedReadersCannotQueryTarget() {
        for (int status : new int[] {0, 2}) {
            UserRepository users = mock(UserRepository.class);
            RoleRepository roles = mock(RoleRepository.class);
            PermissionRepository permissions = mock(PermissionRepository.class);
            when(users.findById(1L))
                    .thenReturn(Mono.just(UserEntity.builder()
                            .id(1L)
                            .tenantId("center")
                            .status(status)
                            .deleted(0)
                            .build()));
            StepVerifier.create(new UnifiedAuthorizationQueryService(users, roles, permissions)
                            .queryForReader("center", 1L, 99L))
                    .expectError(AccessDeniedException.class)
                    .verify();
            verify(users, never()).findById(99L);
            verifyNoInteractions(roles, permissions);
        }
    }

    @Test
    void returnsOnlyValidSameTenantRolesAndDistinctPermissions() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PermissionRepository permissions = mock(PermissionRepository.class);
        when(users.findById(1L))
                .thenReturn(Mono.just(UserEntity.builder()
                        .id(1L)
                        .tenantId("center")
                        .status(1)
                        .deleted(0)
                        .build()));
        when(users.findById(99L))
                .thenReturn(Mono.just(UserEntity.builder()
                        .id(99L)
                        .tenantId("center")
                        .status(1)
                        .deleted(0)
                        .build()));
        when(roles.findByUserId(99L, "center"))
                .thenReturn(reactor.core.publisher.Flux.just(
                        role(10L, "center", true, false), role(11L, "center", false, false),
                        role(12L, "foreign", true, false), role(13L, "center", true, true)));
        when(permissions.findByRoleId(10L))
                .thenReturn(reactor.core.publisher.Flux.just(
                        permission("user:read", "center", true, false),
                        permission("user:read", "center", true, false),
                        permission("foreign:read", "foreign", true, false),
                        permission("disabled:read", "center", false, false),
                        permission("deleted:read", "center", true, true)));
        StepVerifier.create(new UnifiedAuthorizationQueryService(users, roles, permissions)
                        .queryForReader("center", 1L, 99L))
                .assertNext(result -> {
                    org.assertj.core.api.Assertions.assertThat(result.permissions())
                            .containsExactly("user:read");
                    org.assertj.core.api.Assertions.assertThat(result.roles())
                            .containsExactly(new UnifiedAuthorizationQueryService.Role("USER", "SELF"));
                })
                .verifyComplete();
        verify(permissions, never()).findByRoleId(11L);
        verify(permissions, never()).findByRoleId(12L);
        verify(permissions, never()).findByRoleId(13L);
    }

    private com.apex.scaffold.auth.domain.model.entity.RoleEntity role(
            Long id, String tenant, boolean enabled, boolean deleted) {
        return com.apex.scaffold.auth.domain.model.entity.RoleEntity.builder()
                .id(id)
                .tenantId(tenant)
                .code("USER")
                .dataScope("SELF")
                .enabled(enabled)
                .deleted(deleted)
                .build();
    }

    private com.apex.scaffold.auth.domain.model.entity.PermissionEntity permission(
            String code, String tenant, boolean enabled, boolean deleted) {
        return com.apex.scaffold.auth.domain.model.entity.PermissionEntity.builder()
                .code(code)
                .tenantId(tenant)
                .enabled(enabled)
                .deleted(deleted)
                .build();
    }
}
