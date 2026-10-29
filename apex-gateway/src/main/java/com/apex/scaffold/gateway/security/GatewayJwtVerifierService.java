package com.apex.scaffold.gateway.security;

import com.apex.scaffold.common.util.JsonUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 网关 JWT 验签服务（JWKS 非对称 + HMAC 过渡兼容）.
 *
 * <p>核心职责：
 * <ol>
 *   <li>缓存由 {@link JwksProperties} 下发的密钥（kid → Key 映射），支持 Nacos 热更新</li>
 *   <li>先解析 JWT header 中的 {@code kid}，再选择对应密钥完成验签，
 *       从而同时支持 RSA（Phase 2 起 auth 服务签发）与 HMAC（过渡期旧 Token）两种算法
 *       （风险缓解：见迁移方案 §16 风险 #1 过渡期双 kid 并行验证）</li>
 *   <li>验签 + 过期校验一体化，失败返回 {@code null}，由过滤器统一收敛为 401 Result</li>
 * </ol>
 *
 * <p><b>线程安全</b>：密钥映射使用 {@link ConcurrentHashMap}，配合 Nacos 配置刷新时
 * 重新加载 {@link #refreshKeys()}，无需重启网关。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Service
@Slf4j
public class GatewayJwtVerifierService {

    /** HMAC 过渡密钥的合成 kid（当配置未显式声明 hmac 条目时兜底使用） */
    private static final String HMAC_FALLBACK_KID = "hmac-legacy";

    /** JWT 对称算法前缀（HS256/HS384/HS512），用于过渡期 HMAC 兜底判定 */
    private static final String HMAC_ALG_PREFIX = "HS";

    /** kid → Key 映射（RSA 公钥或 HMAC 对称密钥） */
    private final Map<String, Key> keyCache = new ConcurrentHashMap<>();

    private final JwksProperties properties;

    /**
     * 构造验签服务并预热密钥缓存.
     *
     * @param properties JWKS 配置（来自本地或 Nacos）
     */
    public GatewayJwtVerifierService(JwksProperties properties) {
        this.properties = properties;
        refreshKeys();
    }

    /**
     * 校验并解析 JWT（验签 + 过期检查）.
     *
     * <p>先依据 header.kid 定位密钥，再交由 jjwt 完成签名与过期校验；
     * 任一步骤失败（未知 kid、密钥不可用、签名无效、已过期、格式错误）统一返回 {@code null}。
     *
     * @param token JWT 字符串
     * @return 验签通过且未过期的 Claims；失败返回 {@code null}
     */
    public Claims verifyAndParse(String token) {
        if (token == null || token.isBlank() || keyCache.isEmpty()) {
            log.debug("JWT 为空或无可用验签密钥, 拒绝");
            return null;
        }
        Key key;
        try {
            key = selectKey(token);
        } catch (RuntimeException e) {
            log.debug("JWT 密钥定位失败: reason={}", e.getMessage());
            return null;
        }
        if (key == null) {
            return null;
        }
        try {
            return parseWithKey(token, key);
        } catch (ExpiredJwtException e) {
            log.debug("JWT 已过期: {}", e.getMessage());
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 验签失败: reason={}", e.getMessage());
            return null;
        }
    }

    /**
     * 依据 JWT header 选择验签密钥.
     *
     * <p>命中顺序：header.kid 精确匹配 → HMAC 过渡密钥兜底（仅当 header 算法为 HS 系列）。
     *
     * @param token JWT 字符串
     * @return 验签密钥；无匹配返回 {@code null}
     */
    private Key selectKey(String token) {
        String kid = readHeaderValue(token, "kid");
        String alg = readHeaderValue(token, "alg");
        Key key = kid == null ? null : keyCache.get(kid);
        if (key != null) {
            return key;
        }
        if (alg != null && alg.startsWith(HMAC_ALG_PREFIX)) {
            return keyCache.get(HMAC_FALLBACK_KID);
        }
        log.warn("未找到 JWT 对应验签密钥: kid={}, alg={}", kid, alg);
        return null;
    }

    /**
     * 使用指定密钥完成验签与解析.
     *
     * @param token JWT 字符串
     * @param key   验签密钥（RSA 公钥或 HMAC 对称密钥）
     * @return Claims
     */
    private Claims parseWithKey(String token, Key key) {
        if (key instanceof SecretKey secretKey) {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        }
        return Jwts.parser()
                .verifyWith((PublicKey) key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 读取 JWT 未受保护头（header）中的字段值.
     *
     * <p>仅做 Base64URL 解码与 JSON 解析，不涉及验签，供密钥定位使用。
     *
     * @param token JWT 字符串
     * @param field 字段名（如 kid / alg）
     * @return 字段值；缺失或解析失败返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    private String readHeaderValue(String token, String field) {
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64Url(token.substring(0, dot)));
            Map<String, Object> header = JsonUtil.fromJson(new String(decoded, StandardCharsets.UTF_8), Map.class);
            Object value = header == null ? null : header.get(field);
            return value == null ? null : String.valueOf(value);
        } catch (RuntimeException e) {
            log.debug("解析 JWT header 失败: field={}, reason={}", field, e.getMessage());
            return null;
        }
    }

    /**
     * 补齐 Base64URL 缺失的填充字符（Java 解码器要求长度为 4 的倍数）.
     *
     * @param segment Base64URL 片段
     * @return 补齐后的片段
     */
    private String padBase64Url(String segment) {
        int remainder = segment.length() % 4;
        if (remainder == 0) {
            return segment;
        }
        return segment + "=".repeat(4 - remainder);
    }

    /**
     * （重新）加载密钥缓存.
     *
     * <p>支持 Nacos 配置刷新后由外部调用以热更新密钥；启动时构造阶段也会调用一次。
     */
    public synchronized void refreshKeys() {
        keyCache.clear();
        if (properties.getKs() != null) {
            for (JwksProfile profile : properties.getKs()) {
                addKey(profile);
            }
        }
        // 过渡期 HMAC 密钥（环境变量注入），以其合成 kid 注册
        if (properties.getHmacSecret() != null && !properties.getHmacSecret().isBlank()) {
            keyCache.put(HMAC_FALLBACK_KID, buildHmacKey(properties.getHmacSecret()));
            log.info("网关已注册 HMAC 过渡密钥: kid={}（过渡期对称验证启用）", HMAC_FALLBACK_KID);
        }
        log.info("网关 JWKS 验签密钥已加载: keys={}", keyCache.size());
    }

    /**
     * 注册单个密钥条目到缓存.
     *
     * @param profile 密钥条目
     */
    private void addKey(JwksProfile profile) {
        try {
            if (profile.isRsa()) {
                PublicKey publicKey = buildRsaPublicKey(profile.getPublicKeyBase64());
                keyCache.put(profile.getKid(), publicKey);
                log.info("已注册 RSA 验签公钥: kid={}, alg={}", profile.getKid(), profile.getAlg());
            } else if (profile.isHmac()) {
                keyCache.put(profile.getKid(), buildHmacKey(profile.getHmacSecret()));
                log.info("已注册 HMAC 验签密钥: kid={}", profile.getKid());
            } else {
                log.warn("忽略不支持的 JWKS 条目: kid={}, alg={}", profile.getKid(), profile.getAlg());
            }
        } catch (Exception e) {
            log.error("注册 JWKS 密钥失败: kid={}, reason={}", profile.getKid(), e.getMessage());
        }
    }

    /**
     * 由 X.509 Base64 公钥构建 {@link PublicKey}（RSA）.
     *
     * @param base64 公钥 DER 的 Base64
     * @return RSA 公钥
     */
    private PublicKey buildRsaPublicKey(String base64) {
        byte[] der = Base64.getDecoder().decode(base64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
        try {
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("非法 RSA 公钥", e);
        }
    }

    /**
     * 由对称密钥字符串构建 HMAC {@link Key}.
     *
     * @param secret 对称密钥（UTF-8）
     * @return HMAC 密钥
     */
    private Key buildHmacKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 返回密钥缓存大小（供测试/指标观察）.
     *
     * @return 已注册密钥数
     */
    public int keyCount() {
        return keyCache.size();
    }
}
