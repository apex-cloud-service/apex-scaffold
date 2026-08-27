package com.apex.scaffold.common.license;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * License自动配置
 *
 * <p>默认启用 License 校验（{@code apex.license.enabled} 缺省为 true）。
 *
 * <p><b>容错降级策略</b>：License 文件缺失、签名无效或已过期时，
 * 不阻塞应用启动，而是注册一个降级验证器 —— 基础功能放行，
 * 所有商业功能（{@code @FeatureGate} 标注）拒绝，并输出明确的 WARN 日志。
 * 该策略保证 fork 用户无法绕过商业功能授权，同时不影响基础版正常使用。</p>
 *
 * @author Apex Team
 * @version 2.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(LicenseProperties.class)
@ConditionalOnProperty(name = "apex.license.enabled", havingValue = "true", matchIfMissing = true)
public class LicenseAutoConfiguration {

    private final LicenseProperties properties;

    @Bean
    public LicenseValidator licenseValidator() {
        try {
            // 1. 加载公钥（优先外部文件，其次配置内嵌，最后随包默认公钥）
            String publicKeyBase64 = loadPublicKey();
            LicenseValidator validator = new LicenseValidator(publicKeyBase64);

            // 2. 加载并验证 License 文件
            String licensePath = properties.getFilePath();
            if (licensePath == null || licensePath.isBlank()) {
                throw new IllegalStateException("未配置 apex.license.file-path");
            }
            loadLicense(validator, licensePath);

            log.info(
                    "License 验证通过: 客户={}, 版本={}",
                    validator.getLicenseInfo().getCustomerName(),
                    validator.getLicenseInfo().getVersion());
            return validator;
        } catch (Exception e) {
            log.warn("License 加载或验证失败（{}），进入降级模式：商业功能不可用，基础功能不受影响", e.getMessage());
            return LicenseValidator.degraded();
        }
    }

    /** 加载公钥：优先外部文件，其次配置内嵌，最后随包默认公钥。 */
    private String loadPublicKey() throws Exception {
        String file = properties.getPublicKeyFile();
        if (file != null && !file.isBlank()) {
            return readResourceOrFile(file);
        }
        String inline = properties.getPublicKey();
        if (inline != null && !inline.isBlank()) {
            return inline.trim();
        }
        // 随包默认公钥
        return readResourceOrFile("classpath:license/public.key");
    }

    /** 加载 License 文件（支持 classpath: 与文件系统路径）。 */
    private void loadLicense(LicenseValidator validator, String licensePath) throws Exception {
        if (licensePath.startsWith("classpath:")) {
            String resourceName = licensePath.substring("classpath:".length());
            var resource = getClass().getClassLoader().getResource(resourceName);
            if (resource == null) {
                throw new IllegalStateException("License文件不存在: " + licensePath);
            }
            String content = new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8);
            validator.loadFromJson(content);
        } else {
            validator.loadFromFile(licensePath);
        }
    }

    /** 读取 classpath 资源或文件系统文件内容。 */
    private String readResourceOrFile(String path) throws Exception {
        if (path.startsWith("classpath:")) {
            String resourceName = path.substring("classpath:".length());
            var resource = getClass().getClassLoader().getResource(resourceName);
            if (resource == null) {
                throw new IllegalStateException("资源不存在: " + path);
            }
            return new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8).trim();
    }
}
