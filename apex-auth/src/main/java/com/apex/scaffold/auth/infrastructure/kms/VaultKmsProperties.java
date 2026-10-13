package com.apex.scaffold.auth.infrastructure.kms;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * HashiCorp Vault KMS配置属性
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "security.kms.vault")
public class VaultKmsProperties {

    /**
     * Vault服务器地址
     */
    private String address = "http://localhost:8200";

    /**
     * Vault Token
     */
    @ToString.Exclude
    private String token;

    /**
     * 命名空间（企业版）
     */
    private String namespace = "";

    /**
     * Transit引擎路径
     */
    private String transitEnginePath = "transit";

    /**
     * 默认密钥名称
     */
    private String keyName = "apex-encryption-key";

    /**
     * 连接超时时间（毫秒）
     */
    private int connectionTimeout = 5000;

    /**
     * 请求超时时间（毫秒）
     */
    private int requestTimeout = 10000;

    /**
     * 是否启用TLS
     */
    private boolean tlsEnabled = false;

    /**
     * TLS证书路径
     */
    private String tlsCertPath;
}
