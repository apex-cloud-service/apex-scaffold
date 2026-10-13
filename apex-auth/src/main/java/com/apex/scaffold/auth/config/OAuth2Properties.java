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
}
