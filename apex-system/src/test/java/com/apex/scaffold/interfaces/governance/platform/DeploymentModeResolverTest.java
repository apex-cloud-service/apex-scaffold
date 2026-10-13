package com.apex.scaffold.interfaces.governance.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.interfaces.governance.config.GovernanceProperties;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 部署模式解析器测试。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("DeploymentModeResolver 单元测试")
class DeploymentModeResolverTest {

    private final DeploymentModeResolver resolver = new DeploymentModeResolver();

    /**
     * 验证显式环境变量优先于配置和环境探测结果。
     */
    @Test
    @DisplayName("显式环境变量应优先选择部署模式")
    void shouldPreferExplicitEnvironmentMode() {
        GovernanceProperties properties = new GovernanceProperties();
        properties.setDeploymentMode("standalone");

        DeploymentMode mode = resolver.resolve(properties, Map.of("APEX_GOVERNANCE_DEPLOYMENT_MODE", "compose"));

        assertThat(mode).isEqualTo(DeploymentMode.COMPOSE);
    }

    /**
     * 验证 Kubernetes 环境证据可以触发自动识别。
     */
    @Test
    @DisplayName("应根据 Kubernetes 环境变量自动识别")
    void shouldDetectKubernetesFromEnvironment() {
        GovernanceProperties properties = new GovernanceProperties();

        DeploymentMode mode = resolver.resolve(properties, Map.of("KUBERNETES_SERVICE_HOST", "10.0.0.1"));

        assertThat(mode).isEqualTo(DeploymentMode.KUBERNETES);
    }

    /**
     * 验证多个平台证据冲突时可以阻止应用启动。
     */
    @Test
    @DisplayName("环境证据冲突时应启动失败")
    void shouldFailWhenEnvironmentEvidenceIsAmbiguous() {
        GovernanceProperties properties = new GovernanceProperties();

        assertThatThrownBy(() -> resolver.resolve(
                        properties,
                        Map.of(
                                "KUBERNETES_SERVICE_HOST", "10.0.0.1",
                                "APEX_DEPLOYMENT_PLATFORM", "compose")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("多个部署环境证据");
    }

    /**
     * 验证关闭自动识别时回退到 standalone。
     */
    @Test
    @DisplayName("关闭自动识别时应使用 standalone")
    void shouldUseStandaloneWhenAutoDetectionIsDisabled() {
        GovernanceProperties properties = new GovernanceProperties();
        properties.getAutoDetection().setEnabled(false);

        assertThat(resolver.resolve(properties, Map.of("KUBERNETES_SERVICE_HOST", "10.0.0.1")))
                .isEqualTo(DeploymentMode.STANDALONE);
    }
}
