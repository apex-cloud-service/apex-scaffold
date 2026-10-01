package com.apex.scaffold.interfaces.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 主应用认证配置属性。
 *
 * <p>登录前使用服务端配置的租户建立可信租户上下文，避免从客户端请求中选择租户。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.auth")
public class AuthProperties {

    /**
     * 普通登录使用的服务端租户 ID。
     */
    private String loginTenantId;
}
