package com.apex.scaffold.common.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

/**
 * License生成工具
 *
 * <p>用于生成商业License文件。
 * 使用RSA私钥签名,生成包含授权信息的JSON文件。
 *
 * @author Apex Team
 * @version 1.0.0
 */
@Slf4j
public class LicenseGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KeyPair keyPair;

    /**
     * 构造函数(使用已有密钥对)
     *
     * @param keyPair RSA密钥对
     */
    public LicenseGenerator(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    /**
     * 生成新的密钥对并保存
     *
     * @param privateKeyFile 私钥文件路径
     * @param publicKeyFile 公钥文件路径
     * @return LicenseGenerator实例
     * @throws Exception 生成失败
     */
    public static LicenseGenerator generateNewKeys(String privateKeyFile, String publicKeyFile) throws Exception {
        KeyPair keyPair = RsaUtil.generateKeyPair();

        // 保存私钥
        String privateKeyBase64 = RsaUtil.privateKeyToBase64(keyPair.getPrivate());
        Files.write(Paths.get(privateKeyFile), privateKeyBase64.getBytes(StandardCharsets.UTF_8));

        // 保存公钥
        String publicKeyBase64 = RsaUtil.publicKeyToBase64(keyPair.getPublic());
        Files.write(Paths.get(publicKeyFile), publicKeyBase64.getBytes(StandardCharsets.UTF_8));

        log.info("密钥对已生成并保存");
        return new LicenseGenerator(keyPair);
    }

    /**
     * 从文件加载密钥对
     *
     * @param privateKeyFile 私钥文件路径
     * @param publicKeyFile 公钥文件路径
     * @return LicenseGenerator实例
     * @throws Exception 加载失败
     */
    public static LicenseGenerator loadKeys(String privateKeyFile, String publicKeyFile) throws Exception {
        String privateKeyBase64 = new String(Files.readAllBytes(Paths.get(privateKeyFile)), StandardCharsets.UTF_8);
        String publicKeyBase64 = new String(Files.readAllBytes(Paths.get(publicKeyFile)), StandardCharsets.UTF_8);

        KeyPair keyPair = new KeyPair(RsaUtil.loadPublicKey(publicKeyBase64), RsaUtil.loadPrivateKey(privateKeyBase64));

        return new LicenseGenerator(keyPair);
    }

    /**
     * 生成License
     *
     * @param customerName 客户名称
     * @param customerEmail 客户邮箱
     * @param version 授权版本
     * @param validFrom 生效时间
     * @param validTo 过期时间(null表示永久)
     * @param maxUsers 最大用户数(-1表示无限制)
     * @param maxConcurrent 最大并发数(-1表示无限制)
     * @param enabledFeatures 启用的功能列表
     * @return LicenseInfo对象
     * @throws Exception 生成失败
     */
    public LicenseInfo generateLicense(
            String customerName,
            String customerEmail,
            LicenseVersion version,
            LocalDateTime validFrom,
            LocalDateTime validTo,
            Integer maxUsers,
            Integer maxConcurrent,
            String[] enabledFeatures)
            throws Exception {

        LicenseInfo license = new LicenseInfo();
        license.setLicenseId(generateLicenseId());
        license.setCustomerName(customerName);
        license.setCustomerEmail(customerEmail);
        license.setVersion(version);
        license.setValidFrom(validFrom != null ? validFrom : LocalDateTime.now());
        license.setValidTo(validTo);
        license.setMaxUsers(maxUsers != null ? maxUsers : -1);
        license.setMaxConcurrent(maxConcurrent != null ? maxConcurrent : -1);

        // 序列化功能列表
        if (enabledFeatures != null && enabledFeatures.length > 0) {
            license.setEnabledFeatures(objectMapper.writeValueAsString(Arrays.asList(enabledFeatures)));
        }

        // 计算公钥指纹
        license.setPublicKeyFingerprint(RsaUtil.calculateFingerprint(keyPair.getPublic()));

        // 生成签名
        String jsonContent = objectMapper.writeValueAsString(license);
        byte[] data = jsonContent.getBytes(StandardCharsets.UTF_8);
        String signature = RsaUtil.sign(data, keyPair.getPrivate());
        license.setSignature(signature);

        log.info("License生成成功: {} ({})", customerName, version);
        return license;
    }

    /**
     * 保存License到文件
     *
     * @param license License对象
     * @param outputFile 输出文件路径
     * @throws Exception 保存失败
     */
    public void saveToFile(LicenseInfo license, String outputFile) throws Exception {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(license);
        Files.write(Paths.get(outputFile), json.getBytes(StandardCharsets.UTF_8));
        log.info("License已保存到: {}", outputFile);
    }

    /**
     * 生成License ID
     */
    private String generateLicenseId() {
        return "LIC-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-"
                + Integer.toHexString((int) (Math.random() * 10000));
    }

    /**
     * 命令行入口
     */
    public static void main(String[] args) {
        try {
            if (args.length < 2) {
                System.out.println("用法:");
                System.out.println("  生成密钥对: java LicenseGenerator gen-keys <private.key> <public.key>");
                System.out.println(
                        "  生成License: java LicenseGenerator gen-license <private.key> <public.key> <output.json>");
                return;
            }

            String command = args[0];

            if ("gen-keys".equals(command)) {
                // 生成密钥对
                String privateKeyFile = args[1];
                String publicKeyFile = args[2];
                generateNewKeys(privateKeyFile, publicKeyFile);
                System.out.println("密钥对已生成");

            } else if ("gen-license".equals(command)) {
                // 生成License
                String privateKeyFile = args[1];
                String publicKeyFile = args[2];
                String outputFile = args[3];

                LicenseGenerator generator = loadKeys(privateKeyFile, publicKeyFile);

                // 示例License
                LicenseInfo license = generator.generateLicense(
                        "示例客户",
                        "customer@example.com",
                        LicenseVersion.ENTERPRISE,
                        LocalDateTime.now(),
                        LocalDateTime.now().plusYears(1),
                        100,
                        50,
                        new String[] {"audit_log", "api_version", "monitoring"});

                generator.saveToFile(license, outputFile);
                System.out.println("License已生成: " + outputFile);
            }

        } catch (Exception e) {
            log.error("执行失败", e);
            System.err.println("错误: " + e.getMessage());
        }
    }
}
