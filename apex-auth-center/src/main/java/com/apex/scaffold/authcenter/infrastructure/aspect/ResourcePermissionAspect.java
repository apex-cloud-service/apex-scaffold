package com.apex.scaffold.authcenter.infrastructure.aspect;

import com.apex.scaffold.authcenter.application.service.ResourcePermissionService;
import com.apex.scaffold.common.annotation.RequireResourcePermission;
import com.apex.scaffold.common.exception.ForbiddenException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 资源级权限校验切面
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Aspect
@Component
@Order(200)
public class ResourcePermissionAspect {

    private final ResourcePermissionService permissionService;

    public ResourcePermissionAspect(ResourcePermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Around("@annotation(com.apex.scaffold.common.annotation.RequireResourcePermission)")
    public Object checkResourcePermission(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireResourcePermission annotation = method.getAnnotation(RequireResourcePermission.class);

        if (annotation == null) {
            return joinPoint.proceed();
        }

        String resourceType = annotation.resourceType();
        String resourceIdParam = annotation.resourceIdParam();
        String action = annotation.permission();
        String errorMessage = annotation.errorMessage();

        // 获取资源ID
        String resourceId = extractResourceId(joinPoint, resourceIdParam);

        if (resourceId == null) {
            log.warn("无法提取资源ID，参数名: {}", resourceIdParam);
            throw new ForbiddenException("无效的资源ID");
        }

        // 获取当前用户信息并检查权限
        return ReactiveSecurityContextHolder.getContext().flatMap(securityContext -> {
            Authentication authentication = securityContext.getAuthentication();
            String userId = authentication.getName();
            String tenantId = (String) authentication.getDetails();

            // 构建权限评估上下文
            Map<String, Object> context = buildEvaluationContext(authentication, joinPoint);

            return permissionService
                    .hasPermission(userId, tenantId, resourceType, resourceId, action, context)
                    .flatMap(hasPermission -> {
                        if (!hasPermission) {
                            log.warn(
                                    "资源权限检查失败: userId={}, resource={}:{}, action={}",
                                    userId,
                                    resourceType,
                                    resourceId,
                                    action);
                            return Mono.error(new ForbiddenException(errorMessage));
                        }

                        try {
                            Object result = joinPoint.proceed();
                            if (result instanceof Mono) {
                                return (Mono<?>) result;
                            }
                            return Mono.just(result);
                        } catch (Throwable e) {
                            return Mono.error(e);
                        }
                    });
        });
    }

    /**
     * 从方法参数中提取资源ID
     */
    private String extractResourceId(ProceedingJoinPoint joinPoint, String paramName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName)) {
                Object arg = args[i];
                return arg != null ? arg.toString() : null;
            }
        }

        return null;
    }

    /**
     * 构建权限评估上下文
     */
    private Map<String, Object> buildEvaluationContext(Authentication authentication, ProceedingJoinPoint joinPoint) {
        Map<String, Object> context = new HashMap<>();

        // 添加认证信息
        context.put(
                "authentication",
                Map.of(
                        "userId", authentication.getName(),
                        "roles", authentication.getAuthorities(),
                        "details", authentication.getDetails() != null ? authentication.getDetails() : Map.of()));

        // 添加方法参数
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            context.put(parameters[i].getName(), args[i]);
        }

        return context;
    }
}
