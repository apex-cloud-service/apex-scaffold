package com.apex.scaffold.license;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.security.KeyPair;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * License 自动配置测试。
 *
 * <p>验证：默认启用、随包 License 模板可加载、验证器创建成功、
 * 文件缺失时降级而不阻塞启动、enabled=false 强制降级（而非整体关闭）、
 * {@code @ConditionalOnLicenseFeature} 条件装配与切面注册。</p>
 *
 * @author Apex Team
 * @version 2.2.0
 */
class LicenseAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(LicenseAutoConfiguration.class));

    @Test
    @DisplayName("默认启用并加载随包基础版 License")
    void loadsBundledLicenseByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LicenseValidator.class);
            LicenseValidator validator = context.getBean(LicenseValidator.class);
            assertThat(validator.isValidated()).isTrue();
            // 基础版功能放行
            assertThat(validator.isFeatureEnabled("jwt_auth")).isTrue();
            // 商业功能拒绝
            assertThat(validator.isFeatureEnabled("rocketmq")).isFalse();
            // 门控切面由自动配置注册，不依赖宿主组件扫描
            assertThat(context).hasSingleBean(FeatureGateAspect.class);
            // 运行期重检调度器默认注册
            assertThat(context).hasSingleBean(LicenseRevalidationScheduler.class);
        });
    }

    @Test
    @DisplayName("enabled=false 强制降级（验证器与门控切面仍然注册，不可整体关闭）")
    void enabledFalseForcesDegradedMode() {
        contextRunner.withPropertyValues("apex.license.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(LicenseValidator.class);
            assertThat(context).hasSingleBean(FeatureGateAspect.class);
            LicenseValidator validator = context.getBean(LicenseValidator.class);
            // 强制降级：全部功能（含基础功能）拒绝
            assertThat(validator.isValidated()).isFalse();
            assertThat(validator.isDegraded()).isTrue();
            assertThat(validator.isFeatureEnabled("jwt_auth")).isFalse();
        });
    }

    @Test
    @DisplayName("License 文件不存在时降级而不阻塞启动")
    void degradesGracefullyWhenLicenseMissing() {
        contextRunner
                .withPropertyValues("apex.license.file-path=classpath:license/not-exist.json")
                .run(context -> {
                    assertThat(context).hasSingleBean(LicenseValidator.class);
                    LicenseValidator validator = context.getBean(LicenseValidator.class);
                    // 降级模式：未验证，商业功能不可用
                    assertThat(validator.isValidated()).isFalse();
                    assertThat(validator.isFeatureEnabled("jwt_auth")).isFalse();
                });
    }

    @Test
    @DisplayName("公钥缺失时降级而不阻塞启动")
    void degradesGracefullyWhenPublicKeyMissing() {
        contextRunner
                .withPropertyValues("apex.license.public-key-file=classpath:license/not-exist.key")
                .run(context -> {
                    assertThat(context).hasSingleBean(LicenseValidator.class);
                    assertThat(context.getBean(LicenseValidator.class).isValidated())
                            .isFalse();
                });
    }

    @Test
    @DisplayName("recheck-interval=0 时调度器为空操作（Bean 仍注册）")
    void schedulerIsNoOpWhenRecheckDisabled() {
        contextRunner
                .withPropertyValues("apex.license.recheck-interval=0")
                .run(context -> assertThat(context).hasSingleBean(LicenseRevalidationScheduler.class));
    }

    // ==================== @ConditionalOnLicenseFeature 条件装配 ====================

    /** 测试用条件装配目标：仅当 License 授权 rbac 时装配。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnLicenseFeature("rbac")
    static class RbacGatedConfig {

        @Bean
        String rbacMarker() {
            return "rbac";
        }
    }

    @Test
    @DisplayName("基础版 License 未授权 rbac：商业组件不装配")
    void conditionBlocksUnauthorizedFeature() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LicenseAutoConfiguration.class))
                .withUserConfiguration(RbacGatedConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(LicenseValidator.class);
                    assertThat(context).doesNotHaveBean("rbacMarker");
                });
    }

    @Test
    @DisplayName("License 授权对应功能：商业组件正常装配")
    void conditionAllowsAuthorizedFeature() throws Exception {
        KeyPair keyPair = RsaUtil.generateKeyPair();
        LicenseInfo license = TestLicenses.signed(
                keyPair,
                LicenseVersion.STANDARD,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(365),
                "jwt_auth",
                "rbac");

        // 将已签名 License 写入临时文件（含 rbac 授权）
        java.nio.file.Path licenseFile =
                java.nio.file.Files.createTempFile("apex-license-test", ".json");
        java.nio.file.Files.write(
                licenseFile, TestLicenses.toJson(license).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        try {
            new ApplicationContextRunner()
                    .withPropertyValues(
                            // 置空 publicKeyFile（默认指向随包公钥且优先级高于内联公钥）
                            "apex.license.public-key-file=",
                            "apex.license.public-key=" + RsaUtil.publicKeyToBase64(keyPair.getPublic()),
                            "apex.license.file-path=" + licenseFile.toAbsolutePath())
                    .withConfiguration(AutoConfigurations.of(LicenseAutoConfiguration.class))
                    .withUserConfiguration(RbacGatedConfig.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(LicenseValidator.class);
                        assertThat(context.getBean(LicenseValidator.class).isValidated()).isTrue();
                        // 授权 License 在场：rbac 商业组件装配
                        assertThat(context).hasBean("rbacMarker");
                    });
        } finally {
            java.nio.file.Files.deleteIfExists(licenseFile);
        }
    }
}
