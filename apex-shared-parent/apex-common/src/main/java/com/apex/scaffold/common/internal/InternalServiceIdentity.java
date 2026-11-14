package com.apex.scaffold.common.internal;

import java.security.Principal;

/**
 * 已验证内部调用身份；仅由内部认证过滤器写入，不使用客户端提供的普通身份头。
 *
 * @param serviceName 已认证调用方
 * @param tenantId 签名绑定的租户；为空只允许明确的无租户端点
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public record InternalServiceIdentity(String serviceName, String tenantId) implements Principal {
    public static final String ATTR_IDENTITY = InternalServiceIdentity.class.getName();

    @Override
    public String getName() {
        return serviceName;
    }
}
