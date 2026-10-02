package com.apex.scaffold.authcenter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
