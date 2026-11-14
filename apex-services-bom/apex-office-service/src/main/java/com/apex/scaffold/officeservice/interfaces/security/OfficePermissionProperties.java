package com.apex.scaffold.officeservice.interfaces.security;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 授权快照配置；每个可信 JWT 租户必须显式提供同租户 reader 访问令牌。
 *
 * <p>目标 ID 直接使用 JWT userId，要求 office 与 auth 共享 userId/tenantId 命名空间；
 * 不支持 accountId 替代或隐式跨中心映射。未接入的租户拒绝受权限注解保护的调用。
 * 配置由 {@link OfficePermissionClient} 在订阅查询时校验，错误配置不会降级放行。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "office.security.authorization")
public class OfficePermissionProperties {
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
