package com.apex.scaffold.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.auth.domain.model.entity.AuthConfigEntity;
import com.apex.scaffold.auth.domain.repository.AuthConfigRepository;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.contract.dto.internal.OpenAppImportRequest;
import com.apex.scaffold.contract.dto.internal.OpenAppRegistrationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * auth 单源开放应用注册测试：主体绑定、停用写回、导入幂等与密钥不落明文。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("auth 开放应用注册")
class AuthOpenAppRegistryServiceTest {

    @Mock
    private AuthConfigRepository repository;

    private AuthOpenAppRegistryService service;

    @BeforeEach
    void setUp() {
        service = new AuthOpenAppRegistryService(
                repository,
                new ObjectMapper(),
                jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());
    }

    @Test
    @DisplayName("写入文件写 scope 但未绑定主体时拒绝")
    void rejectsWriteScopeWithoutSubject() {
        OpenAppRegistrationRequest request = OpenAppRegistrationRequest.builder()
                .clientId("partner-app")
                .appName("合作方")
                .scopes(Set.of("open:file:write"))
                .build();

        StepVerifier.create(service.create(request).contextWrite(TenantContext.withTenant("t-1")))
                .expectError(BusinessException.class)
                .verify();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("创建仅读应用时密钥以 bcrypt 落库且响应含一次性明文")
    void hashesSecretOnCreate() throws Exception {
        when(repository.findByTypeAndKey(eq("OAUTH2_CLIENT"), eq("partner-app"), eq("t-1")))
                .thenReturn(Mono.empty());
        when(repository.save(any(AuthConfigEntity.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));

        OpenAppRegistrationRequest request = OpenAppRegistrationRequest.builder()
                .clientId("partner-app")
                .appName("合作方")
                .scopes(Set.of("open:file:read"))
                .clientSecret("plain-secret")
                .build();

        StepVerifier.create(service.create(request).contextWrite(TenantContext.withTenant("t-1")))
                .assertNext(response -> {
                    assertThat(response.getClientSecret()).isEqualTo("plain-secret");
                    assertThat(response.getEnabled()).isTrue();
                })
                .verifyComplete();

        var captor = org.mockito.ArgumentCaptor.forClass(AuthConfigEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getConfigValue()).doesNotContain("plain-secret");
        assertThat(new BCryptPasswordEncoder()
                        .matches(
                                "plain-secret",
                                new ObjectMapper()
                                        .readTree(captor.getValue().getConfigValue())
                                        .path("clientSecret")
                                        .asText()))
                .isTrue();
    }

    @Test
    @DisplayName("停用写回 auth_config.enabled=false，签发查询不再命中")
    void statusWritesDisabledFlag() {
        AuthConfigEntity entity = new AuthConfigEntity();
        entity.setId(1L);
        entity.setTenantId("t-1");
        entity.setConfigKey("partner-app");
        entity.setConfigValue("{\"clientId\":\"partner-app\",\"scopes\":[\"open:file:read\"]}");
        entity.setEnabled(true);
        entity.setVersion(1);
        when(repository.findByTypeAndKey(any(), any(), any())).thenReturn(Mono.just(entity));
        when(repository.updateOpenApp(any(), any(Integer.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(service.status("partner-app", false).contextWrite(TenantContext.withTenant("t-1")))
                .assertNext(response -> assertThat(response.getEnabled()).isFalse())
                .verifyComplete();

        var captor = org.mockito.ArgumentCaptor.forClass(AuthConfigEntity.class);
        verify(repository).updateOpenApp(captor.capture(), any(Integer.class));
        assertThat(captor.getValue().isEnabled()).isFalse();
        assertThat(captor.getValue().getConfigValue()).contains("\"enabled\":false");
    }

    @Test
    @DisplayName("导入明文密钥与错误哈希算法一律拒绝")
    void rejectsPlainOrWrongHashImport() {
        OpenAppImportRequest plain = new OpenAppImportRequest();
        plain.setRegistration(OpenAppRegistrationRequest.builder()
                .clientId("legacy")
                .appName("旧应用")
                .clientSecret("plain")
                .build());
        plain.setImportId("legacy:1");
        plain.setSourceFingerprint("a".repeat(64));
        plain.setHashAlgorithm("BCRYPT");
        plain.setSecretHash("$2a$10$" + "x".repeat(53));

        StepVerifier.create(service.importApp(plain).contextWrite(TenantContext.withTenant("t-1")))
                .expectError(BusinessException.class)
                .verify();

        OpenAppImportRequest wrongAlgorithm = new OpenAppImportRequest();
        wrongAlgorithm.setRegistration(OpenAppRegistrationRequest.builder()
                .clientId("legacy")
                .appName("旧应用")
                .build());
        wrongAlgorithm.setImportId("legacy:1");
        wrongAlgorithm.setSourceFingerprint("a".repeat(64));
        wrongAlgorithm.setHashAlgorithm("PLAIN");
        wrongAlgorithm.setSecretHash("$2a$10$" + "x".repeat(53));

        StepVerifier.create(service.importApp(wrongAlgorithm).contextWrite(TenantContext.withTenant("t-1")))
                .expectError(BusinessException.class)
                .verify();
    }
}
