package com.apex.scaffold.authcenter.application.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.authcenter.domain.model.entity.JwtKeyEntity;
import com.apex.scaffold.authcenter.domain.repository.JwtKeyRepository;
import com.apex.scaffold.authcenter.domain.service.TokenEncryptionService;
import com.apex.scaffold.common.audit.SecurityAuditService;
import com.apex.scaffold.common.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

// 说明（2026-09-30）：SecurityAuditService 已由 authcenter 内部下沉至 apex-common，
// 本类仅同步更新 import 路径为 com.apex.scaffold.common.audit.SecurityAuditService，
// 其余测试逻辑与断言保持不变。
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtKeyRotationService(application) 单元测试")
class JwtKeyRotationServiceTest {

    @Mock
    private JwtKeyRepository jwtKeyRepository;

    @Mock
    private TokenEncryptionService encryptionService;

    @Mock
    private SecurityAuditService auditService;

    private JwtKeyRotationService service;

    @BeforeEach
    void setUp() {
        service = new JwtKeyRotationService(jwtKeyRepository, encryptionService, auditService);
    }

    private JwtKeyEntity buildKey(String version, String status) {
        return JwtKeyEntity.builder()
                .id(1L)
                .keyVersion(version)
                .encryptedSecret("iv:encrypted")
                .algorithm("HS256")
                .status(status)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("generateNewKey() 方法测试")
    class GenerateNewKeyTest {

        @Test
        @DisplayName("成功生成新密钥")
        void testGenerateNewKey_Success() {
            when(encryptionService.encryptForStorage(anyString())).thenReturn("encrypted-secret");
            when(jwtKeyRepository.save(any(JwtKeyEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            StepVerifier.create(service.generateNewKey("SCHEDULED", 1L, "admin"))
                    .assertNext(key -> {
                        org.assertj.core.api.Assertions.assertThat(key.getStatus())
                                .isEqualTo("PENDING");
                        org.assertj.core.api.Assertions.assertThat(key.getAlgorithm())
                                .isEqualTo("HS256");
                        org.assertj.core.api.Assertions.assertThat(key.getKeyVersion())
                                .startsWith("v");
                    })
                    .verifyComplete();

            verify(auditService).logKeyGeneration(anyString(), eq("SCHEDULED"), eq("admin"));
        }
    }

    @Nested
    @DisplayName("rotateKey() 方法测试")
    class RotateKeyTest {

        @Test
        @DisplayName("成功执行密钥轮换")
        void testRotateKey_Success() {
            when(encryptionService.encryptForStorage(anyString())).thenReturn("encrypted-secret");
            when(jwtKeyRepository.save(any(JwtKeyEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(jwtKeyRepository.markAllActiveAsRotating()).thenReturn(Mono.just(1));

            StepVerifier.create(service.rotateKey("SCHEDULED", 1L, "admin"))
                    .assertNext(key -> org.assertj.core.api.Assertions.assertThat(key.getStatus())
                            .isEqualTo("ACTIVE"))
                    .verifyComplete();

            verify(auditService).logKeyRotation(isNull(), anyString(), eq("SCHEDULED"), eq("admin"));
        }

        @Test
        @DisplayName("轮换失败时记录失败审计日志")
        void testRotateKey_Failure() {
            when(encryptionService.encryptForStorage(anyString())).thenReturn("encrypted-secret");
            when(jwtKeyRepository.save(any(JwtKeyEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(jwtKeyRepository.markAllActiveAsRotating()).thenReturn(Mono.error(new RuntimeException("db error")));

            StepVerifier.create(service.rotateKey("SCHEDULED", 1L, "admin"))
                    .expectError(RuntimeException.class)
                    .verify();

            verify(auditService).logKeyRotationFailure(eq("SCHEDULED"), eq("admin"), anyString());
        }
    }

    @Nested
    @DisplayName("revokeKey() 方法测试")
    class RevokeKeyTest {

        @Test
        @DisplayName("密钥版本不存在")
        void testRevokeKey_NotFound() {
            when(jwtKeyRepository.findByKeyVersion("v-missing")).thenReturn(Mono.empty());

            StepVerifier.create(service.revokeKey("v-missing", "LEAKED", 1L, "admin"))
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("密钥版本不存在"))
                    .verify();
        }

        @Test
        @DisplayName("吊销非ACTIVE密钥不触发轮换")
        void testRevokeKey_NonActive_NoRotation() {
            JwtKeyEntity key = buildKey("v1", "DEPRECATED");
            when(jwtKeyRepository.findByKeyVersion("v1")).thenReturn(Mono.just(key));
            when(jwtKeyRepository.save(any(JwtKeyEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            StepVerifier.create(service.revokeKey("v1", "LEAKED", 1L, "admin"))
                    .assertNext(revoked -> org.assertj.core.api.Assertions.assertThat(revoked.getStatus())
                            .isEqualTo("REVOKED"))
                    .verifyComplete();

            verify(jwtKeyRepository, never()).markAllActiveAsRotating();
            verify(auditService).logKeyRevocation(eq("v1"), eq("LEAKED"), eq("admin"));
        }

        @Test
        @DisplayName("吊销密钥后返回吊销后的实体（状态已置为REVOKED）")
        void testRevokeKey_ReturnsRevokedEntity() {
            JwtKeyEntity key = buildKey("v1", "ACTIVE");
            when(jwtKeyRepository.findByKeyVersion("v1")).thenReturn(Mono.just(key));
            when(jwtKeyRepository.save(any(JwtKeyEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            StepVerifier.create(service.revokeKey("v1", "LEAKED", 1L, "admin"))
                    .assertNext(revoked -> org.assertj.core.api.Assertions.assertThat(revoked.getStatus())
                            .isEqualTo("REVOKED"))
                    .verifyComplete();

            verify(jwtKeyRepository, never()).markAllActiveAsRotating();
        }
    }

    @Nested
    @DisplayName("deprecateExpiredRotatingKeys() 方法测试")
    class DeprecateExpiredRotatingKeysTest {

        @Test
        @DisplayName("废弃超过24小时的ROTATING密钥")
        void testDeprecate_Success() {
            JwtKeyEntity rotatingKey = buildKey("v1", "ROTATING");
            rotatingKey.setActivatedAt(LocalDateTime.now().minusHours(25));

            JwtKeyEntity recentRotatingKey = buildKey("v2", "ROTATING");
            recentRotatingKey.setActivatedAt(LocalDateTime.now().minusHours(1));

            JwtKeyEntity activeKey = buildKey("v3", "ACTIVE");

            when(jwtKeyRepository.findUsableKeys()).thenReturn(Flux.just(rotatingKey, recentRotatingKey, activeKey));
            when(jwtKeyRepository.save(any(JwtKeyEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            StepVerifier.create(service.deprecateExpiredRotatingKeys())
                    .expectNext(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("没有过期的ROTATING密钥")
        void testDeprecate_NoneExpired() {
            when(jwtKeyRepository.findUsableKeys()).thenReturn(Flux.empty());

            StepVerifier.create(service.deprecateExpiredRotatingKeys())
                    .expectNext(0)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getActiveKeySecret() 方法测试")
    class GetActiveKeySecretTest {

        @Test
        @DisplayName("成功获取活跃密钥明文")
        void testGetActiveKeySecret_Success() {
            JwtKeyEntity key = buildKey("v1", "ACTIVE");
            when(jwtKeyRepository.findActiveKey()).thenReturn(Mono.just(key));
            when(encryptionService.decrypt(anyString())).thenReturn("plain-secret");

            StepVerifier.create(service.getActiveKeySecret())
                    .expectNext("plain-secret")
                    .verifyComplete();
        }

        @Test
        @DisplayName("没有活跃密钥时报错")
        void testGetActiveKeySecret_NotFound() {
            when(jwtKeyRepository.findActiveKey()).thenReturn(Mono.empty());

            StepVerifier.create(service.getActiveKeySecret())
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("未找到活跃的JWT密钥"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("getUsableKeySecrets() 方法测试")
    class GetUsableKeySecretsTest {

        @Test
        @DisplayName("返回全部可用密钥明文")
        void testGetUsableKeySecrets_Success() {
            JwtKeyEntity key1 = buildKey("v1", "ACTIVE");
            JwtKeyEntity key2 = buildKey("v2", "ROTATING");
            when(jwtKeyRepository.findUsableKeys()).thenReturn(Flux.just(key1, key2));
            when(encryptionService.decrypt(anyString())).thenReturn("secret1", "secret2");

            StepVerifier.create(service.getUsableKeySecrets())
                    .expectNext("secret1", "secret2")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("cleanupRevokedKeys() 方法测试")
    class CleanupRevokedKeysTest {

        @Test
        @DisplayName("成功清理过期的已吊销密钥")
        void testCleanupRevokedKeys_Success() {
            when(jwtKeyRepository.deleteRevokedKeysOlderThan(365)).thenReturn(Mono.just(3));

            StepVerifier.create(service.cleanupRevokedKeys()).expectNext(3).verifyComplete();
        }
    }

    @Nested
    @DisplayName("initializeKeyIfNeeded() 方法测试")
    class InitializeKeyIfNeededTest {

        @Test
        @DisplayName("已存在活跃密钥时直接返回")
        void testInitialize_ExistingActiveKey() {
            JwtKeyEntity key = buildKey("v1", "ACTIVE");
            when(jwtKeyRepository.findActiveKey()).thenReturn(Mono.just(key));

            StepVerifier.create(service.initializeKeyIfNeeded()).expectNext(key).verifyComplete();

            verify(jwtKeyRepository, never()).save(any());
        }

        @Test
        @DisplayName("没有活跃密钥时自动生成并激活")
        void testInitialize_NoActiveKey_GeneratesAndActivates() {
            when(jwtKeyRepository.findActiveKey()).thenReturn(Mono.empty());
            when(encryptionService.encryptForStorage(anyString())).thenReturn("encrypted-secret");
            when(jwtKeyRepository.save(any(JwtKeyEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            StepVerifier.create(service.initializeKeyIfNeeded())
                    .assertNext(key -> org.assertj.core.api.Assertions.assertThat(key.getStatus())
                            .isEqualTo("ACTIVE"))
                    .verifyComplete();

            verify(auditService).logKeyGeneration(anyString(), eq("SYSTEM_INITIALIZATION"), eq("SYSTEM"));
        }
    }

    @Nested
    @DisplayName("findExpiringSoonKeys() 方法测试")
    class FindExpiringSoonKeysTest {

        @Test
        @DisplayName("返回即将过期密钥列表")
        void testFindExpiringSoonKeys() {
            JwtKeyEntity key = buildKey("v1", "ACTIVE");
            when(jwtKeyRepository.findExpiringSoonKeys()).thenReturn(Flux.just(key));

            StepVerifier.create(service.findExpiringSoonKeys()).expectNext(key).verifyComplete();
        }
    }
}
