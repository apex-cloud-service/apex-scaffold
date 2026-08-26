package com.apex.scaffold.common.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PublicKey;
import lombok.extern.slf4j.Slf4j;

/**
 * License验证器
 *
 * <p>负责加载、解析和验证License文件。
 * 支持RSA签名验证、过期检查、功能权限控制。
 *
 * <p>当 License 缺失/无效时，可通过 {@link #degraded()} 创建降级验证器：
 * 基础功能放行，所有商业功能（@FeatureGate 标注）拒绝，并在日志中明确提示。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 */
@Slf4j
public class LicenseValidator {

    private final ObjectMapper objectMapper;
    private final PublicKey publicKey;
    private final boolean degraded;
    private LicenseInfo licenseInfo;
    private boolean validated = false;

    /**
     * 构造函数
     *
     * @param base64PublicKey Base64编码的公钥
     * @throws Exception 公钥加载失败
     */
    public LicenseValidator(String base64PublicKey) throws Exception {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.publicKey = RsaUtil.loadPublicKey(base64PublicKey);
        this.degraded = false;
    }

    /**
     * 创建降级验证器（无有效 License 时使用）。
     *
     * <p>降级模式下：基础功能可用，所有商业功能拒绝。</p>
     *
     * @return 降级验证器
     */
    public static LicenseValidator degraded() {
        LicenseValidator validator = new LicenseValidator();
        log.warn("License 验证器进入降级模式：未检测到有效 License，商业功能不可用");
        return validator;
    }

    private LicenseValidator() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.publicKey = null;
        this.degraded = true;
    }

    /**
     * 从文件加载License
     *
     * @param licenseFile License文件路径
     * @return 当前验证器实例(链式调用)
     * @throws Exception 加载或验证失败
     */
    public LicenseValidator loadFromFile(String licenseFile) throws Exception {
        File file = new File(licenseFile);
        if (!file.exists()) {
            throw new IllegalArgumentException("License文件不存在: " + licenseFile);
        }

        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        return loadFromJson(content);
    }

    /**
     * 从JSON字符串加载License
     *
     * @param json License JSON内容
     * @return 当前验证器实例(链式调用)
     * @throws Exception 解析或验证失败
     */
    public LicenseValidator loadFromJson(String json) throws Exception {
        // 解析JSON
        this.licenseInfo = objectMapper.readValue(json, LicenseInfo.class);

        // 验证签名
        validateSignature(json);

        // 验证公钥指纹
        validateFingerprint();

        // 验证有效期
        validateExpiration();

        this.validated = true;
        log.info(
                "License验证成功: {} ({}), 剩余{}天",
                licenseInfo.getCustomerName(),
                licenseInfo.getVersion(),
                licenseInfo.getRemainingDays());

        return this;
    }

    /**
     * 验证签名
     *
     * <p>签名数据为"剔除 signature 字段后的规范化 JSON"，与
     * {@link LicenseGenerator#generateLicense} 生成时的待签数据保持一致。</p>
     */
    private void validateSignature(String json) throws Exception {
        var node = objectMapper.readTree(json);
        if (!node.has("signature") || node.get("signature").isNull()) {
            throw new LicenseException("License缺少签名字段");
        }

        // 移除 signature 字段后作为待验数据
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).remove("signature");
        byte[] data = objectMapper.writeValueAsBytes(node);
        boolean valid = RsaUtil.verify(data, licenseInfo.getSignature(), publicKey);

        if (!valid) {
            throw new LicenseException("License签名验证失败,可能被篡改");
        }
    }

    /**
     * 验证公钥指纹
     */
    private void validateFingerprint() throws Exception {
        if (licenseInfo.getPublicKeyFingerprint() == null) {
            log.warn("License未包含公钥指纹,跳过验证");
            return;
        }

        String calculatedFingerprint = RsaUtil.calculateFingerprint(publicKey);
        if (!calculatedFingerprint.equals(licenseInfo.getPublicKeyFingerprint())) {
            throw new LicenseException("公钥不匹配,License可能属于其他系统");
        }
    }

    /**
     * 验证有效期
     */
    private void validateExpiration() {
        if (licenseInfo.isExpired()) {
            throw new LicenseException("License已过期 (过期时间: " + licenseInfo.getValidTo() + ")");
        }

        // 警告: 剩余30天内过期
        if (licenseInfo.getRemainingDays() <= 30) {
            log.warn("License将在{}天后过期,请及时续期", licenseInfo.getRemainingDays());
        }
    }

    /**
     * 检查是否已验证
     */
    public boolean isValidated() {
        return validated;
    }

    /**
     * 获取License信息
     */
    public LicenseInfo getLicenseInfo() {
        if (!validated) {
            throw new IllegalStateException("License尚未验证或已降级");
        }
        return licenseInfo;
    }

    /**
     * 检查功能是否启用
     *
     * @param feature 功能标识
     * @return 是否启用
     */
    public boolean isFeatureEnabled(String feature) {
        if (!validated || degraded) {
            return false;
        }

        if (licenseInfo.getEnabledFeatures() == null
                || licenseInfo.getEnabledFeatures().isEmpty()) {
            // 未指定功能列表,默认全部启用
            return true;
        }

        try {
            java.util.List<String> features =
                    objectMapper.readValue(licenseInfo.getEnabledFeatures(), java.util.List.class);
            return features.contains(feature);
        } catch (Exception e) {
            log.error("解析功能列表失败", e);
            return false;
        }
    }

    /**
     * 检查用户数限制
     *
     * @param currentUserCount 当前用户数
     * @return 是否允许
     */
    public boolean checkUserLimit(int currentUserCount) {
        if (!validated || degraded) {
            return false;
        }

        if (licenseInfo.getMaxUsers() == null || licenseInfo.getMaxUsers() == -1) {
            return true; // 无限制
        }

        return currentUserCount <= licenseInfo.getMaxUsers();
    }

    /**
     * 检查并发数限制
     *
     * @param currentConcurrent 当前并发数
     * @return 是否允许
     */
    public boolean checkConcurrentLimit(int currentConcurrent) {
        if (!validated || degraded) {
            return false;
        }

        if (licenseInfo.getMaxConcurrent() == null || licenseInfo.getMaxConcurrent() == -1) {
            return true; // 无限制
        }

        return currentConcurrent <= licenseInfo.getMaxConcurrent();
    }
}
