package com.apex.scaffold.integration.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.connector.application.CredentialReferenceResolver;
import com.apex.scaffold.integration.connector.domain.Credential;
import com.apex.scaffold.integration.connector.domain.CredentialCipher;
import com.apex.scaffold.integration.connector.domain.repository.CredentialRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 连接器配置凭据引用解析测试（方案 M5 / 批次 E2）。
 *
 * <p>覆盖：空配置解析为空树、非法 JSON 拒绝、credentialRef/secretRef 解密注入
 * secret 键、引用缺失返回 404、空白或非文本引用按普通字段保留、嵌套 Map 递归解析。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("凭据引用解析测试")
class CredentialReferenceResolverTest {

    private static final String KEY_32 = "0123456789abcdef0123456789abcdef";

    private CredentialRepository repository;

    private CredentialCipher cipher;

    private CredentialReferenceResolver resolver;

    @BeforeEach
    void setUp() {
        repository = mock(CredentialRepository.class);
        cipher = new CredentialCipher(KEY_32);
        resolver = new CredentialReferenceResolver(repository, cipher, new ObjectMapper());
    }

    @Test
    @DisplayName("空配置解析为空树")
    void blankConfigResolvesToEmptyMap() {
        StepVerifier.create(resolver.resolve("t1", null))
                .assertNext(config -> assertThat(config).isEmpty())
                .verifyComplete();
        StepVerifier.create(resolver.resolve("t1", " "))
                .assertNext(config -> assertThat(config).isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("非法 JSON 拒绝")
    void invalidJsonIsRejected() {
        StepVerifier.create(resolver.resolve("t1", "[1,2]"))
                .expectErrorSatisfies(error ->
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode()))
                .verify();
    }

    @Test
    @DisplayName("credentialRef：解密后以 secret 键注入，并移除引用键")
    void credentialRefInjectsPlaintextSecret() {
        when(repository.findByKey("t1", "gh-token")).thenReturn(Mono.just(credential("gh-token", "sk-live")));

        StepVerifier.create(resolver.resolve("t1", "{\"endpoint\":\"https://x\",\"credentialRef\":\"gh-token\"}"))
                .assertNext(config -> {
                    assertThat(config).containsEntry("endpoint", "https://x");
                    assertThat(config).containsEntry("secret", "sk-live");
                    assertThat(config).doesNotContainKey("credentialRef");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("secretRef：与 credentialRef 等价")
    void secretRefInjectsPlaintextSecret() {
        when(repository.findByKey("t1", "gh-token")).thenReturn(Mono.just(credential("gh-token", "sk-live")));

        StepVerifier.create(resolver.resolve("t1", "{\"secretRef\":\"gh-token\",\"mode\":\"token\"}"))
                .assertNext(config -> assertThat(config)
                        .containsEntry("secret", "sk-live")
                        .containsEntry("mode", "token")
                        .doesNotContainKey("secretRef"))
                .verifyComplete();
    }

    @Test
    @DisplayName("引用不存在：404")
    void missingCredentialIsNotFound() {
        when(repository.findByKey("t1", "ghost")).thenReturn(Mono.empty());

        StepVerifier.create(resolver.resolve("t1", "{\"credentialRef\":\"ghost\"}"))
                .expectErrorSatisfies(error ->
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()))
                .verify();
    }

    @Test
    @DisplayName("空白或非文本引用按普通字段保留，不触发凭据查询")
    void nonTextualReferenceKeepsField() {
        StepVerifier.create(resolver.resolve("t1", "{\"credentialRef\":\"  \",\"a\":1}"))
                .assertNext(config ->
                        assertThat(config).containsEntry("credentialRef", "  ").containsEntry("a", 1))
                .verifyComplete();
        StepVerifier.create(resolver.resolve("t1", "{\"credentialRef\":123}"))
                .assertNext(config -> assertThat(config).containsEntry("credentialRef", 123))
                .verifyComplete();

        verify(repository, never())
                .findByKey(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("嵌套 Map 递归解析凭据引用")
    void nestedMapIsResolvedRecursively() {
        when(repository.findByKey("t1", "gh-token")).thenReturn(Mono.just(credential("gh-token", "sk-live")));

        StepVerifier.create(resolver.resolve(
                        "t1", "{\"outer\":{\"secretRef\":\"gh-token\",\"path\":\"/v1\"},\"plain\":\"v\"}"))
                .assertNext(config -> {
                    assertThat(config).containsEntry("plain", "v");
                    Object outer = config.get("outer");
                    assertThat(outer).isInstanceOf(java.util.Map.class);
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> nested = (java.util.Map<String, Object>) outer;
                    assertThat(nested).containsEntry("secret", "sk-live").containsEntry("path", "/v1");
                })
                .verifyComplete();
    }

    private Credential credential(String credentialKey, String plaintext) {
        CredentialCipher.EncryptedSecret encrypted = cipher.encrypt(plaintext);
        Credential credential = new Credential();
        credential.setCredentialKey(credentialKey);
        credential.setSecretCipher(encrypted.cipherText());
        credential.setSecretIv(encrypted.iv());
        credential.setVersionNo(1);
        return credential;
    }
}
