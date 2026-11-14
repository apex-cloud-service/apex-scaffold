package com.apex.scaffold.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.auth.domain.model.entity.PermissionEntity;
import com.apex.scaffold.auth.domain.model.entity.RoleEntity;
import com.apex.scaffold.auth.domain.repository.RoleRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {
    @Mock
    private RoleService roleService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private AuthorizationService service;

    @Test
    void emptyRequirementsUseExplicitDefaultsWithoutRepositoryAccess() {
        for (Set<String> codes : java.util.Arrays.<Set<String>>asList(null, Set.of())) {
            StepVerifier.create(service.hasAnyPermission(1L, "tenant", codes))
                    .expectNext(false)
                    .verifyComplete();
            StepVerifier.create(service.hasAllPermissions(1L, "tenant", codes))
                    .expectNext(true)
                    .verifyComplete();
            StepVerifier.create(service.hasAnyRole(1L, "tenant", codes))
                    .expectNext(false)
                    .verifyComplete();
        }
        verifyNoInteractions(roleService, permissionService, roleRepository);
    }

    @Test
    void absentRolesDenyAccessAndUseSelfScope() {
        when(roleService.getUserRoles(1L, "tenant")).thenReturn(Flux.empty());
        StepVerifier.create(service.hasPermission(1L, "tenant", "read"))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(service.hasAnyPermission(1L, "tenant", Set.of("read")))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(service.hasAllPermissions(1L, "tenant", Set.of("read")))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(service.hasRole(1L, "tenant", "USER"))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(service.hasAnyRole(1L, "tenant", Set.of("USER")))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(service.isSuperAdmin(1L, "tenant"))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(service.canAccessResource(1L, "tenant", "/users", "GET"))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(service.getUserPermissions(1L, "tenant"))
                .expectNext(Set.of())
                .verifyComplete();
        StepVerifier.create(service.getUserDataScope(1L, "tenant"))
                .expectNext("SELF")
                .verifyComplete();
        verifyNoInteractions(permissionService);
    }

    @Test
    void superAdminBypassesPermissionLookup() {
        when(roleService.getUserRoles(1L, "tenant")).thenReturn(Flux.just(role("SUPER_ADMIN", Set.of(), "ALL")));
        StepVerifier.create(service.hasPermission(1L, "tenant", "read"))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(service.hasAnyPermission(1L, "tenant", Set.of("read")))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(service.hasAllPermissions(1L, "tenant", Set.of("read", "write")))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(service.canAccessResource(1L, "tenant", "/users", "GET"))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(service.isSuperAdmin(1L, "tenant")).expectNext(true).verifyComplete();
        verifyNoInteractions(permissionService);
    }

    @Test
    void invalidRolesCannotGrantSuperAdminAccess() {
        RoleEntity disabled = role("SUPER_ADMIN", Set.of(), "ALL");
        disabled.setEnabled(false);
        RoleEntity deleted = role("SYSTEM_ADMIN", Set.of(), "ALL");
        deleted.setDeleted(true);
        when(roleService.getUserRoles(1L, "tenant")).thenReturn(Flux.just(disabled, deleted));
        StepVerifier.create(service.hasPermission(1L, "tenant", "read"))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(service.isSuperAdmin(1L, "tenant"))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(service.getUserDataScope(1L, "tenant"))
                .expectNext("SELF")
                .verifyComplete();
        verifyNoInteractions(permissionService);
    }

    @Test
    void permissionsAreMergedAcrossRolesAndInvalidPermissionsAreFiltered() {
        when(roleService.getUserRoles(1L, "tenant"))
                .thenReturn(Flux.just(role("READER", Set.of(1L), "SELF"), role("WRITER", Set.of(2L), "DEPT")));
        PermissionEntity disabled = permission("delete");
        disabled.setEnabled(false);
        PermissionEntity deleted = permission("manage");
        deleted.setDeleted(true);
        when(permissionService.getPermissionsByIds(Set.of(1L)))
                .thenReturn(Flux.just(permission("read"), disabled, deleted));
        when(permissionService.getPermissionsByIds(Set.of(2L)))
                .thenReturn(Flux.just(permission("write"), permission("read")));
        StepVerifier.create(service.hasAllPermissions(1L, "tenant", Set.of("read", "write")))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(service.hasAllPermissions(1L, "tenant", Set.of("read", "delete")))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(service.hasPermission(1L, "tenant", "manage"))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(service.hasPermission(1L, "tenant", "write"))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(service.hasAnyPermission(1L, "tenant", Set.of("write", "delete")))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(service.hasAnyPermission(1L, "tenant", Set.of("delete", "manage")))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(service.getUserPermissions(1L, "tenant"))
                .assertNext(codes -> assertThat(codes).containsExactlyInAnyOrder("read", "write"))
                .verifyComplete();
    }

    @Test
    void superAdminPermissionListUsesTenantEnabledPermissions() {
        when(roleService.getUserRoles(1L, "tenant")).thenReturn(Flux.just(role("SYSTEM_ADMIN", Set.of(), "ALL")));
        when(permissionService.getEnabledPermissions("tenant"))
                .thenReturn(Flux.just(permission("read"), permission("write")));
        StepVerifier.create(service.getUserPermissions(1L, "tenant"))
                .expectNext(Set.of("read", "write"))
                .verifyComplete();
    }

    @Test
    void roleChecksRequireMatchingValidRole() {
        when(roleService.getUserRoles(1L, "tenant")).thenReturn(Flux.just(role("READER", Set.of(), "SELF")));
        StepVerifier.create(service.hasRole(1L, "tenant", "READER"))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(service.hasRole(1L, "tenant", "WRITER"))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(service.hasAnyRole(1L, "tenant", Set.of("READER", "WRITER")))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(service.hasAnyRole(1L, "tenant", Set.of("WRITER")))
                .expectNext(false)
                .verifyComplete();
    }

    @ParameterizedTest
    @CsvSource({"/users,GET,true", "/users,get,true", "/users,POST,false", "/admin,GET,false"})
    void resourceAndActionMustBothMatch(String resource, String action, boolean expected) {
        when(roleService.getUserRoles(1L, "tenant")).thenReturn(Flux.just(role("READER", Set.of(1L), "SELF")));
        PermissionEntity permission = permission("read");
        permission.setResourceIdentifier("/users");
        permission.setAction("GET");
        when(permissionService.getPermissionsByIds(Set.of(1L))).thenReturn(Flux.just(permission));
        StepVerifier.create(service.canAccessResource(1L, "tenant", resource, action))
                .expectNext(expected)
                .verifyComplete();
    }

    @ParameterizedTest
    @CsvSource({
        "SELF,SELF,SELF",
        "SELF,DEPT,DEPT",
        "DEPT,SELF,DEPT",
        "DEPT,DEPT_AND_SUB,DEPT_AND_SUB",
        "DEPT_AND_SUB,DEPT,DEPT_AND_SUB",
        "ALL,DEPT,ALL",
        "DEPT,ALL,ALL"
    })
    void dataScopeUsesWidestRole(String first, String second, String expected) {
        when(roleService.getUserRoles(1L, "tenant"))
                .thenReturn(Flux.just(role("FIRST", Set.of(), first), role("SECOND", Set.of(), second)));
        StepVerifier.create(service.getUserDataScope(1L, "tenant"))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void roleLookupFailureIsNotConvertedToAuthorizationSuccess() {
        IllegalStateException failure = new IllegalStateException("lookup failed");
        when(roleService.getUserRoles(1L, "tenant")).thenReturn(Flux.error(failure));
        StepVerifier.create(service.hasPermission(1L, "tenant", "read"))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();
        verifyNoInteractions(permissionService);
    }

    private RoleEntity role(String code, Set<Long> ids, String scope) {
        return RoleEntity.builder()
                .code(code)
                .enabled(true)
                .deleted(false)
                .permissionIds(ids)
                .dataScope(scope)
                .build();
    }

    private PermissionEntity permission(String code) {
        return PermissionEntity.builder()
                .code(code)
                .enabled(true)
                .deleted(false)
                .build();
    }
}
