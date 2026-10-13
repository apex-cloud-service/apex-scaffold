package com.apex.scaffold.authservice.interfaces.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.apex.scaffold.auth.application.assembler.PermissionAssembler;
import com.apex.scaffold.auth.application.dto.PermissionDTO;
import com.apex.scaffold.auth.application.service.PermissionService;
import com.apex.scaffold.auth.domain.model.entity.PermissionEntity;
import com.apex.scaffold.authservice.AuthCenterSliceTestConfiguration;
import com.apex.scaffold.authservice.interfaces.router.PermissionRouter;
import com.apex.scaffold.common.result.ResultCode;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
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
 * 权限Handler集成测试
 *
 * <p>测试权限管理相关的HTTP接口，验证：
 * <ul>
 *   <li>创建权限：正常创建、参数校验</li>
 *   <li>更新权限：正常更新、不存在的权限</li>
 *   <li>删除权限：正常删除</li>
 *   <li>查询权限：根据ID查询、列出所有权限</li>
 *   <li>按资源查询：根据资源标识查询相关权限</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@WebFluxTest(controllers = PermissionHandler.class)
@Import({PermissionHandler.class, PermissionRouter.class, AuthCenterSliceTestConfiguration.class})
@DisplayName("权限Handler集成测试")
@WithMockUser
class PermissionHandlerIntegrationTest {

    /**
     * {@code WebTestClient} 在 {@code @WebFluxTest} 中无法通过构造器解析，
     * 故采用 JSR-250 {@code @Resource} 字段注入。
     */
    @Resource
    private WebTestClient webTestClient;

    @MockBean
    private PermissionService permissionApplicationService;

    @MockBean
    private PermissionAssembler permissionAssembler;

    private PermissionEntity testPermissionEntity;
    private PermissionDTO testPermissionDto;

    @BeforeEach
    void setUp() {
        testPermissionEntity = PermissionEntity.builder()
                .id(1L)
                .tenantId("default")
                .code("user:read")
                .name("查看用户")
                .resourceType("user")
                .resourceIdentifier("read")
                .description("查看用户信息权限")
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testPermissionDto = PermissionDTO.builder()
                .id(1L)
                .tenantId("default")
                .code("user:read")
                .name("查看用户")
                .resource("user")
                .action("read")
                .description("查看用户信息权限")
                .enabled(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("创建权限 - 成功")
    void testCreatePermission_Success() {
        when(permissionAssembler.toEntity(any(PermissionDTO.class))).thenReturn(testPermissionEntity);
        when(permissionApplicationService.createPermission(any(PermissionEntity.class)))
                .thenReturn(Mono.just(testPermissionEntity));
        when(permissionAssembler.toDto(any(PermissionEntity.class))).thenReturn(testPermissionDto);

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/auth/v1/permissions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(testPermissionDto)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.code")
                .isEqualTo("user:read")
                .jsonPath("$.data.name")
                .isEqualTo("查看用户");
    }

    @Test
    @DisplayName("更新权限 - 成功")
    void testUpdatePermission_Success() {
        when(permissionAssembler.toEntity(any(PermissionDTO.class))).thenReturn(testPermissionEntity);
        when(permissionApplicationService.updatePermission(any(PermissionEntity.class)))
                .thenReturn(Mono.just(testPermissionEntity));
        when(permissionAssembler.toDto(any(PermissionEntity.class))).thenReturn(testPermissionDto);

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .put()
                .uri("/auth/v1/permissions/1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(testPermissionDto)
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
    @DisplayName("删除权限 - 成功")
    void testDeletePermission_Success() {
        when(permissionApplicationService.deletePermission(anyLong())).thenReturn(Mono.empty());

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .delete()
                .uri("/auth/v1/permissions/1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("根据ID查询权限 - 成功")
    void testGetPermissionById_Success() {
        when(permissionApplicationService.getPermissionById(anyLong())).thenReturn(Mono.just(testPermissionEntity));
        when(permissionAssembler.toDto(any(PermissionEntity.class))).thenReturn(testPermissionDto);

        webTestClient
                .get()
                .uri("/auth/v1/permissions/1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.id")
                .isEqualTo(1)
                .jsonPath("$.data.name")
                .isEqualTo("查看用户");
    }

    @Test
    @DisplayName("列出所有权限 - 成功")
    void testListPermissions_Success() {
        when(permissionApplicationService.listPermissions()).thenReturn(Flux.just(testPermissionEntity));
        when(permissionAssembler.toDto(any(PermissionEntity.class))).thenReturn(testPermissionDto);

        webTestClient
                .get()
                .uri("/auth/v1/permissions")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isArray()
                .jsonPath("$.data[0].code")
                .isEqualTo("user:read");
    }

    @Test
    @DisplayName("按资源查询权限 - 成功")
    void testGetPermissionsByResource_Success() {
        PermissionEntity readPermission = PermissionEntity.builder()
                .id(1L)
                .code("user:read")
                .resourceType("user")
                .resourceIdentifier("read")
                .build();

        PermissionEntity writePermission = PermissionEntity.builder()
                .id(2L)
                .code("user:write")
                .resourceType("user")
                .resourceIdentifier("write")
                .build();

        when(permissionApplicationService.getPermissionsByResource(anyString()))
                .thenReturn(Flux.just(readPermission, writePermission));
        when(permissionAssembler.toDto(any(PermissionEntity.class))).thenReturn(testPermissionDto);

        webTestClient
                .get()
                .uri("/auth/v1/permissions/resource/user")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isArray()
                .jsonPath("$.data.length()")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("查询不存在的权限 - 返回空")
    void testGetPermissionById_NotFound() {
        when(permissionApplicationService.getPermissionById(anyLong())).thenReturn(Mono.empty());

        webTestClient
                .get()
                .uri("/auth/v1/permissions/999")
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
    @DisplayName("查询不存在的资源权限 - 返回空列表")
    void testGetPermissionsByResource_NotFound() {
        when(permissionApplicationService.getPermissionsByResource(anyString())).thenReturn(Flux.empty());

        webTestClient
                .get()
                .uri("/auth/v1/permissions/resource/nonexistent")
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
    void testCreatePermission_SuccessServiceError() {
        when(permissionAssembler.toEntity(any(PermissionDTO.class))).thenReturn(testPermissionEntity);
        when(permissionApplicationService.createPermission(any(PermissionEntity.class)))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));
        when(permissionAssembler.toDto(any(PermissionEntity.class))).thenReturn(testPermissionDto);

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/auth/v1/permissions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(testPermissionDto)
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
    void testUpdatePermission_SuccessServiceError() {
        when(permissionAssembler.toEntity(any(PermissionDTO.class))).thenReturn(testPermissionEntity);
        when(permissionApplicationService.updatePermission(any(PermissionEntity.class)))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));
        when(permissionAssembler.toDto(any(PermissionEntity.class))).thenReturn(testPermissionDto);

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .put()
                .uri("/auth/v1/permissions/1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(testPermissionDto)
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
    void testDeletePermission_SuccessServiceError() {
        when(permissionApplicationService.deletePermission(anyLong()))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .delete()
                .uri("/auth/v1/permissions/1")
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
    void testGetPermissionById_SuccessServiceError() {
        when(permissionApplicationService.getPermissionById(anyLong()))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));
        when(permissionAssembler.toDto(any(PermissionEntity.class))).thenReturn(testPermissionDto);

        webTestClient
                .get()
                .uri("/auth/v1/permissions/1")
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
    void testListPermissions_SuccessServiceError() {
        when(permissionApplicationService.listPermissions())
                .thenReturn(Flux.error(new IllegalStateException("service unavailable")));
        when(permissionAssembler.toDto(any(PermissionEntity.class))).thenReturn(testPermissionDto);

        webTestClient
                .get()
                .uri("/auth/v1/permissions")
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
    void testGetPermissionsByResource_SuccessServiceError() {
        PermissionEntity readPermission = PermissionEntity.builder()
                .id(1L)
                .code("user:read")
                .resourceType("user")
                .resourceIdentifier("read")
                .build();

        PermissionEntity writePermission = PermissionEntity.builder()
                .id(2L)
                .code("user:write")
                .resourceType("user")
                .resourceIdentifier("write")
                .build();

        when(permissionApplicationService.getPermissionsByResource(anyString()))
                .thenReturn(Flux.error(new IllegalStateException("service unavailable")));
        when(permissionAssembler.toDto(any(PermissionEntity.class))).thenReturn(testPermissionDto);

        webTestClient
                .get()
                .uri("/auth/v1/permissions/resource/user")
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
        testPermissionDto.setId(999L);
        when(permissionAssembler.toEntity(any(PermissionDTO.class))).thenReturn(testPermissionEntity);
        when(permissionApplicationService.updatePermission(any(PermissionEntity.class)))
                .thenReturn(Mono.just(testPermissionEntity));
        when(permissionAssembler.toDto(any(PermissionEntity.class))).thenReturn(testPermissionDto);
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .put()
                .uri("/auth/v1/permissions/1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(testPermissionDto)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
        org.mockito.Mockito.verify(permissionAssembler).toEntity(org.mockito.ArgumentMatchers.argThat(dto -> dto.getId()
                .equals(1L)));
    }

    @Test
    void testListPermissionsEmpty() {
        when(permissionApplicationService.listPermissions()).thenReturn(Flux.empty());
        webTestClient
                .get()
                .uri("/auth/v1/permissions")
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
                .uri("/auth/v1/permissions/invalid")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(testPermissionDto)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode())
                .jsonPath("$.data")
                .doesNotExist();
        org.mockito.Mockito.verifyNoInteractions(permissionApplicationService);
    }

    @Test
    void testCreateMalformedJsonDoesNotCallService() {
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/auth/v1/permissions")
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
        org.mockito.Mockito.verifyNoInteractions(permissionApplicationService);
    }
}
