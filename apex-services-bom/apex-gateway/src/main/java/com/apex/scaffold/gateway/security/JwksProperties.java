package com.apex.scaffold.gateway.security;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
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
@Getter
@ConfigurationProperties(prefix = "apex.gateway.jwks")
public class JwksProperties {

    /** 过渡期 HMAC 对称密钥（环境变量注入，用于兼容旧 Token） */
    private String hmacSecret = "";

    /** 按 kid 组织的 RSA 公钥清单（可热更新下发） */
    private List<JwksProfile> ks = new ArrayList<>();

    /**
     * 远程 JWKS 拉取地址（认证中心 {@code /.well-known/jwks.json} 的完整 URL）。
     *
     * <p>统一认证收口（A2）：非空时网关按 {@link #remoteRefreshIntervalSeconds} 周期
     * 拉取认证中心轮换公钥并合并进密钥缓存（同 kid 覆盖静态条目）；为空时仅使用
     * 静态 {@code ks} 清单与 HMAC 过渡密钥。
     */
    private String remoteUrl = "";

    /** 远程 JWKS 拉取间隔（秒），下限 30 */
    private long remoteRefreshIntervalSeconds = 300;

    public String getRemoteUrl() {
        return remoteUrl == null ? "" : remoteUrl;
    }

    public void setRemoteUrl(String remoteUrl) {
        this.remoteUrl = remoteUrl == null ? "" : remoteUrl;
    }

    public long getRemoteRefreshIntervalSeconds() {
        return remoteRefreshIntervalSeconds;
    }

    public void setRemoteRefreshIntervalSeconds(long remoteRefreshIntervalSeconds) {
        this.remoteRefreshIntervalSeconds = remoteRefreshIntervalSeconds;
    }

    public void setHmacSecret(String hmacSecret) {
        this.hmacSecret = hmacSecret == null ? "" : hmacSecret;
    }

    public void setKs(List<JwksProfile> ks) {
        this.ks = ks == null ? new ArrayList<>() : ks;
    }
}
