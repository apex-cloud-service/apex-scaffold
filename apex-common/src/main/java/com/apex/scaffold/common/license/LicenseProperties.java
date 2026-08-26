package com.apex.scaffold.common.license;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * License配置属性
 *
 * @author Apex Team
 * @version 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.license")
public class LicenseProperties {

    /**
     * 是否启用License验证
     */
    private boolean enabled = false;

    /**
     * License文件路径
     */
    private String filePath = "classpath:license.json";

    /**
     * 公钥(Base64编码)
     */
    private String publicKey;

    /**
     * 公钥文件路径(优先级高于publicKey)
     */
    private String publicKeyFile;
}
