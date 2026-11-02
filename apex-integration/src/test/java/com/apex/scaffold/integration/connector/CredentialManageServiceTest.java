package com.apex.scaffold.integration.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.integration.connector.application.CredentialManageService;
import com.apex.scaffold.integration.connector.domain.Credential;
import com.apex.scaffold.integration.connector.domain.CredentialCipher;
import com.apex.scaffold.integration.connector.domain.repository.CredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 凭据登记与轮换用例测试（方案 M5 / 批次 E2）。
 *
 * <p>覆盖：登记前加密（落库参数为密文+IV，无明文字段）、初始版本 1、
 * 轮换后密文更换且仓储负责版本递增、响应实体不含明文。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("凭据加密落库与轮换测试")
class CredentialManageServiceTest {

    private static final String KEY_32 = "0123456789abcdef0123456789abcdef";

    private static final String PLAINTEXT = "sk-live-secret-value";

    private CredentialRepository repository;

    private CredentialManageService service;

    @BeforeEach
    void setUp() {
        repository = mock(CredentialRepository.class);
        service = new CredentialManageService(repository, new CredentialCipher(KEY_32));
    }

    @Test
    @DisplayName("登记：明文先加密再落库，初始版本 1，实体无明文字段")
    void registerEncryptsBeforePersist() {
        Credential saved = new Credential();
        saved.setId(1L);
        saved.setCredentialKey("gh-token");
        saved.setVersionNo(1);
        when(repository.insert(any(Credential.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(service.register("t1", "gh-token", "API_KEY", PLAINTEXT))
                .assertNext(response -> {
                    assertThat(response.getVersionNo()).isEqualTo(1);
                    assertThat(response.toString()).doesNotContain(PLAINTEXT);
                })
                .verifyComplete();

        ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
        verify(repository).insert(captor.capture());
        Credential persisted = captor.getValue();
        assertThat(persisted.getTenantId()).isEqualTo("t1");
        assertThat(persisted.getCredentialKey()).isEqualTo("gh-token");
        assertThat(persisted.getSecretCipher()).isNotBlank().isNotEqualTo(PLAINTEXT);
        assertThat(persisted.getSecretIv()).isNotBlank();
        assertThat(persisted.getVersionNo()).isEqualTo(1);
        // 领域实体本身不携带明文字段
        assertThat(persisted.toString()).doesNotContain(PLAINTEXT);
    }

    @Test
    @DisplayName("轮换：新密文替换旧密文，版本由仓储递增")
    void rotateReplacesCipherAndBumpsVersion() {
        Credential existing = new Credential();
        existing.setId(7L);
        existing.setTenantId("t1");
        existing.setCredentialKey("gh-token");
        existing.setSecretCipher("old-cipher");
        existing.setSecretIv("old-iv");
        existing.setVersionNo(3);
        when(repository.findById("t1", 7L)).thenReturn(Mono.just(existing));
        when(repository.rotate(any(Credential.class))).thenAnswer(invocation -> {
            Credential arg = invocation.getArgument(0);
            arg.setVersionNo(arg.getVersionNo() + 1);
            return Mono.just(arg);
        });

        StepVerifier.create(service.rotate("t1", 7L, "new-plaintext"))
                .assertNext(response -> assertThat(response.getVersionNo()).isEqualTo(4))
                .verifyComplete();

        ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
        verify(repository).rotate(captor.capture());
        Credential rotated = captor.getValue();
        assertThat(rotated.getSecretCipher()).isNotBlank().isNotEqualTo("old-cipher");
        assertThat(rotated.getSecretIv()).isNotBlank().isNotEqualTo("old-iv");
        assertThat(rotated.toString()).doesNotContain("new-plaintext");
    }

    @Test
    @DisplayName("明文为空时登记失败且不触碰仓储")
    void registerRejectsBlankSecret() {
        StepVerifier.create(service.register("t1", "k", "API_KEY", " "))
                .expectError(com.apex.scaffold.common.exception.BusinessException.class)
                .verify();
        verify(repository, org.mockito.Mockito.never()).insert(any());
    }
}
