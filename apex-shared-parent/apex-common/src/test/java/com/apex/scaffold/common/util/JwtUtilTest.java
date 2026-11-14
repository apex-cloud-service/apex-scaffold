package com.apex.scaffold.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * JwtUtil 工具类测试
 *
 * <p>当前 JwtUtil 的密钥通过 环境变量JWT_SECRET > 系统属性jwt.secret 读取,
 * 测试环境使用系统属性注入密钥。
 */
class JwtUtilTest {

    private static final String SECRET_KEY = "test-secret-key-for-jwt-util-testing-min-32-chars";

    @BeforeAll
    static void setup() {
        // 测试环境通过系统属性注入密钥
        System.setProperty("jwt.secret", SECRET_KEY);
    }

    @Test
    void testGenerateAndValidateToken() {
        String subject = "123";

        String token = JwtUtil.generateToken(subject);

        assertThat(token).isNotNull();
        assertThat(JwtUtil.validateToken(token)).isTrue();
        assertThat(JwtUtil.getSubject(token)).isEqualTo(subject);
    }

    @Test
    void testInvalidToken() {
        String invalidToken = "invalid.token.here";

        assertThat(JwtUtil.validateToken(invalidToken)).isFalse();
    }

    @Test
    void testExpiredToken() {
        // 生成一个立即过期的token(过期时间设为0)
        String token = JwtUtil.generateToken("456", 0);

        // 短暂等待让token过期
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(JwtUtil.validateToken(token)).isFalse();
    }
}
