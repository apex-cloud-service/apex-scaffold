package com.apex.scaffold.auth.application.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.auth.domain.model.entity.AuthConfigEntity;
import com.apex.scaffold.auth.domain.repository.AuthConfigRepository;
import java.time.LocalDateTime;
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
 * 认证配置服务单元测试
 *
 * @author Apex Scaffold Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("认证配置服务单元测试")
class AuthConfigServiceImplTest {

    @Mock
    private AuthConfigRepository authConfigRepository;

    @InjectMocks
    private AuthConfigServiceImpl authConfigService;

    private AuthConfigEntity mockConfig;

    @BeforeEach
    void setUp() {
        mockConfig = AuthConfigEntity.builder()
                .id(1L)
                .tenantId("test-tenant")
                .configType("PASSWORD_POLICY")
                .configKey("default")
                .configValue("{\"minLength\":8}")
                .description("Test password policy")
                .enabled(true)
                .isDefault(false)
                .version(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("createConfig() 方法测试")
    class CreateConfigTest {

        @Test
        @DisplayName("配置键不存在时创建成功")
        void testCreateConfigSuccess() {
            AuthConfigEntity newConfig = AuthConfigEntity.builder()
                    .configType("SESSION_POLICY")
                    .configKey("default")
                    .configValue("{\"timeout\":3600}")
                    .tenantId("test-tenant")
                    .build();

            when(authConfigRepository.existsByTypeAndKey("SESSION_POLICY", "default", "test-tenant"))
                    .thenReturn(Mono.just(false));
            when(authConfigRepository.save(any(AuthConfigEntity.class))).thenAnswer(invocation -> {
                AuthConfigEntity config = invocation.getArgument(0);
                config.setId(2L);
                return Mono.just(config);
            });

            Mono<AuthConfigEntity> result = authConfigService.createConfig(newConfig);

            StepVerifier.create(result)
                    .assertNext(config -> {
                        assertThat(config.getId()).isEqualTo(2L);
                        assertThat(config.getConfigType()).isEqualTo("SESSION_POLICY");
                        assertThat(config.getConfigKey()).isEqualTo("default");
                        assertThat(config.getCreatedAt()).isNotNull();
                        assertThat(config.getUpdatedAt()).isNotNull();
                        assertThat(config.getVersion()).isEqualTo(1);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("配置键已存在时创建失败")
        void testCreateConfigAlreadyExists() {
            AuthConfigEntity newConfig = AuthConfigEntity.builder()
                    .configType("PASSWORD_POLICY")
                    .configKey("default")
                    .tenantId("test-tenant")
                    .build();

            when(authConfigRepository.existsByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant"))
                    .thenReturn(Mono.just(true));

            Mono<AuthConfigEntity> result = authConfigService.createConfig(newConfig);

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                            && ex.getMessage().contains("Config already exists"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("updateConfig() 方法测试")
    class UpdateConfigTest {

        @Test
        @DisplayName("更新配置成功")
        void testUpdateConfigSuccess() {
            AuthConfigEntity updateData = AuthConfigEntity.builder()
                    .configValue("{\"minLength\":10}")
                    .description("Updated policy")
                    .enabled(false)
                    .build();

            when(authConfigRepository.findById(1L, "test-tenant")).thenReturn(Mono.just(mockConfig));
            when(authConfigRepository.update(any(AuthConfigEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            Mono<AuthConfigEntity> result = authConfigService.updateConfig(1L, updateData, "test-tenant");

            StepVerifier.create(result)
                    .assertNext(config -> {
                        assertThat(config.getId()).isEqualTo(1L);
                        assertThat(config.getConfigValue()).isEqualTo("{\"minLength\":10}");
                        assertThat(config.getDescription()).isEqualTo("Updated policy");
                        assertThat(config.getEnabled()).isFalse();
                        assertThat(config.getVersion()).isEqualTo(2);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("更新不存在的配置时失败")
        void testUpdateConfigNotFound() {
            AuthConfigEntity updateData =
                    AuthConfigEntity.builder().configValue("{\"minLength\":10}").build();

            when(authConfigRepository.findById(999L, "test-tenant")).thenReturn(Mono.empty());

            Mono<AuthConfigEntity> result = authConfigService.updateConfig(999L, updateData, "test-tenant");

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                            && ex.getMessage().contains("Config not found"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("deleteConfig() 方法测试")
    class DeleteConfigTest {

        @Test
        @DisplayName("删除非默认配置成功")
        void testDeleteConfigSuccess() {
            when(authConfigRepository.findById(1L, "test-tenant")).thenReturn(Mono.just(mockConfig));
            when(authConfigRepository.deleteById(1L, "test-tenant")).thenReturn(Mono.empty());

            Mono<Void> result = authConfigService.deleteConfig(1L, "test-tenant");

            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("删除默认配置时失败")
        void testDeleteDefaultConfigFails() {
            AuthConfigEntity defaultConfig =
                    AuthConfigEntity.builder().id(1L).isDefault(true).build();

            when(authConfigRepository.findById(1L, "test-tenant")).thenReturn(Mono.just(defaultConfig));

            Mono<Void> result = authConfigService.deleteConfig(1L, "test-tenant");

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                            && ex.getMessage().contains("Cannot delete default config"))
                    .verify();
        }

        @Test
        @DisplayName("删除不存在的配置时失败")
        void testDeleteConfigNotFound() {
            when(authConfigRepository.findById(999L, "test-tenant")).thenReturn(Mono.empty());

            Mono<Void> result = authConfigService.deleteConfig(999L, "test-tenant");

            StepVerifier.create(result)
                    .expectErrorMatches(ex -> ex instanceof IllegalArgumentException
                            && ex.getMessage().contains("Config not found"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("getConfigById() 方法测试")
    class GetConfigByIdTest {

        @Test
        @DisplayName("根据ID获取配置成功")
        void testGetConfigByIdSuccess() {
            when(authConfigRepository.findById(1L, "test-tenant")).thenReturn(Mono.just(mockConfig));

            Mono<AuthConfigEntity> result = authConfigService.getConfigById(1L, "test-tenant");

            StepVerifier.create(result)
                    .assertNext(config -> {
                        assertThat(config.getId()).isEqualTo(1L);
                        assertThat(config.getConfigType()).isEqualTo("PASSWORD_POLICY");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getConfigByTypeAndKey() 方法测试")
    class GetConfigByTypeAndKeyTest {

        @Test
        @DisplayName("根据类型和键获取配置成功")
        void testGetConfigByTypeAndKeySuccess() {
            when(authConfigRepository.findByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant"))
                    .thenReturn(Mono.just(mockConfig));

            Mono<AuthConfigEntity> result =
                    authConfigService.getConfigByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant");

            StepVerifier.create(result)
                    .assertNext(config -> {
                        assertThat(config.getConfigType()).isEqualTo("PASSWORD_POLICY");
                        assertThat(config.getConfigKey()).isEqualTo("default");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("配置不存在时返回空")
        void testGetConfigByTypeAndKeyNotFound() {
            when(authConfigRepository.findByTypeAndKey("NON_EXISTENT", "default", "test-tenant"))
                    .thenReturn(Mono.empty());

            Mono<AuthConfigEntity> result =
                    authConfigService.getConfigByTypeAndKey("NON_EXISTENT", "default", "test-tenant");

            StepVerifier.create(result).verifyComplete();
        }
    }

    @Nested
    @DisplayName("listConfigsByType() 方法测试")
    class ListConfigsByTypeTest {

        @Test
        @DisplayName("根据类型列出配置成功")
        void testListConfigsByTypeSuccess() {
            AuthConfigEntity config2 = AuthConfigEntity.builder()
                    .id(2L)
                    .configType("PASSWORD_POLICY")
                    .configKey("custom")
                    .build();

            when(authConfigRepository.findByType("PASSWORD_POLICY", "test-tenant"))
                    .thenReturn(Flux.just(mockConfig, config2));

            Flux<AuthConfigEntity> result = authConfigService.listConfigsByType("PASSWORD_POLICY", "test-tenant");

            StepVerifier.create(result)
                    .expectNext(mockConfig)
                    .expectNext(config2)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("listAllConfigs() 方法测试")
    class ListAllConfigsTest {

        @Test
        @DisplayName("列出所有配置成功")
        void testListAllConfigsSuccess() {
            when(authConfigRepository.findAll("test-tenant")).thenReturn(Flux.just(mockConfig));

            Flux<AuthConfigEntity> result = authConfigService.listAllConfigs("test-tenant");

            StepVerifier.create(result).expectNext(mockConfig).verifyComplete();
        }
    }

    @Nested
    @DisplayName("listEnabledConfigs() 方法测试")
    class ListEnabledConfigsTest {

        @Test
        @DisplayName("列出已启用配置成功")
        void testListEnabledConfigsSuccess() {
            when(authConfigRepository.findAllEnabled("test-tenant")).thenReturn(Flux.just(mockConfig));

            Flux<AuthConfigEntity> result = authConfigService.listEnabledConfigs("test-tenant");

            StepVerifier.create(result).expectNext(mockConfig).verifyComplete();
        }
    }

    @Nested
    @DisplayName("enableConfig() 方法测试")
    class EnableConfigTest {

        @Test
        @DisplayName("启用配置成功")
        void testEnableConfigSuccess() {
            mockConfig.setEnabled(false);

            when(authConfigRepository.findById(1L, "test-tenant")).thenReturn(Mono.just(mockConfig));
            when(authConfigRepository.update(any(AuthConfigEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            Mono<AuthConfigEntity> result = authConfigService.enableConfig(1L, "test-tenant");

            StepVerifier.create(result)
                    .assertNext(config -> {
                        assertThat(config.getEnabled()).isTrue();
                        assertThat(config.getVersion()).isEqualTo(2);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("disableConfig() 方法测试")
    class DisableConfigTest {

        @Test
        @DisplayName("禁用配置成功")
        void testDisableConfigSuccess() {
            when(authConfigRepository.findById(1L, "test-tenant")).thenReturn(Mono.just(mockConfig));
            when(authConfigRepository.update(any(AuthConfigEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            Mono<AuthConfigEntity> result = authConfigService.disableConfig(1L, "test-tenant");

            StepVerifier.create(result)
                    .assertNext(config -> {
                        assertThat(config.getEnabled()).isFalse();
                        assertThat(config.getVersion()).isEqualTo(2);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getPasswordPolicy() 方法测试")
    class GetPasswordPolicyTest {

        @Test
        @DisplayName("获取自定义密码策略成功")
        void testGetPasswordPolicyCustom() {
            when(authConfigRepository.findByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant"))
                    .thenReturn(Mono.just(mockConfig));

            Mono<AuthConfigEntity> result = authConfigService.getPasswordPolicy("test-tenant");

            StepVerifier.create(result)
                    .assertNext(config -> {
                        assertThat(config.getConfigType()).isEqualTo("PASSWORD_POLICY");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("无自定义策略时返回默认密码策略")
        void testGetPasswordPolicyDefault() {
            when(authConfigRepository.findByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant"))
                    .thenReturn(Mono.empty());

            Mono<AuthConfigEntity> result = authConfigService.getPasswordPolicy("test-tenant");

            StepVerifier.create(result)
                    .assertNext(config -> {
                        assertThat(config.getConfigType()).isEqualTo("PASSWORD_POLICY");
                        assertThat(config.isDefaultConfig()).isTrue();
                        assertThat(config.getConfigValue()).contains("minLength");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getSessionPolicy() 方法测试")
    class GetSessionPolicyTest {

        @Test
        @DisplayName("无自定义策略时返回默认会话策略")
        void testGetSessionPolicyDefault() {
            when(authConfigRepository.findByTypeAndKey("SESSION_POLICY", "default", "test-tenant"))
                    .thenReturn(Mono.empty());

            Mono<AuthConfigEntity> result = authConfigService.getSessionPolicy("test-tenant");

            StepVerifier.create(result)
                    .assertNext(config -> {
                        assertThat(config.getConfigType()).isEqualTo("SESSION_POLICY");
                        assertThat(config.isDefaultConfig()).isTrue();
                        assertThat(config.getConfigValue()).contains("timeout");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getSecurityPolicy() 方法测试")
    class GetSecurityPolicyTest {

        @Test
        @DisplayName("无自定义策略时返回默认安全策略")
        void testGetSecurityPolicyDefault() {
            when(authConfigRepository.findByTypeAndKey("SECURITY_POLICY", "default", "test-tenant"))
                    .thenReturn(Mono.empty());

            Mono<AuthConfigEntity> result = authConfigService.getSecurityPolicy("test-tenant");

            StepVerifier.create(result)
                    .assertNext(config -> {
                        assertThat(config.getConfigType()).isEqualTo("SECURITY_POLICY");
                        assertThat(config.isDefaultConfig()).isTrue();
                        assertThat(config.getConfigValue()).contains("mfaEnabled");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getOauth2ClientConfig() 方法测试")
    class GetOAuth2ClientConfigTest {

        @Test
        @DisplayName("获取OAuth2客户端配置成功")
        void testGetOAuth2ClientConfigSuccess() {
            AuthConfigEntity oauth2Config = AuthConfigEntity.builder()
                    .id(2L)
                    .configType("OAUTH2_CLIENT")
                    .configKey("client-123")
                    .configValue("{\"clientId\":\"client-123\"}")
                    .build();

            when(authConfigRepository.findByTypeAndKey("OAUTH2_CLIENT", "client-123", "test-tenant"))
                    .thenReturn(Mono.just(oauth2Config));

            Mono<AuthConfigEntity> result = authConfigService.getOauth2ClientConfig("client-123", "test-tenant");

            StepVerifier.create(result)
                    .assertNext(config -> {
                        assertThat(config.getConfigType()).isEqualTo("OAUTH2_CLIENT");
                        assertThat(config.getConfigKey()).isEqualTo("client-123");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("batchUpdateConfigs() 方法测试")
    class BatchUpdateConfigsTest {

        @Test
        @DisplayName("批量更新配置成功")
        void testBatchUpdateConfigsSuccess() {
            AuthConfigEntity config1 = AuthConfigEntity.builder()
                    .id(1L)
                    .configValue("{\"updated\":true}")
                    .build();

            AuthConfigEntity config2 = AuthConfigEntity.builder()
                    .id(2L)
                    .configValue("{\"updated\":true}")
                    .build();

            when(authConfigRepository.findById(eq(1L), eq("test-tenant"))).thenReturn(Mono.just(mockConfig));
            when(authConfigRepository.findById(eq(2L), eq("test-tenant"))).thenReturn(Mono.just(mockConfig));
            when(authConfigRepository.update(any(AuthConfigEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            Flux<AuthConfigEntity> configs = Flux.just(config1, config2);
            Mono<Long> result = authConfigService.batchUpdateConfigs(configs, "test-tenant");

            StepVerifier.create(result).expectNext(2L).verifyComplete();
        }
    }

    @Nested
    @DisplayName("resetToDefault() 方法测试")
    class ResetToDefaultTest {

        @Test
        @DisplayName("重置为默认密码策略成功")
        void testResetToDefaultPasswordPolicy() {
            when(authConfigRepository.findByType("PASSWORD_POLICY", "test-tenant"))
                    .thenReturn(Flux.just(mockConfig));
            when(authConfigRepository.deleteById(1L, "test-tenant")).thenReturn(Mono.empty());

            Mono<AuthConfigEntity> result = authConfigService.resetToDefault("PASSWORD_POLICY", "test-tenant");

            StepVerifier.create(result)
                    .assertNext(config -> {
                        assertThat(config.getConfigType()).isEqualTo("PASSWORD_POLICY");
                        assertThat(config.isDefaultConfig()).isTrue();
                    })
                    .verifyComplete();
        }
    }

    @Test
    void cacheIsReusedAndIsolatedByTenant() {
        AuthConfigEntity other = AuthConfigEntity.builder()
                .tenantId("other")
                .configValue("other")
                .build();
        when(authConfigRepository.findByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant"))
                .thenReturn(Mono.just(mockConfig));
        when(authConfigRepository.findByTypeAndKey("PASSWORD_POLICY", "default", "other"))
                .thenReturn(Mono.just(other));
        StepVerifier.create(authConfigService.getPasswordPolicy("test-tenant"))
                .expectNext(mockConfig)
                .verifyComplete();
        StepVerifier.create(authConfigService.getPasswordPolicy("other"))
                .expectNext(other)
                .verifyComplete();
        StepVerifier.create(authConfigService.getPasswordPolicy("test-tenant"))
                .expectNext(mockConfig)
                .verifyComplete();
        org.mockito.Mockito.verify(authConfigRepository).findByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant");
        org.mockito.Mockito.verify(authConfigRepository).findByTypeAndKey("PASSWORD_POLICY", "default", "other");
    }

    @Test
    void missingConfigIsNotCached() {
        when(authConfigRepository.findByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant"))
                .thenReturn(Mono.empty(), Mono.just(mockConfig));
        StepVerifier.create(authConfigService.getConfigByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant"))
                .verifyComplete();
        StepVerifier.create(authConfigService.getPasswordPolicy("test-tenant"))
                .expectNext(mockConfig)
                .verifyComplete();
        org.mockito.Mockito.verify(authConfigRepository, org.mockito.Mockito.times(2))
                .findByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant");
    }

    @Test
    void nullUpdateFieldsPreserveStoredValuesAndInvalidateCache() {
        AuthConfigEntity fresh = AuthConfigEntity.builder().configValue("fresh").build();
        when(authConfigRepository.findByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant"))
                .thenReturn(Mono.just(mockConfig), Mono.just(fresh));
        StepVerifier.create(authConfigService.getPasswordPolicy("test-tenant"))
                .expectNext(mockConfig)
                .verifyComplete();
        when(authConfigRepository.findById(1L, "test-tenant")).thenReturn(Mono.just(mockConfig));
        when(authConfigRepository.update(mockConfig)).thenReturn(Mono.just(mockConfig));
        StepVerifier.create(authConfigService.updateConfig(
                        1L, AuthConfigEntity.builder().build(), "test-tenant"))
                .assertNext(config -> {
                    assertThat(config.getConfigValue()).isEqualTo("{\"minLength\":8}");
                    assertThat(config.getDescription()).isEqualTo("Test password policy");
                    assertThat(config.getEnabled()).isTrue();
                    assertThat(config.getVersion()).isEqualTo(2);
                    assertThat(config.getTenantId()).isEqualTo("test-tenant");
                })
                .verifyComplete();
        StepVerifier.create(authConfigService.getPasswordPolicy("test-tenant"))
                .expectNext(fresh)
                .verifyComplete();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void missingConfigCannotChangeEnabledState(boolean enabled) {
        when(authConfigRepository.findById(99L, "test-tenant")).thenReturn(Mono.empty());
        StepVerifier.create(
                        enabled
                                ? authConfigService.enableConfig(99L, "test-tenant")
                                : authConfigService.disableConfig(99L, "test-tenant"))
                .expectError(IllegalArgumentException.class)
                .verify();
        org.mockito.Mockito.verify(authConfigRepository, org.mockito.Mockito.never())
                .update(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"SESSION_POLICY", "SECURITY_POLICY", "UNKNOWN"})
    void resetHandlesEachDefaultType(String type) {
        when(authConfigRepository.findByType(type, "test-tenant")).thenReturn(Flux.empty());
        StepVerifier.create(authConfigService
                        .resetToDefault(type, "test-tenant")
                        .flux()
                        .collectList())
                .assertNext(configs -> {
                    if ("UNKNOWN".equals(type)) {
                        assertThat(configs).isEmpty();
                    } else {
                        assertThat(configs).singleElement().satisfies(config -> {
                            assertThat(config.getConfigType()).isEqualTo(type);
                            assertThat(config.isDefaultConfig()).isTrue();
                        });
                    }
                })
                .verifyComplete();
    }

    @Test
    void failedResetDoesNotReturnDefault() {
        IllegalStateException failure = new IllegalStateException("delete failed");
        when(authConfigRepository.findByType("PASSWORD_POLICY", "test-tenant")).thenReturn(Flux.just(mockConfig));
        when(authConfigRepository.deleteById(1L, "test-tenant")).thenReturn(Mono.error(failure));
        StepVerifier.create(authConfigService.resetToDefault("PASSWORD_POLICY", "test-tenant"))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();
    }

    @Test
    void emptyBatchDoesNotAccessRepository() {
        StepVerifier.create(authConfigService.batchUpdateConfigs(Flux.empty(), "test-tenant"))
                .expectNext(0L)
                .verifyComplete();
        org.mockito.Mockito.verifyNoInteractions(authConfigRepository);
    }

    @Test
    void policyRepositoryErrorDoesNotFallBackToDefault() {
        IllegalStateException failure = new IllegalStateException("read failed");
        when(authConfigRepository.findByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant"))
                .thenReturn(Mono.error(failure));
        StepVerifier.create(authConfigService.getPasswordPolicy("test-tenant"))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();
    }

    @Test
    void failedExistenceCheckPreventsConfigSave() {
        IllegalStateException failure = new IllegalStateException("lookup failed");
        when(authConfigRepository.existsByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant"))
                .thenReturn(Mono.error(failure));

        StepVerifier.create(authConfigService.createConfig(mockConfig))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();

        verify(authConfigRepository, never()).save(any());
    }

    @Test
    void failedConfigSaveIsPropagated() {
        IllegalStateException failure = new IllegalStateException("save failed");
        when(authConfigRepository.existsByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant"))
                .thenReturn(Mono.just(false));
        when(authConfigRepository.save(mockConfig)).thenReturn(Mono.error(failure));

        StepVerifier.create(authConfigService.createConfig(mockConfig))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();
    }

    @Test
    void configReadFailureIsNotCached() {
        IllegalStateException failure = new IllegalStateException("read failed");
        when(authConfigRepository.findByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant"))
                .thenReturn(Mono.error(failure), Mono.just(mockConfig));

        StepVerifier.create(authConfigService.getPasswordPolicy("test-tenant"))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();
        StepVerifier.create(authConfigService.getPasswordPolicy("test-tenant"))
                .expectNext(mockConfig)
                .verifyComplete();
        StepVerifier.create(authConfigService.getPasswordPolicy("test-tenant"))
                .expectNext(mockConfig)
                .verifyComplete();

        verify(authConfigRepository, times(2)).findByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void changingEnabledStateInvalidatesCachedConfig(boolean enabled) {
        AuthConfigEntity fresh = AuthConfigEntity.builder().enabled(enabled).build();
        mockConfig.setEnabled(!enabled);
        mockConfig.setVersion(null);
        when(authConfigRepository.findByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant"))
                .thenReturn(Mono.just(mockConfig), Mono.just(fresh));
        when(authConfigRepository.findById(1L, "test-tenant")).thenReturn(Mono.just(mockConfig));
        when(authConfigRepository.update(mockConfig)).thenReturn(Mono.just(mockConfig));
        StepVerifier.create(authConfigService.getPasswordPolicy("test-tenant"))
                .expectNext(mockConfig)
                .verifyComplete();

        StepVerifier.create(
                        enabled
                                ? authConfigService.enableConfig(1L, "test-tenant")
                                : authConfigService.disableConfig(1L, "test-tenant"))
                .assertNext(config -> {
                    assertThat(config.getEnabled()).isEqualTo(enabled);
                    assertThat(config.getVersion()).isEqualTo(1);
                })
                .verifyComplete();
        StepVerifier.create(authConfigService.getPasswordPolicy("test-tenant"))
                .expectNext(fresh)
                .verifyComplete();

        verify(authConfigRepository, times(2)).findByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant");
    }

    @Test
    void deletingDefaultConfigKeepsCacheAndDoesNotDelete() {
        mockConfig.setIsDefault(true);
        when(authConfigRepository.findByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant"))
                .thenReturn(Mono.just(mockConfig));
        when(authConfigRepository.findById(1L, "test-tenant")).thenReturn(Mono.just(mockConfig));
        StepVerifier.create(authConfigService.getPasswordPolicy("test-tenant"))
                .expectNext(mockConfig)
                .verifyComplete();

        StepVerifier.create(authConfigService.deleteConfig(1L, "test-tenant"))
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && error.getMessage().contains("Cannot delete default config"))
                .verify();
        StepVerifier.create(authConfigService.getPasswordPolicy("test-tenant"))
                .expectNext(mockConfig)
                .verifyComplete();

        verify(authConfigRepository, never()).deleteById(any(), any());
        verify(authConfigRepository).findByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant");
    }

    @Test
    void deletedConfigIsReloadedInsteadOfReturnedFromCache() {
        when(authConfigRepository.findByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant"))
                .thenReturn(Mono.just(mockConfig), Mono.empty());
        when(authConfigRepository.findById(1L, "test-tenant")).thenReturn(Mono.just(mockConfig));
        when(authConfigRepository.deleteById(1L, "test-tenant")).thenReturn(Mono.empty());
        StepVerifier.create(authConfigService.getPasswordPolicy("test-tenant"))
                .expectNext(mockConfig)
                .verifyComplete();

        StepVerifier.create(authConfigService.deleteConfig(1L, "test-tenant")).verifyComplete();
        StepVerifier.create(authConfigService.getConfigByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant"))
                .verifyComplete();

        verify(authConfigRepository, times(2)).findByTypeAndKey("PASSWORD_POLICY", "default", "test-tenant");
    }

    @Test
    void updateIgnoresIdentityFieldsAndAcceptsEmptyValues() {
        AuthConfigEntity patch = AuthConfigEntity.builder()
                .id(99L)
                .tenantId("other-tenant")
                .configType("OAUTH2_CLIENT")
                .configKey("other")
                .configValue("")
                .description("")
                .enabled(false)
                .version(99)
                .isDefault(true)
                .build();
        when(authConfigRepository.findById(1L, "test-tenant")).thenReturn(Mono.just(mockConfig));
        when(authConfigRepository.update(mockConfig)).thenReturn(Mono.just(mockConfig));

        StepVerifier.create(authConfigService.updateConfig(1L, patch, "test-tenant"))
                .assertNext(config -> {
                    assertThat(config.getId()).isEqualTo(1L);
                    assertThat(config.getTenantId()).isEqualTo("test-tenant");
                    assertThat(config.getConfigType()).isEqualTo("PASSWORD_POLICY");
                    assertThat(config.getConfigKey()).isEqualTo("default");
                    assertThat(config.getConfigValue()).isEmpty();
                    assertThat(config.getDescription()).isEmpty();
                    assertThat(config.getEnabled()).isFalse();
                    assertThat(config.isDefaultConfig()).isFalse();
                    assertThat(config.getVersion()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    void batchUpdateFailureDoesNotEmitPartialCount() {
        AuthConfigEntity missing = AuthConfigEntity.builder().id(99L).build();
        when(authConfigRepository.findById(1L, "test-tenant")).thenReturn(Mono.just(mockConfig));
        when(authConfigRepository.update(mockConfig)).thenReturn(Mono.just(mockConfig));
        when(authConfigRepository.findById(99L, "test-tenant")).thenReturn(Mono.empty());

        StepVerifier.create(authConfigService.batchUpdateConfigs(Flux.just(mockConfig, missing), "test-tenant"))
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && error.getMessage().equals("Config not found: 99"))
                .verify();

        verify(authConfigRepository).update(mockConfig);
        verify(authConfigRepository, never()).update(missing);
    }

    @Test
    void batchSourceFailureDoesNotAccessRepository() {
        IllegalStateException failure = new IllegalStateException("source failed");

        StepVerifier.create(authConfigService.batchUpdateConfigs(Flux.error(failure), "test-tenant"))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();

        verifyNoInteractions(authConfigRepository);
    }
}
