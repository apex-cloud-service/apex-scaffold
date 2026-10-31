package com.apex.scaffold.interfaces.security;

import io.jsonwebtoken.Claims;

/**
 * 令牌验签器契约：解析并验签紧凑型 JWT，返回声明集合。
 *
 * <p>统一认证收口（A1）引入，替代过滤器对 {@code JwtUtil} 静态验签的直接依赖，
 * 使认证中心 RS256 令牌与主应用 HS256 令牌可在同一入口验签；生产实现为
 * {@link JwtVerifierSupplier}，测试注入替身。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@FunctionalInterface
public interface TokenVerifier {

    /**
     * 验签并解析令牌。
     *
     * @param token 紧凑型 JWT 字符串
     * @return 验签通过后的声明集合
     * @throws io.jsonwebtoken.ExpiredJwtException 令牌已过期
     * @throws io.jsonwebtoken.JwtException       签名无效、算法不支持或密钥缺失
     */
    Claims parse(String token);
}
