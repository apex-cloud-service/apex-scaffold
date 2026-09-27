package com.apex.scaffold.authcenter.infrastructure.kms;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * HashiCorp Vault密钥管理服务实现
 *
 * 使用HashiCorp Vault进行密钥管理和加密解密操作
 *
 * 配置示例：
 * security:
 *   kms:
 *     provider: vault
 *     vault:
 *       address: http://localhost:8200
 *       token: s.xxxxxxxxxxxxxx
 *       namespace: admin
 *       transit-engine-path: transit
 *       key-name: apex-encryption-key
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "security.kms.provider", havingValue = "vault")
public class VaultKmsService implements KeyManagementService {

    private final WebClient webClient;
    private final VaultKmsProperties properties;

    public VaultKmsService(VaultKmsProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getAddress())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Vault-Token", properties.getToken())
                .defaultHeader("X-Vault-Namespace", properties.getNamespace())
                .build();

        log.info("HashiCorp Vault KMS服务已初始化，地址: {}", properties.getAddress());
    }

    @Override
    public Mono<String> encrypt(String plaintext, String keyId) {
        String keyName = keyId != null ? keyId : properties.getKeyName();
        String path = String.format("/v1/%s/encrypt/%s", properties.getTransitEnginePath(), keyName);

        Map<String, Object> request = Map.of("plaintext", Base64.getEncoder().encodeToString(plaintext.getBytes()));

        return webClient
                .post()
                .uri(path)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(VaultResponse.class)
                .map(response -> response.getData().get("ciphertext").toString())
                .doOnSuccess(ciphertext -> log.debug("Vault加密成功，密钥: {}", keyName))
                .doOnError(error -> log.error("Vault加密失败: {}", error.getMessage()));
    }

    @Override
    public Mono<String> decrypt(String ciphertext, String keyId) {
        String keyName = keyId != null ? keyId : properties.getKeyName();
        String path = String.format("/v1/%s/decrypt/%s", properties.getTransitEnginePath(), keyName);

        Map<String, Object> request = Map.of("ciphertext", ciphertext);

        return webClient
                .post()
                .uri(path)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(VaultResponse.class)
                .map(response -> {
                    String base64Plaintext = response.getData().get("plaintext").toString();
                    return new String(Base64.getDecoder().decode(base64Plaintext));
                })
                .doOnSuccess(plaintext -> log.debug("Vault解密成功"))
                .doOnError(error -> log.error("Vault解密失败: {}", error.getMessage()));
    }

    @Override
    public Mono<DataKey> generateDataKey(String keyId) {
        String keyName = keyId != null ? keyId : properties.getKeyName();
        String path = String.format("/v1/%s/datakey/plaintext/%s", properties.getTransitEnginePath(), keyName);

        Map<String, Object> request = Map.of("bits", 256);

        return webClient
                .post()
                .uri(path)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(VaultResponse.class)
                .map(response -> {
                    Map<String, Object> data = response.getData();
                    return (DataKey) new VaultDataKey(
                            data.get("plaintext").toString(),
                            data.get("ciphertext").toString());
                })
                .doOnSuccess(dataKey -> log.info("Vault数据密钥生成成功"))
                .doOnError(error -> log.error("Vault数据密钥生成失败: {}", error.getMessage()));
    }

    @Override
    public Mono<String> rotateKey(String keyId) {
        String keyName = keyId != null ? keyId : properties.getKeyName();
        String path = String.format("/v1/%s/keys/%s/rotate", properties.getTransitEnginePath(), keyName);

        return webClient
                .post()
                .uri(path)
                .retrieve()
                .bodyToMono(Void.class)
                .thenReturn(keyName)
                .doOnSuccess(key -> log.info("Vault密钥轮换成功: {}", keyName))
                .doOnError(error -> log.error("Vault密钥轮换失败: {}", error.getMessage()));
    }

    @Override
    public Mono<KeyMetadata> getKeyMetadata(String keyId) {
        String keyName = keyId != null ? keyId : properties.getKeyName();
        String path = String.format("/v1/%s/keys/%s", properties.getTransitEnginePath(), keyName);

        return webClient
                .get()
                .uri(path)
                .retrieve()
                .bodyToMono(VaultResponse.class)
                .map(response -> {
                    Map<String, Object> data = response.getData();
                    return (KeyMetadata) new VaultKeyMetadata(
                            keyName,
                            keyName,
                            data.get("type").toString(),
                            data.get("deletion_allowed").toString(),
                            Instant.now(),
                            !(boolean) data.getOrDefault("deletion_allowed", false));
                })
                .doOnError(error -> log.error("获取Vault密钥元数据失败: {}", error.getMessage()));
    }

    @Override
    public Mono<Boolean> disableKey(String keyId) {
        String keyName = keyId != null ? keyId : properties.getKeyName();
        String path = String.format("/v1/%s/keys/%s/config", properties.getTransitEnginePath(), keyName);

        Map<String, Object> request = Map.of("deletion_allowed", true);

        return webClient
                .post()
                .uri(path)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .map(v -> true)
                .doOnSuccess(success -> log.warn("Vault密钥已配置为可删除: {}", keyName))
                .doOnError(error -> log.error("配置Vault密钥失败: {}", error.getMessage()))
                .onErrorReturn(false);
    }

    @Override
    public Mono<Boolean> enableKey(String keyId) {
        String keyName = keyId != null ? keyId : properties.getKeyName();
        String path = String.format("/v1/%s/keys/%s/config", properties.getTransitEnginePath(), keyName);

        Map<String, Object> request = Map.of("deletion_allowed", false);

        return webClient
                .post()
                .uri(path)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .map(v -> true)
                .doOnSuccess(success -> log.info("Vault密钥已配置为不可删除: {}", keyName))
                .doOnError(error -> log.error("配置Vault密钥失败: {}", error.getMessage()))
                .onErrorReturn(false);
    }

    /**
     * Vault API响应
     */
    @Data
    private static class VaultResponse {
        private Map<String, Object> data;
        private Map<String, Object> auth;
    }

    /**
     * Vault数据密钥实现
     */
    private static class VaultDataKey implements DataKey {
        private String plaintextKey;
        private String encryptedKey;

        public VaultDataKey(String plaintextKey, String encryptedKey) {
            this.plaintextKey = plaintextKey;
            this.encryptedKey = encryptedKey;
        }

        /**
         * 获取明文密钥
         *
         * @return 明文数据密钥（Base64编码）
         */
        @Override
        public String getPlaintextKey() {
            return plaintextKey;
        }

        /**
         * 获取加密后的密钥
         *
         * @return 被主密钥加密的数据密钥密文
         */
        @Override
        public String getEncryptedKey() {
            return encryptedKey;
        }
    }

    /**
     * Vault密钥元数据实现
     */
    private static class VaultKeyMetadata implements KeyMetadata {
        private String keyId;
        private String keyArn;
        private String algorithm;
        private String keyState;
        private Instant creationDate;
        private boolean enabled;

        public VaultKeyMetadata(
                String keyId, String keyArn, String algorithm, String keyState, Instant creationDate, boolean enabled) {
            this.keyId = keyId;
            this.keyArn = keyArn;
            this.algorithm = algorithm;
            this.keyState = keyState;
            this.creationDate = creationDate;
            this.enabled = enabled;
        }

        /**
         * 获取密钥ID
         *
         * @return 密钥ID（Vault transit密钥名称）
         */
        @Override
        public String getKeyId() {
            return keyId;
        }

        /**
         * 获取密钥ARN
         *
         * @return 密钥ARN（Vault实现中与密钥名称一致）
         */
        @Override
        public String getKeyArn() {
            return keyArn;
        }

        /**
         * 获取密钥算法类型
         *
         * @return 密钥的算法类型（来自Vault密钥信息的type字段）
         */
        @Override
        public String getAlgorithm() {
            return algorithm;
        }

        /**
         * 获取密钥状态
         *
         * @return 密钥状态信息（来自Vault密钥信息的deletion_allowed字段）
         */
        @Override
        public String getKeyState() {
            return keyState;
        }

        /**
         * 获取密钥创建时间
         *
         * @return 密钥的创建时间
         */
        @Override
        public Instant getCreationDate() {
            return creationDate;
        }

        /**
         * 判断密钥是否启用
         *
         * @return 密钥未开放删除（可正常使用）时返回true
         */
        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }
}
