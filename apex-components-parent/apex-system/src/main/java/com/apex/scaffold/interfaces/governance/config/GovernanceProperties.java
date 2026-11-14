package com.apex.scaffold.interfaces.governance.config;

import com.apex.scaffold.interfaces.governance.platform.FailMode;
import com.apex.scaffold.interfaces.governance.platform.RegistrationProvider;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务治理配置。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "apex.governance")
public class GovernanceProperties {
    private boolean enabled = true;
    private String deploymentMode = "auto";
    private final AutoDetection autoDetection = new AutoDetection();
    private final Registration registration = new Registration();
    private final Mq mq = new Mq();
    private final Shutdown shutdown = new Shutdown();

    /** MQ 排空配置。 */
    @Getter
    @Setter
    public static class Mq {
        private Duration drainTimeout = Duration.ofSeconds(30);
    }

    /** 自动识别配置。 */
    @Getter
    @Setter
    public static class AutoDetection {
        private boolean enabled = true;
        private boolean failOnAmbiguousEnvironment = true;
    }

    /** 注册配置。 */
    @Getter
    @Setter
    public static class Registration {
        private RegistrationProvider provider = RegistrationProvider.AUTO;
        private FailMode failMode = FailMode.AUTO;
    }

    /** 关停配置。 */
    @Getter
    @Setter
    public static class Shutdown {
        private Duration totalTimeout = Duration.ofSeconds(90);
        private Duration readinessWithdrawTimeout = Duration.ofSeconds(5);
        private boolean forceShutdownAfterTimeout = true;
    }
}
