package com.apex.scaffold.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 多租户感知注解（C10 多租户）。
 *
 * <p>标注在需要租户隔离的 Service/Handler 方法上，由 {@code TenantAspect}
 * 从可信认证身份装配租户上下文到 {@code TenantContext}，请求头 {@code X-Tenant-Id} 仅用于选择并校验租户，
 * 业务查询按租户 ID 过滤。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * @TenantAware
 * public Mono<PageResponse<OrderDTO>> listOrders(...) {
 *     String tenantId = TenantContext.currentTenantId().orElseThrow(...);
 *     // 按 tenantId 过滤查询
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 * @see com.apex.scaffold.common.context.TenantContext
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantAware {}
