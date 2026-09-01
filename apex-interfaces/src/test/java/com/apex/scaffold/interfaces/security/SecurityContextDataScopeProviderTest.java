package com.apex.scaffold.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.ReactiveDataScopeProvider;
import com.apex.scaffold.common.enums.DataScopeType;
import com.apex.scaffold.domain.model.entity.DeptEntity;
import com.apex.scaffold.domain.model.entity.RoleEntity;
import com.apex.scaffold.domain.repository.DeptRepository;
import com.apex.scaffold.domain.repository.RoleRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SecurityContextDataScopeProviderTest {

    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final DeptRepository deptRepository = mock(DeptRepository.class);
    private final SecurityContextDataScopeProvider provider =
            new SecurityContextDataScopeProvider(roleRepository, deptRepository);

    @Test
    void implementsReactiveProviderAndRegistersAsComponent() {
        assertThat(provider).isInstanceOf(ReactiveDataScopeProvider.class);
        assertThat(AnnotationUtils.findAnnotation(SecurityContextDataScopeProvider.class, Component.class))
                .isNotNull();
    }

    @Test
    void resolvesSelfFromExplicitRoleScope() {
        RoleEntity role = role(1L, DataScopeType.SELF);
        when(roleRepository.findByUserId(7L)).thenReturn(Mono.just(List.of(role)));

        StepVerifier.create(withUser(provider.resolve(), 7L, 10L))
                .assertNext(scope -> {
                    assertThat(scope.type()).isEqualTo(DataScopeType.SELF);
                    assertThat(scope.userId()).isEqualTo(7L);
                })
                .verifyComplete();
    }

    @Test
    void resolvesEnabledDepartmentAndChildren() {
        RoleEntity role = role(1L, DataScopeType.DEPT_AND_CHILD);
        when(roleRepository.findByUserId(7L)).thenReturn(Mono.just(List.of(role)));
        when(deptRepository.findAll())
                .thenReturn(Mono.just(
                        List.of(dept(10L, "0", 1), dept(11L, "0,10", 1), dept(12L, "0,10", 0), dept(20L, "0", 1))));

        StepVerifier.create(withUser(provider.resolve(), 7L, 10L))
                .assertNext(scope -> assertThat(scope.deptIds()).containsExactlyInAnyOrder(10L, 11L))
                .verifyComplete();
    }

    @Test
    void resolvesEnabledDepartmentScope() {
        when(roleRepository.findByUserId(7L)).thenReturn(Mono.just(List.of(role(1L, DataScopeType.DEPT))));
        when(deptRepository.findById(10L)).thenReturn(Mono.just(dept(10L, "0", 1)));

        StepVerifier.create(withUser(provider.resolve(), 7L, 10L))
                .assertNext(scope -> {
                    assertThat(scope.type()).isEqualTo(DataScopeType.DEPT);
                    assertThat(scope.deptId()).isEqualTo(10L);
                })
                .verifyComplete();
    }

    @Test
    void rejectsMissingDepartmentForDepartmentScope() {
        when(roleRepository.findByUserId(7L)).thenReturn(Mono.just(List.of(role(1L, DataScopeType.DEPT))));
        when(deptRepository.findById(10L)).thenReturn(Mono.empty());

        StepVerifier.create(withUser(provider.resolve(), 7L, 10L))
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("No enabled department data scope"))
                .verify();
    }

    @Test
    void rejectsNullDepartmentRepositoryReturnForDepartmentScope() {
        when(roleRepository.findByUserId(7L)).thenReturn(Mono.just(List.of(role(1L, DataScopeType.DEPT))));
        when(deptRepository.findById(10L)).thenReturn(null);

        StepVerifier.create(withUser(provider.resolve(), 7L, 10L))
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("No enabled department data scope"))
                .verify();
    }

    @Test
    void rejectsDisabledDepartmentForDepartmentScope() {
        when(roleRepository.findByUserId(7L)).thenReturn(Mono.just(List.of(role(1L, DataScopeType.DEPT))));
        when(deptRepository.findById(10L)).thenReturn(Mono.just(dept(10L, "0", 0)));

        StepVerifier.create(withUser(provider.resolve(), 7L, 10L))
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("No enabled department data scope"))
                .verify();
    }

    @Test
    void resolvesOnlyEnabledCustomDepartments() {
        RoleEntity first = role(1L, DataScopeType.CUSTOM);
        RoleEntity second = role(2L, DataScopeType.CUSTOM);
        when(roleRepository.findByUserId(7L)).thenReturn(Mono.just(List.of(first, second)));
        when(roleRepository.findDataScopeDeptIds(List.of(1L, 2L))).thenReturn(Mono.just(List.of(10L, 11L, 12L)));
        when(deptRepository.findAll())
                .thenReturn(Mono.just(List.of(dept(10L, "0", 1), dept(11L, "0", 0), dept(12L, "0", 1))));

        StepVerifier.create(withUser(provider.resolve(), 7L, 20L))
                .assertNext(scope -> assertThat(scope.deptIds()).containsExactlyInAnyOrder(10L, 12L))
                .verifyComplete();
    }

    @Test
    void resolvesExplicitAllScope() {
        when(roleRepository.findByUserId(7L)).thenReturn(Mono.just(List.of(role(1L, DataScopeType.ALL))));

        StepVerifier.create(withUser(provider.resolve(), 7L, 10L))
                .assertNext(scope -> assertThat(scope.isAll()).isTrue())
                .verifyComplete();
    }

    @Test
    void rejectsEmptyDepartmentRepositoryResultForDepartmentAndChildren() {
        when(roleRepository.findByUserId(7L)).thenReturn(Mono.just(List.of(role(1L, DataScopeType.DEPT_AND_CHILD))));
        when(deptRepository.findAll()).thenReturn(Mono.empty());

        StepVerifier.create(withUser(provider.resolve(), 7L, 10L))
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("No enabled department data scope"))
                .verify();
    }

    @Test
    void rejectsNullDepartmentRepositoryReturnForDepartmentAndChildren() {
        when(roleRepository.findByUserId(7L)).thenReturn(Mono.just(List.of(role(1L, DataScopeType.DEPT_AND_CHILD))));
        when(deptRepository.findAll()).thenReturn(null);

        StepVerifier.create(withUser(provider.resolve(), 7L, 10L))
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("No enabled department data scope"))
                .verify();
    }

    @Test
    void rejectsEmptyCustomAuthorizationRepositoryResult() {
        when(roleRepository.findByUserId(7L)).thenReturn(Mono.just(List.of(role(1L, DataScopeType.CUSTOM))));
        when(roleRepository.findDataScopeDeptIds(List.of(1L))).thenReturn(Mono.empty());

        StepVerifier.create(withUser(provider.resolve(), 7L, 10L))
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("No enabled custom department data scope"))
                .verify();
    }

    @Test
    void rejectsConflictingRoleScopes() {
        when(roleRepository.findByUserId(7L))
                .thenReturn(Mono.just(List.of(role(1L, DataScopeType.SELF), role(2L, DataScopeType.ALL))));

        StepVerifier.create(withUser(provider.resolve(), 7L, 10L))
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("Conflicting data scopes"))
                .verify();
    }

    @Test
    void rejectsMissingRoles() {
        when(roleRepository.findByUserId(7L)).thenReturn(Mono.just(List.of()));

        StepVerifier.create(withUser(provider.resolve(), 7L, 10L))
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("No enabled role data scope"))
                .verify();
    }

    @Test
    void rejectsSelfScopeWithoutUserId() {
        when(roleRepository.findByUserId(null)).thenReturn(Mono.just(List.of(role(1L, DataScopeType.SELF))));

        StepVerifier.create(withUser(provider.resolve(), null, 10L))
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("Authenticated user id is required"))
                .verify();
    }

    @Test
    void rejectsEmptyRoleRepositoryResult() {
        when(roleRepository.findByUserId(7L)).thenReturn(Mono.empty());

        StepVerifier.create(withUser(provider.resolve(), 7L, 10L))
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("No enabled role data scope"))
                .verify();
    }

    @Test
    void rejectsMissingSecurityContext() {
        StepVerifier.create(provider.resolve())
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("Authenticated user is required"))
                .verify();
    }

    private <T> Mono<T> withUser(Mono<T> publisher, Long userId, Long deptId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "openid", "tenant-a", deptId);
        return publisher.contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())));
    }

    private RoleEntity role(Long id, DataScopeType type) {
        RoleEntity role = RoleEntity.create("role", "ROLE_" + id, "test");
        role.setId(id);
        role.setDataScope(type);
        return role;
    }

    private DeptEntity dept(Long id, String ancestors, int status) {
        DeptEntity dept = new DeptEntity();
        dept.setId(id);
        dept.setAncestors(ancestors);
        dept.setStatus(status);
        return dept;
    }
}
