package com.apex.scaffold.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OAuth2 授权服务器配置属性（issuer、RSA 密钥对等）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since  1.3.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "auth-center.oauth2")
@SuppressWarnings({"PMD.ClassNamingShouldBeCamelRule", "PMD.ClassMustHaveAuthorRule"})
public class OAuth2Properties {
    private String issuer = "http://localhost:8081";
    private String privateKey;
    private String publicKey;
    private String keyId = "oauth2-signing";

    /**
     * 是否把配置注入的静态密钥并入 {@code /.well-known/jwks.json} 聚合输出。
     *
     * <p>统一认证收口（A2）：令牌签发已切换到 {@code JwtKeyMaterialService}（Redis 轮换
     * RSA），此静态密钥仅在过渡期保留用于第三方缓存旧公钥的回退验签；过渡期结束后
     * 置为 {@code false} 并与 {@code privateKey}/{@code publicKey} 一并移除。
     */
    private boolean legacyKeyInJwks = true;
}
