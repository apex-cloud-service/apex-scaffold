package com.apex.scaffold.authcenter.infrastructure.kms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AWS KMS配置属性
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "security.kms.aws")
public class AwsKmsProperties {

    /**
     * AWS区域
     */
    private String region = "us-east-1";

    /**
     * 主密钥ID或ARN
     */
    private String masterKeyId;

    /**
     * 访问密钥ID（可选，推荐使用IAM角色）
     */
    private String accessKeyId;

    /**
     * 访问密钥（可选，推荐使用IAM角色）
     */
    private String secretAccessKey;

    /**
     * 是否启用密钥自动轮换
     */
    private boolean enableAutoRotation = true;

    /**
     * 连接超时时间（毫秒）
     */
    private int connectionTimeout = 5000;

    /**
     * 请求超时时间（毫秒）
     */
    private int requestTimeout = 10000;
}
