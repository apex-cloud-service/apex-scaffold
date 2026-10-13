package com.apex.scaffold.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.auth.domain.model.entity.RoleEntity;
import com.apex.scaffold.auth.domain.repository.PermissionRepository;
import com.apex.scaffold.auth.domain.repository.RoleRepository;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 角色服务单元测试
 *
 * @author Apex Scaffold Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("角色服务单元测试")
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private RoleService roleService;

    private RoleEntity mockRole;

    @BeforeEach
    void setUp() {
        Set<Long> permissionIds = new HashSet<>();
        permissionIds.add(1001L);
        permissionIds.add(1002L);

        mockRole = RoleEntity.builder()
                .id(1L)
                .tenantId("test-tenant")
                .code("ADMIN")
                .name("管理员")
                .description("系统管理员角色")
                .dataScope("ALL")
                .sortOrder(1)
                .enabled(true)
                .deleted(false)
                .permissionIds(permissionIds)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("createRole() 方法测试")
    class CreateRoleTest {

        @Test
        @DisplayName("角色编码不存在时创建成功")
        void testCreateRoleSuccess() {
            RoleEntity newRole = RoleEntity.builder()
                    .code("USER")
                    .name("普通用户")
                    .tenantId("test-tenant")
                    .build();

            when(roleRepository.existsByCode("USER", "test-tenant")).thenReturn(Mono.just(false));
            when(roleRepository.save(any(RoleEntity.class))).thenAnswer(invocation -> {
                RoleEntity role = invocation.getArgument(0);
                role.setId(2L);
                return Mono.just(role);
            });

            Mono<RoleEntity> result = roleService.createRole(newRole);

            StepVerifier.create(result)
                    .assertNext(role -> {
                        assertThat(role.getId()).isEqualTo(2L);
                        assertThat(role.getCode()).isEqualTo("USER");
                        assertThat(role.getName()).isEqualTo("普通用户");
                        assertThat(role.getCreatedAt()).isNotNull();
                        assertThat(role.getUpdatedAt()).isNotNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("角色编码已存在时创建失败")
        void testCreateRoleDuplicateCode() {
            RoleEntity newRole = RoleEntity.builder()
                    .code("ADMIN")
                    .name("管理员")
                    .tenantId("test-tenant")
                    .build();

            when(roleRepository.existsByCode("ADMIN", "test-tenant")).thenReturn(Mono.just(true));

            Mono<RoleEntity> result = roleService.createRole(newRole);

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.PARAM_ERROR.getCode())
                            && ex.getMessage().contains("角色编码已存在"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("getRoleById() 方法测试")
    class GetRoleByIdTest {

        @Test
        @DisplayName("根据ID查询角色成功")
        void testGetRoleByIdSuccess() {
            when(roleRepository.findById(1L)).thenReturn(Mono.just(mockRole));

            Mono<RoleEntity> result = roleService.getRoleById(1L);

            StepVerifier.create(result)
                    .assertNext(role -> {
                        assertThat(role.getId()).isEqualTo(1L);
                        assertThat(role.getCode()).isEqualTo("ADMIN");
                        assertThat(role.getName()).isEqualTo("管理员");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("角色不存在时抛出异常")
        void testGetRoleByIdNotFound() {
            when(roleRepository.findById(999L)).thenReturn(Mono.empty());

            Mono<RoleEntity> result = roleService.getRoleById(999L);

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.NOT_FOUND.getCode())
                            && ex.getMessage().contains("角色不存在"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("getRoleByCode() 方法测试")
    class GetRoleByCodeTest {

        @Test
        @DisplayName("根据编码查询角色成功")
        void testGetRoleByCodeSuccess() {
            when(roleRepository.findByCode("ADMIN", "test-tenant")).thenReturn(Mono.just(mockRole));

            Mono<RoleEntity> result = roleService.getRoleByCode("ADMIN", "test-tenant");

            StepVerifier.create(result)
                    .assertNext(role -> {
                        assertThat(role.getCode()).isEqualTo("ADMIN");
                        assertThat(role.getTenantId()).isEqualTo("test-tenant");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("角色编码不存在时抛出异常")
        void testGetRoleByCodeNotFound() {
            when(roleRepository.findByCode("NON_EXISTENT", "test-tenant")).thenReturn(Mono.empty());

            Mono<RoleEntity> result = roleService.getRoleByCode("NON_EXISTENT", "test-tenant");

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.NOT_FOUND.getCode()))
                    .verify();
        }
    }

    @Nested
    @DisplayName("getRolesByTenantId() 方法测试")
    class GetRolesByTenantIdTest {

        @Test
        @DisplayName("获取租户角色列表成功")
        void testGetRolesByTenantIdSuccess() {
            RoleEntity role2 = RoleEntity.builder()
                    .id(2L)
                    .code("USER")
                    .name("普通用户")
                    .tenantId("test-tenant")
                    .build();

            when(roleRepository.findByTenantId("test-tenant")).thenReturn(Flux.just(mockRole, role2));

            Flux<RoleEntity> result = roleService.getRolesByTenantId("test-tenant");

            StepVerifier.create(result).expectNext(mockRole).expectNext(role2).verifyComplete();
        }

        @Test
        @DisplayName("租户无角色时返回空流")
        void testGetRolesByTenantIdEmpty() {
            when(roleRepository.findByTenantId("empty-tenant")).thenReturn(Flux.empty());

            Flux<RoleEntity> result = roleService.getRolesByTenantId("empty-tenant");

            StepVerifier.create(result).verifyComplete();
        }
    }

    @Nested
    @DisplayName("getEnabledRoles() 方法测试")
    class GetEnabledRolesTest {

        @Test
        @DisplayName("获取已启用角色列表成功")
        void testGetEnabledRolesSuccess() {
            when(roleRepository.findEnabledByTenantId("test-tenant")).thenReturn(Flux.just(mockRole));

            Flux<RoleEntity> result = roleService.getEnabledRoles("test-tenant");

            StepVerifier.create(result).expectNext(mockRole).verifyComplete();
        }
    }

    @Nested
    @DisplayName("getUserRoles() 方法测试")
    class GetUserRolesTest {

        @Test
        @DisplayName("获取用户角色列表成功")
        void testGetUserRolesSuccess() {
            when(roleRepository.findByUserId(100L, "test-tenant")).thenReturn(Flux.just(mockRole));

            Flux<RoleEntity> result = roleService.getUserRoles(100L, "test-tenant");

            StepVerifier.create(result).expectNext(mockRole).verifyComplete();
        }
    }

    @Nested
    @DisplayName("updateRole() 方法测试")
    class UpdateRoleTest {

        @Test
        @DisplayName("更新角色成功")
        void testUpdateRoleSuccess() {
            RoleEntity updatedRole = RoleEntity.builder()
                    .id(1L)
                    .code("ADMIN")
                    .name("超级管理员")
                    .description("更新后的描述")
                    .build();

            when(roleRepository.findById(1L)).thenReturn(Mono.just(mockRole));
            when(roleRepository.update(any(RoleEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            Mono<RoleEntity> result = roleService.updateRole(updatedRole);

            StepVerifier.create(result)
                    .assertNext(role -> {
                        assertThat(role.getId()).isEqualTo(1L);
                        assertThat(role.getName()).isEqualTo("超级管理员");
                        assertThat(role.getUpdatedAt()).isNotNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("更新不存在的角色时抛出异常")
        void testUpdateRoleNotFound() {
            RoleEntity updatedRole =
                    RoleEntity.builder().id(999L).name("不存在的角色").build();

            when(roleRepository.findById(999L)).thenReturn(Mono.empty());

            Mono<RoleEntity> result = roleService.updateRole(updatedRole);

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.NOT_FOUND.getCode()))
                    .verify();
        }
    }

    @Nested
    @DisplayName("deleteRole() 方法测试")
    class DeleteRoleTest {

        @Test
        @DisplayName("删除角色成功")
        void testDeleteRoleSuccess() {
            when(roleRepository.deleteById(1L)).thenReturn(Mono.just(true));

            Mono<Void> result = roleService.deleteRole(1L);

            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("删除失败时抛出异常")
        void testDeleteRoleFailure() {
            when(roleRepository.deleteById(1L)).thenReturn(Mono.just(false));

            Mono<Void> result = roleService.deleteRole(1L);

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.ERROR.getCode())
                            && ex.getMessage().contains("角色删除失败"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("assignPermissionsToRole() 方法测试")
    class AssignPermissionsToRoleTest {

        @Test
        @DisplayName("分配权限给角色成功")
        void testAssignPermissionsToRoleSuccess() {
            Set<Long> newPermissions = new HashSet<>();
            newPermissions.add(2001L);
            newPermissions.add(2002L);

            when(roleRepository.findById(1L)).thenReturn(Mono.just(mockRole));
            when(roleRepository.update(any(RoleEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            Mono<Void> result = roleService.assignPermissionsToRole(1L, newPermissions);

            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("角色不存在时分配权限失败")
        void testAssignPermissionsToRoleNotFound() {
            Set<Long> newPermissions = new HashSet<>();
            newPermissions.add(2001L);

            when(roleRepository.findById(999L)).thenReturn(Mono.empty());

            Mono<Void> result = roleService.assignPermissionsToRole(999L, newPermissions);

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.NOT_FOUND.getCode()))
                    .verify();
        }
    }

    @Nested
    @DisplayName("assignRolesToUser() 方法测试")
    class AssignRolesToUserTest {

        @Test
        @DisplayName("分配角色给用户成功")
        void testAssignRolesToUserSuccess() {
            Set<Long> roleIds = new HashSet<>();
            roleIds.add(1L);
            roleIds.add(2L);

            when(roleRepository.assignRolesToUser(100L, roleIds, "test-tenant")).thenReturn(Mono.just(true));

            Mono<Void> result = roleService.assignRolesToUser(100L, roleIds, "test-tenant");

            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("分配失败时抛出异常")
        void testAssignRolesToUserFailure() {
            Set<Long> roleIds = new HashSet<>();
            roleIds.add(1L);

            when(roleRepository.assignRolesToUser(100L, roleIds, "test-tenant")).thenReturn(Mono.just(false));

            Mono<Void> result = roleService.assignRolesToUser(100L, roleIds, "test-tenant");

            StepVerifier.create(result)
                    .expectErrorMatches(ex ->
                            ex instanceof BusinessException && ex.getMessage().contains("用户角色分配失败"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("removeRolesFromUser() 方法测试")
    class RemoveRolesFromUserTest {

        @Test
        @DisplayName("移除用户角色成功")
        void testRemoveRolesFromUserSuccess() {
            Set<Long> roleIds = new HashSet<>();
            roleIds.add(1L);

            when(roleRepository.removeRolesFromUser(100L, roleIds, "test-tenant"))
                    .thenReturn(Mono.just(true));

            Mono<Void> result = roleService.removeRolesFromUser(100L, roleIds, "test-tenant");

            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("移除失败时抛出异常")
        void testRemoveRolesFromUserFailure() {
            Set<Long> roleIds = new HashSet<>();
            roleIds.add(1L);

            when(roleRepository.removeRolesFromUser(100L, roleIds, "test-tenant"))
                    .thenReturn(Mono.just(false));

            Mono<Void> result = roleService.removeRolesFromUser(100L, roleIds, "test-tenant");

            StepVerifier.create(result)
                    .expectErrorMatches(ex ->
                            ex instanceof BusinessException && ex.getMessage().contains("用户角色移除失败"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("listRoles() 方法测试")
    class ListRolesTest {

        @Test
        @DisplayName("列出所有角色成功")
        void testListRolesSuccess() {
            RoleEntity role2 = RoleEntity.builder().id(2L).code("USER").build();

            when(roleRepository.findAll()).thenReturn(Flux.just(mockRole, role2));

            Flux<RoleEntity> result = roleService.listRoles();

            StepVerifier.create(result).expectNext(mockRole).expectNext(role2).verifyComplete();
        }
    }

    @Nested
    @DisplayName("assignPermissions() 方法测试")
    class AssignPermissionsTest {

        @Test
        @DisplayName("分配权限成功（委托方法）")
        void testAssignPermissionsSuccess() {
            Set<Long> permissionIds = new HashSet<>();
            permissionIds.add(3001L);

            when(roleRepository.findById(1L)).thenReturn(Mono.just(mockRole));
            when(roleRepository.update(any(RoleEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            Mono<Void> result = roleService.assignPermissions(1L, permissionIds);

            StepVerifier.create(result).verifyComplete();
        }
    }

    @Nested
    @DisplayName("getRolePermissions() 方法测试")
    class GetRolePermissionsTest {

        @Test
        @DisplayName("获取角色权限列表成功")
        void testGetRolePermissionsSuccess() {
            when(roleRepository.findById(1L)).thenReturn(Mono.just(mockRole));

            Mono<Set<Long>> result = roleService.getRolePermissions(1L);

            StepVerifier.create(result)
                    .assertNext(permissions -> {
                        assertThat(permissions).hasSize(2);
                        assertThat(permissions).contains(1001L, 1002L);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("角色不存在时抛出异常")
        void testGetRolePermissionsNotFound() {
            when(roleRepository.findById(999L)).thenReturn(Mono.empty());

            Mono<Set<Long>> result = roleService.getRolePermissions(999L);

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof BusinessException
                            && ((BusinessException) ex).getCode().equals(ResultCode.NOT_FOUND.getCode()))
                    .verify();
        }
    }

    @Test
    void assigningEmptyPermissionsClearsStoredPermissions() {
        when(roleRepository.findById(1L)).thenReturn(Mono.just(mockRole));
        when(roleRepository.update(mockRole)).thenAnswer(invocation -> {
            RoleEntity role = invocation.getArgument(0);
            assertThat(role.getPermissionIds()).isEmpty();
            return Mono.just(role);
        });

        StepVerifier.create(roleService.assignPermissions(1L, Set.of())).verifyComplete();

        verify(roleRepository).update(mockRole);
    }

    @Test
    void permissionAssignmentLookupFailurePreventsUpdate() {
        IllegalStateException failure = new IllegalStateException("lookup failed");
        when(roleRepository.findById(1L)).thenReturn(Mono.error(failure));

        StepVerifier.create(roleService.assignPermissionsToRole(1L, Set.of(3001L)))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();

        assertThat(mockRole.getPermissionIds()).containsExactlyInAnyOrder(1001L, 1002L);
        verify(roleRepository, never()).update(any());
    }

    @Test
    void permissionAssignmentUpdateFailureIsPropagated() {
        IllegalStateException failure = new IllegalStateException("update failed");
        when(roleRepository.findById(1L)).thenReturn(Mono.just(mockRole));
        when(roleRepository.update(mockRole)).thenReturn(Mono.error(failure));

        StepVerifier.create(roleService.assignPermissions(1L, Set.of(3001L)))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();
    }

    @Test
    void userRoleAssignmentRepositoryFailureIsPropagatedWithTenant() {
        Set<Long> roleIds = Set.of(1L);
        IllegalStateException failure = new IllegalStateException("assignment failed");
        when(roleRepository.assignRolesToUser(100L, roleIds, "test-tenant")).thenReturn(Mono.error(failure));

        StepVerifier.create(roleService.assignRolesToUser(100L, roleIds, "test-tenant"))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();

        verify(roleRepository).assignRolesToUser(100L, roleIds, "test-tenant");
    }
}
