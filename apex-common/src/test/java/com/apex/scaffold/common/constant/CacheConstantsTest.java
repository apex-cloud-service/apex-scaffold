package com.apex.scaffold.common.constant;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * CacheConstants 缓存常量类测试
 *
 * <p>验证 CacheConstants 类中所有缓存 key 前缀和 TTL 过期时间值
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 */
@DisplayName("CacheConstants 缓存常量测试")
class CacheConstantsTest {
    @Test
    @DisplayName("验证 CacheConstants 是 final 类且构造函数为私有")
    void shouldBeFinalClassWithPrivateConstructor() throws Exception {
        assertTrue(Modifier.isFinal(CacheConstants.class.getModifiers()), "CacheConstants 应为 final 类");

        Constructor<?>[] constructors = CacheConstants.class.getDeclaredConstructors();
        assertEquals(1, constructors.length, "应只有一个构造函数");
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()), "构造函数应为 private");
    }

    @Nested
    @DisplayName("缓存 Key 前缀测试")
    class CacheKeyPrefixTest {
        @Test
        @DisplayName("CACHE_PREFIX 应为 'apex:'")
        void shouldHaveCorrectCachePrefix() {
            assertEquals("apex:", CacheConstants.CACHE_PREFIX);
        }

        @Test
        @DisplayName("USER_TOKEN_KEY 应为 'apex:user:token:'")
        void shouldHaveCorrectUserTokenKey() {
            assertEquals("apex:user:token:", CacheConstants.USER_TOKEN_KEY);
        }

        @Test
        @DisplayName("USER_INFO_KEY 应为 'apex:user:info:'")
        void shouldHaveCorrectUserInfoKey() {
            assertEquals("apex:user:info:", CacheConstants.USER_INFO_KEY);
        }

        @Test
        @DisplayName("CAPTCHA_KEY 应为 'apex:captcha:'")
        void shouldHaveCorrectCaptchaKey() {
            assertEquals("apex:captcha:", CacheConstants.CAPTCHA_KEY);
        }

        @Test
        @DisplayName("WECHAT_ACCESS_TOKEN_KEY 应为 'apex:wechat:access_token'")
        void shouldHaveCorrectWechatAccessTokenKey() {
            assertEquals("apex:wechat:access_token", CacheConstants.WECHAT_ACCESS_TOKEN_KEY);
        }

        @Test
        @DisplayName("WECHAT_JSAPI_TICKET_KEY 应为 'apex:wechat:jsapi_ticket'")
        void shouldHaveCorrectWechatJsapiTicketKey() {
            assertEquals("apex:wechat:jsapi_ticket", CacheConstants.WECHAT_JSAPI_TICKET_KEY);
        }

        @Test
        @DisplayName("LOCK_PREFIX 应为 'apex:lock:'")
        void shouldHaveCorrectLockPrefix() {
            assertEquals("apex:lock:", CacheConstants.LOCK_PREFIX);
        }

        @Test
        @DisplayName("LOGIN_LOCK_KEY 应为 'apex:lock:login:'")
        void shouldHaveCorrectLoginLockKey() {
            assertEquals("apex:lock:login:", CacheConstants.LOGIN_LOCK_KEY);
        }
    }

    @Nested
    @DisplayName("缓存过期时间（TTL）测试")
    class CacheTtlTest {
        @Test
        @DisplayName("DEFAULT_EXPIRE_TIME 应为 3600 秒（1 小时）")
        void shouldHaveCorrectDefaultExpireTime() {
            assertEquals(3600L, CacheConstants.DEFAULT_EXPIRE_TIME);
        }

        @Test
        @DisplayName("TOKEN_EXPIRE_TIME 应为 7200 秒（2 小时）")
        void shouldHaveCorrectTokenExpireTime() {
            assertEquals(7200L, CacheConstants.TOKEN_EXPIRE_TIME);
        }

        @Test
        @DisplayName("CAPTCHA_EXPIRE_TIME 应为 300 秒（5 分钟）")
        void shouldHaveCorrectCaptchaExpireTime() {
            assertEquals(300L, CacheConstants.CAPTCHA_EXPIRE_TIME);
        }

        @Test
        @DisplayName("WECHAT_TOKEN_EXPIRE_TIME 应为 6600 秒（110 分钟）")
        void shouldHaveCorrectWechatTokenExpireTime() {
            assertEquals(6600L, CacheConstants.WECHAT_TOKEN_EXPIRE_TIME);
        }
    }

    @Nested
    @DisplayName("Key 格式一致性测试")
    class KeyConsistencyTest {
        @Test
        @DisplayName("所有缓存 Key 均应以 CACHE_PREFIX 开头")
        void allCacheKeysShouldStartWithCachePrefix() {
            assertTrue(CacheConstants.USER_TOKEN_KEY.startsWith(CacheConstants.CACHE_PREFIX));
            assertTrue(CacheConstants.USER_INFO_KEY.startsWith(CacheConstants.CACHE_PREFIX));
            assertTrue(CacheConstants.CAPTCHA_KEY.startsWith(CacheConstants.CACHE_PREFIX));
            assertTrue(CacheConstants.WECHAT_ACCESS_TOKEN_KEY.startsWith(CacheConstants.CACHE_PREFIX));
            assertTrue(CacheConstants.WECHAT_JSAPI_TICKET_KEY.startsWith(CacheConstants.CACHE_PREFIX));
            assertTrue(CacheConstants.LOCK_PREFIX.startsWith(CacheConstants.CACHE_PREFIX));
            assertTrue(CacheConstants.LOGIN_LOCK_KEY.startsWith(CacheConstants.CACHE_PREFIX));
        }

        @Test
        @DisplayName("LOGIN_LOCK_KEY 应以 LOCK_PREFIX 开头")
        void loginLockKeyShouldStartWithLockPrefix() {
            assertTrue(CacheConstants.LOGIN_LOCK_KEY.startsWith(CacheConstants.LOCK_PREFIX));
        }
    }

    @Nested
    @DisplayName("过期时间合理性测试")
    class TtlReasonablenessTest {
        @Test
        @DisplayName("验证码过期时间应最短")
        void captchaExpireTimeShouldBeShortest() {
            assertTrue(CacheConstants.CAPTCHA_EXPIRE_TIME < CacheConstants.DEFAULT_EXPIRE_TIME);
            assertTrue(CacheConstants.CAPTCHA_EXPIRE_TIME < CacheConstants.TOKEN_EXPIRE_TIME);
        }

        /**
         * 转换为{@code kenExpireTimeShouldBeGreaterThanDefault}。
         */
        @Test
        @DisplayName("Token 过期时间应大于默认过期时间")
        void tokenExpireTimeShouldBeGreaterThanDefault() {
            assertTrue(CacheConstants.TOKEN_EXPIRE_TIME > CacheConstants.DEFAULT_EXPIRE_TIME);
        }

        @Test
        @DisplayName("微信 Token 过期时间应小于 Token 过期时间（提前过期）")
        void wechatTokenExpireTimeShouldBeLessThanTokenExpireTime() {
            assertTrue(CacheConstants.WECHAT_TOKEN_EXPIRE_TIME < CacheConstants.TOKEN_EXPIRE_TIME);
        }
    }
}
