package com.apex.scaffold.common.license;

import java.nio.file.Files;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * License自动配置
 *
 * <p>当apex.license.enabled=true时自动加载和验证License。
 *
 * @author Apex Team
 * @version 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "apex.license.enabled", havingValue = "true")
public class LicenseAutoConfiguration {

    private final LicenseProperties properties;

    @Bean
    public LicenseValidator licenseValidator() throws Exception {
        // 加载公钥
        String publicKeyBase64;
        if (properties.getPublicKeyFile() != null
                && !properties.getPublicKeyFile().isEmpty()) {
            publicKeyBase64 = new String(Files.readAllBytes(Paths.get(properties.getPublicKeyFile())));
        } else if (properties.getPublicKey() != null
                && !properties.getPublicKey().isEmpty()) {
            publicKeyBase64 = properties.getPublicKey();
        } else {
            throw new IllegalStateException("未配置License公钥,请设置apex.license.public-key或apex.license.public-key-file");
        }

        // 创建验证器
        LicenseValidator validator = new LicenseValidator(publicKeyBase64);

        // 加载License文件
        String licensePath = properties.getFilePath();
        if (licensePath.startsWith("classpath:")) {
            // 从classpath加载
            String resourceName = licensePath.substring("classpath:".length());
            var resource = getClass().getClassLoader().getResource(resourceName);
            if (resource == null) {
                throw new IllegalStateException("License文件不存在: " + licensePath);
            }
            validator.loadFromFile(resource.getPath());
        } else {
            // 从文件系统加载
            validator.loadFromFile(licensePath);
        }

        log.info("License验证器初始化成功");
        return validator;
    }
}
