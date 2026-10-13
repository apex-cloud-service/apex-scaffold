package com.apex.scaffold.interfaces.governance.config;

import com.apex.scaffold.interfaces.governance.platform.FailMode;
import com.apex.scaffold.interfaces.governance.platform.RegistrationProvider;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务治理配置。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "apex.governance")
public class GovernanceProperties {
    private boolean enabled = true;
    private String deploymentMode = "auto";
    private final AutoDetection autoDetection = new AutoDetection();
    private final Registration registration = new Registration();
    private final Mq mq = new Mq();
    private final Shutdown shutdown = new Shutdown();

    /** @return 是否启用治理协调 */
    public boolean isEnabled() {
        return enabled;
    }
    /** @param enabled 是否启用治理协调 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    /** @return 配置的部署模式 */
    public String getDeploymentMode() {
        return deploymentMode;
    }
    /** @param deploymentMode 配置的部署模式 */
    public void setDeploymentMode(String deploymentMode) {
        this.deploymentMode = deploymentMode;
    }
    /** @return 自动识别配置 */
    public AutoDetection getAutoDetection() {
        return autoDetection;
    }
    /** @return 注册配置 */
    public Registration getRegistration() {
        return registration;
    }
    /** @return MQ 配置 */
    public Mq getMq() {
        return mq;
    }
    /** @return 关停配置 */
    public Shutdown getShutdown() {
        return shutdown;
    }

    /** MQ 排空配置。 */
    public static class Mq {
        private Duration drainTimeout = Duration.ofSeconds(30);
        /** @return MQ 排空超时 */
        public Duration getDrainTimeout() {
            return drainTimeout;
        }
        /** @param value MQ 排空超时 */
        public void setDrainTimeout(Duration value) {
            this.drainTimeout = value;
        }
    }

    /** 自动识别配置。 */
    public static class AutoDetection {
        private boolean enabled = true;
        private boolean failOnAmbiguousEnvironment = true;
        /** @return 是否启用自动识别 */
        public boolean isEnabled() {
            return enabled;
        }
        /** @param enabled 是否启用自动识别 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        /** @return 冲突时是否启动失败 */
        public boolean isFailOnAmbiguousEnvironment() {
            return failOnAmbiguousEnvironment;
        }
        /** @param value 冲突时是否启动失败 */
        public void setFailOnAmbiguousEnvironment(boolean value) {
            this.failOnAmbiguousEnvironment = value;
        }
    }

    /** 注册配置。 */
    public static class Registration {
        private RegistrationProvider provider = RegistrationProvider.AUTO;
        private FailMode failMode = FailMode.AUTO;
        /** @return 注册方式 */
        public RegistrationProvider getProvider() {
            return provider;
        }
        /** @param provider 注册方式 */
        public void setProvider(RegistrationProvider provider) {
            this.provider = provider;
        }
        /** @return 失败模式 */
        public FailMode getFailMode() {
            return failMode;
        }
        /** @param failMode 失败模式 */
        public void setFailMode(FailMode failMode) {
            this.failMode = failMode;
        }
    }

    /** 关停配置。 */
    public static class Shutdown {
        private Duration totalTimeout = Duration.ofSeconds(90);
        private Duration readinessWithdrawTimeout = Duration.ofSeconds(5);
        private boolean forceShutdownAfterTimeout = true;
        /** @return 总关停超时 */
        public Duration getTotalTimeout() {
            return totalTimeout;
        }
        /** @param value 总关停超时 */
        public void setTotalTimeout(Duration value) {
            this.totalTimeout = value;
        }
        /** @return readiness 摘流等待时间 */
        public Duration getReadinessWithdrawTimeout() {
            return readinessWithdrawTimeout;
        }
        /** @param value readiness 摘流等待时间 */
        public void setReadinessWithdrawTimeout(Duration value) {
            this.readinessWithdrawTimeout = value;
        }
        /** @return 是否超时强制停止 */
        public boolean isForceShutdownAfterTimeout() {
            return forceShutdownAfterTimeout;
        }
        /** @param value 是否超时强制停止 */
        public void setForceShutdownAfterTimeout(boolean value) {
            this.forceShutdownAfterTimeout = value;
        }
    }
}
