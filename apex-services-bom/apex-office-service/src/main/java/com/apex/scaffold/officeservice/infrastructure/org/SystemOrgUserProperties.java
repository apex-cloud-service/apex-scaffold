package com.apex.scaffold.officeservice.infrastructure.org;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 系统服务内部组织接口配置。
 *
 * <p>组织同步需要把外部通讯录成员解析为本租户真实系统用户，该能力仅由
 * {@code apex-system-service} 的内部接口提供。未配置基址时解析整体跳过，
 * 成员同步仍会写入镜像与映射，只是不绑定系统用户。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.6.0
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "office.organization")
public class SystemOrgUserProperties {

    /** 系统服务基址，集群内直连地址（如 {@code http://apex-system-service:8082}）。 */
    private String systemBaseUrl;

    /** 单次解析请求超时。 */
    private Duration timeout = Duration.ofSeconds(3);
}
