package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.domain.gateway.RoleDeletionGateway;
import com.apex.scaffold.domain.model.entity.RoleEntity;
import com.apex.scaffold.domain.model.entity.UserRoleEntity;
import com.apex.scaffold.domain.repository.RolePermissionRepository;
import com.apex.scaffold.domain.repository.RoleRepository;
import com.apex.scaffold.domain.repository.UserRoleRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 角色服务单元测试类
 *
 * <p>测试 RoleService 的核心业务逻辑，包括角色查询、角色校验、角色管理和用户角色分配功能
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private RoleDeletionGateway roleDeletionGateway;

    @InjectMocks
    private RoleService roleService;

    private RoleEntity role;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        role = new RoleEntity();
        role.setId(1L);
        role.setName("管理员");
        role.setCode("ADMIN");
        role.setDescription("系统管理员");
    }

    @Test
    @DisplayName("getRolesByUserId - 成功获取用户角色列表")
    void getRolesByUserId_Success() {
        when(roleRepository.findByUserId(1L)).thenReturn(Mono.just(List.of(role)));

        roleService
                .getRolesByUserId(1L)
                .as(StepVerifier::create)
                .assertNext(roles -> assertThat(roles).hasSize(1))
                .verifyComplete();
    }

    @Test
    @DisplayName("getRolesByUserId - 用户无角色时返回空列表")
    void getRolesByUserId_EmptyList() {
        when(roleRepository.findByUserId(1L)).thenReturn(Mono.just(List.of()));

        roleService
                .getRolesByUserId(1L)
                .as(StepVerifier::create)
                .assertNext(roles -> assertThat(roles).isEmpty())
                .verifyComplete();
    }

    /**
     * 判断是否具有{@code role_True}。
     */
    @Test
    @DisplayName("hasRole - 用户拥有指定角色返回true")
    void hasRole_True() {
        when(roleRepository.findByUserId(1L)).thenReturn(Mono.just(List.of(role)));

        roleService
                .hasRole(1L, "ADMIN")
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();
    }

    /**
     * 判断是否具有{@code role_False}。
     */
    @Test
    @DisplayName("hasRole - 用户无指定角色返回false")
    void hasRole_False() {
        when(roleRepository.findByUserId(1L)).thenReturn(Mono.just(List.of(role)));

        roleService
                .hasRole(1L, "USER")
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    /**
     * 判断是否具有{@code role_EmptyRoles_False}。
     */
    @Test
    @DisplayName("hasRole - 用户无任何角色返回false")
    void hasRole_EmptyRoles_False() {
        when(roleRepository.findByUserId(1L)).thenReturn(Mono.just(List.of()));

        roleService
                .hasRole(1L, "ADMIN")
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("getAllRoles - 成功获取所有角色")
    void getAllRoles_Success() {
        when(roleRepository.findAll()).thenReturn(Mono.just(List.of(role)));

        roleService
                .getAllRoles()
                .as(StepVerifier::create)
                .assertNext(roles -> assertThat(roles).hasSize(1))
                .verifyComplete();
    }

    @Test
    @DisplayName("getAllRoles - 无角色时返回空列表")
    void getAllRoles_Empty() {
        when(roleRepository.findAll()).thenReturn(Mono.just(List.of()));

        roleService
                .getAllRoles()
                .as(StepVerifier::create)
                .assertNext(roles -> assertThat(roles).isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("getRoleByCode - 成功按编码获取角色")
    void getRoleByCode_Success() {
        when(roleRepository.findOne("code", "ADMIN")).thenReturn(Mono.just(role));

        roleService
                .getRoleByCode("ADMIN")
                .as(StepVerifier::create)
                .assertNext(r -> assertThat(r.getCode()).isEqualTo("ADMIN"))
                .verifyComplete();
    }

    @Test
    @DisplayName("getRoleByCode - 角色不存在时返回空")
    void getRoleByCode_NotFound() {
        when(roleRepository.findOne("code", "UNKNOWN")).thenReturn(Mono.empty());

        roleService.getRoleByCode("UNKNOWN").as(StepVerifier::create).verifyComplete();
    }

    @Test
    @DisplayName("createRole - 成功创建角色")
    void createRole_Success() {
        RoleEntity newRole = new RoleEntity();
        newRole.setName("普通用户");
        newRole.setCode("USER");
        newRole.setDescription("普通用户角色");

        when(roleRepository.insert(any(RoleEntity.class))).thenReturn(Mono.just(1));

        roleService
                .createRole(newRole)
                .as(StepVerifier::create)
                .assertNext(r -> assertThat(r.getCode()).isEqualTo("USER"))
                .verifyComplete();

        verify(roleRepository).insert(any(RoleEntity.class));
    }

    /**
     * 更新{@code role_Success}。
     */
    @Test
    @DisplayName("updateRole - 成功更新角色")
    void updateRole_Success() {
        RoleEntity updateRole = new RoleEntity();
        updateRole.setId(1L);
        updateRole.setName("管理员-更新");
        updateRole.setDescription("更新后的角色描述");

        when(roleRepository.findById(1L)).thenReturn(Mono.just(role));
        when(roleRepository.updateById(any(RoleEntity.class))).thenReturn(Mono.just(1));

        roleService
                .updateRole(updateRole)
                .as(StepVerifier::create)
                .assertNext(r -> assertThat(r.getId()).isEqualTo(1L))
                .verifyComplete();

        verify(roleRepository).updateById(any(RoleEntity.class));
    }

    /**
     * 更新{@code role_NotFound}。
     */
    @Test
    @DisplayName("updateRole - 角色不存在时返回空")
    void updateRole_NotFound() {
        RoleEntity updateRole = new RoleEntity();
        updateRole.setId(999L);

        when(roleRepository.findById(999L)).thenReturn(Mono.empty());

        roleService.updateRole(updateRole).as(StepVerifier::create).verifyComplete();
    }

    /**
     * 删除{@code role_Success}。
     */
    @Test
    @DisplayName("deleteRole - 成功删除角色及关联关系")
    void deleteRole_Success() {
        when(roleDeletionGateway.deleteRole(1L)).thenReturn(true);

        roleService.deleteRole(1L).as(StepVerifier::create).expectNext(true).verifyComplete();

        verify(roleDeletionGateway).deleteRole(1L);
    }

    /**
     * 删除{@code role_NoRowsDeleted}。
     */
    @Test
    @DisplayName("deleteRole - 未删除任何记录返回false")
    void deleteRole_NoRowsDeleted() {
        when(roleDeletionGateway.deleteRole(999L)).thenReturn(false);

        roleService.deleteRole(999L).as(StepVerifier::create).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("assignRoleToUser - 成功为用户分配角色")
    void assignRoleToUser_Success() {
        when(userRoleRepository.insert(any(UserRoleEntity.class))).thenReturn(Mono.just(1));

        roleService
                .assignRoleToUser(1L, 2L)
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();

        verify(userRoleRepository).insert(any(UserRoleEntity.class));
    }

    @Test
    @DisplayName("assignRoleToUser - 未插入记录返回false")
    void assignRoleToUser_NoRowsInserted() {
        when(userRoleRepository.insert(any(UserRoleEntity.class))).thenReturn(Mono.just(0));

        roleService
                .assignRoleToUser(1L, 2L)
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    /**
     * 移除{@code roleFromUser_Success}。
     */
    @Test
    @DisplayName("removeRoleFromUser - 成功移除用户角色")
    void removeRoleFromUser_Success() {
        when(userRoleRepository.delete("user_id", 1L, "role_id", 2L)).thenReturn(Mono.just(1));

        roleService
                .removeRoleFromUser(1L, 2L)
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();

        verify(userRoleRepository).delete("user_id", 1L, "role_id", 2L);
    }

    /**
     * 移除{@code roleFromUser_NoRowsDeleted}。
     */
    @Test
    @DisplayName("removeRoleFromUser - 未删除记录返回false")
    void removeRoleFromUser_NoRowsDeleted() {
        when(userRoleRepository.delete("user_id", 1L, "role_id", 2L)).thenReturn(Mono.just(0));

        roleService
                .removeRoleFromUser(1L, 2L)
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }
}
