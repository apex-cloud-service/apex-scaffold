package com.apex.scaffold.auth.application.aspect;

import com.apex.scaffold.common.annotation.RequireAdminRole;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.lang.reflect.Method;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 管理员角色校验AOP切面
 *
 * <p>拦截被 {@link RequireAdminRole} 注解标记的方法，校验当前登录用户是否具备
 * 注解声明的管理员角色之一。与 {@link ResourcePermissionAspect} 的资源实例级校验不同，
 * 本切面只做角色层面的门禁判断，不解析任何资源ID，适用于解锁账户、终止会话、
 * 清理审计日志、分配角色权限等后台高危操作。</p>
 *
 * <p>工作流程：</p>
 * <ol>
 *   <li>从Security上下文获取当前用户的 {@link Authentication}</li>
 *   <li>校验用户是否已登录</li>
 *   <li>判断用户权限集合中是否包含注解声明的任一角色</li>
 *   <li>校验通过则继续执行方法，否则抛出 {@link BusinessException}（403）</li>
 * </ol>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Aspect
@Component
@Slf4j
public class AdminRoleAspect {

    /**
     * 环绕通知：管理员角色校验
     *
     * @param joinPoint 切点
     * @return 方法执行结果（Mono），未登录或角色不匹配时返回错误Mono
     * @throws Throwable 目标方法执行过程中抛出的其他异常
     */
    @Around("@annotation(com.apex.scaffold.common.annotation.RequireAdminRole)")
    public Object checkAdminRole(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireAdminRole annotation = method.getAnnotation(RequireAdminRole.class);

        Set<String> requiredRoles = Set.of(annotation.roles());
        String errorMessage = annotation.errorMessage();

        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .flatMap(authentication -> {
                    if (authentication == null || !authentication.isAuthenticated()) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "未登录"));
                    }

                    boolean hasRequiredRole = authentication.getAuthorities().stream()
                            .map(authority -> authority.getAuthority())
                            .anyMatch(requiredRoles::contains);

                    if (!hasRequiredRole) {
                        log.warn(
                                "管理员角色校验失败: userId={}, method={}, requiredRoles={}",
                                authentication.getName(),
                                method.getName(),
                                requiredRoles);
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, errorMessage));
                    }

                    try {
                        Object result = joinPoint.proceed();
                        if (result instanceof Mono) {
                            return (Mono<?>) result;
                        } else {
                            return Mono.just(result);
                        }
                    } catch (Throwable e) {
                        return Mono.error(e);
                    }
                })
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "未登录")));
    }
}
