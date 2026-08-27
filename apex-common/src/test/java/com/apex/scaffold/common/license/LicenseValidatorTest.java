package com.apex.scaffold.common.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.security.KeyPair;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * License 生成与验证集成测试。
 *
 * @author Apex Team
 * @version 2.0.0
 */
class LicenseValidatorTest {

    private KeyPair keyPair;
    private String publicKeyBase64;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = RsaUtil.generateKeyPair();
        publicKeyBase64 = RsaUtil.publicKeyToBase64(keyPair.getPublic());
    }

    private LicenseGenerator generator() {
        return new LicenseGenerator(keyPair);
    }

    /** 与 LicenseGenerator 一致的序列化配置（ISO 时间格式，非时间戳）。 */
    private ObjectMapper mapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private String toJson(LicenseInfo license) throws Exception {
        return mapper().writerWithDefaultPrettyPrinter().writeValueAsString(license);
    }

    private LicenseInfo basicLicense() throws Exception {
        return generator()
                .generateLicense(
                        "测试客户",
                        "test@example.com",
                        LicenseVersion.BASIC,
                        LocalDateTime.now().minusDays(1),
                        LocalDateTime.now().plusDays(365),
                        -1,
                        -1,
                        new String[] {"jwt_auth", "webflux"});
    }

    @Test
    @DisplayName("合法 License 验证通过，功能清单生效")
    void validLicensePasses() throws Exception {
        LicenseInfo license = basicLicense();
        String json = toJson(license);

        LicenseValidator validator = new LicenseValidator(publicKeyBase64);
        validator.loadFromJson(json);

        assertThat(validator.isValidated()).isTrue();
        assertThat(validator.isFeatureEnabled("jwt_auth")).isTrue();
        assertThat(validator.isFeatureEnabled("webflux")).isTrue();
        // 未授权功能拒绝
        assertThat(validator.isFeatureEnabled("rocketmq")).isFalse();
        assertThat(validator.isFeatureEnabled("rbac")).isFalse();
        // 无限制用户/并发
        assertThat(validator.checkUserLimit(10000)).isTrue();
        assertThat(validator.checkConcurrentLimit(10000)).isTrue();
    }

    @Test
    @DisplayName("篡改 License 内容后签名验证失败")
    void tamperedLicenseRejected() throws Exception {
        LicenseInfo license = basicLicense();
        String json = toJson(license);

        // 篡改客户名称
        String tampered = json.replace("测试客户", "恶意篡改");

        LicenseValidator validator = new LicenseValidator(publicKeyBase64);
        assertThatThrownBy(() -> validator.loadFromJson(tampered))
                .isInstanceOf(LicenseException.class)
                .hasMessageContaining("签名验证失败");
    }

    @Test
    @DisplayName("过期 License 被拒绝")
    void expiredLicenseRejected() throws Exception {
        LicenseInfo license = generator()
                .generateLicense(
                        "测试客户",
                        "test@example.com",
                        LicenseVersion.BASIC,
                        LocalDateTime.now().minusDays(30),
                        LocalDateTime.now().minusDays(1),
                        -1,
                        -1,
                        null);
        String json = toJson(license);

        LicenseValidator validator = new LicenseValidator(publicKeyBase64);
        assertThatThrownBy(() -> validator.loadFromJson(json))
                .isInstanceOf(LicenseException.class)
                .hasMessageContaining("已过期");
    }

    @Test
    @DisplayName("错误公钥（其他系统）验证失败")
    void wrongPublicKeyRejected() throws Exception {
        LicenseInfo license = basicLicense();
        String json = toJson(license);

        // 使用另一对密钥的公钥验证
        KeyPair otherKeyPair = RsaUtil.generateKeyPair();
        String otherPublicKey = RsaUtil.publicKeyToBase64(otherKeyPair.getPublic());
        LicenseValidator validator = new LicenseValidator(otherPublicKey);
        assertThatThrownBy(() -> validator.loadFromJson(json)).isInstanceOf(LicenseException.class);
    }

    @Test
    @DisplayName("降级验证器：基础功能可用，商业功能拒绝")
    void degradedValidatorBehavior() {
        LicenseValidator degraded = LicenseValidator.degraded();
        assertThat(degraded.isValidated()).isFalse();
        assertThat(degraded.isFeatureEnabled("jwt_auth")).isFalse();
        assertThat(degraded.isFeatureEnabled("any")).isFalse();
        assertThat(degraded.checkUserLimit(1)).isFalse();
        assertThatThrownBy(degraded::getLicenseInfo).isInstanceOf(IllegalStateException.class);
    }
}
