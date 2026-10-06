package com.apex.scaffold.interfaces.aspect;

import com.apex.scaffold.application.service.PermissionService;
import com.apex.scaffold.common.annotation.Logical;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.interfaces.security.AuthenticatedUser;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 权限校验切面（WebFlux 响应式版本）。
 *
 * <p>拦截标注 {@link RequirePermission} 的方法，基于当前登录用户权限进行校验。
 * 支持 AND/OR 逻辑关系的多权限组合校验和 RBAC 角色模型。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see RequirePermission
 * @see PermissionService
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RequirePermissionAspect {

    private final PermissionService permissionService;

    /**
     * 环绕通知：对标注 {@link RequirePermission} 的方法执行权限校验。
     *
     * <p>方法返回 Mono 或 Flux 时，先完成权限校验再放行原方法调用，并将租户上下文写入
     * Reactor Context；其他返回类型不支持响应式校验，直接抛出禁止访问异常。</p>
     *
     * @param joinPoint 切点，代表被拦截的目标方法调用
     * @param requirePermission 目标方法上的 {@link RequirePermission} 注解
     * @return 原方法的执行结果（Mono、Flux 或同步返回值）
     * @throws BusinessException 目标方法返回类型不是 Mono 或 Flux 时抛出
     */
    @Around("@annotation(requirePermission)")
    public Object around(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> returnType = signature.getReturnType();

        if (Mono.class.isAssignableFrom(returnType)) {
            return authorize(requirePermission).flatMap(user -> Mono.defer(() -> {
                        try {
                            return (Mono<?>) joinPoint.proceed();
                        } catch (Throwable e) {
                            return Mono.error(e);
                        }
                    })
                    .contextWrite(TenantContext.withTenant(user.tenantId())));
        }

        if (Flux.class.isAssignableFrom(returnType)) {
            return authorize(requirePermission).flatMapMany(user -> Flux.defer(() -> {
                        try {
                            return (Flux<?>) joinPoint.proceed();
                        } catch (Throwable e) {
                            return Flux.error(e);
                        }
                    })
                    .contextWrite(TenantContext.withTenant(user.tenantId())));
        }

        throw new BusinessException(ResultCode.FORBIDDEN);
    }

    private Mono<AuthenticatedUser> authorize(RequirePermission requirePermission) {
        List<String> requiredCodes = parseCodes(requirePermission.value());
        if (requiredCodes.isEmpty()) {
            return Mono.error(new BusinessException(ResultCode.FORBIDDEN));
        }

        return ReactiveSecurityContextHolder.getContext()
                .flatMap(context -> Mono.justOrEmpty(context.getAuthentication()))
                .filter(Authentication::isAuthenticated)
                .filter(authentication -> !(authentication instanceof AnonymousAuthenticationToken))
                .flatMap(authentication -> Mono.justOrEmpty(authentication.getPrincipal()))
                .ofType(AuthenticatedUser.class)
                .filter(user -> user.userId() != null)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED)))
                .flatMap(user -> {
                    if (user.tenantId() == null || user.tenantId().isBlank()) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN));
                    }
                    return Mono.defer(() -> permissionService.getPermissionsByUserId(user.userId()))
                            .map(permissions -> permissions.stream()
                                    .map(permission -> permission.getCode() == null ? "" : permission.getCode())
                                    .collect(Collectors.toSet()))
                            .filter(userCodes -> checkPermission(userCodes, requiredCodes, requirePermission.logical()))
                            .switchIfEmpty(Mono.error(new BusinessException(ResultCode.FORBIDDEN)))
                            .thenReturn(user)
                            .contextWrite(TenantContext.withTenant(user.tenantId()));
                });
    }

    /**
     * 解析权限编码字符串为列表
     *
     * @param value 逗号分隔的权限编码字符串
     * @return 权限编码列表
     */
    private List<String> parseCodes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    /**
     * 校验用户权限编码是否满足要求
     *
     * @param userCodes 用户拥有的权限编码集合
     * @param required  所需的权限编码列表
     * @param logical   逻辑关系（AND-全部满足，OR-满足其一）
     * @return 是否满足权限要求
     */
    private boolean checkPermission(Set<String> userCodes, List<String> required, Logical logical) {
        if (logical == Logical.AND) {
            return required.stream().allMatch(userCodes::contains);
        }
        return required.stream().anyMatch(userCodes::contains);
    }
}
