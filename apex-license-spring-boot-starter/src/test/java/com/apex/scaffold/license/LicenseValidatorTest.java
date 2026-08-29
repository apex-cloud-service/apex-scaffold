package com.apex.scaffold.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * License 验证器测试。
 *
 * <p>覆盖：签名/指纹/有效期校验、降级模式、运行期过期即时失效、
 * 运行期刷新（续期生效 / 失败降级）。</p>
 *
 * @author Apex Team
 * @version 2.2.0
 */
class LicenseValidatorTest {

    private KeyPair keyPair;
    private String publicKeyBase64;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = RsaUtil.generateKeyPair();
        publicKeyBase64 = RsaUtil.publicKeyToBase64(keyPair.getPublic());
    }

    private LicenseInfo basicLicense() throws Exception {
        return TestLicenses.signed(
                keyPair,
                LicenseVersion.BASIC,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(365),
                "jwt_auth",
                "webflux");
    }

    @Test
    @DisplayName("合法 License 验证通过，功能清单生效")
    void validLicensePasses() throws Exception {
        LicenseValidator validator = new LicenseValidator(publicKeyBase64);
        validator.loadFromJson(TestLicenses.toJson(basicLicense()));

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
        String tampered = TestLicenses.toJson(basicLicense()).replace("测试客户", "恶意篡改");

        LicenseValidator validator = new LicenseValidator(publicKeyBase64);
        assertThatThrownBy(() -> validator.loadFromJson(tampered))
                .isInstanceOf(LicenseException.class)
                .hasMessageContaining("签名验证失败");
    }

    @Test
    @DisplayName("过期 License 被拒绝")
    void expiredLicenseRejected() throws Exception {
        LicenseInfo expired = TestLicenses.signed(
                keyPair,
                LicenseVersion.BASIC,
                LocalDateTime.now().minusDays(30),
                LocalDateTime.now().minusDays(1),
                "jwt_auth");

        LicenseValidator validator = new LicenseValidator(publicKeyBase64);
        assertThatThrownBy(() -> validator.loadFromJson(TestLicenses.toJson(expired)))
                .isInstanceOf(LicenseException.class)
                .hasMessageContaining("已过期");
    }

    @Test
    @DisplayName("错误公钥（其他系统）验证失败")
    void wrongPublicKeyRejected() throws Exception {
        KeyPair otherKeyPair = RsaUtil.generateKeyPair();
        LicenseValidator validator =
                new LicenseValidator(RsaUtil.publicKeyToBase64(otherKeyPair.getPublic()));
        assertThatThrownBy(() -> validator.loadFromJson(TestLicenses.toJson(basicLicense())))
                .isInstanceOf(LicenseException.class);
    }

    @Test
    @DisplayName("降级验证器：基础功能可用，商业功能拒绝")
    void degradedValidatorBehavior() {
        LicenseValidator degraded = LicenseValidator.degraded();
        assertThat(degraded.isValidated()).isFalse();
        assertThat(degraded.isDegraded()).isTrue();
        assertThat(degraded.isFeatureEnabled("jwt_auth")).isFalse();
        assertThat(degraded.isFeatureEnabled("any")).isFalse();
        assertThat(degraded.checkUserLimit(1)).isFalse();
        assertThatThrownBy(degraded::getLicenseInfo).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("运行期过期：License 到期后无需重启，功能检查即时拒绝")
    void runtimeExpiryDegradesImmediately() throws Exception {
        LicenseValidator validator = new LicenseValidator(publicKeyBase64);
        validator.loadFromJson(TestLicenses.toJson(basicLicense()));
        assertThat(validator.isFeatureEnabled("jwt_auth")).isTrue();

        // 运行期将过期时间改为过去（直接模拟到期）
        LicenseInfo info = validator.getLicenseInfo();
        info.setValidTo(LocalDateTime.now().minusMinutes(1));

        assertThat(validator.isFeatureEnabled("jwt_auth")).isFalse();
        assertThat(validator.isValidated()).isFalse();
        assertThat(validator.isDegraded()).isTrue();
    }

    @Test
    @DisplayName("运行期刷新：续期 License 不重启即生效")
    void runtimeRefreshRestoresAuthorization() throws Exception {
        LicenseValidator validator = new LicenseValidator(publicKeyBase64);
        validator.loadFromJson(TestLicenses.toJson(basicLicense()));

        // 先降级（模拟过期）
        validator.invalidate("测试降级");
        assertThat(validator.isFeatureEnabled("jwt_auth")).isFalse();

        // 刷新为新的有效 License
        LicenseInfo renewed = TestLicenses.signed(
                keyPair,
                LicenseVersion.STANDARD,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(90),
                "jwt_auth",
                "rbac",
                "redis_cache");
        String renewedJson = TestLicenses.toJson(renewed);

        validator.refresh(() -> new LicenseValidator(publicKeyBase64).loadFromJson(renewedJson));

        assertThat(validator.isValidated()).isTrue();
        assertThat(validator.isFeatureEnabled("rbac")).isTrue();
        assertThat(validator.isFeatureEnabled("redis_cache")).isTrue();
    }

    @Test
    @DisplayName("运行期刷新失败（License 文件损坏/移除）进入降级模式")
    void runtimeRefreshFailureDegrades() throws Exception {
        LicenseValidator validator = new LicenseValidator(publicKeyBase64);
        validator.loadFromJson(TestLicenses.toJson(basicLicense()));

        validator.refresh(() -> {
            throw new IllegalStateException("License文件不存在");
        });

        assertThat(validator.isValidated()).isFalse();
        assertThat(validator.isDegraded()).isTrue();
        assertThat(validator.isFeatureEnabled("jwt_auth")).isFalse();
    }
}
