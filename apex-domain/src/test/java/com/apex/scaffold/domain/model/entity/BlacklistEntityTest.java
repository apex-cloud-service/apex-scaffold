package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link BlacklistEntity} 的单元测试类
 *
 * <p>测试黑名单的创建、有效性判断、解除拉黑、重新拉黑等核心领域行为
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class BlacklistEntityTest {

    @Nested
    @DisplayName("静态工厂方法 create()")
    class CreateTest {

        /**
         * 执行 {@code withValidParamsShouldCreateBlacklist} 操作。
         */
        @Test
        @DisplayName("使用有效参数应创建有效拉黑记录并设置正确初始状态")
        void withValidParamsShouldCreateBlacklist() {
            Long userId = 1L;
            Long targetUserId = 100L;

            BlacklistEntity blacklist = BlacklistEntity.create(userId, targetUserId);

            assertThat(blacklist.getUserId()).isEqualTo(userId);
            assertThat(blacklist.getTargetUserId()).isEqualTo(targetUserId);
            assertThat(blacklist.getEnabled()).isEqualTo(1);
            assertThat(blacklist.isEnabled()).isTrue();
            assertThat(blacklist.getCreateTime()).isNotNull();
            assertThat(blacklist.getUpdateTime()).isNotNull();
            assertThat(blacklist.isDeleted()).isFalse();
        }

        /**
         * 创建{@code timeShouldEqualUpdateTime}。
         */
        @Test
        @DisplayName("创建时间应与更新时间一致")
        void createTimeShouldEqualUpdateTime() {
            BlacklistEntity blacklist = BlacklistEntity.create(1L, 100L);
            assertThat(blacklist.getCreateTime()).isEqualTo(blacklist.getUpdateTime());
        }
    }

    @Nested
    @DisplayName("有效性判断 isEnabled()")
    class IsEnabledTest {

        /**
         * 执行 {@code newlyCreatedBlacklistShouldBeEnabled} 操作。
         */
        @Test
        @DisplayName("新创建的拉黑记录应处于有效状态")
        void newlyCreatedBlacklistShouldBeEnabled() {
            BlacklistEntity blacklist = BlacklistEntity.create(1L, 100L);
            assertThat(blacklist.isEnabled()).isTrue();
        }

        /**
         * 执行 {@code afterUnblockShouldBeDisabled} 操作。
         */
        @Test
        @DisplayName("解除后应返回 false")
        void afterUnblockShouldBeDisabled() {
            BlacklistEntity blacklist = BlacklistEntity.create(1L, 100L);
            blacklist.unblock();
            assertThat(blacklist.isEnabled()).isFalse();
        }

        /**
         * 执行 {@code afterReblockShouldBeEnabled} 操作。
         */
        @Test
        @DisplayName("重新拉黑后应返回 true")
        void afterReblockShouldBeEnabled() {
            BlacklistEntity blacklist = BlacklistEntity.create(1L, 100L);
            blacklist.unblock();
            blacklist.reblock();
            assertThat(blacklist.isEnabled()).isTrue();
        }

        /**
         * 执行 {@code nullEnabledShouldReturnFalse} 操作。
         */
        @Test
        @DisplayName("enabled 为 null 时应返回 false")
        void nullEnabledShouldReturnFalse() {
            BlacklistEntity blacklist = new BlacklistEntity();
            assertThat(blacklist.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("解除拉黑 unblock()")
    class UnblockTest {

        /**
         * 执行 {@code shouldSetEnabledToZero} 操作。
         */
        @Test
        @DisplayName("应将 enabled 设为 0")
        void shouldSetEnabledToZero() {
            BlacklistEntity blacklist = BlacklistEntity.create(1L, 100L);
            blacklist.unblock();
            assertThat(blacklist.getEnabled()).isEqualTo(0);
            assertThat(blacklist.isEnabled()).isFalse();
        }

        /**
         * 执行 {@code unblockShouldTouchUpdateTime} 操作。
         */
        @Test
        @DisplayName("取消拉黑应刷新更新时间")
        void unblockShouldTouchUpdateTime() {
            BlacklistEntity blacklist = BlacklistEntity.create(1L, 100L);
            var originalUpdateTime = blacklist.getUpdateTime();
            blacklist.unblock();
            assertThat(blacklist.getUpdateTime()).isAfterOrEqualTo(originalUpdateTime);
        }
    }

    @Nested
    @DisplayName("重新拉黑 reblock()")
    class ReblockTest {

        /**
         * 执行 {@code shouldRestoreEnabledToOne} 操作。
         */
        @Test
        @DisplayName("应将 enabled 恢复为 1")
        void shouldRestoreEnabledToOne() {
            BlacklistEntity blacklist = BlacklistEntity.create(1L, 100L);
            blacklist.unblock();
            blacklist.reblock();
            assertThat(blacklist.getEnabled()).isEqualTo(1);
            assertThat(blacklist.isEnabled()).isTrue();
        }

        /**
         * 执行 {@code shouldTouchUpdateTime} 操作。
         */
        @Test
        @DisplayName("应刷新更新时间")
        void shouldTouchUpdateTime() {
            BlacklistEntity blacklist = BlacklistEntity.create(1L, 100L);
            blacklist.unblock();
            var originalUpdateTime = blacklist.getUpdateTime();
            blacklist.reblock();
            assertThat(blacklist.getUpdateTime()).isAfterOrEqualTo(originalUpdateTime);
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
            BlacklistEntity blacklist = new BlacklistEntity();
            blacklist.setUserId(50L);
            blacklist.setTargetUserId(60L);
            blacklist.setEnabled(1);

            assertThat(blacklist.getUserId()).isEqualTo(50L);
            assertThat(blacklist.getTargetUserId()).isEqualTo(60L);
            assertThat(blacklist.getEnabled()).isEqualTo(1);
        }
    }
}
