package com.apex.scaffold.interfaces.test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * 测试 Token 工具类
 *
 * <p>提供 JWT Token 生成相关的静态工具方法，用于集成测试中模拟认证凭证。
 *
 * @author Apex Team
 */
@SuppressWarnings("deprecation")
public final class TestTokenHelper {

    public static final String TEST_JWT_SECRET = "KdT3xL9mN8pQ2rV5wY7zA1cE4gH6jK0sM2nP4rT6vW8xZ0bD3fG5hJ7kL9mN1q";

    /**
     * 创建 {@code TestTokenHelper} 实例。
     */
    private TestTokenHelper() {
        // 工具类，禁止实例化
    }

    /**
     * 生成测试用 JWT accessToken
     *
     * @param openId  用户 OpenID
     * @param minutes 过期时间（分钟）
     * @return JWT Token 字符串
     */
    public static String generateAccessToken(String openId, int minutes) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + Duration.ofMinutes(minutes).toMillis());
        var key = Keys.hmacShaKeyFor(resolveJwtSecret().getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(openId)
                .issuedAt(now)
                .expiration(expiration)
                .claim("type", "access")
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 生成测试用 JWT refreshToken
     *
     * @param openId  用户 OpenID
     * @param minutes 过期时间（分钟）
     * @return JWT Token 字符串
     */
    public static String generateRefreshToken(String openId, int minutes) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + Duration.ofMinutes(minutes).toMillis());
        var key = Keys.hmacShaKeyFor(resolveJwtSecret().getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(openId)
                .issuedAt(now)
                .expiration(expiration)
                .claim("type", "refresh")
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 生成已过期的 JWT accessToken
     *
     * @param openId 用户 OpenID
     * @return 已过期的 JWT Token 字符串
     */
    public static String generateExpiredToken(String openId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() - Duration.ofMinutes(10).toMillis());
        var key = Keys.hmacShaKeyFor(resolveJwtSecret().getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(openId)
                .issuedAt(now)
                .expiration(expiration)
                .claim("type", "access")
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析并获取{@code jwtSecret}。
     * @return 字符串结果
     */
    private static String resolveJwtSecret() {
        String envSecret = System.getenv("JWT_SECRET");
        if (envSecret != null && !envSecret.isBlank()) {
            return envSecret;
        }
        String sysSecret = System.getProperty("jwt.secret");
        if (sysSecret != null && !sysSecret.isBlank()) {
            return sysSecret;
        }
        return TEST_JWT_SECRET;
    }
}
