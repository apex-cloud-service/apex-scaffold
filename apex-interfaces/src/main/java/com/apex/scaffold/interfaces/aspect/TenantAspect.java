package com.apex.scaffold.interfaces.aspect;

import com.apex.scaffold.common.annotation.TenantAware;
import com.apex.scaffold.common.context.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * 多租户切面（C10 多租户）。
 *
 * <p>拦截标注 {@link TenantAware} 的方法：若首个参数为 {@link ServerRequest}，
 * 从请求头 {@code X-Tenant-Id} 读取租户 ID 并装配到 {@link TenantContext}，
 * 业务层读取后按租户隔离；方法执行后释放上下文。</p>
 *
 * <p><b>隔离策略说明</b>：本切面提供租户上下文装配机制；完整的数据隔离可
 * 扩展 MyBatis-Plus {@code TenantLineInnerInterceptor} 自动追加
 * {@code tenant_id} 过滤条件（消费 {@code TenantContext}）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 * @see TenantAware
 * @see TenantContext
 */
@Slf4j
@Aspect
@Component
public class TenantAspect {

    /** 租户 ID 请求头名 */
    public static final String TENANT_HEADER = "X-Tenant-Id";

    /**
     * 环绕通知：装配/释放租户上下文。
     *
     * @param joinPoint 连接点（@TenantAware 标注方法）
     * @return 方法执行结果
     * @throws Throwable 方法异常原样抛出
     */
    @Around("@annotation(com.apex.scaffold.common.annotation.TenantAware)")
    public Object applyTenant(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            for (Object arg : joinPoint.getArgs()) {
                if (arg instanceof ServerRequest request) {
                    String tenantId = request.headers().firstHeader(TENANT_HEADER);
                    if (tenantId != null && !tenantId.isBlank()) {
                        TenantContext.setTenantId(tenantId);
                    }
                    break;
                }
            }
            return joinPoint.proceed();
        } finally {
            TenantContext.clear();
        }
    }
}
