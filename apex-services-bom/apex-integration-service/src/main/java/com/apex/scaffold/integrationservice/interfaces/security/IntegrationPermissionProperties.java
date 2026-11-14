package com.apex.scaffold.integrationservice.interfaces.security;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 集成服务授权快照配置；每个可信 JWT 租户必须显式提供同租户 reader 访问令牌。
 *
 * <p>目标 ID 直接使用 JWT userId，要求 integration 与 auth 共享 userId/tenantId 命名空间；
 * 未接入的租户拒绝受权限注解保护的调用，配置错误不会降级放行。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "integration.security.authorization")
public class IntegrationPermissionProperties {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    /** 是否已确认两端共享用户及租户 ID 命名空间，默认不确认并拒绝权限查询。 */
    private boolean sharedUserNamespace;

    /** 授权中心 HTTPS 基址，不允许包含用户信息、查询参数或片段。 */
    private String baseUrl;

    /** 单次授权查询超时，必须为正且不超过客户端允许的三十秒上限。 */
    private Duration timeout = DEFAULT_TIMEOUT;

    /** 租户 ID 到同租户 reader 凭据的映射；值为敏感信息，不得写入日志。 */
    private Map<String, String> readerTokens = new HashMap<>();
}
