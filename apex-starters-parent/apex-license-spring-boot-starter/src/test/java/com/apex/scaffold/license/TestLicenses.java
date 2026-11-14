package com.apex.scaffold.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.security.KeyPair;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * License 测试夹具工厂
 *
 * <p>测试内构造签名 License（等价于卖方内部 LicenseGenerator 的生成逻辑，
 * 但不依赖 apex-license-tools 模块，避免测试作用域反向依赖造成构建循环）。</p>
 *
 * @author Apex Team
 * @version 2.2.0
 */
final class TestLicenses {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private TestLicenses() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 构造已签名的 License
     *
     * @param keyPair 签发密钥对
     * @param version 授权版本
     * @param validFrom 生效时间
     * @param validTo 过期时间（null 表示永久）
     * @param features 功能标识列表
     * @return 已填充签名的 LicenseInfo
     */
    static LicenseInfo signed(
            KeyPair keyPair, LicenseVersion version, LocalDateTime validFrom, LocalDateTime validTo, String... features)
            throws Exception {
        LicenseInfo license = new LicenseInfo();
        license.setLicenseId("LIC-TEST");
        license.setCustomerName("测试客户");
        license.setCustomerEmail("test@example.com");
        license.setVersion(version);
        license.setValidFrom(validFrom != null ? validFrom : LocalDateTime.now());
        license.setValidTo(validTo);
        license.setMaxUsers(-1);
        license.setMaxConcurrent(-1);
        if (features != null && features.length > 0) {
            license.setEnabledFeatures(MAPPER.writeValueAsString(Arrays.asList(features)));
        }
        license.setPublicKeyFingerprint(RsaUtil.calculateFingerprint(keyPair.getPublic()));
        license.setSignature(sign(keyPair, license));
        return license;
    }

    /** 与 LicenseValidator.validateSignature 一致的待签数据 → RSA-SHA256 签名。 */
    static String sign(KeyPair keyPair, LicenseInfo license) throws Exception {
        String json = MAPPER.writeValueAsString(license);
        ObjectNode node = (ObjectNode) MAPPER.readTree(json);
        node.remove("signature");
        return RsaUtil.sign(MAPPER.writeValueAsBytes(node), keyPair.getPrivate());
    }

    /** 序列化为 JSON（ISO 时间格式）。 */
    static String toJson(LicenseInfo license) throws Exception {
        return MAPPER.writeValueAsString(license);
    }

    /** 与验证器一致的 ObjectMapper 配置。 */
    static ObjectMapper mapper() {
        return MAPPER;
    }
}
