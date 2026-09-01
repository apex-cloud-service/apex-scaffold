package com.apex.scaffold.license;

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
 * <p><b>运行期重检</b>：{@link #isFeatureEnabled(String)} 每次调用都会检查
 * 有效期 —— License 在运行期间过期后，无需重启即自动进入降级模式，
 * 所有商业功能立即拒绝。配合 {@link #refresh(LicenseReloader)} 可在
 * 运行期更换/续期 License 文件（由 {@code LicenseRevalidationScheduler} 定时驱动）。</p>
 *
 * @author Apex Team
 * @version 2.2.0
 */
@Slf4j
public class LicenseValidator {

    private final ObjectMapper objectMapper;
    private final PublicKey publicKey;
    private final boolean machineFingerprintSkip;
    private final String machineFingerprintOverride;
    private volatile boolean degraded;
    private volatile LicenseInfo licenseInfo;
    private volatile boolean validated = false;

    /**
     * License 重载器：重新加载并验证 License，返回新的验证器实例。
     */
    @FunctionalInterface
    public interface LicenseReloader {

        /**
         * 加载并验证 License
         *
         * @return 加载完成的验证器
         * @throws Exception 加载或验证失败
         */
        LicenseValidator load() throws Exception;
    }

    /**
     * 构造函数
     *
     * @param base64PublicKey Base64编码的公钥
     * @throws Exception 公钥加载失败
     */
    public LicenseValidator(String base64PublicKey) throws Exception {
        this(base64PublicKey, false, null);
    }

    /**
     * 构造函数（带机器指纹配置）。
     *
     * @param base64PublicKey            Base64 编码的公钥
     * @param machineFingerprintSkip    是否跳过机器指纹验证（仅测试用）
     * @param machineFingerprintOverride 手动覆盖的机器指纹（容器部署用）
     * @throws Exception 公钥加载失败
     * @since 1.2.1
     */
    public LicenseValidator(String base64PublicKey, boolean machineFingerprintSkip, String machineFingerprintOverride)
            throws Exception {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.publicKey = RsaUtil.loadPublicKey(base64PublicKey);
        this.degraded = false;
        this.machineFingerprintSkip = machineFingerprintSkip;
        this.machineFingerprintOverride = machineFingerprintOverride;
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
        this.machineFingerprintSkip = true;
        this.machineFingerprintOverride = null;
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

        // 验证机器指纹（1.2.1 新增）
        validateMachineFingerprint();

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
     * 运行期刷新：用新的加载结果替换当前授权状态。
     *
     * <p>用于 License 续期/更换后不重启生效。加载失败时当前验证器进入降级模式
     * （例如运行期删除 License 文件或替换为无效文件）。</p>
     *
     * @param reloader License 重载器
     */
    public synchronized void refresh(LicenseReloader reloader) {
        try {
            LicenseValidator fresh = reloader.load();
            if (!fresh.validated) {
                throw new LicenseException("License未通过验证");
            }
            this.licenseInfo = fresh.licenseInfo;
            this.degraded = false;
            this.validated = true;
            log.info(
                    "License 运行期刷新成功: {} ({}), 剩余{}天",
                    licenseInfo.getCustomerName(),
                    licenseInfo.getVersion(),
                    licenseInfo.getRemainingDays());
        } catch (Exception e) {
            log.warn("License 运行期刷新失败（{}），进入降级模式：商业功能不可用", e.getMessage());
            invalidate("运行期刷新失败: " + e.getMessage());
        }
    }

    /**
     * 运行期失效：验证器进入降级模式，所有商业功能拒绝。
     *
     * @param reason 失效原因（记入日志）
     */
    public void invalidate(String reason) {
        this.validated = false;
        this.degraded = true;
        log.warn("License 已失效（{}），商业功能不可用", reason);
    }

    /**
     * 验证签名
     *
     * <p>签名数据为"剔除 signature 字段后的规范化 JSON"，与 LicenseGenerator
     * 生成时的待签数据保持一致。</p>
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
     * 验证机器指纹。
     *
     * <p>比对 License 中签发的 {@code machineFingerprint} 与当前机器采集的指纹哈希，
     * 防止 License 被复制到未授权机器运行。验证规则（按优先级）：</p>
     * <ol>
     *   <li>License 未包含指纹字段（null/空）→ 跳过，兼容旧版 License（WARN 提示）</li>
     *   <li>{@code machineFingerprintSkip=true} → 跳过（仅测试/开发用）</li>
     *   <li>{@code machineFingerprintOverride} 非空 → 用 override 值比对</li>
     *   <li>否则 → 自动采集本机指纹比对</li>
     * </ol>
     * <p>不匹配时抛出 {@link LicenseException}，由调用方进入降级模式。</p>
     *
     * @since 1.2.1
     */
    private void validateMachineFingerprint() {
        String licenseFingerprint = licenseInfo.getMachineFingerprint();
        if (licenseFingerprint == null || licenseFingerprint.isBlank()) {
            log.warn("License 未包含机器指纹，跳过机器绑定验证（建议签发时绑定机器指纹）");
            return;
        }
        if (machineFingerprintSkip) {
            log.warn("机器指纹验证已跳过（apex.license.machine-fingerprint-skip=true），生产环境不应启用");
            return;
        }
        String actualFingerprint;
        if (machineFingerprintOverride != null && !machineFingerprintOverride.isBlank()) {
            actualFingerprint = machineFingerprintOverride;
            log.debug("使用手动覆盖的机器指纹进行验证");
        } else {
            actualFingerprint = MachineFingerprintCollector.collect();
            log.debug("自动采集机器指纹: {}", actualFingerprint);
        }
        if (!licenseFingerprint.equals(actualFingerprint)) {
            throw new LicenseException("机器指纹不匹配，License 可能被复制到未授权机器");
        }
        log.info("机器指纹验证通过");
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
        return validated && !degraded;
    }

    /**
     * 是否处于降级模式（无有效 License）
     */
    public boolean isDegraded() {
        return degraded;
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
     * <p>每次调用均进行运行期过期检查：License 过期后无需重启即拒绝全部商业功能。</p>
     *
     * @param feature 功能标识
     * @return 是否启用
     */
    public boolean isFeatureEnabled(String feature) {
        if (!validated || degraded) {
            return false;
        }

        // 运行期过期检查：过期即时降级
        LicenseInfo info = this.licenseInfo;
        if (info != null && info.isExpired()) {
            invalidate("License已于 " + info.getValidTo() + " 过期");
            return false;
        }

        if (info.getEnabledFeatures() == null || info.getEnabledFeatures().isEmpty()) {
            // 未指定功能列表,默认全部启用
            return true;
        }

        try {
            java.util.List<String> features = objectMapper.readValue(info.getEnabledFeatures(), java.util.List.class);
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
