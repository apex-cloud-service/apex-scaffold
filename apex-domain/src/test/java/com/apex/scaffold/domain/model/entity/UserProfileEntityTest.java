package com.apex.scaffold.domain.model.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link UserProfileEntity} 的单元测试类
 *
 * <p>测试用户画像创建、Bio/交友目的/MBTI 更新、社交用户状态变更等核心领域行为
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class UserProfileEntityTest {

    @Nested
    @DisplayName("静态工厂方法 create()")
    class CreateTest {

        /**
         * 执行 {@code shouldSetUserIdAndDefaultNormal} 操作。
         */
        @Test
        @DisplayName("创建时应正确设置 userId 并默认非社交用户")
        void shouldSetUserIdAndDefaultNormal() {
            Long userId = 1L;

            UserProfileEntity profile = UserProfileEntity.create(userId);

            assertEquals(userId, profile.getUserId());
            assertFalse(profile.isSocialUser());
            assertFalse(profile.isDeleted());
            assertNotNull(profile.getCreateTime());
            assertNotNull(profile.getUpdateTime());
        }

        /**
         * 执行 {@code nullUserIdShouldThrowException} 操作。
         */
        @Test
        @DisplayName("userId 为 null 时应抛出异常")
        void nullUserIdShouldThrowException() {
            assertThrows(IllegalArgumentException.class, () -> UserProfileEntity.create(null));
        }

        /**
         * 创建{@code timeShouldEqualUpdateTime}。
         */
        @Test
        @DisplayName("创建时间应与更新时间一致")
        void createTimeShouldEqualUpdateTime() {
            UserProfileEntity profile = UserProfileEntity.create(1L);
            assertEquals(profile.getCreateTime(), profile.getUpdateTime());
        }
    }

    @Nested
    @DisplayName("updateBio()")
    class UpdateBioTest {

        /**
         * 执行 {@code shouldUpdateBioAndTouch} 操作。
         */
        @Test
        @DisplayName("应更新个人简介并刷新更新时间")
        void shouldUpdateBioAndTouch() {
            UserProfileEntity profile = UserProfileEntity.create(1L);
            profile.updateBio("Hello, I'm a test user!");
            assertEquals("Hello, I'm a test user!", profile.getBio());
            assertNotNull(profile.getUpdateTime());
        }

        /**
         * 执行 {@code shouldSupportNullBio} 操作。
         */
        @Test
        @DisplayName("支持更新为 null")
        void shouldSupportNullBio() {
            UserProfileEntity profile = UserProfileEntity.create(1L);
            profile.updateBio("old bio");
            profile.updateBio(null);
            assertNull(profile.getBio());
        }

        /**
         * 执行 {@code shouldSupportEmptyBio} 操作。
         */
        @Test
        @DisplayName("支持更新为空字符串")
        void shouldSupportEmptyBio() {
            UserProfileEntity profile = UserProfileEntity.create(1L);
            profile.updateBio("");
            assertEquals("", profile.getBio());
        }
    }

    @Nested
    @DisplayName("updateDatingGoal()")
    class UpdateDatingGoalTest {

        /**
         * 执行 {@code shouldUpdateDatingGoalAndTouch} 操作。
         */
        @Test
        @DisplayName("应更新交友目的并刷新更新时间")
        void shouldUpdateDatingGoalAndTouch() {
            UserProfileEntity profile = UserProfileEntity.create(1L);
            profile.updateDatingGoal("寻找灵魂伴侣");
            assertEquals("寻找灵魂伴侣", profile.getDatingGoal());
        }
    }

    @Nested
    @DisplayName("updateMbti()")
    class UpdateMbtiTest {

        /**
         * 执行 {@code shouldUpdateMbtiAndTouch} 操作。
         */
        @Test
        @DisplayName("应更新 MBTI 并刷新更新时间")
        void shouldUpdateMbtiAndTouch() {
            UserProfileEntity profile = UserProfileEntity.create(1L);
            profile.updateMbti("INTJ");
            assertEquals("INTJ", profile.getMbti());
        }
    }

    @Nested
    @DisplayName("社交用户状态")
    class SocialUserTest {

        /**
         * 执行 {@code defaultUserShouldNotBeSocial} 操作。
         */
        @Test
        @DisplayName("默认用户不应是社交用户")
        void defaultUserShouldNotBeSocial() {
            UserProfileEntity profile = UserProfileEntity.create(1L);
            assertFalse(profile.isSocialUser());
        }

        /**
         * 执行 {@code upgradeToSocialUserShouldUpgradeAndRecordTime} 操作。
         */
        @Test
        @DisplayName("upgradeToSocialUser() 应升级为社交用户并记录时间")
        void upgradeToSocialUserShouldUpgradeAndRecordTime() {
            UserProfileEntity profile = UserProfileEntity.create(1L);
            profile.upgradeToSocialUser();
            assertTrue(profile.isSocialUser());
            assertNotNull(profile.getSocialUserAt());
        }

        /**
         * 执行 {@code downgradeToNormalUserShouldDowngrade} 操作。
         */
        @Test
        @DisplayName("downgradeToNormalUser() 应降级为普通用户")
        void downgradeToNormalUserShouldDowngrade() {
            UserProfileEntity profile = UserProfileEntity.create(1L);
            profile.upgradeToSocialUser();
            assertTrue(profile.isSocialUser());

            profile.downgradeToNormalUser();
            assertFalse(profile.isSocialUser());
        }

        /**
         * 执行 {@code repeatedDowngradeShouldStayNormal} 操作。
         */
        @Test
        @DisplayName("重复降级应保持非社交用户")
        void repeatedDowngradeShouldStayNormal() {
            UserProfileEntity profile = UserProfileEntity.create(1L);
            profile.downgradeToNormalUser();
            assertFalse(profile.isSocialUser());
        }

        /**
         * 执行 {@code nullSocialUserShouldReturnFalse} 操作。
         */
        @Test
        @DisplayName("socialUser 为 null 时 isSocialUser 应返回 false")
        void nullSocialUserShouldReturnFalse() {
            UserProfileEntity profile = UserProfileEntity.create(1L);
            profile.setSocialUser(null);
            assertFalse(profile.isSocialUser());
        }
    }
}
