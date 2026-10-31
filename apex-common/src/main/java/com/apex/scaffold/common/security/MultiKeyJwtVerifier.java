package com.apex.scaffold.common.security;

import com.apex.scaffold.common.util.JsonUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;

/**
 * 多密钥 JWT 验签器：按 Token 头部 {@code kid}/{@code alg} 在 RSA 公钥集合与 HMAC 过渡密钥之间选择验签密钥。
 *
 * <p>统一认证收口（A1）的核心组件：认证中心以 RS256 签发令牌（header 携带轮换密钥
 * {@code kid}，形如 {@code key-<uuid>}），主应用等存量签发方仍为 HS256 对称密钥。
 * 验签方据此实现双算法并存：
 * <ul>
 *   <li>alg=RS256：按 {@code kid} 精确匹配公钥集合（来源为静态配置或 {@link JwksFetcher} 拉取快照）；</li>
 *   <li>alg=HS256/384/512：在 {@code legacyHmacEnabled} 开启且提供对称密钥时验签，
 *       用于存量令牌过渡；主应用令牌迁移 RS256 前不得关闭；</li>
 *   <li>其余算法或未知 kid 一律拒绝，防止算法混淆（alg 冒充）攻击。</li>
 * </ul>
 *
 * <p>解析失败时抛出 jjwt 原始 {@link JwtException}（含 {@code ExpiredJwtException}），
 * 由调用方沿用既有异常分支区分"过期"与"无效"。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
public final class MultiKeyJwtVerifier {

    /** RSA 签名算法标识（仅接受 RS256，与认证中心签发口径一致） */
    private static final String ALG_RS256 = "RS256";

    /** kid → RSA 公钥映射（不可变快照） */
    private final Map<String, java.security.PublicKey> publicKeys;

    /** HMAC 过渡密钥；未配置时为 {@code null} */
    private final SecretKey hmacKey;

    /**
     * 构建验签器。
     *
     * @param publicKeys        kid → X509 公钥映射，可为空映射（纯 HMAC 场景）
     * @param hmacSecret        HMAC 过渡密钥（UTF-8 至少 256 位）；空白表示不启用
     * @param legacyHmacEnabled 是否允许 HS 系列算法走 HMAC 过渡验签
     * @return 验签器实例
     */
    public static MultiKeyJwtVerifier of(
            Map<String, java.security.PublicKey> publicKeys, String hmacSecret, boolean legacyHmacEnabled) {
        SecretKey hmacKey = legacyHmacEnabled && hmacSecret != null && !hmacSecret.isBlank()
                ? Keys.hmacShaKeyFor(hmacSecret.getBytes(StandardCharsets.UTF_8))
                : null;
        return new MultiKeyJwtVerifier(publicKeys == null ? Map.of() : Map.copyOf(publicKeys), hmacKey);
    }

    private MultiKeyJwtVerifier(Map<String, java.security.PublicKey> publicKeys, SecretKey hmacKey) {
        this.publicKeys = publicKeys;
        this.hmacKey = hmacKey;
    }

    /**
     * 按 header kid/alg 选钥并完成验签与解析。
     *
     * <p>仅读取未受保护头定位密钥，再交由 jjwt 完成签名与过期校验；
     * 对非法 Token（缺 alg、未知 kid、不支持的算法、HMAC 未启用）抛出
     * {@link JwtException}，签名或过期错误由 jjwt 原生抛出。
     *
     * @param token 紧凑型 JWT 字符串
     * @return 验签通过后的声明集合
     * @throws JwtException 定位失败或验签失败
     */
    public Claims parse(String token) {
        String alg = headerValue(token, "alg");
        if (alg == null || alg.isBlank()) {
            throw new JwtException("JWT 缺少 alg 声明，拒绝验签");
        }
        if (ALG_RS256.equals(alg)) {
            String kid = headerValue(token, "kid");
            java.security.PublicKey publicKey = kid == null ? null : publicKeys.get(kid);
            if (publicKey == null) {
                throw new JwtException("未找到 kid 对应的验签公钥: " + kid);
            }
            return parseWithKey(token, publicKey);
        }
        if (isHmacAlgorithm(alg)) {
            if (hmacKey == null) {
                throw new JwtException("HMAC 过渡验签未启用，拒绝算法: " + alg);
            }
            return parseWithKey(token, hmacKey);
        }
        throw new JwtException("不支持的签名算法: " + alg);
    }

    /**
     * 按密钥类型分派验签（jjwt 0.12 的 {@code verifyWith} 分别重载 SecretKey 与 PublicKey）。
     *
     * @param token 紧凑型 JWT
     * @param key   验签密钥
     * @return Claims
     */
    private Claims parseWithKey(String token, java.security.Key key) {
        io.jsonwebtoken.JwtParserBuilder builder = Jwts.parser();
        if (key instanceof javax.crypto.SecretKey secretKey) {
            builder.verifyWith(secretKey);
        } else {
            builder.verifyWith((java.security.PublicKey) key);
        }
        return builder.build().parseSignedClaims(token).getPayload();
    }

    /**
     * 返回当前公钥快照大小（供测试与指标观察）。
     *
     * @return 已注册 RSA 公钥数
     */
    public int publicKeyCount() {
        return publicKeys.size();
    }

    private boolean isHmacAlgorithm(String alg) {
        return Jwts.SIG.HS256.getId().equals(alg)
                || Jwts.SIG.HS384.getId().equals(alg)
                || Jwts.SIG.HS512.getId().equals(alg);
    }

    /**
     * 读取 JWT 未受保护头中的字段值（仅 Base64URL 解码与 JSON 解析，不验签）。
     *
     * @param token 紧凑型 JWT
     * @param field 字段名（如 kid / alg）
     * @return 字段值；缺失或解析失败返回 {@code null}
     */
    private String headerValue(String token, String field) {
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        try {
            String segment = token.substring(0, dot);
            String padded = padBase64Url(segment);
            Map<?, ?> header = JsonUtil.fromJson(
                    new String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8), Map.class);
            Object value = header == null ? null : header.get(field);
            return value == null ? null : String.valueOf(value);
        } catch (RuntimeException e) {
            log.debug("解析 JWT header 失败: field={}, reason={}", field, e.getMessage());
            return null;
        }
    }

    /**
     * 补齐 Base64URL 缺失的填充字符（Java 解码器要求长度为 4 的倍数）。
     *
     * @param segment Base64URL 片段
     * @return 补齐后的片段
     */
    private String padBase64Url(String segment) {
        int remainder = segment.length() % 4;
        return remainder == 0 ? segment : segment + "=".repeat(4 - remainder);
    }
}
