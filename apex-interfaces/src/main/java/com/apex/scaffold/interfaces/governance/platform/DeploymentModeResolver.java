package com.apex.scaffold.interfaces.governance.platform;

import com.apex.scaffold.interfaces.governance.config.GovernanceProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 启动期部署模式解析器。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class DeploymentModeResolver {
    private static final String EXPLICIT_ENV = "APEX_GOVERNANCE_DEPLOYMENT_MODE";
    private static final String PLATFORM_ENV = "APEX_DEPLOYMENT_PLATFORM";
    private static final String KUBERNETES_SERVICE_HOST = "KUBERNETES_SERVICE_HOST";
    private static final String SERVICE_ACCOUNT_TOKEN = "/var/run/secrets/kubernetes.io/serviceaccount/token";

    /**
     * 解析最终部署模式。
     *
     * @param properties 治理配置
     * @param environment 环境变量
     * @return 最终部署模式
     */
    public DeploymentMode resolve(GovernanceProperties properties, Map<String, String> environment) {
        String explicit = firstNonBlank(environment.get(EXPLICIT_ENV), properties.getDeploymentMode());
        if (isExplicit(explicit)) {
            return parse(explicit);
        }
        if (!properties.getAutoDetection().isEnabled()) {
            return DeploymentMode.STANDALONE;
        }
        List<DeploymentMode> detected = detect(environment);
        if (detected.size() > 1 && properties.getAutoDetection().isFailOnAmbiguousEnvironment()) {
            throw new IllegalStateException("检测到多个部署环境证据: " + detected);
        }
        return detected.isEmpty() ? DeploymentMode.STANDALONE : detected.get(0);
    }

    private List<DeploymentMode> detect(Map<String, String> environment) {
        List<DeploymentMode> modes = new ArrayList<>();
        if (hasKubernetesEvidence(environment)) {
            modes.add(DeploymentMode.KUBERNETES);
        }
        if ("compose".equalsIgnoreCase(environment.get(PLATFORM_ENV))) {
            modes.add(DeploymentMode.COMPOSE);
        }
        return modes;
    }

    private boolean hasKubernetesEvidence(Map<String, String> environment) {
        return hasText(environment.get(KUBERNETES_SERVICE_HOST)) || Files.exists(Path.of(SERVICE_ACCOUNT_TOKEN));
    }

    private boolean isExplicit(String value) {
        return hasText(value) && !"auto".equalsIgnoreCase(value);
    }

    private DeploymentMode parse(String value) {
        try {
            return DeploymentMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的部署模式: " + value, exception);
        }
    }

    private String firstNonBlank(String first, String second) {
        return hasText(first) ? first : second;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
