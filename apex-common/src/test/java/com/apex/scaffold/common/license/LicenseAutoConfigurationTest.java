package com.apex.scaffold.common.license;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * License 自动配置测试。
 *
 * <p>验证：默认启用、随包 License 模板可加载、验证器创建成功、
 * 文件缺失时降级而不阻塞启动。</p>
 *
 * @author Apex Team
 * @version 2.0.0
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
        });
    }

    @Test
    @DisplayName("显式关闭 License 校验时验证器不注册")
    void disabledWhenPropertyFalse() {
        contextRunner.withPropertyValues("apex.license.enabled=false").run(context -> assertThat(context)
                .doesNotHaveBean(LicenseValidator.class));
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
}
