package com.apex.scaffold.interfaces.governance.platform;

/**
 * 应用部署运行模式。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum DeploymentMode {
    /** 启动时自动识别。 */
    AUTO,
    /** 普通 java -jar 或 systemd。 */
    STANDALONE,
    /** Docker Compose。 */
    COMPOSE,
    /** Kubernetes Pod。 */
    KUBERNETES
}
