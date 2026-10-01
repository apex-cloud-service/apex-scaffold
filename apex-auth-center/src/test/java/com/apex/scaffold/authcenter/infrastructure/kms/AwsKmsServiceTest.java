package com.apex.scaffold.authcenter.infrastructure.kms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsAsyncClient;
import software.amazon.awssdk.services.kms.model.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AWS KMS服务测试")
class AwsKmsServiceTest {

    @Mock
    private KmsAsyncClient kmsClient;

    private AwsKmsService awsKmsService;
    private AwsKmsProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AwsKmsProperties();
        properties.setMasterKeyId("arn:aws:kms:us-east-1:123456789012:key/test-key-id");
        properties.setRegion("us-east-1");

        awsKmsService = new AwsKmsService(kmsClient, properties);
    }

    @Test
    @DisplayName("加密数据 - 成功")
    void testEncrypt_Success() {
        String plaintext = "sensitive-data";
        byte[] ciphertextBytes = "encrypted-data".getBytes(StandardCharsets.UTF_8);

        EncryptResponse response = EncryptResponse.builder()
                .ciphertextBlob(SdkBytes.fromByteArray(ciphertextBytes))
                .keyId(properties.getMasterKeyId())
                .build();

        when(kmsClient.encrypt(any(EncryptRequest.class))).thenReturn(CompletableFuture.completedFuture(response));

        StepVerifier.create(awsKmsService.encrypt(plaintext, null))
                .expectNext(Base64.getEncoder().encodeToString(ciphertextBytes))
                .verifyComplete();
    }

    @Test
    @DisplayName("加密数据 - 指定密钥")
    void testEncrypt_WithSpecificKey() {
        String plaintext = "sensitive-data";
        String customKeyId = "custom-key-id";
        byte[] ciphertextBytes = "encrypted-data".getBytes(StandardCharsets.UTF_8);

        EncryptResponse response = EncryptResponse.builder()
                .ciphertextBlob(SdkBytes.fromByteArray(ciphertextBytes))
                .keyId(customKeyId)
                .build();

        when(kmsClient.encrypt(any(EncryptRequest.class))).thenReturn(CompletableFuture.completedFuture(response));

        StepVerifier.create(awsKmsService.encrypt(plaintext, customKeyId))
                .expectNextMatches(result -> result != null && !result.isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("解密数据 - 成功")
    void testDecrypt_Success() {
        String expectedPlaintext = "decrypted-data";
        String ciphertext = Base64.getEncoder().encodeToString("encrypted".getBytes());

        DecryptResponse response = DecryptResponse.builder()
                .plaintext(SdkBytes.fromUtf8String(expectedPlaintext))
                .keyId(properties.getMasterKeyId())
                .build();

        when(kmsClient.decrypt(any(DecryptRequest.class))).thenReturn(CompletableFuture.completedFuture(response));

        StepVerifier.create(awsKmsService.decrypt(ciphertext, properties.getMasterKeyId()))
                .expectNext(expectedPlaintext)
                .verifyComplete();
    }

    @Test
    @DisplayName("生成数据密钥 - 成功")
    void testGenerateDataKey_Success() {
        byte[] plaintextKey = "plaintext-key-32-bytes-long!!".getBytes();
        byte[] encryptedKey = "encrypted-key".getBytes();

        GenerateDataKeyResponse response = GenerateDataKeyResponse.builder()
                .plaintext(SdkBytes.fromByteArray(plaintextKey))
                .ciphertextBlob(SdkBytes.fromByteArray(encryptedKey))
                .keyId(properties.getMasterKeyId())
                .build();

        when(kmsClient.generateDataKey(any(GenerateDataKeyRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        StepVerifier.create(awsKmsService.generateDataKey(null))
                .expectNextMatches(dataKey -> dataKey.getPlaintextKey() != null && dataKey.getEncryptedKey() != null)
                .verifyComplete();
    }

    @Test
    @DisplayName("轮换密钥 - 成功")
    void testRotateKey_Success() {
        EnableKeyRotationResponse response = EnableKeyRotationResponse.builder().build();

        when(kmsClient.enableKeyRotation(any(EnableKeyRotationRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        StepVerifier.create(awsKmsService.rotateKey(properties.getMasterKeyId()))
                .expectNext(properties.getMasterKeyId())
                .verifyComplete();
    }

    @Test
    @DisplayName("加密数据 - KMS异常")
    void testEncrypt_KmsException() {
        String plaintext = "sensitive-data";

        when(kmsClient.encrypt(any(EncryptRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        KmsException.builder().message("KMS service error").build()));

        StepVerifier.create(awsKmsService.encrypt(plaintext, null))
                .expectError(KmsException.class)
                .verify();
    }

    @Test
    @DisplayName("解密数据 - 无效密文")
    void testDecrypt_InvalidCiphertext() {
        String invalidCiphertext = "not-base64!@#$%";

        StepVerifier.create(awsKmsService.decrypt(invalidCiphertext, properties.getMasterKeyId()))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("获取密钥元数据 - 成功")
    void testGetKeyMetadata_Success() {
        software.amazon.awssdk.services.kms.model.KeyMetadata metadata =
                software.amazon.awssdk.services.kms.model.KeyMetadata.builder()
                        .keyId("key-id-123")
                        .arn("arn:aws:kms:us-east-1:123456789012:key/key-id-123")
                        .keySpec(KeySpec.RSA_2048)
                        .keyState(KeyState.ENABLED)
                        .creationDate(Instant.now())
                        .enabled(true)
                        .build();

        DescribeKeyResponse response =
                DescribeKeyResponse.builder().keyMetadata(metadata).build();

        when(kmsClient.describeKey(any(DescribeKeyRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        StepVerifier.create(awsKmsService.getKeyMetadata(null))
                .expectNextMatches(meta -> meta.getKeyId().equals("key-id-123") && meta.isEnabled())
                .verifyComplete();
    }

    @Test
    @DisplayName("禁用密钥 - 成功")
    void testDisableKey_Success() {
        DisableKeyResponse response = DisableKeyResponse.builder().build();

        when(kmsClient.disableKey(any(DisableKeyRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        StepVerifier.create(awsKmsService.disableKey(properties.getMasterKeyId()))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("禁用密钥 - 失败")
    void testDisableKey_Failure() {
        when(kmsClient.disableKey(any(DisableKeyRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        KmsException.builder().message("Disable key failed").build()));

        StepVerifier.create(awsKmsService.disableKey(properties.getMasterKeyId()))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("启用密钥 - 成功")
    void testEnableKey_Success() {
        EnableKeyResponse response = EnableKeyResponse.builder().build();

        when(kmsClient.enableKey(any(EnableKeyRequest.class))).thenReturn(CompletableFuture.completedFuture(response));

        StepVerifier.create(awsKmsService.enableKey(properties.getMasterKeyId()))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("启用密钥 - 失败")
    void testEnableKey_Failure() {
        when(kmsClient.enableKey(any(EnableKeyRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        KmsException.builder().message("Enable key failed").build()));

        StepVerifier.create(awsKmsService.enableKey(properties.getMasterKeyId()))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("生成数据密钥 - 指定密钥ID")
    void testGenerateDataKey_WithKeyId() {
        String customKeyId = "custom-key-id";
        byte[] plaintextKey = "test-plaintext-key".getBytes();
        byte[] encryptedKey = "test-encrypted-key".getBytes();

        GenerateDataKeyResponse response = GenerateDataKeyResponse.builder()
                .plaintext(SdkBytes.fromByteArray(plaintextKey))
                .ciphertextBlob(SdkBytes.fromByteArray(encryptedKey))
                .keyId(customKeyId)
                .build();

        when(kmsClient.generateDataKey(any(GenerateDataKeyRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        StepVerifier.create(awsKmsService.generateDataKey(customKeyId))
                .expectNextMatches(dataKey -> dataKey != null)
                .verifyComplete();
    }

    @Test
    @DisplayName("轮换密钥 - 使用默认密钥")
    void testRotateKey_DefaultKey() {
        EnableKeyRotationResponse response = EnableKeyRotationResponse.builder().build();
        String customKeyId = "my-custom-key";

        when(kmsClient.enableKeyRotation(any(EnableKeyRotationRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        StepVerifier.create(awsKmsService.rotateKey(customKeyId))
                .expectNext(customKeyId)
                .verifyComplete();
    }

    @Test
    @DisplayName("解密数据 - KMS异常")
    void testDecrypt_KmsException() {
        String ciphertext = Base64.getEncoder().encodeToString("test".getBytes());

        when(kmsClient.decrypt(any(DecryptRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        KmsException.builder().message("Decrypt failed").build()));

        StepVerifier.create(awsKmsService.decrypt(ciphertext, properties.getMasterKeyId()))
                .expectError(KmsException.class)
                .verify();
    }

    @Test
    @DisplayName("获取密钥元数据 - 失败")
    void testGetKeyMetadata_Failure() {
        when(kmsClient.describeKey(any(DescribeKeyRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        KmsException.builder().message("Key not found").build()));

        StepVerifier.create(awsKmsService.getKeyMetadata(properties.getMasterKeyId()))
                .expectError(KmsException.class)
                .verify();
    }

    @Test
    @DisplayName("生成数据密钥 - 失败")
    void testGenerateDataKey_Failure() {
        when(kmsClient.generateDataKey(any(GenerateDataKeyRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(KmsException.builder()
                        .message("Generate data key failed")
                        .build()));

        StepVerifier.create(awsKmsService.generateDataKey(null))
                .expectError(KmsException.class)
                .verify();
    }

    @Test
    @DisplayName("轮换密钥 - 失败")
    void testRotateKey_Failure() {
        when(kmsClient.enableKeyRotation(any(EnableKeyRotationRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        KmsException.builder().message("Key rotation failed").build()));

        StepVerifier.create(awsKmsService.rotateKey(properties.getMasterKeyId()))
                .expectError(KmsException.class)
                .verify();
    }
}
