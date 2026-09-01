package com.apex.scaffold.license;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * License 加载器
 *
 * <p>从 {@link LicenseProperties} 加载公钥与 License 文件并完成验证。
 * 供 {@link LicenseAutoConfiguration}（Bean 装配）与
 * {@link LicenseFeatureGate}（条件装配期提前初始化）共用，
 * 保证两条路径读到同一份授权状态。</p>
 *
 * @author Apex Team
 * @version 2.2.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LicenseLoader {

    /**
     * 加载公钥与 License 并验证
     *
     * @param properties License 配置
     * @return 验证通过的验证器
     * @throws Exception 加载或验证失败
     */
    public static LicenseValidator load(LicenseProperties properties) throws Exception {
        // 1. 加载公钥（优先外部文件，其次配置内嵌，最后随包默认公钥）
        String publicKeyBase64 = loadPublicKey(properties);
        LicenseValidator validator = new LicenseValidator(
                publicKeyBase64, properties.isMachineFingerprintSkip(), properties.getMachineFingerprintOverride());

        // 2. 加载并验证 License 文件
        String licensePath = properties.getFilePath();
        if (licensePath == null || licensePath.isBlank()) {
            throw new IllegalStateException("未配置 apex.license.file-path");
        }
        loadLicense(validator, licensePath);
        return validator;
    }

    /** 加载公钥：优先外部文件，其次配置内嵌，最后随包默认公钥。 */
    private static String loadPublicKey(LicenseProperties properties) throws Exception {
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
    private static void loadLicense(LicenseValidator validator, String licensePath) throws Exception {
        if (licensePath.startsWith("classpath:")) {
            String resourceName = licensePath.substring("classpath:".length());
            var resource = LicenseLoader.class.getClassLoader().getResource(resourceName);
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
    private static String readResourceOrFile(String path) throws Exception {
        if (path.startsWith("classpath:")) {
            String resourceName = path.substring("classpath:".length());
            var resource = LicenseLoader.class.getClassLoader().getResource(resourceName);
            if (resource == null) {
                throw new IllegalStateException("资源不存在: " + path);
            }
            return new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8).trim();
    }
}
