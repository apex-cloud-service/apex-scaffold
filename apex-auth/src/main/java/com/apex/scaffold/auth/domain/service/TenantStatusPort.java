package com.apex.scaffold.auth.domain.service;

import reactor.core.publisher.Mono;

/**
 * 租户认证期状态校验端口（统一认证收口 A6）。
 *
 * <p>登录前置校验租户存在性、状态机与有效期：租户未知、停用/冻结/归档或过期时
 * 拒绝登录。实现侧（远程租户中心适配器）负责缓存、降级口径与平台默认租户白名单。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface TenantStatusPort {

    /**
     * 校验租户可登录性。
     *
     * @param tenantCode 登录请求声明的租户编码
     * @return 通过时为完成信号；拒绝时以 {@link com.apex.scaffold.common.exception.BusinessException}
     *         （UNAUTHORIZED/FORBIDDEN）终止登录链
     */
    Mono<Void> validateLoginTenant(String tenantCode);
}
