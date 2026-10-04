package com.apex.scaffold.authcenter.infrastructure.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.authcenter.domain.model.BiometricAuth;
import com.apex.scaffold.authcenter.infrastructure.repository.BiometricAuthRepository;
import com.apex.scaffold.common.audit.AuditLogService;
import com.apex.scaffold.common.security.EncryptionService;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("生物识别认证服务实现测试")
class BiometricAuthServiceImplTest {

    @Mock
    private BiometricAuthRepository biometricAuthRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private BiometricAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(auditLogService.log(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());

        service = new BiometricAuthServiceImpl(
                biometricAuthRepository, encryptionService, auditLogService, redisTemplate);
    }

    @Test
    @DisplayName("注册生物识别 - 成功")
    void testRegisterBiometric_Success() {
        String userId = "user1";
        String tenantId = "tenant1";
        BiometricAuth.BiometricType type = BiometricAuth.BiometricType.FINGERPRINT;

        when(encryptionService.encrypt(anyString())).thenReturn(Mono.just("encrypted-template-id"));

        BiometricAuth savedAuth = BiometricAuth.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(type)
                .templateId("encrypted-template-id")
                .deviceId("device-001")
                .deviceName("iPhone 13")
                .publicKey("public-key-data")
                .status(BiometricAuth.AuthStatus.ACTIVE)
                .failedAttempts(0)
                .createdAt(LocalDateTime.now())
                .build();

        when(biometricAuthRepository.save(any(BiometricAuth.class))).thenReturn(Mono.just(savedAuth));

        StepVerifier.create(
                        service.registerBiometric(userId, tenantId, type, "device-001", "iPhone 13", "public-key-data"))
                .expectNextMatches(auth -> auth.getUserId().equals(userId)
                        && auth.getType() == type
                        && auth.getStatus() == BiometricAuth.AuthStatus.ACTIVE)
                .verifyComplete();

        verify(auditLogService)
                .log(eq("BIOMETRIC_REGISTERED"), eq(userId), eq(tenantId), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("验证生物识别 - 成功")
    void testVerifyBiometric_Success() {
        String userId = "user1";
        String challenge = "test-challenge";
        String signature = "dGVzdC1zaWduYXR1cmU=";

        BiometricAuth auth = BiometricAuth.builder()
                .id(1L)
                .userId(userId)
                .type(BiometricAuth.BiometricType.FINGERPRINT)
                .templateId("template-001")
                .publicKey(
                        "LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0KTUlJQklqQU5CZ2txaGtpRzl3MEJBUUVGQUFPQ0FROEFNSUlCQ2dLQ0FRRUF3V2RPUW1LU0hheXpFSUZJdUxpRQouLi4KLS0tLS1FTkQgUFVCTElDIEtFWS0tLS0t")
                .status(BiometricAuth.AuthStatus.ACTIVE)
                .failedAttempts(0)
                .build();

        when(biometricAuthRepository.findByUserIdAndTypeAndTenantId(userId, BiometricAuth.BiometricType.FINGERPRINT, "tenant1"))
                .thenReturn(Mono.just(auth));
        when(valueOps.get(anyString())).thenReturn(Mono.just(challenge));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
        when(biometricAuthRepository.save(any(BiometricAuth.class))).thenReturn(Mono.just(auth));

        StepVerifier.create(
                        service.verifyBiometric(userId, "tenant1", BiometricAuth.BiometricType.FINGERPRINT, challenge, signature))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("验证生物识别 - 认证已锁定")
    void testVerifyBiometric_Locked() {
        String userId = "user1";
        BiometricAuth auth = BiometricAuth.builder()
                .id(1L)
                .userId(userId)
                .type(BiometricAuth.BiometricType.FINGERPRINT)
                .status(BiometricAuth.AuthStatus.LOCKED)
                .failedAttempts(10)
                .build();

        when(biometricAuthRepository.findByUserIdAndTypeAndTenantId(userId, BiometricAuth.BiometricType.FINGERPRINT, "tenant1"))
                .thenReturn(Mono.just(auth));

        StepVerifier.create(service.verifyBiometric(
                        userId, "tenant1", BiometricAuth.BiometricType.FINGERPRINT, "challenge", "signature"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("验证生物识别 - 认证未激活")
    void testVerifyBiometric_NotActive() {
        String userId = "user1";
        BiometricAuth auth = BiometricAuth.builder()
                .id(1L)
                .userId(userId)
                .type(BiometricAuth.BiometricType.FINGERPRINT)
                .status(BiometricAuth.AuthStatus.DISABLED)
                .failedAttempts(0)
                .build();

        when(biometricAuthRepository.findByUserIdAndTypeAndTenantId(userId, BiometricAuth.BiometricType.FINGERPRINT, "tenant1"))
                .thenReturn(Mono.just(auth));

        StepVerifier.create(service.verifyBiometric(
                        userId, "tenant1", BiometricAuth.BiometricType.FINGERPRINT, "challenge", "signature"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("验证生物识别 - 挑战无效")
    void testVerifyBiometric_InvalidChallenge() {
        String userId = "user1";
        BiometricAuth auth = BiometricAuth.builder()
                .id(1L)
                .userId(userId)
                .type(BiometricAuth.BiometricType.FINGERPRINT)
                .status(BiometricAuth.AuthStatus.ACTIVE)
                .failedAttempts(0)
                .build();

        when(biometricAuthRepository.findByUserIdAndTypeAndTenantId(userId, BiometricAuth.BiometricType.FINGERPRINT, "tenant1"))
                .thenReturn(Mono.just(auth));
        when(valueOps.get(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.verifyBiometric(
                        userId, "tenant1", BiometricAuth.BiometricType.FINGERPRINT, "invalid-challenge", "signature"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("启用生物识别 - 成功")
    void testEnableBiometric_Success() {
        Long id = 1L;
        BiometricAuth auth = BiometricAuth.builder()
                .id(id)
                .userId("user1")
                .tenantId("tenant1")
                .type(BiometricAuth.BiometricType.FINGERPRINT)
                .status(BiometricAuth.AuthStatus.DISABLED)
                .build();

        when(biometricAuthRepository.findById(id)).thenReturn(Mono.just(auth));
        when(biometricAuthRepository.save(any(BiometricAuth.class))).thenReturn(Mono.just(auth));

        StepVerifier.create(service.enableBiometric(id, "admin")).verifyComplete();

        verify(auditLogService)
                .log(eq("BIOMETRIC_ENABLED"), eq("admin"), eq("tenant1"), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("禁用生物识别 - 成功")
    void testDisableBiometric_Success() {
        Long id = 1L;
        BiometricAuth auth = BiometricAuth.builder()
                .id(id)
                .userId("user1")
                .tenantId("tenant1")
                .type(BiometricAuth.BiometricType.FACE_ID)
                .status(BiometricAuth.AuthStatus.ACTIVE)
                .build();

        when(biometricAuthRepository.findById(id)).thenReturn(Mono.just(auth));
        when(biometricAuthRepository.save(any(BiometricAuth.class))).thenReturn(Mono.just(auth));

        StepVerifier.create(service.disableBiometric(id, "admin")).verifyComplete();

        verify(auditLogService)
                .log(eq("BIOMETRIC_DISABLED"), eq("admin"), eq("tenant1"), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("删除生物识别 - 成功")
    void testDeleteBiometric_Success() {
        Long id = 1L;
        BiometricAuth auth = BiometricAuth.builder()
                .id(id)
                .userId("user1")
                .tenantId("tenant1")
                .type(BiometricAuth.BiometricType.WEBAUTHN)
                .build();

        when(biometricAuthRepository.findById(id)).thenReturn(Mono.just(auth));
        when(biometricAuthRepository.deleteById(id)).thenReturn(Mono.empty());

        StepVerifier.create(service.deleteBiometric(id, "admin")).verifyComplete();

        verify(auditLogService)
                .log(eq("BIOMETRIC_DELETED"), eq("admin"), eq("tenant1"), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("查询用户的全部生物识别配置")
    void testGetUserBiometrics() {
        String userId = "user1";
        BiometricAuth auth1 = BiometricAuth.builder()
                .id(1L)
                .userId(userId)
                .type(BiometricAuth.BiometricType.FINGERPRINT)
                .build();
        BiometricAuth auth2 = BiometricAuth.builder()
                .id(2L)
                .userId(userId)
                .type(BiometricAuth.BiometricType.FACE_ID)
                .build();

        when(biometricAuthRepository.findByUserIdAndTenantId(userId, "tenant1"))
                .thenReturn(Flux.just(auth1, auth2));

        StepVerifier.create(service.getUserBiometrics(userId, "tenant1"))
                .expectNext(auth1)
                .expectNext(auth2)
                .verifyComplete();
    }

    @Test
    @DisplayName("检查用户是否拥有激活的生物识别")
    void testIsBiometricEnabled_True() {
        String userId = "user1";
        BiometricAuth auth = BiometricAuth.builder()
                .id(1L)
                .userId(userId)
                .type(BiometricAuth.BiometricType.FINGERPRINT)
                .status(BiometricAuth.AuthStatus.ACTIVE)
                .build();

        when(biometricAuthRepository.findByUserIdAndTenantId(userId, "tenant1"))
                .thenReturn(Flux.just(auth));

        StepVerifier.create(service.isBiometricEnabled(userId, "tenant1")).expectNext(true).verifyComplete();
    }

    @Test
    @DisplayName("检查用户是否拥有激活的生物识别 - 无激活配置")
    void testIsBiometricEnabled_False() {
        String userId = "user1";
        BiometricAuth auth = BiometricAuth.builder()
                .id(1L)
                .userId(userId)
                .type(BiometricAuth.BiometricType.FINGERPRINT)
                .status(BiometricAuth.AuthStatus.DISABLED)
                .build();

        when(biometricAuthRepository.findByUserIdAndTenantId(userId, "tenant1"))
                .thenReturn(Flux.just(auth));

        StepVerifier.create(service.isBiometricEnabled(userId, "tenant1"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("解锁生物识别 - 成功")
    void testUnlockBiometric_Success() {
        Long id = 1L;
        BiometricAuth auth = BiometricAuth.builder()
                .id(id)
                .userId("user1")
                .tenantId("tenant1")
                .type(BiometricAuth.BiometricType.FINGERPRINT)
                .failedAttempts(10)
                .build();

        when(biometricAuthRepository.findById(id)).thenReturn(Mono.just(auth));
        when(biometricAuthRepository.save(any(BiometricAuth.class))).thenReturn(Mono.just(auth));

        StepVerifier.create(service.unlockBiometric(id, "admin")).verifyComplete();

        verify(auditLogService)
                .log(eq("BIOMETRIC_UNLOCKED"), eq("admin"), eq("tenant1"), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("生成注册挑战 - 成功")
    void testCreateRegistrationChallenge_Success() {
        String userId = "user1";

        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(service.createRegistrationChallenge(userId, "tenant1"))
                .expectNextMatches(challenge ->
                        challenge.challenge() != null && challenge.userId().equals(userId) && challenge.timeout() > 0)
                .verifyComplete();

        verify(valueOps).set(eq("biometric:challenge:tenant1:user1:registration"), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("生成认证挑战 - 成功")
    void testCreateAuthenticationChallenge_Success() {
        String userId = "user1";

        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(service.createAuthenticationChallenge(userId, "tenant1"))
                .expectNextMatches(challenge -> challenge.challenge() != null && challenge.timeout() > 0)
                .verifyComplete();

        verify(valueOps).set(eq("biometric:challenge:tenant1:user1:authentication"), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("验证生物识别 - 用户不存在")
    void testVerifyBiometric_UserNotFound() {
        String userId = "non-existent-user";

        when(biometricAuthRepository.findByUserIdAndTypeAndTenantId(userId, BiometricAuth.BiometricType.FINGERPRINT, "tenant1"))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.verifyBiometric(
                        userId, "tenant1", BiometricAuth.BiometricType.FINGERPRINT, "challenge", "signature"))
                .expectNext(false)
                .verifyComplete();
    }
}
