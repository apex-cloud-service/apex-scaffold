package com.apex.scaffold.authcenter.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link JwtKeyRotationService} 的单元测试类
 *
 * <p>测试JWT密钥轮换服务的核心逻辑：密钥获取、手动轮换、历史密钥验证、过期清理。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@DisplayName("JwtKeyRotationService（infrastructure.security）单元测试")
class JwtKeyRotationServiceTest {

    private JwtKeyRotationService service;

    @BeforeEach
    void setUp() {
        service = new JwtKeyRotationService();
    }

    @Nested
    @DisplayName("getCurrentSigningKey / getCurrentKeyId 方法")
    class CurrentKeyTests {

        @Test
        @DisplayName("初始化后应存在当前签名密钥和密钥ID")
        void shouldHaveCurrentKeyAfterInit() {
            assertThat(service.getCurrentSigningKey()).isNotNull();
            assertThat(service.getCurrentKeyId()).isNotBlank();
            assertThat(service.getCurrentKeyId()).startsWith("key-");
        }
    }

    @Nested
    @DisplayName("getVerificationKey 方法")
    class GetVerificationKeyTests {

        @Test
        @DisplayName("传入当前密钥ID应返回当前密钥")
        void shouldReturnCurrentKeyWhenIdMatchesCurrent() {
            String currentKeyId = service.getCurrentKeyId();

            assertThat(service.getVerificationKey(currentKeyId)).isSameAs(service.getCurrentSigningKey());
        }

        @Test
        @DisplayName("传入旧密钥ID（轮换后）应返回历史密钥")
        void shouldReturnHistoricalKeyAfterRotation() {
            String oldKeyId = service.getCurrentKeyId();
            byte[] oldKeyBytes = service.getCurrentSigningKey().getEncoded();

            service.rotateKey();

            assertThat(service.getVerificationKey(oldKeyId).getEncoded()).isEqualTo(oldKeyBytes);
            assertThat(service.getCurrentKeyId()).isNotEqualTo(oldKeyId);
        }

        @Test
        @DisplayName("传入未知密钥ID应抛出BusinessException")
        void shouldThrowWhenKeyIdUnknown() {
            assertThatThrownBy(() -> service.getVerificationKey("unknown-key-id"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未知的密钥ID");
        }
    }

    @Nested
    @DisplayName("rotateKey 方法")
    class RotateKeyTests {

        @Test
        @DisplayName("轮换后当前密钥应变化且旧密钥转入历史记录")
        void shouldRotateKeySuccessfully() {
            String oldKeyId = service.getCurrentKeyId();
            byte[] oldKeyBytes = service.getCurrentSigningKey().getEncoded();

            service.rotateKey();

            assertThat(service.getCurrentKeyId()).isNotEqualTo(oldKeyId);
            assertThat(service.getCurrentSigningKey().getEncoded()).isNotEqualTo(oldKeyBytes);
            assertThat(service.getVerificationKey(oldKeyId).getEncoded()).isEqualTo(oldKeyBytes);
        }

        @Test
        @DisplayName("多次轮换应保留多个历史密钥")
        void shouldKeepMultipleHistoricalKeysAfterMultipleRotations() {
            String keyId1 = service.getCurrentKeyId();
            service.rotateKey();
            String keyId2 = service.getCurrentKeyId();
            service.rotateKey();
            String keyId3 = service.getCurrentKeyId();

            assertThat(service.getVerificationKey(keyId1)).isNotNull();
            assertThat(service.getVerificationKey(keyId2)).isNotNull();
            assertThat(service.getCurrentKeyId()).isEqualTo(keyId3);
        }
    }

    @Nested
    @DisplayName("scheduledKeyRotation 方法")
    class ScheduledKeyRotationTests {

        @Test
        @DisplayName("密钥未过期时定时检查不应触发轮换")
        void shouldNotRotateWhenKeyNotExpired() {
            String keyIdBefore = service.getCurrentKeyId();

            service.scheduledKeyRotation();

            assertThat(service.getCurrentKeyId()).isEqualTo(keyIdBefore);
        }
    }

    @Nested
    @DisplayName("cleanupExpiredKeys 方法")
    class CleanupExpiredKeysTests {

        @Test
        @DisplayName("刚生成的历史密钥不应被清理")
        void shouldNotCleanupFreshHistoricalKey() {
            String oldKeyId = service.getCurrentKeyId();
            service.rotateKey();

            service.cleanupExpiredKeys();

            assertThat(service.getVerificationKey(oldKeyId)).isNotNull();
        }
    }

    @Nested
    @DisplayName("getStats 方法")
    class GetStatsTests {

        @Test
        @DisplayName("应返回当前密钥的统计信息")
        void shouldReturnCurrentStats() {
            JwtKeyRotationService.KeyRotationStats stats = service.getStats();

            assertThat(stats.getCurrentKeyId()).isEqualTo(service.getCurrentKeyId());
            assertThat(stats.getCurrentKeyAge()).isEqualTo(0);
            assertThat(stats.getHistoricalKeyCount()).isEqualTo(0);
            assertThat(stats.getNextRotationDays()).isEqualTo(30);
        }

        @Test
        @DisplayName("轮换一次后历史密钥数量应为1")
        void shouldReflectHistoricalKeyCountAfterRotation() {
            service.rotateKey();

            JwtKeyRotationService.KeyRotationStats stats = service.getStats();

            assertThat(stats.getHistoricalKeyCount()).isEqualTo(1);
        }
    }
}
