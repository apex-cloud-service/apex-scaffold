package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link SubscriptionEntity} 的单元测试类
 *
 * <p>测试订阅的创建（VIP/SVIP）、续订、取消、有效性/过期判断、标记过期、升级等核心领域行为
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class SubscriptionEntityTest {

    @Nested
    @DisplayName("静态工厂方法 createVipSubscription()")
    class CreateVipSubscriptionTest {
        @Test
        @DisplayName("使用有效参数应创建 VIP 订阅并设置正确初始状态")
        void withValidParamsShouldCreateVipSubscription() {
            Long userId = 1L;
            int durationDays = 30;

            SubscriptionEntity subscription = SubscriptionEntity.createVipSubscription(userId, durationDays);

            assertThat(subscription.getUserId()).isEqualTo(userId);
            assertThat(subscription.getLevel()).isEqualTo(SubscriptionEntity.SubscriptionLevel.VIP.getCode());
            assertThat(subscription.getStartAt()).isNotNull();
            assertThat(subscription.getEndAt()).isNotNull();
            assertThat(subscription.getStatus()).isEqualTo(SubscriptionEntity.SubscriptionStatus.ACTIVE.getCode());
            assertThat(subscription.isActive()).isTrue();
            assertThat(subscription.isExpired()).isFalse();
            assertThat(subscription.getCreateTime()).isNotNull();
            assertThat(subscription.getUpdateTime()).isNotNull();
            assertThat(subscription.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("创建时间应与更新时间一致")
        void createTimeShouldEqualUpdateTime() {
            SubscriptionEntity subscription = SubscriptionEntity.createVipSubscription(1L, 30);
            assertThat(subscription.getCreateTime()).isEqualTo(subscription.getUpdateTime());
        }
    }

    @Nested
    @DisplayName("静态工厂方法 createSvipSubscription()")
    class CreateSvipSubscriptionTest {
        @Test
        @DisplayName("使用有效参数应创建 SVIP 订阅并设置正确初始状态")
        void withValidParamsShouldCreateSvipSubscription() {
            Long userId = 1L;
            int durationDays = 365;

            SubscriptionEntity subscription = SubscriptionEntity.createSvipSubscription(userId, durationDays);

            assertThat(subscription.getUserId()).isEqualTo(userId);
            assertThat(subscription.getLevel()).isEqualTo(SubscriptionEntity.SubscriptionLevel.SVIP.getCode());
            assertThat(subscription.getStartAt()).isNotNull();
            assertThat(subscription.getEndAt()).isNotNull();
            assertThat(subscription.getStatus()).isEqualTo(SubscriptionEntity.SubscriptionStatus.ACTIVE.getCode());
            assertThat(subscription.isActive()).isTrue();
            assertThat(subscription.isExpired()).isFalse();
            assertThat(subscription.getCreateTime()).isNotNull();
            assertThat(subscription.getUpdateTime()).isNotNull();
        }
    }

    @Nested
    @DisplayName("续订 renew()")
    class RenewTest {
        @Test
        @DisplayName("有效期内续订应延长结束时间")
        void renewDuringActiveShouldExtendEndAt() {
            SubscriptionEntity subscription = SubscriptionEntity.createVipSubscription(1L, 30);
            var originalEndAt = subscription.getEndAt();

            subscription.renew(30);

            assertThat(subscription.getEndAt()).isAfter(originalEndAt);
            assertThat(subscription.getStatus()).isEqualTo(SubscriptionEntity.SubscriptionStatus.ACTIVE.getCode());
            assertThat(subscription.isActive()).isTrue();
        }

        @Test
        @DisplayName("已过期后续订应重新设置开始和结束时间")
        void renewAfterExpiredShouldResetStartAndEndAt() {
            SubscriptionEntity subscription = SubscriptionEntity.createVipSubscription(1L, 30);
            subscription.setEndAt(LocalDateTime.now().minusDays(1));
            var originalStartAt = subscription.getStartAt();

            subscription.renew(30);

            assertThat(subscription.getStartAt()).isAfterOrEqualTo(originalStartAt);
            assertThat(subscription.getEndAt()).isAfter(subscription.getStartAt());
            assertThat(subscription.getStatus()).isEqualTo(SubscriptionEntity.SubscriptionStatus.ACTIVE.getCode());
        }

        @Test
        @DisplayName("应刷新更新时间")
        void shouldTouchUpdateTime() {
            SubscriptionEntity subscription = SubscriptionEntity.createVipSubscription(1L, 30);
            var originalUpdateTime = subscription.getUpdateTime();
            subscription.renew(7);
            assertThat(subscription.getUpdateTime()).isAfterOrEqualTo(originalUpdateTime);
        }
    }

    @Nested
    @DisplayName("取消订阅 cancel()")
    class CancelTest {
        @Test
        @DisplayName("应将状态设为 CANCELED")
        void shouldSetStatusToCanceled() {
            SubscriptionEntity subscription = SubscriptionEntity.createVipSubscription(1L, 30);
            subscription.cancel();

            assertThat(subscription.getStatus()).isEqualTo(SubscriptionEntity.SubscriptionStatus.CANCELED.getCode());
            assertThat(subscription.isActive()).isFalse();
        }

        @Test
        @DisplayName("应刷新更新时间")
        void shouldTouchUpdateTime() {
            SubscriptionEntity subscription = SubscriptionEntity.createVipSubscription(1L, 30);
            var originalUpdateTime = subscription.getUpdateTime();
            subscription.cancel();
            assertThat(subscription.getUpdateTime()).isAfterOrEqualTo(originalUpdateTime);
        }
    }

    @Nested
    @DisplayName("有效性判断 isActive()")
    class IsActiveTest {
        @Test
        @DisplayName("ACTIVE 状态且未过期时应返回 true")
        void activeAndNotExpiredShouldBeActive() {
            SubscriptionEntity subscription = SubscriptionEntity.createVipSubscription(1L, 30);
            assertThat(subscription.isActive()).isTrue();
        }

        @Test
        @DisplayName("CANCELED 状态应返回 false")
        void canceledShouldNotBeActive() {
            SubscriptionEntity subscription = SubscriptionEntity.createVipSubscription(1L, 30);
            subscription.cancel();
            assertThat(subscription.isActive()).isFalse();
        }

        @Test
        @DisplayName("EXPIRED 状态应返回 false")
        void expiredShouldNotBeActive() {
            SubscriptionEntity subscription = SubscriptionEntity.createVipSubscription(1L, 30);
            subscription.markAsExpired();
            assertThat(subscription.isActive()).isFalse();
        }

        @Test
        @DisplayName("ACTIVE 但 endAt 已过去应返回 false")
        void activeButEndAtPassedShouldNotBeActive() {
            SubscriptionEntity subscription = SubscriptionEntity.createVipSubscription(1L, 30);
            subscription.setEndAt(LocalDateTime.now().minusDays(1));
            assertThat(subscription.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("过期判断 isExpired()")
    class IsExpiredTest {
        @Test
        @DisplayName("新创建的不应过期")
        void newlyCreatedShouldNotBeExpired() {
            SubscriptionEntity subscription = SubscriptionEntity.createVipSubscription(1L, 30);
            assertThat(subscription.isExpired()).isFalse();
        }

        @Test
        @DisplayName("endAt 为 null 时应返回 false")
        void nullEndAtShouldNotBeExpired() {
            SubscriptionEntity subscription = new SubscriptionEntity();
            assertThat(subscription.isExpired()).isFalse();
        }

        @Test
        @DisplayName("endAt 已过去时应返回 true")
        void pastEndAtShouldBeExpired() {
            SubscriptionEntity subscription = new SubscriptionEntity();
            subscription.setEndAt(LocalDateTime.now().minusMinutes(1));
            assertThat(subscription.isExpired()).isTrue();
        }

        @Test
        @DisplayName("endAt 在未来时应返回 false")
        void futureEndAtShouldNotBeExpired() {
            SubscriptionEntity subscription = new SubscriptionEntity();
            subscription.setEndAt(LocalDateTime.now().plusDays(10));
            assertThat(subscription.isExpired()).isFalse();
        }
    }

    @Nested
    @DisplayName("标记过期 markAsExpired()")
    class MarkAsExpiredTest {
        @Test
        @DisplayName("应将状态设为 EXPIRED")
        void shouldSetStatusToExpired() {
            SubscriptionEntity subscription = SubscriptionEntity.createVipSubscription(1L, 30);
            subscription.markAsExpired();

            assertThat(subscription.getStatus()).isEqualTo(SubscriptionEntity.SubscriptionStatus.EXPIRED.getCode());
            assertThat(subscription.isActive()).isFalse();
        }

        @Test
        @DisplayName("应刷新更新时间")
        void shouldTouchUpdateTime() {
            SubscriptionEntity subscription = SubscriptionEntity.createVipSubscription(1L, 30);
            var originalUpdateTime = subscription.getUpdateTime();
            subscription.markAsExpired();
            assertThat(subscription.getUpdateTime()).isAfterOrEqualTo(originalUpdateTime);
        }
    }

    @Nested
    @DisplayName("升级到 SVIP upgradeToSvip()")
    class UpgradeToSvipTest {
        @Test
        @DisplayName("VIP 升级后 level 应为 SVIP")
        void vipUpgradeShouldSetLevelToSvip() {
            SubscriptionEntity subscription = SubscriptionEntity.createVipSubscription(1L, 30);
            subscription.upgradeToSvip(365);

            assertThat(subscription.getLevel()).isEqualTo(SubscriptionEntity.SubscriptionLevel.SVIP.getCode());
            assertThat(subscription.getStatus()).isEqualTo(SubscriptionEntity.SubscriptionStatus.ACTIVE.getCode());
            assertThat(subscription.isActive()).isTrue();
        }

        @Test
        @DisplayName("升级应延长结束时间")
        void upgradeShouldExtendEndAt() {
            SubscriptionEntity subscription = SubscriptionEntity.createVipSubscription(1L, 30);
            var originalEndAt = subscription.getEndAt();
            subscription.upgradeToSvip(365);

            assertThat(subscription.getEndAt()).isAfter(originalEndAt);
        }
    }

    @Nested
    @DisplayName("setter 方法")
    class SetterTest {

        /**
         * 设置{@code tersShouldUpdateFields}。
         */
        @Test
        @DisplayName("使用 setter 设置属性应正确更新")
        void settersShouldUpdateFields() {
            SubscriptionEntity subscription = new SubscriptionEntity();
            subscription.setUserId(99L);
            subscription.setLevel(SubscriptionEntity.SubscriptionLevel.SVIP.getCode());
            LocalDateTime now = LocalDateTime.now();
            subscription.setStartAt(now);
            subscription.setEndAt(now.plusDays(30));
            subscription.setStatus(SubscriptionEntity.SubscriptionStatus.ACTIVE.getCode());

            assertThat(subscription.getUserId()).isEqualTo(99L);
            assertThat(subscription.getLevel()).isEqualTo(SubscriptionEntity.SubscriptionLevel.SVIP.getCode());
            assertThat(subscription.getStartAt()).isEqualTo(now);
            assertThat(subscription.getEndAt()).isEqualTo(now.plusDays(30));
            assertThat(subscription.getStatus()).isEqualTo(SubscriptionEntity.SubscriptionStatus.ACTIVE.getCode());
        }
    }

    @Nested
    @DisplayName("SubscriptionLevel 枚举")
    class SubscriptionLevelEnumTest {
        @Test
        @DisplayName("VIP 的 code 应为 VIP")
        void vipCodeShouldBeVip() {
            assertThat(SubscriptionEntity.SubscriptionLevel.VIP.getCode()).isEqualTo("VIP");
        }

        @Test
        @DisplayName("SVIP 的 code 应为 SVIP")
        void svipCodeShouldBeSvip() {
            assertThat(SubscriptionEntity.SubscriptionLevel.SVIP.getCode()).isEqualTo("SVIP");
        }
    }

    @Nested
    @DisplayName("SubscriptionStatus 枚举")
    class SubscriptionStatusEnumTest {
        @Test
        @DisplayName("ACTIVE 的 code 应为 ACTIVE")
        void activeCodeShouldBeActive() {
            assertThat(SubscriptionEntity.SubscriptionStatus.ACTIVE.getCode()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("EXPIRED 的 code 应为 EXPIRED")
        void expiredCodeShouldBeExpired() {
            assertThat(SubscriptionEntity.SubscriptionStatus.EXPIRED.getCode()).isEqualTo("EXPIRED");
        }

        @Test
        @DisplayName("CANCELED 的 code 应为 CANCELED")
        void canceledCodeShouldBeCanceled() {
            assertThat(SubscriptionEntity.SubscriptionStatus.CANCELED.getCode()).isEqualTo("CANCELED");
        }
    }
}
