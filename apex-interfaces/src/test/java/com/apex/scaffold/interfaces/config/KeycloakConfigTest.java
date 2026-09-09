package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link KeycloakConfig} 的集成测试类
 *
 * <p>测试 Keycloak 配置类的属性绑定和 Bean 初始化。
 *
 * <p><b>注意</b>：此测试需要真实的 Keycloak 服务器环境，因此默认禁用。
 * 要运行此测试，需要：
 * <ol>
 *   <li>启动 Keycloak 服务器（默认 http://localhost:8180）</li>
 *   <li>创建 test-realm 并配置 admin-cli 客户端</li>
 *   <li>移除 @Disabled 注解</li>
 * </ol>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.4.0
 */
@Disabled("需要真实的 Keycloak 服务器环境，作为集成测试在 CI/CD 中运行")
@DisplayName("KeycloakConfig 集成测试")
@SpringBootTest(classes = KeycloakConfig.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "keycloak.enabled=true",
            "keycloak.server-url=http://localhost:8180",
            "keycloak.realm=test-realm",
            "keycloak.admin-username=admin",
            "keycloak.admin-password=admin123",
            "keycloak.admin-client-id=admin-cli",
            "keycloak.client-id=test-client",
            "keycloak.client-secret=test-secret",
            "keycloak.user-sync-enabled=true",
            "keycloak.frontend-callback-url=http://localhost:3000/auth/callback"
        })
class KeycloakConfigTest {

    @Autowired(required = false)
    private KeycloakConfig keycloakConfig;

    @Autowired(required = false)
    private Keycloak keycloakAdminClient;

    /**
     * 验证 KeycloakConfig Bean 被正确创建
     */
    @Test
    @DisplayName("KeycloakConfig Bean 应被正确创建")
    void keycloakConfigShouldBeCreated() {
        assertThat(keycloakConfig).isNotNull();
    }

    /**
     * 验证配置属性被正确绑定
     */
    @Test
    @DisplayName("配置属性应被正确绑定")
    void configPropertiesShouldBeBindCorrectly() {
        assertThat(keycloakConfig).isNotNull();
        assertThat(keycloakConfig.getServerUrl()).isEqualTo("http://localhost:8180");
        assertThat(keycloakConfig.getRealm()).isEqualTo("test-realm");
        assertThat(keycloakConfig.getAdminUsername()).isEqualTo("admin");
        assertThat(keycloakConfig.getAdminPassword()).isEqualTo("admin123");
        assertThat(keycloakConfig.getAdminClientId()).isEqualTo("admin-cli");
        assertThat(keycloakConfig.getClientId()).isEqualTo("test-client");
        assertThat(keycloakConfig.getClientSecret()).isEqualTo("test-secret");
        assertThat(keycloakConfig.isUserSyncEnabled()).isTrue();
        assertThat(keycloakConfig.getFrontendCallbackUrl()).isEqualTo("http://localhost:3000/auth/callback");
    }

    /**
     * 验证 Keycloak Admin Client Bean 被正确创建
     */
    @Test
    @DisplayName("Keycloak Admin Client Bean 应被正确创建")
    void keycloakAdminClientShouldBeCreated() {
        assertThat(keycloakAdminClient).isNotNull();
    }
}
