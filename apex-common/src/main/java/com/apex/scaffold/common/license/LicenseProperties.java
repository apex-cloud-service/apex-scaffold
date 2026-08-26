package com.apex.scaffold.common.license;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * License配置属性
 *
 * @author Apex Team
 * @version 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "apex.license")
public class LicenseProperties {

    /**
     * 是否启用License验证（默认启用；关闭将导致商业功能不可用且无法被授权）
     */
    private boolean enabled = true;

    /**
     * License文件路径（支持 classpath: 前缀与文件系统路径）
     */
    private String filePath = "classpath:license/license-basic.json";

    /**
     * 公钥(Base64编码)
     */
    private String publicKey;

    /**
     * 公钥文件路径(优先级高于publicKey，支持 classpath: 前缀)
     */
    private String publicKeyFile = "classpath:license/public.key";
}
