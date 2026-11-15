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
     * @return 校验通过时为空完成；拒绝登录时以错误信号抛出
     * @throws com.apex.scaffold.common.exception.BusinessException 拒绝登录时抛出，
     *         错误码为 {@code UNAUTHORIZED} 或 {@code FORBIDDEN}
     */
    Mono<Void> validateLoginTenant(String tenantCode);
}
