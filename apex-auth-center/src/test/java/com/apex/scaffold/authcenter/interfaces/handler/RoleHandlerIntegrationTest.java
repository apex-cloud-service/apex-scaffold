package com.apex.scaffold.authcenter.interfaces.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import com.apex.scaffold.authcenter.application.assembler.RoleAssembler;
import com.apex.scaffold.authcenter.application.dto.AssignPermissionsRequest;
import com.apex.scaffold.authcenter.application.dto.RoleDTO;
import com.apex.scaffold.authcenter.application.service.RoleService;
import com.apex.scaffold.authcenter.domain.model.entity.RoleEntity;
import com.apex.scaffold.authcenter.interfaces.router.RoleRouter;
import com.apex.scaffold.common.result.ResultCode;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 角色Handler集成测试
 *
 * <p>测试角色管理相关的HTTP接口，验证：
 * <ul>
 *   <li>创建角色：正常创建、参数校验</li>
 *   <li>更新角色：正常更新、不存在的角色</li>
 *   <li>删除角色：正常删除</li>
 *   <li>查询角色：根据ID查询、列出所有角色</li>
 *   <li>分配权限：正常分配、空权限集</li>
 *   <li>查询权限：查询角色的权限列表</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@WebFluxTest(controllers = RoleHandler.class)
@Import({RoleHandler.class, RoleRouter.class})
@DisplayName("角色Handler集成测试")
@WithMockUser
class RoleHandlerIntegrationTest {

    /**
     * {@code WebTestClient} 在 {@code @WebFluxTest} 中无法通过构造器解析，
     * 故采用 JSR-250 {@code @Resource} 字段注入。
     */
    @Resource
    private WebTestClient webTestClient;

    @MockBean
    private RoleService roleApplicationService;

    @MockBean
    private RoleAssembler roleAssembler;

    private RoleEntity testRoleEntity;
    private RoleDTO testRoleDto;

    @BeforeEach
    void setUp() {
        testRoleEntity = RoleEntity.builder()
                .id(1L)
                .tenantId("default")
                .code("ROLE_TEST")
                .name("测试角色")
                .description("测试角色描述")
                .dataScope("ALL")
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testRoleDto = RoleDTO.builder()
                .id(1L)
                .tenantId("default")
                .code("ROLE_TEST")
                .name("测试角色")
                .description("测试角色描述")
                .dataScope(0)
                .enabled(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("创建角色 - 成功")
    void testCreateRole_Success() {
        when(roleAssembler.toEntity(any(RoleDTO.class))).thenReturn(testRoleEntity);
        when(roleApplicationService.createRole(any(RoleEntity.class))).thenReturn(Mono.just(testRoleEntity));
        when(roleAssembler.toDto(any(RoleEntity.class))).thenReturn(testRoleDto);

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/auth/v1/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(testRoleDto)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.code")
                .isEqualTo("ROLE_TEST")
                .jsonPath("$.data.name")
                .isEqualTo("测试角色");
    }

    @Test
    @DisplayName("更新角色 - 成功")
    void testUpdateRole_Success() {
        when(roleAssembler.toEntity(any(RoleDTO.class))).thenReturn(testRoleEntity);
        when(roleApplicationService.updateRole(any(RoleEntity.class))).thenReturn(Mono.just(testRoleEntity));
        when(roleAssembler.toDto(any(RoleEntity.class))).thenReturn(testRoleDto);

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .put()
                .uri("/auth/v1/roles/1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(testRoleDto)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.id")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("删除角色 - 成功")
    void testDeleteRole_Success() {
        when(roleApplicationService.deleteRole(anyLong())).thenReturn(Mono.empty());

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .delete()
                .uri("/auth/v1/roles/1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("根据ID查询角色 - 成功")
    void testGetRoleById_Success() {
        when(roleApplicationService.getRoleById(anyLong())).thenReturn(Mono.just(testRoleEntity));
        when(roleAssembler.toDto(any(RoleEntity.class))).thenReturn(testRoleDto);

        webTestClient
                .get()
                .uri("/auth/v1/roles/1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.id")
                .isEqualTo(1)
                .jsonPath("$.data.name")
                .isEqualTo("测试角色");
    }

    @Test
    @DisplayName("列出所有角色 - 成功")
    void testListRoles_Success() {
        when(roleApplicationService.listRoles()).thenReturn(Flux.just(testRoleEntity));
        when(roleAssembler.toDto(any(RoleEntity.class))).thenReturn(testRoleDto);

        webTestClient
                .get()
                .uri("/auth/v1/roles")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isArray()
                .jsonPath("$.data[0].code")
                .isEqualTo("ROLE_TEST");
    }

    @Test
    @DisplayName("分配权限 - 成功")
    void testAssignPermissions_Success() {
        Set<Long> permissionIds = new HashSet<>(Arrays.asList(1L, 2L, 3L));
        AssignPermissionsRequest request = AssignPermissionsRequest.builder()
                .roleId(1L)
                .permissionIds(permissionIds)
                .build();

        when(roleApplicationService.assignPermissions(anyLong(), anySet())).thenReturn(Mono.empty());

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/auth/v1/roles/assign-permissions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("查询角色权限 - 成功")
    void testGetRolePermissions_Success() {
        when(roleApplicationService.getRolePermissions(anyLong())).thenReturn(Mono.just(Set.of(1L, 2L, 3L)));

        webTestClient
                .get()
                .uri("/auth/v1/roles/1/permissions")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isArray()
                .jsonPath("$.data.length()")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("查询不存在的角色 - 返回空")
    void testGetRoleById_NotFound() {
        when(roleApplicationService.getRoleById(anyLong())).thenReturn(Mono.empty());

        webTestClient
                .get()
                .uri("/auth/v1/roles/999")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testCreateRole_SuccessServiceError() {
        when(roleAssembler.toEntity(any(RoleDTO.class))).thenReturn(testRoleEntity);
        when(roleApplicationService.createRole(any(RoleEntity.class)))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));
        when(roleAssembler.toDto(any(RoleEntity.class))).thenReturn(testRoleDto);

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/auth/v1/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(testRoleDto)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testUpdateRole_SuccessServiceError() {
        when(roleAssembler.toEntity(any(RoleDTO.class))).thenReturn(testRoleEntity);
        when(roleApplicationService.updateRole(any(RoleEntity.class)))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));
        when(roleAssembler.toDto(any(RoleEntity.class))).thenReturn(testRoleDto);

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .put()
                .uri("/auth/v1/roles/1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(testRoleDto)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testDeleteRole_SuccessServiceError() {
        when(roleApplicationService.deleteRole(anyLong()))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .delete()
                .uri("/auth/v1/roles/1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testGetRoleById_SuccessServiceError() {
        when(roleApplicationService.getRoleById(anyLong()))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));
        when(roleAssembler.toDto(any(RoleEntity.class))).thenReturn(testRoleDto);

        webTestClient
                .get()
                .uri("/auth/v1/roles/1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testListRoles_SuccessServiceError() {
        when(roleApplicationService.listRoles())
                .thenReturn(Flux.error(new IllegalStateException("service unavailable")));
        when(roleAssembler.toDto(any(RoleEntity.class))).thenReturn(testRoleDto);

        webTestClient
                .get()
                .uri("/auth/v1/roles")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testAssignPermissions_SuccessServiceError() {
        Set<Long> permissionIds = new HashSet<>(Arrays.asList(1L, 2L, 3L));
        AssignPermissionsRequest request = AssignPermissionsRequest.builder()
                .roleId(1L)
                .permissionIds(permissionIds)
                .build();

        when(roleApplicationService.assignPermissions(anyLong(), anySet()))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/auth/v1/roles/assign-permissions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testGetRolePermissions_SuccessServiceError() {
        when(roleApplicationService.getRolePermissions(anyLong()))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .get()
                .uri("/auth/v1/roles/1/permissions")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.msg")
                .isNotEmpty()
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void testUpdateUsesPathId() {
        testRoleDto.setId(999L);
        when(roleAssembler.toEntity(any(RoleDTO.class))).thenReturn(testRoleEntity);
        when(roleApplicationService.updateRole(any(RoleEntity.class))).thenReturn(Mono.just(testRoleEntity));
        when(roleAssembler.toDto(any(RoleEntity.class))).thenReturn(testRoleDto);
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .put()
                .uri("/auth/v1/roles/1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(testRoleDto)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
        org.mockito.Mockito.verify(roleAssembler).toEntity(org.mockito.ArgumentMatchers.argThat(dto -> dto.getId()
                .equals(1L)));
    }

    @Test
    void testListRolesEmpty() {
        when(roleApplicationService.listRoles()).thenReturn(Flux.empty());
        webTestClient
                .get()
                .uri("/auth/v1/roles")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isArray()
                .jsonPath("$.data.length()")
                .isEqualTo(0);
    }

    @Test
    void testUpdateInvalidPathIdDoesNotCallService() {
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .put()
                .uri("/auth/v1/roles/invalid")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(testRoleDto)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.data")
                .doesNotExist();
        org.mockito.Mockito.verifyNoInteractions(roleApplicationService);
    }

    @Test
    void testCreateMalformedJsonDoesNotCallService() {
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/auth/v1/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.data")
                .doesNotExist();
        org.mockito.Mockito.verifyNoInteractions(roleApplicationService);
    }

    @Test
    void testAssignEmptyPermissionsPassesRoleAndEmptySet() {
        when(roleApplicationService.assignPermissions(1L, java.util.Set.of())).thenReturn(Mono.empty());
        AssignPermissionsRequest request = AssignPermissionsRequest.builder()
                .roleId(1L)
                .permissionIds(java.util.Set.of())
                .build();
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/auth/v1/roles/assign-permissions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
        org.mockito.Mockito.verify(roleApplicationService).assignPermissions(1L, java.util.Set.of());
    }
}
