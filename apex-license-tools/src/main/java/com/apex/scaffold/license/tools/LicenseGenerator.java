package com.apex.scaffold.license.tools;

import com.apex.scaffold.license.LicenseInfo;
import com.apex.scaffold.license.LicenseVersion;
import com.apex.scaffold.license.MachineFingerprintCollector;
import com.apex.scaffold.license.RsaUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
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

    private static final int GEN_KEYS_MIN_ARGS = 3;
    private static final int GEN_LICENSE_MIN_ARGS = 4;
    private static final int FEATURES_ARG_INDEX = 7;
    private static final int FINGERPRINT_ARG_INDEX = 8;
    private static final int RANDOM_BOUND = 10000;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ObjectMapper objectMapper = createObjectMapper();
    private final KeyPair keyPair;

    /** 创建支持 Java 8 时间类型的 ObjectMapper。 */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

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
     * @param machineFingerprint 机器指纹哈希（SHA-256），null 表示不绑定机器
     * @return LicenseInfo对象
     * @throws Exception 生成失败
     * @since 1.2.1 新增 machineFingerprint 参数
     */
    public LicenseInfo generateLicense(
            String customerName,
            String customerEmail,
            LicenseVersion version,
            LocalDateTime validFrom,
            LocalDateTime validTo,
            Integer maxUsers,
            Integer maxConcurrent,
            String[] enabledFeatures,
            String machineFingerprint)
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

        // 写入机器指纹（在签名前写入，使该字段参与 RSA 签名覆盖）
        license.setMachineFingerprint(machineFingerprint);

        // 计算公钥指纹
        license.setPublicKeyFingerprint(RsaUtil.calculateFingerprint(keyPair.getPublic()));

        // 生成签名：签名数据为"剔除 signature 字段后的规范化 JSON"，
        // 与 LicenseValidator 验证时的待验数据保持一致。
        String jsonContent = objectMapper.writeValueAsString(license);
        var node = objectMapper.readTree(jsonContent);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).remove("signature");
        byte[] data = objectMapper.writeValueAsBytes(node);
        String signature = RsaUtil.sign(data, keyPair.getPrivate());
        license.setSignature(signature);

        log.info("License生成成功: {} ({}), 机器指纹: {}", customerName, version, machineFingerprint != null ? "已绑定" : "未绑定");
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
                + Integer.toHexString(SECURE_RANDOM.nextInt(RANDOM_BOUND));
    }

    /**
     * 命令行入口。
     *
     * <p>支持以下子命令：</p>
     * <pre>
     *   # 生成密钥对
     *   java LicenseGenerator gen-keys &lt;private.key&gt; &lt;public.key&gt;
     *
     *   # 采集当前机器指纹（客户运行后发给卖方）
     *   java LicenseGenerator gen-fingerprint
     *
     *   # 生成 License（完整参数）
     *   java LicenseGenerator gen-license &lt;private.key&gt; &lt;public.key&gt; &lt;output.json&gt; \
     *        &lt;version:BASIC|STANDARD|ENTERPRISE&gt; &lt;customer&gt; &lt;valid-days&gt; [feature1,feature2,...] [--fingerprint &lt;hash&gt;]
     * </pre>
     *
     * <p>子命令通过 {@link Map} 分发表按首个参数定位，注册式扩展可读性与维护性更高。</p>
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        Map<String, CommandHandler> dispatcher = buildDispatcher();
        if (args.length < 1) {
            printUsage();
            return;
        }
        CommandHandler handler = dispatcher.get(args[0]);
        if (handler == null) {
            System.err.println("未知命令: " + args[0]);
            printUsage();
            return;
        }
        try {
            handler.execute(args);
        } catch (Exception e) {
            log.error("执行失败", e);
            System.err.println("错误: " + e.getMessage());
        }
    }

    /**
     * 构建子命令分发表。
     *
     * @return 命令名到处理函数的映射
     */
    private static Map<String, CommandHandler> buildDispatcher() {
        return Map.of(
                "gen-keys", LicenseGenerator::handleGenKeys,
                "gen-fingerprint", LicenseGenerator::handleGenFingerprint,
                "gen-license", LicenseGenerator::handleGenLicense);
    }

    /**
     * 处理 gen-keys 子命令：生成并保存密钥对。
     *
     * @param args 命令行参数，args[1]/args[2] 为私钥/公钥文件路径
     * @throws Exception 生成或保存失败
     */
    private static void handleGenKeys(String[] args) throws Exception {
        if (args.length < GEN_KEYS_MIN_ARGS) {
            throw new IllegalArgumentException("gen-keys 需要 <private.key> <public.key> 两个参数");
        }
        String privateKeyFile = args[1];
        String publicKeyFile = args[2];
        generateNewKeys(privateKeyFile, publicKeyFile);
        System.out.println("密钥对已生成: " + privateKeyFile + " / " + publicKeyFile);
    }

    /**
     * 处理 gen-fingerprint 子命令：采集并打印当前机器指纹。
     *
     * @param args 命令行参数（无额外参数）
     */
    private static void handleGenFingerprint(String[] args) {
        String fingerprint = MachineFingerprintCollector.collect();
        if (fingerprint == null || fingerprint.isBlank()) {
            System.err.println("错误: 机器指纹采集失败，请检查系统权限");
            System.exit(1);
        }
        System.out.println("机器指纹（SHA-256）:");
        System.out.println(fingerprint);
        System.out.println("");
        System.out.println("请将此指纹发给卖方用于 License 签发。");
    }

    /**
     * 处理 gen-license 子命令：生成并保存 License 文件。
     *
     * <p>参数顺序：&lt;private.key&gt; &lt;public.key&gt; &lt;output.json&gt; &lt;version&gt;
     * &lt;customer&gt; &lt;valid-days&gt; [feature1,...] [--fingerprint &lt;hash&gt;]。</p>
     *
     * @param args 命令行参数
     * @throws Exception 加载密钥、生成或保存 License 失败
     */
    private static void handleGenLicense(String[] args) throws Exception {
        if (args.length < GEN_LICENSE_MIN_ARGS) {
            throw new IllegalArgumentException("gen-license 至少需要 <private.key> <public.key> <output.json> 三个参数");
        }
        String privateKeyFile = args[1];
        String publicKeyFile = args[2];
        String outputFile = args[3];
        LicenseVersion version =
                args.length > 4 ? LicenseVersion.valueOf(args[4].toUpperCase()) : LicenseVersion.ENTERPRISE;
        String customerName = args.length > 5 ? args[5] : "示例客户";
        int validDays = args.length > 6 ? Integer.parseInt(args[6]) : 365;
        String[] features = args.length > FEATURES_ARG_INDEX && !args[FEATURES_ARG_INDEX].startsWith("--") ? args[FEATURES_ARG_INDEX].split(",") : null;

        // 解析 --fingerprint 可选参数
        String machineFingerprint = null;
        for (int i = (features != null ? FINGERPRINT_ARG_INDEX : FEATURES_ARG_INDEX); i < args.length; i++) {
            if ("--fingerprint".equals(args[i]) && i + 1 < args.length) {
                machineFingerprint = args[i + 1];
                break;
            }
        }

        LicenseGenerator generator = loadKeys(privateKeyFile, publicKeyFile);
        LicenseInfo license = generator.generateLicense(
                customerName,
                "customer@example.com",
                version,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(validDays),
                -1,
                -1,
                features,
                machineFingerprint);

        generator.saveToFile(license, outputFile);
        System.out.println("License已生成: " + outputFile
                + " (版本: " + version + ", 客户: " + customerName
                + (machineFingerprint != null ? ", 机器指纹: 已绑定" : ", 机器指纹: 未绑定") + ")");
    }

    private static void printUsage() {
        System.out.println("用法:");
        System.out.println("  生成密钥对: java LicenseGenerator gen-keys <private.key> <public.key>");
        System.out.println("  采集机器指纹: java LicenseGenerator gen-fingerprint");
        System.out.println(
                "  生成License: java LicenseGenerator gen-license <private.key> <public.key> <output.json> <version> <customer> <days> [feature1,feature2,...] [--fingerprint <hash>]");
    }

    /**
     * 支持受检异常的命令处理函数式接口。
     *
     * <p>标准 {@link java.util.function.Consumer} 不允许抛出受检异常，而各子命令处理逻辑
     * 需抛出 {@link Exception}，故定义本接口并在 {@link #main} 中统一捕获。</p>
     */
    @FunctionalInterface
    private interface CommandHandler {
        /**
         * 执行命令处理逻辑。
         *
         * @param args 命令行参数
         * @throws Exception 命令执行失败
         */
        void execute(String[] args) throws Exception;
    }
}
