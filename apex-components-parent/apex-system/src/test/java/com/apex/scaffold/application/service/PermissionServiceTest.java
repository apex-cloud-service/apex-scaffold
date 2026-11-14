package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.model.entity.PermissionEntity;
import com.apex.scaffold.domain.repository.PermissionRepository;
import java.util.List;
import java.util.Set;
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
 * 权限服务单元测试类
 *
 * <p>测试 PermissionService 的核心业务逻辑，包括权限查询、权限校验和权限管理功能
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private TenantGuard tenantGuard;

    @Mock
    private TenantPackageService tenantPackageService;

    @InjectMocks
    private PermissionService permissionService;

    private PermissionEntity permission;

    /**
     * 初始化权限夹具与默认全量套餐桩。
     */
    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(tenantPackageService.resolveScope(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.just(TenantPackageService.PackageScope.full()));
        permission = new PermissionEntity();
        permission.setId(1L);
        permission.setName("用户管理");
        permission.setCode("USER_MANAGE");
        permission.setDescription("管理用户信息");
        permission.setType(1);
        lenient().when(tenantGuard.assertActive()).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("getPermissionsByUserId - 成功获取用户权限列表")
    void getPermissionsByUserId_Success() {
        when(permissionRepository.findByUserId(1L)).thenReturn(Mono.just(List.of(permission)));

        permissionService
                .getPermissionsByUserId(1L)
                .contextWrite(TenantContext.withTenant("default"))
                .as(StepVerifier::create)
                .assertNext(permissions -> assertThat(permissions).hasSize(1))
                .verifyComplete();
    }

    @Test
    @DisplayName("getPermissionsByUserId - 用户无权限时返回空列表")
    void getPermissionsByUserId_EmptyList() {
        when(permissionRepository.findByUserId(1L)).thenReturn(Mono.just(List.of()));

        permissionService
                .getPermissionsByUserId(1L)
                .contextWrite(TenantContext.withTenant("default"))
                .as(StepVerifier::create)
                .assertNext(permissions -> assertThat(permissions).isEmpty())
                .verifyComplete();
    }

    /**
     * 验证无租户上下文时不读取角色权限，直接返回空列表。
     */
    @Test
    @DisplayName("getPermissionsByUserId - 无租户上下文返回空列表且不查仓储")
    void getPermissionsByUserId_NoTenantContextFailsClosed() {
        permissionService
                .getPermissionsByUserId(1L)
                .as(StepVerifier::create)
                .assertNext(permissions -> assertThat(permissions).isEmpty())
                .verifyComplete();

        verify(permissionRepository, never()).findByUserId(anyLong());
        verify(tenantPackageService, never()).resolveScope(anyString());
    }

    /**
     * 验证拒绝范围不读取角色权限并返回空列表。
     */
    @Test
    @DisplayName("getPermissionsByUserId - 拒绝范围返回空列表且不查仓储")
    void getPermissionsByUserId_DeniedScopeFailsClosed() {
        when(tenantPackageService.resolveScope("tenant-a"))
                .thenReturn(Mono.just(TenantPackageService.PackageScope.deniedScope()));

        permissionService
                .getPermissionsByUserId(1L)
                .contextWrite(TenantContext.withTenant("tenant-a"))
                .as(StepVerifier::create)
                .assertNext(permissions -> assertThat(permissions).isEmpty())
                .verifyComplete();

        verify(permissionRepository, never()).findByUserId(anyLong());
    }

    /**
     * 验证受限套餐只保留权限码交集。
     */
    @Test
    @DisplayName("getPermissionsByUserId - 受限套餐返回权限码交集")
    void getPermissionsByUserId_ScopedByPackage() {
        PermissionEntity write = new PermissionEntity();
        write.setId(2L);
        write.setCode("USER_WRITE");
        when(tenantPackageService.resolveScope("tenant-a"))
                .thenReturn(Mono.just(new TenantPackageService.PackageScope(
                        false, false, Set.of(), Set.of("USER_MANAGE"), Set.of())));
        when(permissionRepository.findByUserId(1L)).thenReturn(Mono.just(List.of(permission, write)));

        permissionService
                .getPermissionsByUserId(1L)
                .contextWrite(TenantContext.withTenant("tenant-a"))
                .as(StepVerifier::create)
                .assertNext(permissions -> assertThat(permissions)
                        .extracting(PermissionEntity::getCode)
                        .containsExactly("USER_MANAGE"))
                .verifyComplete();
    }

    /**
     * 判断是否具有{@code permission_True}。
     */
    @Test
    @DisplayName("hasPermission - 用户拥有指定权限返回true")
    void hasPermission_True() {
        when(permissionRepository.findByUserId(1L)).thenReturn(Mono.just(List.of(permission)));

        permissionService
                .hasPermission(1L, "USER_MANAGE")
                .contextWrite(TenantContext.withTenant("default"))
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();
    }

    /**
     * 判断是否具有{@code permission_False}。
     */
    @Test
    @DisplayName("hasPermission - 用户无指定权限返回false")
    void hasPermission_False() {
        when(permissionRepository.findByUserId(1L)).thenReturn(Mono.just(List.of(permission)));

        permissionService
                .hasPermission(1L, "ADMIN_MANAGE")
                .contextWrite(TenantContext.withTenant("default"))
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    /**
     * 判断是否具有{@code permission_EmptyPermissions_False}。
     */
    @Test
    @DisplayName("hasPermission - 用户无任何权限返回false")
    void hasPermission_EmptyPermissions_False() {
        when(permissionRepository.findByUserId(1L)).thenReturn(Mono.just(List.of()));

        permissionService
                .hasPermission(1L, "USER_MANAGE")
                .contextWrite(TenantContext.withTenant("default"))
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    /**
     * 判断是否具有{@code anyPermission_True}。
     */
    @Test
    @DisplayName("hasAnyPermission - 用户拥有任一权限返回true")
    void hasAnyPermission_True() {
        when(permissionRepository.findByUserId(1L)).thenReturn(Mono.just(List.of(permission)));

        permissionService
                .hasAnyPermission(1L, List.of("USER_MANAGE", "ADMIN_MANAGE"))
                .contextWrite(TenantContext.withTenant("default"))
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();
    }

    /**
     * 判断是否具有{@code anyPermission_False}。
     */
    @Test
    @DisplayName("hasAnyPermission - 用户无任一权限返回false")
    void hasAnyPermission_False() {
        when(permissionRepository.findByUserId(1L)).thenReturn(Mono.just(List.of(permission)));

        permissionService
                .hasAnyPermission(1L, List.of("ADMIN_MANAGE", "SYSTEM_MANAGE"))
                .contextWrite(TenantContext.withTenant("default"))
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    /**
     * 判断是否具有{@code allPermissions_True}。
     */
    @Test
    @DisplayName("hasAllPermissions - 用户拥有所有权限返回true")
    void hasAllPermissions_True() {
        PermissionEntity perm2 = new PermissionEntity();
        perm2.setId(2L);
        perm2.setCode("ADMIN_MANAGE");

        when(permissionRepository.findByUserId(1L)).thenReturn(Mono.just(List.of(permission, perm2)));

        permissionService
                .hasAllPermissions(1L, List.of("USER_MANAGE", "ADMIN_MANAGE"))
                .contextWrite(TenantContext.withTenant("default"))
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();
    }

    /**
     * 判断是否具有{@code allPermissions_False}。
     */
    @Test
    @DisplayName("hasAllPermissions - 用户缺少部分权限返回false")
    void hasAllPermissions_False() {
        when(permissionRepository.findByUserId(1L)).thenReturn(Mono.just(List.of(permission)));

        permissionService
                .hasAllPermissions(1L, List.of("USER_MANAGE", "ADMIN_MANAGE"))
                .contextWrite(TenantContext.withTenant("default"))
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("getAllPermissions - 成功获取所有权限")
    void getAllPermissions_Success() {
        when(permissionRepository.findAll()).thenReturn(Mono.just(List.of(permission)));

        permissionService
                .getAllPermissions()
                .as(StepVerifier::create)
                .assertNext(permissions -> assertThat(permissions).hasSize(1))
                .verifyComplete();
    }

    @Test
    @DisplayName("getAllPermissions - 无权限时返回空列表")
    void getAllPermissions_Empty() {
        when(permissionRepository.findAll()).thenReturn(Mono.just(List.of()));

        permissionService
                .getAllPermissions()
                .as(StepVerifier::create)
                .assertNext(permissions -> assertThat(permissions).isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("getPermissionByCode - 成功按编码获取权限")
    void getPermissionByCode_Success() {
        when(permissionRepository.findOne("code", "USER_MANAGE")).thenReturn(Mono.just(permission));

        permissionService
                .getPermissionByCode("USER_MANAGE")
                .as(StepVerifier::create)
                .assertNext(p -> assertThat(p.getCode()).isEqualTo("USER_MANAGE"))
                .verifyComplete();
    }

    @Test
    @DisplayName("getPermissionByCode - 权限不存在时返回空")
    void getPermissionByCode_NotFound() {
        when(permissionRepository.findOne("code", "UNKNOWN")).thenReturn(Mono.empty());

        permissionService
                .getPermissionByCode("UNKNOWN")
                .as(StepVerifier::create)
                .verifyComplete();
    }

    @Test
    @DisplayName("createPermission - 成功创建权限")
    void createPermission_Success() {
        PermissionEntity newPerm = new PermissionEntity();
        newPerm.setName("角色管理");
        newPerm.setCode("ROLE_MANAGE");
        newPerm.setDescription("管理角色信息");
        newPerm.setType(1);

        when(permissionRepository.insert(any(PermissionEntity.class))).thenReturn(Mono.just(1));

        permissionService
                .createPermission(newPerm)
                .as(StepVerifier::create)
                .assertNext(p -> assertThat(p.getCode()).isEqualTo("ROLE_MANAGE"))
                .verifyComplete();

        verify(permissionRepository).insert(any(PermissionEntity.class));
    }

    /**
     * 更新{@code permission_Success}。
     */
    @Test
    @DisplayName("updatePermission - 成功更新权限")
    void updatePermission_Success() {
        PermissionEntity updatePerm = new PermissionEntity();
        updatePerm.setId(1L);
        updatePerm.setName("用户管理-更新");
        updatePerm.setDescription("更新后的描述");
        updatePerm.setType(1);

        when(permissionRepository.findById(1L)).thenReturn(Mono.just(permission));
        when(permissionRepository.updateById(any(PermissionEntity.class))).thenReturn(Mono.just(1));

        permissionService
                .updatePermission(updatePerm)
                .as(StepVerifier::create)
                .assertNext(p -> assertThat(p.getId()).isEqualTo(1L))
                .verifyComplete();

        verify(permissionRepository).updateById(any(PermissionEntity.class));
    }

    /**
     * 更新{@code permission_NotFound}。
     */
    @Test
    @DisplayName("updatePermission - 权限不存在时返回空")
    void updatePermission_NotFound() {
        PermissionEntity updatePerm = new PermissionEntity();
        updatePerm.setId(999L);

        when(permissionRepository.findById(999L)).thenReturn(Mono.empty());

        permissionService.updatePermission(updatePerm).as(StepVerifier::create).verifyComplete();
    }

    /**
     * 删除{@code permission_Success}。
     */
    @Test
    @DisplayName("deletePermission - 成功删除权限")
    void deletePermission_Success() {
        when(permissionRepository.deleteById(1L)).thenReturn(Mono.just(1));

        permissionService
                .deletePermission(1L)
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();

        verify(permissionRepository).deleteById(1L);
    }

    /**
     * 删除{@code permission_NoRowsDeleted}。
     */
    @Test
    @DisplayName("deletePermission - 未删除任何记录返回false")
    void deletePermission_NoRowsDeleted() {
        when(permissionRepository.deleteById(999L)).thenReturn(Mono.just(0));

        permissionService
                .deletePermission(999L)
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }
}
