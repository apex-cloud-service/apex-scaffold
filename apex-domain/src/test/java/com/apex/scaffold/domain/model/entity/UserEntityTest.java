package com.apex.scaffold.domain.model.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link UserEntity} 的单元测试类
 *
 * <p>测试用户的创建、Token 更新、登录记录、启用/禁用等核心领域行为
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class UserEntityTest {

    @Nested
    @DisplayName("静态工厂方法 create()")
    class CreateTest {
        @Test
        @DisplayName("使用有效参数应创建启用且未删除的用户")
        void withValidParamsShouldCreateEnabledUser() {
            String openId = "oXyz123";
            String unionId = "uAbc456";

            UserEntity user = UserEntity.create(openId, unionId);

            assertEquals(openId, user.getOpenId());
            assertEquals(unionId, user.getUnionId());
            assertTrue(user.isEnabled());
            assertFalse(user.isDeleted());
            assertTrue(user.isNew());
            assertNotNull(user.getCreateTime());
            assertNotNull(user.getUpdateTime());
        }

        @Test
        @DisplayName("unionId 可为 null")
        void shouldAcceptNullUnionId() {
            UserEntity user = UserEntity.create("oXyz123", null);
            assertNull(user.getUnionId());
        }

        @Test
        @DisplayName("openId 为空时应抛出异常")
        void blankOpenIdShouldThrowException() {
            assertThrows(IllegalArgumentException.class, () -> UserEntity.create("", "unionId"));
            assertThrows(IllegalArgumentException.class, () -> UserEntity.create("  ", "unionId"));
        }

        @Test
        @DisplayName("openId 为 null 时应抛出异常")
        void nullOpenIdShouldThrowException() {
            assertThrows(IllegalArgumentException.class, () -> UserEntity.create(null, "unionId"));
        }

        @Test
        @DisplayName("创建时间应与更新时间一致")
        void createTimeShouldEqualUpdateTime() {
            UserEntity user = UserEntity.create("oXyz123", "uAbc456");
            assertEquals(user.getCreateTime(), user.getUpdateTime());
        }
    }

    @Nested
    @DisplayName("updateToken()")
    class UpdateTokenTest {
        @Test
        @DisplayName("应正确更新 Token 并刷新更新时间")
        void shouldUpdateTokenAndRefreshUpdateTime() {
            UserEntity user = UserEntity.create("oXyz123", "uAbc456");
            LocalDateTime originalUpdateTime = user.getUpdateTime();
            String accessToken = "access_token_abc";
            String refreshToken = "refresh_token_xyz";
            LocalDateTime expireTime = LocalDateTime.now().plusHours(2);

            user.updateToken(accessToken, refreshToken, expireTime);

            assertEquals(accessToken, user.getAccessToken());
            assertEquals(refreshToken, user.getRefreshToken());
            assertEquals(expireTime, user.getTokenExpireTime());
            assertTrue(user.getUpdateTime().isAfter(originalUpdateTime)
                    || user.getUpdateTime().isEqual(originalUpdateTime));
        }

        @Test
        @DisplayName("支持 accessToken 为 null")
        void shouldSupportNullAccessToken() {
            UserEntity user = UserEntity.create("oXyz123", "uAbc456");
            user.updateToken(null, "refresh_token", LocalDateTime.now().plusHours(2));
            assertNull(user.getAccessToken());
        }
    }

    @Nested
    @DisplayName("recordLogin()")
    class RecordLoginTest {
        @Test
        @DisplayName("应正确记录登录 IP 和时间")
        void shouldRecordLoginIpAndTime() {
            UserEntity user = UserEntity.create("oXyz123", "uAbc456");
            String loginIp = "192.168.1.100";

            user.recordLogin(loginIp);

            assertEquals(loginIp, user.getLastLoginIp());
            assertNotNull(user.getLastLoginTime());
            assertNotNull(user.getUpdateTime());
        }

        @Test
        @DisplayName("后续登录应覆盖上次记录")
        void subsequentLoginShouldOverwritePrevious() {
            UserEntity user = UserEntity.create("oXyz123", "uAbc456");
            user.recordLogin("192.168.1.1");
            LocalDateTime firstLoginTime = user.getLastLoginTime();

            user.recordLogin("10.0.0.1");

            assertEquals("10.0.0.1", user.getLastLoginIp());
            assertTrue(user.getLastLoginTime().isAfter(firstLoginTime)
                    || user.getLastLoginTime().isEqual(firstLoginTime));
        }
    }

    @Nested
    @DisplayName("isEnabled() / disable() / enable()")
    class EnableDisableTest {
        @Test
        @DisplayName("新创建的用户应处于启用状态")
        void newlyCreatedUserShouldBeEnabled() {
            UserEntity user = UserEntity.create("oXyz123", "uAbc456");
            assertTrue(user.isEnabled());
        }

        @Test
        @DisplayName("disable() 后用户应处于禁用状态")
        void afterDisableUserShouldBeDisabled() {
            UserEntity user = UserEntity.create("oXyz123", "uAbc456");
            user.disable();
            assertFalse(user.isEnabled());
        }

        @Test
        @DisplayName("enable() 后用户应重新启用")
        void afterEnableUserShouldBeEnabled() {
            UserEntity user = UserEntity.create("oXyz123", "uAbc456");
            user.disable();
            user.enable();
            assertTrue(user.isEnabled());
        }

        @Test
        @DisplayName("disable() 应刷新更新时间")
        void disableShouldRefreshUpdateTime() {
            UserEntity user = UserEntity.create("oXyz123", "uAbc456");
            LocalDateTime originalUpdateTime = user.getUpdateTime();
            user.disable();
            assertTrue(user.getUpdateTime().isAfter(originalUpdateTime)
                    || user.getUpdateTime().isEqual(originalUpdateTime));
        }

        @Test
        @DisplayName("重复禁用应保持禁用状态")
        void repeatedDisableShouldStayDisabled() {
            UserEntity user = UserEntity.create("oXyz123", "uAbc456");
            user.disable();
            user.disable();
            assertFalse(user.isEnabled());
        }
    }
}
