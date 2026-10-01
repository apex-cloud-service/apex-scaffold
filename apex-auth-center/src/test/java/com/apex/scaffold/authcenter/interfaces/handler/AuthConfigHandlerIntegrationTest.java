package com.apex.scaffold.authcenter.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.apex.scaffold.authcenter.application.dto.CreateAuthConfigRequest;
import com.apex.scaffold.authcenter.application.dto.UpdateAuthConfigRequest;
import com.apex.scaffold.authcenter.application.service.AuthConfigService;
import com.apex.scaffold.authcenter.domain.model.entity.AuthConfigEntity;
import com.apex.scaffold.authcenter.interfaces.router.AuthConfigRouter;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AuthConfigHandler 集成测试
 *
 * @author Apex Scaffold Team
 */
@WebFluxTest(
        controllers = AuthConfigHandler.class,
        excludeAutoConfiguration = {
            org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration.class,
            org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration.class
        })
@AutoConfigureWebTestClient
@Import({AuthConfigRouter.class, com.apex.scaffold.authcenter.application.assembler.AuthConfigAssemblerImpl.class})
@DisplayName("认证配置Handler集成测试")
class AuthConfigHandlerIntegrationTest {

    /**
     * {@code WebTestClient} 在 {@code @WebFluxTest} 中无法通过构造器解析，
     * 故采用 JSR-250 {@code @Resource} 字段注入。
     */
    @Resource
    private WebTestClient webTestClient;

    @MockBean
    private AuthConfigService authConfigService;

    private AuthConfigEntity testConfig;
    private CreateAuthConfigRequest createRequest;
    private UpdateAuthConfigRequest updateRequest;

    @Test
    void shouldExposeAllOpenApiOperations(org.springframework.context.ApplicationContext context) {
        org.springdoc.core.annotations.RouterOperations operations =
                context.findAnnotationOnBean("authConfigRoutes", org.springdoc.core.annotations.RouterOperations.class);
        assertThat(operations).isNotNull();
        assertThat(operations.value()).hasSize(7);
    }

    @BeforeEach
    void setUp() {
        testConfig = AuthConfigEntity.builder()
                .id(1L)
                .tenantId("test-tenant")
                .configType("PASSWORD_POLICY")
                .configKey("default")
                .configValue("{\"minLength\": 8}")
                .description("测试密码策略")
                .enabled(true)
                .isDefault(false)
                .version(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(1L)
                .updatedBy(1L)
                .build();

        createRequest = CreateAuthConfigRequest.builder()
                .tenantId("test-tenant")
                .configType("PASSWORD_POLICY")
                .configKey("default")
                .configValue("{\"minLength\": 8}")
                .description("测试密码策略")
                .build();

        updateRequest = UpdateAuthConfigRequest.builder()
                .configValue("{\"minLength\": 10}")
                .description("更新后的密码策略")
                .build();
    }

    @Test
    @DisplayName("创建认证配置")
    void testCreateConfig() {
        when(authConfigService.createConfig(any(AuthConfigEntity.class))).thenReturn(Mono.just(testConfig));

        webTestClient
                .post()
                .uri("/api/v1/auth/configs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isEqualTo(0);
                    assertThat(result.getData()).isNotNull();
                });
    }

    @Test
    @DisplayName("更新认证配置")
    void testUpdateConfig() {
        AuthConfigEntity updatedConfig = AuthConfigEntity.builder()
                .id(1L)
                .tenantId("test-tenant")
                .configType("PASSWORD_POLICY")
                .configKey("default")
                .configValue("{\"minLength\": 10}")
                .description("更新后的密码策略")
                .enabled(true)
                .isDefault(false)
                .version(2)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(1L)
                .updatedBy(1L)
                .build();

        when(authConfigService.updateConfig(eq(1L), any(AuthConfigEntity.class), anyString()))
                .thenReturn(Mono.just(updatedConfig));

        webTestClient
                .put()
                .uri("/api/v1/auth/configs/1?tenantId=test-tenant")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isEqualTo(0);
                    assertThat(result.getData()).isNotNull();
                });
    }

    @Test
    @DisplayName("删除认证配置")
    void testDeleteConfig() {
        when(authConfigService.deleteConfig(eq(1L), anyString())).thenReturn(Mono.empty());

        webTestClient
                .delete()
                .uri("/api/v1/auth/configs/1?tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isEqualTo(0);
                });
    }

    @Test
    @DisplayName("获取认证配置详情")
    void testGetConfig() {
        when(authConfigService.getConfigById(eq(1L), anyString())).thenReturn(Mono.just(testConfig));

        webTestClient
                .get()
                .uri("/api/v1/auth/configs/1?tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isEqualTo(0);
                    assertThat(result.getData()).isNotNull();
                });
    }

    @Test
    @DisplayName("获取认证配置列表")
    void testListConfigs() {
        when(authConfigService.listAllConfigs(anyString())).thenReturn(Flux.just(testConfig));

        webTestClient
                .get()
                .uri("/api/v1/auth/configs?tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isArray();
    }

    @Test
    @DisplayName("根据类型获取配置")
    void testGetConfigsByType() {
        when(authConfigService.listConfigsByType(anyString(), anyString())).thenReturn(Flux.just(testConfig));

        webTestClient
                .get()
                .uri("/api/v1/auth/configs/type/PASSWORD_POLICY?tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isArray();
    }

    @Test
    @DisplayName("获取有效配置")
    void testGetEffectiveConfig() {
        when(authConfigService.getConfigByTypeAndKey(anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(testConfig));

        webTestClient
                .get()
                .uri("/api/v1/auth/configs/effective?configType=PASSWORD_POLICY&configKey=default&tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.id")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("服务拒绝配置时返回错误码")
    void testCreateConfigRejectedByService() {
        CreateAuthConfigRequest invalidRequest =
                CreateAuthConfigRequest.builder().configType("PASSWORD_POLICY").build();

        when(authConfigService.createConfig(any(AuthConfigEntity.class)))
                .thenReturn(
                        Mono.error(new com.apex.scaffold.common.exception.BusinessException(ResultCode.PARAM_ERROR)));

        webTestClient
                .post()
                .uri("/api/v1/auth/configs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isEqualTo(ResultCode.ERROR.getCode());
                });
    }

    @Test
    @DisplayName("获取不存在的配置")
    void testGetNonExistentConfig() {
        when(authConfigService.getConfigById(eq(999L), anyString())).thenReturn(Mono.empty());

        webTestClient
                .get()
                .uri("/api/v1/auth/configs/999?tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Result.class)
                .value(result -> {
                    assertThat(result.getCode()).isNotEqualTo(200);
                });
    }

    @Test
    void testCreateConfigServiceError() {
        when(authConfigService.createConfig(any(AuthConfigEntity.class)))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .post()
                .uri("/api/v1/auth/configs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRequest)
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
    void testUpdateConfigServiceError() {
        AuthConfigEntity updatedConfig = AuthConfigEntity.builder()
                .id(1L)
                .tenantId("test-tenant")
                .configType("PASSWORD_POLICY")
                .configKey("default")
                .configValue("{\"minLength\": 10}")
                .description("更新后的密码策略")
                .enabled(true)
                .isDefault(false)
                .version(2)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(1L)
                .updatedBy(1L)
                .build();

        when(authConfigService.updateConfig(eq(1L), any(AuthConfigEntity.class), anyString()))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .put()
                .uri("/api/v1/auth/configs/1?tenantId=test-tenant")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateRequest)
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
    void testDeleteConfigServiceError() {
        when(authConfigService.deleteConfig(eq(1L), anyString()))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .delete()
                .uri("/api/v1/auth/configs/1?tenantId=test-tenant")
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
    void testGetConfigServiceError() {
        when(authConfigService.getConfigById(eq(1L), anyString()))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .get()
                .uri("/api/v1/auth/configs/1?tenantId=test-tenant")
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
    void testListConfigsServiceError() {
        when(authConfigService.listAllConfigs(anyString()))
                .thenReturn(Flux.error(new IllegalStateException("service unavailable")));

        webTestClient
                .get()
                .uri("/api/v1/auth/configs?tenantId=test-tenant")
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
    void testGetConfigsByTypeServiceError() {
        when(authConfigService.listConfigsByType(anyString(), anyString()))
                .thenReturn(Flux.error(new IllegalStateException("service unavailable")));

        webTestClient
                .get()
                .uri("/api/v1/auth/configs/type/PASSWORD_POLICY?tenantId=test-tenant")
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
    void testGetEffectiveConfigServiceError() {
        when(authConfigService.getConfigByTypeAndKey(anyString(), anyString(), anyString()))
                .thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("service unavailable")));

        webTestClient
                .get()
                .uri("/api/v1/auth/configs/effective?configType=PASSWORD_POLICY&configKey=default&tenantId=test-tenant")
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
    void testEffectiveConfigNotFound() {
        when(authConfigService.getConfigByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant"))
                .thenReturn(Mono.empty());
        webTestClient
                .get()
                .uri("/api/v1/auth/configs/effective?configType=PASSWORD_POLICY&configKey=default&tenantId=test-tenant")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.NOT_FOUND.getCode())
                .jsonPath("$.data")
                .doesNotExist();
        org.mockito.Mockito.verify(authConfigService)
                .getConfigByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant");
    }

    @Test
    void testCreateConfigInitializesServerFields() {
        when(authConfigService.createConfig(any(AuthConfigEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, AuthConfigEntity.class)));
        webTestClient
                .post()
                .uri("/api/v1/auth/configs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.tenantId")
                .isEqualTo("test-tenant")
                .jsonPath("$.data.configType")
                .isEqualTo("PASSWORD_POLICY")
                .jsonPath("$.data.configKey")
                .isEqualTo("default")
                .jsonPath("$.data.isDefault")
                .isEqualTo(false)
                .jsonPath("$.data.version")
                .isEqualTo(1)
                .jsonPath("$.data.createdAt")
                .isNotEmpty();
        org.mockito.Mockito.verify(authConfigService)
                .createConfig(argThat(entity -> Boolean.FALSE.equals(entity.getIsDefault())
                        && Integer.valueOf(1).equals(entity.getVersion())
                        && entity.getCreatedAt() != null
                        && createRequest.getConfigValue().equals(entity.getConfigValue())));
    }

    @Test
    void testUpdateConfigPassesPathTenantAndMutableFields() {
        updateRequest.setEnabled(false);
        when(authConfigService.updateConfig(eq(7L), any(AuthConfigEntity.class), eq("tenant-b")))
                .thenReturn(Mono.just(testConfig));
        webTestClient
                .put()
                .uri("/api/v1/auth/configs/7?tenantId=tenant-b")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
        org.mockito.Mockito.verify(authConfigService)
                .updateConfig(
                        eq(7L),
                        argThat(entity -> updateRequest.getConfigValue().equals(entity.getConfigValue())
                                && updateRequest.getDescription().equals(entity.getDescription())
                                && Boolean.FALSE.equals(entity.getEnabled())),
                        eq("tenant-b"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "/type/PASSWORD_POLICY"})
    void testConfigListsEmpty(String path) {
        when(authConfigService.listAllConfigs("test-tenant")).thenReturn(Flux.empty());
        when(authConfigService.listConfigsByType("PASSWORD_POLICY", "test-tenant"))
                .thenReturn(Flux.empty());
        webTestClient
                .get()
                .uri("/api/v1/auth/configs" + path + "?tenantId=test-tenant")
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {
                "",
                "/1",
                "/type/PASSWORD_POLICY",
                "/effective?configKey=default&tenantId=test-tenant",
                "/effective?configType=PASSWORD_POLICY&tenantId=test-tenant",
                "/effective?configType=PASSWORD_POLICY&configKey=default"
            })
    void testMissingQueryParameterCurrentlyReturnsServerError(String path) {
        webTestClient
                .get()
                .uri("/api/v1/auth/configs" + path)
                .exchange()
                .expectStatus()
                .isEqualTo(500);
        org.mockito.Mockito.verifyNoInteractions(authConfigService);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"PUT", "DELETE"})
    void testMutationMissingTenantCurrentlyReturnsServerError(String method) {
        webTestClient
                .method(org.springframework.http.HttpMethod.valueOf(method))
                .uri("/api/v1/auth/configs/1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateRequest)
                .exchange()
                .expectStatus()
                .isEqualTo(500);
        org.mockito.Mockito.verifyNoInteractions(authConfigService);
    }
}
