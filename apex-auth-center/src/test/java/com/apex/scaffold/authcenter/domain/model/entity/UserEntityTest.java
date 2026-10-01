package com.apex.scaffold.authcenter.domain.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 用户实体单元测试
 *
 * <p>测试用户锁定逻辑、登录失败记录和状态管理
 *
 * @author Apex Team
 * @since 2026-09-20
 */
@DisplayName("用户实体测试")
class UserEntityTest {

    @Nested
    @DisplayName("锁定逻辑测试")
    class LockingLogicTest {

        @Test
        @DisplayName("时间未到期且状态为锁定时应返回锁定")
        void shouldBeLockedWhenTimeNotExpiredAndStatusLocked() {
            UserEntity user = UserEntity.builder()
                    .status(2)
                    .lockedUntil(LocalDateTime.now().plusMinutes(10))
                    .build();

            assertThat(user.isLocked()).isTrue();
        }

        @Test
        @DisplayName("时间已到期但状态为锁定时应返回锁定")
        void shouldBeUnlockedWhenTimedLockExpired() {
            UserEntity user = UserEntity.builder()
                    .status(2)
                    .lockedUntil(LocalDateTime.now().minusMinutes(10))
                    .build();

            assertThat(user.isLocked()).isFalse();
            assertThat(user.isActive()).isTrue();
        }

        @Test
        @DisplayName("时间未到期但状态正常时应返回锁定")
        void shouldBeLockedWhenTimeNotExpiredButStatusActive() {
            UserEntity user = UserEntity.builder()
                    .status(1)
                    .lockedUntil(LocalDateTime.now().plusMinutes(10))
                    .build();

            assertThat(user.isLocked()).isTrue();
        }

        @Test
        @DisplayName("时间已到期且状态正常时应返回未锁定")
        void shouldNotBeLockedWhenTimeExpiredAndStatusActive() {
            UserEntity user = UserEntity.builder()
                    .status(1)
                    .lockedUntil(LocalDateTime.now().minusMinutes(10))
                    .build();

            assertThat(user.isLocked()).isFalse();
        }

        @Test
        @DisplayName("无锁定时间且状态正常时应返回未锁定")
        void shouldNotBeLockedWhenNoTimeAndStatusActive() {
            UserEntity user = UserEntity.builder().status(1).lockedUntil(null).build();

            assertThat(user.isLocked()).isFalse();
        }

        @Test
        @DisplayName("无锁定时间但状态为锁定时应返回锁定")
        void shouldBeLockedWhenNoTimeButStatusLocked() {
            UserEntity user = UserEntity.builder().status(2).lockedUntil(null).build();

            assertThat(user.isLocked()).isTrue();
        }
    }

    @Nested
    @DisplayName("登录失败记录测试")
    class LoginFailureTest {

        @Test
        @DisplayName("达到5次失败后应自动锁定账户")
        void shouldLockAccountAfter5Failures() {
            UserEntity user =
                    UserEntity.builder().status(1).failedLoginAttempts(4).build();

            user.recordLoginFailure();

            assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
            assertThat(user.getStatus()).isEqualTo(2);
            assertThat(user.getLockedUntil()).isNotNull();
            assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(29));
        }

        @Test
        @DisplayName("未达到阈值时不应锁定账户")
        void shouldNotLockAccountBeforeThreshold() {
            UserEntity user =
                    UserEntity.builder().status(1).failedLoginAttempts(3).build();

            user.recordLoginFailure();

            assertThat(user.getFailedLoginAttempts()).isEqualTo(4);
            assertThat(user.getStatus()).isEqualTo(1);
            assertThat(user.getLockedUntil()).isNull();
        }

        @Test
        @DisplayName("失败次数为空时应从0开始计数")
        void shouldStartCountingFromZeroWhenNull() {
            UserEntity user =
                    UserEntity.builder().status(1).failedLoginAttempts(null).build();

            user.recordLoginFailure();

            assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("登录成功记录测试")
    class LoginSuccessTest {

        @Test
        @DisplayName("登录成功应重置失败次数和锁定时间")
        void shouldResetFailureCountAndLockTime() {
            UserEntity user = UserEntity.builder()
                    .status(1)
                    .failedLoginAttempts(3)
                    .lockedUntil(LocalDateTime.now().plusMinutes(10))
                    .build();

            user.recordLoginSuccess("192.168.1.1");

            assertThat(user.getFailedLoginAttempts()).isEqualTo(0);
            assertThat(user.getLockedUntil()).isNull();
            assertThat(user.getLastLoginIp()).isEqualTo("192.168.1.1");
            assertThat(user.getLastLoginAt()).isNotNull();
        }

        @Test
        @DisplayName("登录成功应将锁定状态重置为正常")
        void shouldResetLockedStatusToActive() {
            UserEntity user = UserEntity.builder()
                    .status(2)
                    .failedLoginAttempts(5)
                    .lockedUntil(LocalDateTime.now().plusMinutes(10))
                    .build();

            user.recordLoginSuccess("192.168.1.1");

            assertThat(user.getStatus()).isEqualTo(1);
            assertThat(user.getFailedLoginAttempts()).isEqualTo(0);
            assertThat(user.getLockedUntil()).isNull();
        }

        @Test
        @DisplayName("禁用状态登录成功不应改变状态")
        void shouldNotChangeDisabledStatus() {
            UserEntity user =
                    UserEntity.builder().status(0).failedLoginAttempts(3).build();

            user.recordLoginSuccess("192.168.1.1");

            assertThat(user.getStatus()).isEqualTo(0);
            assertThat(user.getFailedLoginAttempts()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("账户状态管理测试")
    class AccountStatusTest {

        @Test
        @DisplayName("状态为1时应为活跃账户")
        void shouldBeActiveWhenStatusIs1() {
            UserEntity user = UserEntity.builder().status(1).build();

            assertThat(user.isActive()).isTrue();
        }

        @Test
        @DisplayName("状态为0时应为非活跃账户")
        void shouldNotBeActiveWhenStatusIs0() {
            UserEntity user = UserEntity.builder().status(0).build();

            assertThat(user.isActive()).isFalse();
        }

        @Test
        @DisplayName("状态为2时应为非活跃账户")
        void shouldNotBeActiveWhenStatusIs2() {
            UserEntity user = UserEntity.builder().status(2).build();

            assertThat(user.isActive()).isFalse();
        }

        @Test
        @DisplayName("解锁操作应重置所有锁定相关字段")
        void unlockShouldResetAllLockFields() {
            UserEntity user = UserEntity.builder()
                    .status(2)
                    .failedLoginAttempts(5)
                    .lockedUntil(LocalDateTime.now().plusMinutes(30))
                    .build();

            user.unlock();

            assertThat(user.getStatus()).isEqualTo(1);
            assertThat(user.getFailedLoginAttempts()).isEqualTo(0);
            assertThat(user.getLockedUntil()).isNull();
        }
    }
}
