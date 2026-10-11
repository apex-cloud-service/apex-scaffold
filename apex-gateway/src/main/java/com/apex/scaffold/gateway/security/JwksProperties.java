package com.apex.scaffold.gateway.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关 JWKS 验签配置属性.
 *
 * <p>绑定 {@code apex.gateway.jwks.*} 配置，来源可为本地 application-*.yml
 * 或 Nacos 配置中心（{@code apex-gateway.yaml}），便于密钥轮换时热更新下发。
 *
 * <p>典型配置示例：
 * <pre>{@code
 * apex:
 *   gateway:
 *     jwks:
 *       # 过渡期兼容旧对称签发的 Token（风险 #1 缓解）；生产由环境变量注入
 *       hmac-secret: ${JWT_SECRET:}
 *       ks:
 *         - kid: hmac-legacy
 *           alg: hmac
 *         - kid: rsa-2026-01
 *           alg: rsa256
 *           public-key-base64: "MIIBIjANBgkqh..."
 * }</pre>
 *
 * @author Apex Team
 * @since 1.3.1
 */
@ConfigurationProperties(prefix = "apex.gateway.jwks")
public class JwksProperties {

    /** 过渡期 HMAC 对称密钥（环境变量注入，用于兼容旧 Token） */
    private String hmacSecret = "";

    /** 按 kid 组织的 RSA 公钥清单（可热更新下发） */
    private List<JwksProfile> ks = new ArrayList<>();

    public String getHmacSecret() {
        return hmacSecret;
    }

    public void setHmacSecret(String hmacSecret) {
        this.hmacSecret = hmacSecret == null ? "" : hmacSecret;
    }

    public List<JwksProfile> getKs() {
        return ks;
    }

    public void setKs(List<JwksProfile> ks) {
        this.ks = ks == null ? new ArrayList<>() : ks;
    }
}
