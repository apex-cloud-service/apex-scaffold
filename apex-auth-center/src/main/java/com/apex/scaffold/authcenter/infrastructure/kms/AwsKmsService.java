package com.apex.scaffold.authcenter.infrastructure.kms;

import java.time.Instant;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsAsyncClient;
import software.amazon.awssdk.services.kms.model.*;

/**
 * AWS KMS密钥管理服务实现
 *
 * 使用AWS KMS进行密钥管理和加密解密操作
 *
 * 配置示例：
 * security:
 *   kms:
 *     provider: aws
 *     aws:
 *       region: us-east-1
 *       master-key-id: arn:aws:kms:us-east-1:123456789012:key/12345678-1234-1234-1234-123456789012
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "security.kms.provider", havingValue = "aws")
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class AwsKmsService implements KeyManagementService {

    private final KmsAsyncClient kmsClient;
    private final String masterKeyId;

    /**
     * 构造AWS KMS服务
     *
     * @param kmsClient AWS KMS异步客户端
     * @param properties AWS KMS配置属性
     */
    public AwsKmsService(KmsAsyncClient kmsClient, AwsKmsProperties properties) {
        this.kmsClient = kmsClient;
        this.masterKeyId = properties.getMasterKeyId();
        log.info("AWS KMS服务已初始化，使用主密钥: {}", maskKeyId(masterKeyId));
    }

    @Override
    public Mono<String> encrypt(String plaintext, String keyId) {
        return Mono.fromFuture(() -> {
                    EncryptRequest request = EncryptRequest.builder()
                            .keyId(keyId != null ? keyId : masterKeyId)
                            .plaintext(SdkBytes.fromUtf8String(plaintext))
                            .build();

                    return kmsClient.encrypt(request);
                })
                .map(response -> Base64.getEncoder()
                        .encodeToString(response.ciphertextBlob().asByteArray()))
                .doOnSuccess(ciphertext -> log.debug("AWS KMS加密成功，密钥: {}", maskKeyId(keyId)))
                .doOnError(
                        error -> log.error("AWS KMS加密失败: {}", error.getClass().getSimpleName()));
    }

    @Override
    public Mono<String> decrypt(String ciphertext, String keyId) {
        return Mono.fromFuture(() -> {
                    byte[] ciphertextBytes = Base64.getDecoder().decode(ciphertext);

                    DecryptRequest request = DecryptRequest.builder()
                            .ciphertextBlob(SdkBytes.fromByteArray(ciphertextBytes))
                            .keyId(keyId)
                            .build();

                    return kmsClient.decrypt(request);
                })
                .map(response -> response.plaintext().asUtf8String())
                .doOnSuccess(plaintext -> log.debug("AWS KMS解密成功"))
                .doOnError(
                        error -> log.error("AWS KMS解密失败: {}", error.getClass().getSimpleName()));
    }

    @Override
    public Mono<DataKey> generateDataKey(String keyId) {
        return Mono.fromFuture(() -> {
                    GenerateDataKeyRequest request = GenerateDataKeyRequest.builder()
                            .keyId(keyId != null ? keyId : masterKeyId)
                            .keySpec(DataKeySpec.AES_256)
                            .build();

                    return kmsClient.generateDataKey(request);
                })
                .map(response -> new AwsDataKey(
                        Base64.getEncoder().encodeToString(response.plaintext().asByteArray()),
                        Base64.getEncoder()
                                .encodeToString(response.ciphertextBlob().asByteArray())))
                .cast(DataKey.class)
                .doOnSuccess(dataKey -> log.info("AWS KMS数据密钥生成成功"))
                .doOnError(error ->
                        log.error("AWS KMS数据密钥生成失败: {}", error.getClass().getSimpleName()));
    }

    @Override
    public Mono<String> rotateKey(String keyId) {
        return Mono.fromFuture(() -> {
                    EnableKeyRotationRequest request = EnableKeyRotationRequest.builder()
                            .keyId(keyId != null ? keyId : masterKeyId)
                            .build();

                    return kmsClient.enableKeyRotation(request);
                })
                .map(response -> keyId)
                .doOnSuccess(key -> log.info("AWS KMS密钥轮换已启用: {}", maskKeyId(keyId)))
                .doOnError(
                        error -> log.error("AWS KMS密钥轮换失败: {}", error.getClass().getSimpleName()));
    }

    @Override
    public Mono<KeyMetadata> getKeyMetadata(String keyId) {
        return Mono.fromFuture(() -> {
                    DescribeKeyRequest request = DescribeKeyRequest.builder()
                            .keyId(keyId != null ? keyId : masterKeyId)
                            .build();

                    return kmsClient.describeKey(request);
                })
                .map(response -> {
                    software.amazon.awssdk.services.kms.model.KeyMetadata metadata = response.keyMetadata();
                    return (KeyMetadata) new AwsKeyMetadata(
                            metadata.keyId(),
                            metadata.arn(),
                            metadata.keySpec().toString(),
                            metadata.keyState().toString(),
                            metadata.creationDate(),
                            metadata.enabled());
                })
                .doOnError(error ->
                        log.error("获取AWS KMS密钥元数据失败: {}", error.getClass().getSimpleName()));
    }

    @Override
    public Mono<Boolean> disableKey(String keyId) {
        return Mono.fromFuture(() -> {
                    DisableKeyRequest request = DisableKeyRequest.builder()
                            .keyId(keyId != null ? keyId : masterKeyId)
                            .build();

                    return kmsClient.disableKey(request);
                })
                .map(response -> true)
                .doOnSuccess(success -> log.warn("AWS KMS密钥已禁用: {}", maskKeyId(keyId)))
                .doOnError(
                        error -> log.error("禁用AWS KMS密钥失败: {}", error.getClass().getSimpleName()))
                .onErrorReturn(false);
    }

    @Override
    public Mono<Boolean> enableKey(String keyId) {
        return Mono.fromFuture(() -> {
                    EnableKeyRequest request = EnableKeyRequest.builder()
                            .keyId(keyId != null ? keyId : masterKeyId)
                            .build();

                    return kmsClient.enableKey(request);
                })
                .map(response -> true)
                .doOnSuccess(success -> log.info("AWS KMS密钥已启用: {}", maskKeyId(keyId)))
                .doOnError(
                        error -> log.error("启用AWS KMS密钥失败: {}", error.getClass().getSimpleName()))
                .onErrorReturn(false);
    }

    private static final int MIN_KEY_ID_LENGTH_FOR_MASK = 12;

    private String maskKeyId(String keyId) {
        if (keyId == null || keyId.length() < MIN_KEY_ID_LENGTH_FOR_MASK) {
            return "***";
        }
        return keyId.substring(0, 8) + "****" + keyId.substring(keyId.length() - 4);
    }

    /**
     * AWS数据密钥实现
     */
    private static class AwsDataKey implements DataKey {
        private String plaintextKey;
        private String encryptedKey;

        /**
         * 构造数据密钥。
         *
         * @param plaintextKey 明文数据密钥
         * @param encryptedKey 加密后的数据密钥
         */
        public AwsDataKey(String plaintextKey, String encryptedKey) {
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
         * @return 被主密钥加密的数据密钥密文（Base64编码）
         */
        @Override
        public String getEncryptedKey() {
            return encryptedKey;
        }
    }

    /**
     * AWS密钥元数据实现
     */
    private static class AwsKeyMetadata implements KeyMetadata {
        private String keyId;
        private String keyArn;
        private String algorithm;
        private String keyState;
        private Instant creationDate;
        private boolean enabled;

        /**
         * 构造密钥元数据。
         *
         * @param keyId 密钥ID
         * @param keyArn 密钥ARN
         * @param algorithm 算法规格
         * @param keyState 密钥状态
         * @param creationDate 创建时间
         * @param enabled 是否启用
         */
        public AwsKeyMetadata(
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
         * @return KMS密钥ID
         */
        @Override
        public String getKeyId() {
            return keyId;
        }

        /**
         * 获取密钥ARN
         *
         * @return KMS密钥的Amazon资源名称（ARN）
         */
        @Override
        public String getKeyArn() {
            return keyArn;
        }

        /**
         * 获取密钥算法规格
         *
         * @return 密钥的算法规格（如AES_256）
         */
        @Override
        public String getAlgorithm() {
            return algorithm;
        }

        /**
         * 获取密钥状态
         *
         * @return 密钥状态（如Enabled、Disabled）
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
         * @return 密钥处于启用状态时返回true
         */
        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }
}
