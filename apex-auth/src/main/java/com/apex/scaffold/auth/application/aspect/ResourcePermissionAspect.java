package com.apex.scaffold.auth.application.aspect;

import com.apex.scaffold.auth.domain.service.ResourcePermissionService;
import com.apex.scaffold.auth.domain.service.ResourcePermissionService.PermissionContext;
import com.apex.scaffold.common.annotation.RequireResourcePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 资源权限AOP切面
 *
 * <p>拦截被 {@link RequireResourcePermission} 注解标记的方法，执行资源级权限检查。</p>
 *
 * <p>工作流程：</p>
 * <ol>
 *   <li>从Security上下文获取当前用户信息</li>
 *   <li>从方法参数或SpEL表达式解析资源ID</li>
 *   <li>调用 {@link ResourcePermissionService} 执行权限检查</li>
 *   <li>检查通过则继续执行方法，否则抛出权限异常</li>
 * </ol>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Aspect
@Component
@RequiredArgsConstructor
public class ResourcePermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(ResourcePermissionAspect.class);

    private final ResourcePermissionService permissionService;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * 环绕通知：资源权限检查
     */
    @Around("@annotation(com.apex.scaffold.common.annotation.RequireResourcePermission)")
    public Object checkResourcePermission(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireResourcePermission annotation = method.getAnnotation(RequireResourcePermission.class);

        // 获取资源类型和所需权限
        String resourceType = annotation.resourceType();
        String requiredPermission = annotation.permission();
        String errorMessage = annotation.errorMessage();

        // 解析资源ID
        String resourceId = resolveResourceId(annotation, joinPoint);

        if (resourceId == null || resourceId.isEmpty()) {
            log.error("无法解析资源ID: method={}, resourceType={}", method.getName(), resourceType);
            throw new BusinessException(ResultCode.PARAM_ERROR, "无效的资源ID");
        }

        log.debug(
                "资源权限检查: resourceType={}, resourceId={}, permission={}", resourceType, resourceId, requiredPermission);

        // 从Security上下文获取用户信息并执行权限检查
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .flatMap(authentication -> {
                    if (authentication == null || !authentication.isAuthenticated()) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "未登录"));
                    }

                    String userId = authentication.getName();

                    // 构建权限上下文
                    PermissionContext context = buildPermissionContext(authentication, joinPoint);

                    // 调用领域权限服务执行检查，未通过则拒绝访问
                    return permissionService
                            .hasPermission(userId, resourceType, resourceId, requiredPermission, context)
                            .flatMap(hasPermission -> {
                                if (!hasPermission) {
                                    log.warn(
                                            "资源权限检查失败: userId={}, resourceType={}, resourceId={}, permission={}",
                                            userId,
                                            resourceType,
                                            resourceId,
                                            requiredPermission);
                                    return Mono.error(new BusinessException(ResultCode.FORBIDDEN, errorMessage));
                                }

                                // 权限检查通过，继续执行方法
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
                            });
                })
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "未登录")));
    }

    /**
     * 解析资源ID
     */
    private String resolveResourceId(RequireResourcePermission annotation, ProceedingJoinPoint joinPoint) {
        // 优先使用参数名称
        if (!annotation.resourceIdParam().isEmpty()) {
            return resolveResourceIdByParam(annotation.resourceIdParam(), joinPoint);
        }

        // 其次使用SpEL表达式
        if (!annotation.resourceIdExpression().isEmpty()) {
            return resolveResourceIdByExpression(annotation.resourceIdExpression(), joinPoint);
        }

        log.warn("资源权限注解配置错误: 必须指定 resourceIdParam 或 resourceIdExpression");
        return null;
    }

    /**
     * 通过参数名称解析资源ID
     */
    private String resolveResourceIdByParam(String paramName, ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName)) {
                Object arg = args[i];
                return arg != null ? arg.toString() : null;
            }
        }

        log.warn("找不到参数: paramName={}, method={}", paramName, method.getName());
        return null;
    }

    /**
     * 通过SpEL表达式解析资源ID
     */
    private String resolveResourceIdByExpression(String expression, ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Parameter[] parameters = method.getParameters();
            Object[] args = joinPoint.getArgs();

            // 构建SpEL上下文
            StandardEvaluationContext context = new StandardEvaluationContext();

            // 将方法参数添加到上下文
            for (int i = 0; i < parameters.length; i++) {
                context.setVariable(parameters[i].getName(), args[i]);
            }

            // 执行 SpEL 表达式求值（异常时返回 null）
            Object value = expressionParser.parseExpression(expression).getValue(context);
            return value != null ? value.toString() : null;

        } catch (Exception e) {
            log.error("SpEL表达式解析失败: expression={}, error={}", expression, e.getMessage());
            return null;
        }
    }

    /**
     * 构建权限上下文
     */
    private PermissionContext buildPermissionContext(Authentication authentication, ProceedingJoinPoint joinPoint) {
        // 检查是否为超级管理员
        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> "ROLE_SUPER_ADMIN".equals(auth.getAuthority()));

        // 获取客户端IP
        String clientIp = extractClientIp(joinPoint);

        // 获取设备ID（从请求头）
        String deviceId = extractDeviceId(joinPoint);

        String tenantId = authentication.getDetails() instanceof String value ? value : null;
        return new PermissionContext(isSuperAdmin, Collections.emptySet(), clientIp, deviceId, tenantId);
    }

    /**
     * 提取客户端IP
     */
    private String extractClientIp(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof ServerWebExchange) {
                ServerWebExchange exchange = (ServerWebExchange) arg;
                String ip = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
                if (ip != null && !ip.isEmpty()) {
                    return ip;
                }
                ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
                if (ip != null && !ip.isEmpty()) {
                    return ip.split(",")[0].trim();
                }
                return exchange.getRequest().getRemoteAddress() != null
                        ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : "unknown";
            }
        }
        return "unknown";
    }

    /**
     * 提取设备ID
     */
    private String extractDeviceId(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof ServerWebExchange) {
                ServerWebExchange exchange = (ServerWebExchange) arg;
                String deviceId = exchange.getRequest().getHeaders().getFirst("X-Device-ID");
                return deviceId != null ? deviceId : "unknown";
            }
        }
        return "unknown";
    }
}
