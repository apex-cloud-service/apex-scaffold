package com.apex.scaffold.interfaces.aspect;

import com.apex.scaffold.common.annotation.DataScope;
import com.apex.scaffold.common.context.DataScopeContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 数据权限切面（部门级）。
 *
 * <p>拦截标注 {@link DataScope} 的 Service 方法（C3 数据权限/部门体系），
 * 在方法执行前装配当前用户的数据权限部门范围到 {@link DataScopeContext}，
 * 业务层读取后构造 {@code dept_id} 过滤条件；方法执行后释放上下文。</p>
 *
 * <p><b>装配来源</b>：优先从 MDC（{@code apex.deptId}，由认证过滤器在登录时写入）
 * 读取；缺失时记录 WARN 并放行（未配置部门的数据不做部门过滤）。
 * 完整的数据权限 SQL 注入可扩展 MyBatis 拦截器消费 {@code DataScopeContext}。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 * @see DataScope
 * @see DataScopeContext
 */
@Slf4j
@Aspect
@Component
public class DataScopeAspect {

    /** MDC 中部门 ID 的键名（与认证过滤器写入保持一致） */
    private static final String MDC_DEPT_ID = "apex.deptId";

    /**
     * 环绕通知：装配/释放数据权限上下文。
     *
     * @param joinPoint 连接点（@DataScope 标注方法）
     * @return 方法执行结果
     * @throws Throwable 方法异常原样抛出
     */
    @Around("@annotation(com.apex.scaffold.common.annotation.DataScope)")
    public Object applyDataScope(ProceedingJoinPoint joinPoint) throws Throwable {
        DataScope dataScope = findAnnotation(joinPoint);
        Long deptId = resolveDeptId();
        if (deptId != null) {
            DataScopeContext.setDeptId(deptId);
            log.debug("数据权限装配: method={}, deptId={}, alias={}",
                    joinPoint.getSignature().getName(), deptId, dataScope.deptAlias());
        } else {
            log.warn("数据权限未装配（MDC 无 deptId）: method={}，将不做部门过滤",
                    joinPoint.getSignature().getName());
        }
        try {
            return joinPoint.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }

    /**
     * 从方法签名解析 {@link DataScope} 注解。
     *
     * @param joinPoint 连接点
     * @return 注解实例（方法上不存在时返回空注解）
     */
    private DataScope findAnnotation(ProceedingJoinPoint joinPoint) {
        var method = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getMethod();
        return method.getAnnotation(DataScope.class);
    }

    /**
     * 从 MDC 解析当前用户部门 ID。
     *
     * @return 部门 ID，未配置时返回 {@code null}
     */
    private Long resolveDeptId() {
        String value = org.slf4j.MDC.get(MDC_DEPT_ID);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("MDC deptId 格式非法: {}", value);
            return null;
        }
    }
}
