package com.apex.scaffold.interfaces.aspect;

import com.apex.scaffold.common.annotation.TenantAware;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.interfaces.security.AuthenticatedUser;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 多租户切面（C10 多租户）。
 *
 * <p>拦截标注 {@link TenantAware} 的方法：从请求中的可信认证主体读取租户 ID，
 * 校验请求头 {@code X-Tenant-Id}（如存在）后装配到 {@link TenantContext}，
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

    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /**
     * 环绕通知：装配/释放租户上下文。
     *
     * @param joinPoint 连接点（@TenantAware 标注方法）
     * @return 方法执行结果
     * @throws Throwable 方法异常原样抛出
     */
    @Around("@annotation(com.apex.scaffold.common.annotation.TenantAware)")
    public Object applyTenant(ProceedingJoinPoint joinPoint) throws Throwable {
        String authenticatedTenant = null;
        try {
            for (Object arg : joinPoint.getArgs()) {
                if (arg instanceof ServerRequest request) {
                    Object authenticatedUserAttribute =
                            request.exchange().getAttributes().get("apex.authenticatedUser");
                    AuthenticatedUser authenticatedUser =
                            authenticatedUserAttribute instanceof AuthenticatedUser user ? user : null;
                    authenticatedTenant = authenticatedUser == null ? null : authenticatedUser.tenantId();
                    String requestedTenant = request.headers().firstHeader(TENANT_HEADER);
                    if (authenticatedTenant == null || authenticatedTenant.isBlank()) {
                        throw new BusinessException(ResultCode.FORBIDDEN, "缺少可信租户身份");
                    }
                    if (requestedTenant != null
                            && !requestedTenant.isBlank()
                            && !TENANT_ID_PATTERN.matcher(requestedTenant).matches()) {
                        throw new BusinessException(ResultCode.FORBIDDEN, "请求头租户 ID 仅允许 1-64 位字母、数字、点、下划线或短横线");
                    }
                    if (!TENANT_ID_PATTERN.matcher(authenticatedTenant).matches()) {
                        throw new BusinessException(ResultCode.FORBIDDEN, "认证用户租户 ID 非法");
                    }
                    if (requestedTenant != null
                            && !requestedTenant.isBlank()
                            && !authenticatedTenant.equals(requestedTenant)) {
                        throw new BusinessException(ResultCode.FORBIDDEN, "无权访问指定租户");
                    }
                    TenantContext.setTenantId(authenticatedTenant);
                    break;
                }
            }
            if (authenticatedTenant == null) {
                throw new BusinessException(ResultCode.FORBIDDEN, "缺少可信租户请求");
            }
            Object result = joinPoint.proceed();
            if (result instanceof Mono<?> mono && authenticatedTenant != null) {
                return TenantContext.withTenant(mono).contextWrite(TenantContext.withTenant(authenticatedTenant));
            }
            if (result instanceof Flux<?> flux && authenticatedTenant != null) {
                return TenantContext.withTenant(flux).contextWrite(TenantContext.withTenant(authenticatedTenant));
            }
            return result;
        } finally {
            TenantContext.clear();
        }
    }
}
