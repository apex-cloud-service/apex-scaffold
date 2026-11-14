package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Keycloak 配置离线测试。
 *
 * <p>加载真实属性绑定基础设施并创建真实 Admin Client，不请求令牌或调用远端 API，
 * 因此仅验证配置和客户端装配，不代表 Keycloak 认证通过。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 2.4.0
 */
@DisplayName("Keycloak 配置离线测试")
class KeycloakConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(KeycloakConfig.class)
            .withPropertyValues(
                    "keycloak.enabled=true",
                    "keycloak.server-url=http://127.0.0.1:1",
                    "keycloak.realm=test-realm",
                    "keycloak.admin-username=test-admin",
                    "keycloak.admin-password=offline-test-password",
                    "keycloak.admin-client-id=admin-cli",
                    "keycloak.client-id=test-client",
                    "keycloak.client-secret=offline-test-secret",
                    "keycloak.user-sync-enabled=false",
                    "keycloak.frontend-callback-url=http://localhost:3000/auth/callback");

    /** 验证启用集成时创建唯一配置 Bean。 */
    @Test
    @DisplayName("启用集成时创建配置 Bean")
    void keycloakConfigShouldBeCreated() {
        contextRunner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(KeycloakConfig.class));
    }

    /** 验证真实属性绑定覆盖全部配置字段及非默认同步开关。 */
    @Test
    @DisplayName("配置属性应被正确绑定")
    void configPropertiesShouldBeBindCorrectly() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            KeycloakConfig config = context.getBean(KeycloakConfig.class);
            assertThat(config.getServerUrl()).isEqualTo("http://127.0.0.1:1");
            assertThat(config.getRealm()).isEqualTo("test-realm");
            assertThat(config.getAdminUsername()).isEqualTo("test-admin");
            assertThat(config.getAdminPassword()).isEqualTo("offline-test-password");
            assertThat(config.getAdminClientId()).isEqualTo("admin-cli");
            assertThat(config.getClientId()).isEqualTo("test-client");
            assertThat(config.getClientSecret()).isEqualTo("offline-test-secret");
            assertThat(config.isUserSyncEnabled()).isFalse();
            assertThat(config.getFrontendCallbackUrl()).isEqualTo("http://localhost:3000/auth/callback");
        });
    }

    /** 验证真实客户端离线创建，并由上下文关闭其资源。 */
    @Test
    @DisplayName("离线创建真实 Keycloak Admin Client")
    void keycloakAdminClientShouldBeCreated() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(Keycloak.class);
            assertThat(context.getBean(Keycloak.class).isClosed()).isFalse();
        });
    }

    /** 验证显式关闭集成时既不绑定必填属性，也不创建客户端。 */
    @Test
    void disabledIntegrationDoesNotCreateBeans() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(KeycloakConfig.class)
                .withPropertyValues("keycloak.enabled=false")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(KeycloakConfig.class)
                        .doesNotHaveBean(Keycloak.class));
    }

    /** 验证启用集成但必填属性为空时绑定失败。 */
    @Test
    void missingRequiredPropertyFailsBinding() {
        contextRunner.withPropertyValues("keycloak.realm=").run(context -> assertThat(context)
                .hasFailed());
    }
}
