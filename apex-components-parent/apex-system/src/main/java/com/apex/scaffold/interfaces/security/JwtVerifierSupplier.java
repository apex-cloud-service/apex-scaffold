package com.apex.scaffold.interfaces.security;

import com.apex.scaffold.common.security.JwksFetcher;
import com.apex.scaffold.common.security.MultiKeyJwtVerifier;
import io.jsonwebtoken.Claims;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 主应用统一令牌验签器（生产实现）：认证中心 RS256 公钥 + 主应用 HS256 对称密钥双算法组合。
 *
 * <p>公钥来源 = 静态配置（{@code apex.system.jwt-center.public-keys}，兜底）
 * ∪ JWKS 拉取快照（{@code apex.jwks.remote-url} 启用时，经共享 {@link JwksFetcher}）；
 * 同 kid 并存时以拉取快照为准，认证中心密钥轮换后的新公钥最迟一个刷新周期内生效。
 * 每次解析时基于当前快照构建验签器，保证轮换公钥无需重启即可用于验签。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public class JwtVerifierSupplier implements TokenVerifier {

    /** 静态配置公钥（kid → 公钥） */
    private final Map<String, PublicKey> staticKeys;

    /** 共享 JWKS 拉取客户端；未装配时为 {@code null} */
    private final JwksFetcher jwksFetcher;

    /** 主应用 HS256 过渡密钥 */
    private final String hmacSecret;

    /** 是否允许 HS 系列过渡验签 */
    private final boolean legacyHmacEnabled;

    /**
     * 构建验签器。
     *
     * @param staticKeys        静态配置公钥（已解码，可为空映射）
     * @param jwksFetcher       共享 JWKS 拉取客户端，可为 {@code null}
     * @param hmacSecret        主应用 HMAC 密钥（{@code jwt.secret}）
     * @param legacyHmacEnabled 是否允许 HS 系列过渡验签
     */
    public JwtVerifierSupplier(
            Map<String, PublicKey> staticKeys, JwksFetcher jwksFetcher, String hmacSecret, boolean legacyHmacEnabled) {
        this.staticKeys = staticKeys == null ? Map.of() : Map.copyOf(staticKeys);
        this.jwksFetcher = jwksFetcher;
        this.hmacSecret = hmacSecret;
        this.legacyHmacEnabled = legacyHmacEnabled;
    }

    @Override
    public Claims parse(String token) {
        return MultiKeyJwtVerifier.of(effectiveKeys(), hmacSecret, legacyHmacEnabled)
                .parse(token);
    }

    /**
     * 合并当前生效公钥：静态配置打底，拉取快照覆盖同 kid 条目。
     *
     * @return 当前生效的公钥映射
     */
    private Map<String, PublicKey> effectiveKeys() {
        if (jwksFetcher == null
                || !jwksFetcher.enabled()
                || jwksFetcher.snapshot().isEmpty()) {
            return staticKeys;
        }
        Map<String, PublicKey> merged = new HashMap<>(staticKeys);
        merged.putAll(jwksFetcher.snapshot());
        return merged;
    }

    /**
     * 解析静态公钥配置（kid → X509 DER Base64），配置非法时启动即失败。
     *
     * @param configured 配置原文
     * @return 解码后的公钥映射
     */
    public static Map<String, PublicKey> decodeConfiguredKeys(Map<String, String> configured) {
        if (configured == null || configured.isEmpty()) {
            return Map.of();
        }
        Map<String, PublicKey> decoded = new HashMap<>();
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            for (Map.Entry<String, String> entry : configured.entrySet()) {
                String kid = entry.getKey();
                String base64 = entry.getValue();
                if (kid == null || kid.isBlank() || base64 == null || base64.isBlank()) {
                    throw new IllegalStateException("认证中心公钥配置无效: kid=" + kid);
                }
                decoded.put(
                        kid,
                        factory.generatePublic(
                                new X509EncodedKeySpec(Base64.getDecoder().decode(base64))));
            }
        } catch (java.security.GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("认证中心公钥配置无效（应为 X509 DER Base64）", e);
        }
        return Map.copyOf(decoded);
    }
}
