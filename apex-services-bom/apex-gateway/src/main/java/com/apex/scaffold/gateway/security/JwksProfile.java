package com.apex.scaffold.gateway.security;

import lombok.Getter;

/**
 * JWKS 单个密钥条目（kid / 算法 / 公钥或共享密钥）.
 *
 * <p>对应 Nacos 配置 {@code apex.gateway.jwks.keys[].*} 或本地
 * {@code apex.gateway.jwks.keys[].*} 的单个条目。网关在过渡期支持两类密钥：
 * <ul>
 *   <li><b>RSA/RS256</b>：通过 {@code publicKeyBase64}（X.509 Base64）承载非对称公钥，
 *       用于校验新阶段由 auth 服务签发的 Token（配合 Phase 2 的 JWKS 端点）。</li>
 *   <li><b>HMAC/HS256</b>：通过 {@code hmacSecret} 承载对称密钥，
 *       用于过渡期兼容旧脆的对称签发 Token（双 kid 并存，见迁移方案 §16 风险 #1）。</li>
 * </ul>
 *
 * <p><b>安全提示</b>：公钥可安全下发；对称密钥 {@code hmacSecret} 仅允许经环境变量/
 * KMS 注入，严禁以明文固化在配置文件，生产环境校验会拒绝弱密钥。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Getter
public class JwksProfile {

    /** 密钥 ID（对应 JWT header 的 kid） */
    private final String kid;

    /** 算法名称：rsa / rsa256 / rs256 视为 RSA；hmac / hs256 视为 HMAC */
    private final String alg;

    /** RSA 公钥（X.509 DER 的 Base64 编码），仅 RSA 模式必填 */
    private final String publicKeyBase64;

    /** HMAC 对称密钥（仅 HMAC 模式使用，生产经环境变量注入） */
    private final String hmacSecret;

    /**
     * 构造 JWKS 密钥条目.
     *
     * @param kid             密钥 ID
     * @param alg             算法标识
     * @param publicKeyBase64 RSA 公钥 Base64
     * @param hmacSecret      HMAC 对称密钥
     */
    public JwksProfile(String kid, String alg, String publicKeyBase64, String hmacSecret) {
        this.kid = kid;
        this.alg = alg == null ? "rs256" : alg.toLowerCase();
        this.publicKeyBase64 = publicKeyBase64;
        this.hmacSecret = hmacSecret;
    }

    /**
     * 是否为 RSA 非对称条目.
     *
     * @return {@code true} 表示按非对称公钥处理
     */
    public boolean isRsa() {
        return "rsa".equals(alg) || "rsa256".equals(alg) || "rs512".equals(alg) || "rs256".equals(alg);
    }

    /**
     * 是否为 HMAC 对称条目（过渡期旧 Token 验证用）.
     *
     * @return {@code true} 表示按对称密钥处理
     */
    public boolean isHmac() {
        return "hmac".equals(alg) || "hs256".equals(alg) || "hs384".equals(alg) || "hs512".equals(alg);
    }
}
