package com.apex.scaffold.auth.domain.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Token实体单元测试
 *
 * <p>测试Token过期检测、撤销管理和有效性验证
 *
 * @author Apex Team
 * @since 2026-09-20
 */
@DisplayName("Token实体测试")
class TokenEntityTest {

    @Nested
    @DisplayName("过期检测测试")
    class ExpirationTest {

        @Test
        @DisplayName("过期时间在未来时不应过期")
        void shouldNotBeExpiredWhenExpiresInFuture() {
            TokenEntity token = TokenEntity.builder()
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();

            assertThat(token.isExpired()).isFalse();
        }

        @Test
        @DisplayName("过期时间在过去时应过期")
        void shouldBeExpiredWhenExpiresInPast() {
            TokenEntity token = TokenEntity.builder()
                    .expiresAt(LocalDateTime.now().minusHours(1))
                    .build();

            assertThat(token.isExpired()).isTrue();
        }

        @Test
        @DisplayName("无过期时间时不应过期")
        void shouldNotBeExpiredWhenNoExpiresAt() {
            TokenEntity token = TokenEntity.builder().expiresAt(null).build();

            assertThat(token.isExpired()).isFalse();
        }
    }

    @Nested
    @DisplayName("撤销状态测试")
    class RevocationTest {

        @Test
        @DisplayName("revoked为1时应为已撤销")
        void shouldBeRevokedWhenRevokedIs1() {
            TokenEntity token = TokenEntity.builder().revoked(1).build();

            assertThat(token.isRevoked()).isTrue();
        }

        @Test
        @DisplayName("revoked为0时应为未撤销")
        void shouldNotBeRevokedWhenRevokedIs0() {
            TokenEntity token = TokenEntity.builder().revoked(0).build();

            assertThat(token.isRevoked()).isFalse();
        }

        @Test
        @DisplayName("revoked为null时应为未撤销")
        void shouldNotBeRevokedWhenRevokedIsNull() {
            TokenEntity token = TokenEntity.builder().revoked(null).build();

            assertThat(token.isRevoked()).isFalse();
        }

        @Test
        @DisplayName("调用revoke应设置撤销标记和时间")
        void revokeShouldSetRevokedFlagAndTime() {
            TokenEntity token = TokenEntity.builder().revoked(0).build();

            token.revoke();

            assertThat(token.getRevoked()).isEqualTo(1);
            assertThat(token.getRevokedAt()).isNotNull();
            assertThat(token.getRevokedAt()).isBeforeOrEqualTo(LocalDateTime.now());
        }
    }

    @Nested
    @DisplayName("有效性验证测试")
    class ValidationTest {

        @Test
        @DisplayName("未过期且未撤销时应有效")
        void shouldBeValidWhenNotExpiredAndNotRevoked() {
            TokenEntity token = TokenEntity.builder()
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .revoked(0)
                    .build();

            assertThat(token.isValid()).isTrue();
        }

        @Test
        @DisplayName("已过期时应无效")
        void shouldBeInvalidWhenExpired() {
            TokenEntity token = TokenEntity.builder()
                    .expiresAt(LocalDateTime.now().minusHours(1))
                    .revoked(0)
                    .build();

            assertThat(token.isValid()).isFalse();
        }

        @Test
        @DisplayName("已撤销时应无效")
        void shouldBeInvalidWhenRevoked() {
            TokenEntity token = TokenEntity.builder()
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .revoked(1)
                    .build();

            assertThat(token.isValid()).isFalse();
        }

        @Test
        @DisplayName("既过期又撤销时应无效")
        void shouldBeInvalidWhenBothExpiredAndRevoked() {
            TokenEntity token = TokenEntity.builder()
                    .expiresAt(LocalDateTime.now().minusHours(1))
                    .revoked(1)
                    .build();

            assertThat(token.isValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("Token不可变性测试")
    class ImmutabilityTest {

        @Test
        @DisplayName("撤销后Token应保持撤销状态")
        void shouldRemainRevokedAfterRevoke() {
            TokenEntity token = TokenEntity.builder().revoked(0).revokedAt(null).build();

            token.revoke();

            assertThat(token.getRevoked()).isEqualTo(1);
            assertThat(token.getRevokedAt()).isNotNull();

            token.revoke();

            assertThat(token.getRevoked()).isEqualTo(1);
            assertThat(token.getRevokedAt()).isNotNull();
        }
    }
}
