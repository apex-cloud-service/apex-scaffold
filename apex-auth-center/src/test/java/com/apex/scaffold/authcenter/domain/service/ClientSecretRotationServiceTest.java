package com.apex.scaffold.authcenter.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("客户端密钥轮换服务测试")
class ClientSecretRotationServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private UnifiedAuditService auditService;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private ClientSecretRotationService service;

    @BeforeEach
    void setUp() {
        service = new ClientSecretRotationService(redisTemplate, auditService);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(auditService.logOAuth2Event(any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(Mono.empty());
        when(auditService.logSecurityEvent(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());
        when(auditService.logSystemEvent(any(), any(), anyBoolean(), any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("生成客户端密钥 - 长度和格式正确")
    void testGenerateClientSecret_CorrectFormat() {
        StepVerifier.create(service.generateClientSecret())
                .assertNext(secret -> {
                    assertNotNull(secret);
                    assertFalse(secret.isEmpty());
                    assertTrue(secret.matches("[A-Za-z0-9_-]+"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("生成客户端密钥 - 每次生成不同")
    void testGenerateClientSecret_UniquenessCheck() {
        String secret1 = service.generateClientSecret().block();
        String secret2 = service.generateClientSecret().block();

        assertNotNull(secret1);
        assertNotNull(secret2);
        assertNotEquals(secret1, secret2);
    }

    @Test
    @DisplayName("轮换客户端密钥 - 首次设置")
    void testRotateClientSecret_FirstTime() {
        String clientId = "test-client";

        when(valueOperations.get("oauth2:client:test-client:secret:current")).thenReturn(Mono.empty());
        when(valueOperations.set(anyString(), anyString())).thenReturn(Mono.just(true));

        StepVerifier.create(service.rotateClientSecret(clientId, false))
                .assertNext(secret -> {
                    assertNotNull(secret);
                    assertFalse(secret.isEmpty());
                })
                .verifyComplete();

        verify(valueOperations, times(4)).set(anyString(), anyString());
        verify(auditService)
                .logOAuth2Event(
                        eq("CLIENT_SECRET_ROTATION"), isNull(), isNull(), eq(clientId), isNull(), eq(true), anyMap());
    }

    @Test
    @DisplayName("轮换客户端密钥 - 已存在旧密钥")
    void testRotateClientSecret_WithExistingSecret() {
        String clientId = "test-client";
        String oldSecretHash = "old-hash-value";

        when(valueOperations.get("oauth2:client:test-client:secret:current")).thenReturn(Mono.just(oldSecretHash));
        when(valueOperations.set(anyString(), anyString())).thenReturn(Mono.just(true));

        StepVerifier.create(service.rotateClientSecret(clientId, false))
                .assertNext(secret -> {
                    assertNotNull(secret);
                    assertFalse(secret.isEmpty());
                })
                .verifyComplete();

        verify(valueOperations).set(eq("oauth2:client:test-client:secret:pending"), eq(oldSecretHash));
        verify(valueOperations).set(eq("oauth2:client:test-client:secret:current"), anyString());
    }

    @Test
    @DisplayName("轮换客户端密钥 - 通知客户端")
    void testRotateClientSecret_WithNotification() {
        String clientId = "test-client";

        when(valueOperations.get(anyString())).thenReturn(Mono.empty());
        when(valueOperations.set(anyString(), anyString())).thenReturn(Mono.just(true));

        StepVerifier.create(service.rotateClientSecret(clientId, true))
                .assertNext(secret -> {
                    assertNotNull(secret);
                })
                .verifyComplete();

        verify(auditService)
                .logSystemEvent(eq("CLIENT_SECRET_ROTATION_NOTIFICATION"), eq("system"), eq(true), anyMap());
    }

    @Test
    @DisplayName("验证客户端密钥 - 当前密钥正确")
    void testValidateClientSecret_CurrentSecretValid() {
        String clientId = "test-client";
        String clientSecret = "test-secret";
        String secretHash = hashSecret(clientSecret);

        when(valueOperations.get("oauth2:client:test-client:secret:current")).thenReturn(Mono.just(secretHash));

        StepVerifier.create(service.validateClientSecret(clientId, clientSecret))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("验证客户端密钥 - 宽限期旧密钥有效")
    void testValidateClientSecret_PendingSecretValid() {
        String clientId = "test-client";
        String clientSecret = "old-secret";
        String secretHash = hashSecret(clientSecret);

        when(valueOperations.get("oauth2:client:test-client:secret:current")).thenReturn(Mono.just("different-hash"));
        when(valueOperations.get("oauth2:client:test-client:secret:pending")).thenReturn(Mono.just(secretHash));

        StepVerifier.create(service.validateClientSecret(clientId, clientSecret))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("验证客户端密钥 - 密钥错误")
    void testValidateClientSecret_Invalid() {
        String clientId = "test-client";
        String clientSecret = "wrong-secret";

        when(valueOperations.get("oauth2:client:test-client:secret:current")).thenReturn(Mono.just("correct-hash"));
        when(valueOperations.get("oauth2:client:test-client:secret:pending")).thenReturn(Mono.just("another-hash"));

        StepVerifier.create(service.validateClientSecret(clientId, clientSecret))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("验证客户端密钥 - 客户端不存在")
    void testValidateClientSecret_ClientNotExist() {
        String clientId = "non-existent-client";
        String clientSecret = "any-secret";

        when(valueOperations.get(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.validateClientSecret(clientId, clientSecret))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("验证客户端密钥 - 安全审计失败向调用方传播")
    void testValidateClientSecret_AuditFailurePropagates() {
        String clientId = "test-client";
        String clientSecret = "wrong-secret";
        RuntimeException auditFailure = new RuntimeException("audit unavailable");

        when(valueOperations.get("oauth2:client:test-client:secret:current")).thenReturn(Mono.just("correct-hash"));
        when(valueOperations.get("oauth2:client:test-client:secret:pending")).thenReturn(Mono.just("another-hash"));
        when(auditService.logSecurityEvent(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.error(auditFailure));

        StepVerifier.create(service.validateClientSecret(clientId, clientSecret))
                .expectErrorMatches(error -> error == auditFailure)
                .verify();
    }

    @Test
    @DisplayName("清理过期密钥 - 已超过宽限期")
    void testCleanupExpiredSecrets_Expired() {
        String clientId = "test-client";
        Instant oldTime = Instant.now().minus(10, ChronoUnit.DAYS);

        when(valueOperations.get("oauth2:client:test-client:secret:created"))
                .thenReturn(Mono.just(String.valueOf(oldTime.toEpochMilli())));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.cleanupExpiredSecrets(clientId)).verifyComplete();

        verify(redisTemplate).delete("oauth2:client:test-client:secret:pending");
        verify(auditService)
                .logOAuth2Event(
                        eq("CLIENT_SECRET_CLEANUP"), isNull(), isNull(), eq(clientId), isNull(), eq(true), anyMap());
    }

    @Test
    @DisplayName("清理过期密钥 - 未超过宽限期")
    void testCleanupExpiredSecrets_NotExpired() {
        String clientId = "test-client";
        Instant recentTime = Instant.now().minus(3, ChronoUnit.DAYS);

        when(valueOperations.get("oauth2:client:test-client:secret:created"))
                .thenReturn(Mono.just(String.valueOf(recentTime.toEpochMilli())));

        StepVerifier.create(service.cleanupExpiredSecrets(clientId)).verifyComplete();

        verify(redisTemplate, never()).delete(anyString());
        verify(auditService, never())
                .logOAuth2Event(
                        anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyMap());
    }

    @Test
    @DisplayName("清理过期密钥 - 无创建记录")
    void testCleanupExpiredSecrets_NoCreatedRecord() {
        String clientId = "test-client";

        when(valueOperations.get("oauth2:client:test-client:secret:created")).thenReturn(Mono.empty());

        StepVerifier.create(service.cleanupExpiredSecrets(clientId)).verifyComplete();

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("强制轮换密钥 - 紧急响应")
    void testForceRotateClientSecret() {
        String clientId = "test-client";
        String reason = "密钥泄露";

        when(valueOperations.get(anyString())).thenReturn(Mono.empty());
        when(valueOperations.set(anyString(), anyString())).thenReturn(Mono.just(true));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.forceRotateClientSecret(clientId, reason))
                .assertNext(secret -> {
                    assertNotNull(secret);
                    assertFalse(secret.isEmpty());
                })
                .verifyComplete();

        verify(auditService)
                .logSecurityEvent(
                        eq("FORCE_CLIENT_SECRET_ROTATION"),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq("HIGH"),
                        eq(reason),
                        anyMap());
        verify(redisTemplate).delete("oauth2:client:test-client:secret:pending");
    }

    @Test
    @DisplayName("定时任务 - 密钥轮换检查")
    void testScheduledSecretRotation() {
        assertDoesNotThrow(() -> service.scheduledSecretRotation());
    }

    private String hashSecret(String secret) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("哈希失败", e);
        }
    }
}
