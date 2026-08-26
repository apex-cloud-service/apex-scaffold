package com.apex.scaffold.interfaces.aspect;

import com.apex.scaffold.application.service.PermissionService;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.common.annotation.Logical;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
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
 * <h2>校验流程</h2>
 * <ol>
 *   <li>从 {@link ReactiveSecurityContextHolder} 获取当前认证信息</li>
 *   <li>通过 {@link UserService} 获取用户信息</li>
 *   <li>先检查是否拥有管理员角色（ROLE_ADMIN），管理员直接放行</li>
 *   <li>通过 {@link PermissionService} 拉取用户权限列表</li>
 *   <li>根据注解的 {@link Logical} 关系（AND/OR）进行权限匹配</li>
 * </ol>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see RequirePermission
 * @see PermissionService
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RequirePermissionAspect {

    private final UserService userService;
    private final PermissionService permissionService;

    /**
     * 环绕通知，对目标方法执行权限校验。
     *
     * <p>根据返回值类型（Mono/Flux/同步）自动适配响应式或同步校验路径。
     * 权限不足时抛出 {@link BusinessException}({@code FORBIDDEN})。</p>
     *
     * @param joinPoint 切点
     * @param requirePermission 权限校验注解
     * @return 方法执行结果（Mono/Flux 或同步值）
     */
    @Around("@annotation(requirePermission)")
    public Object around(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) {
        Mono<Boolean> allowed = isAllowed(requirePermission);

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> returnType = signature.getReturnType();

        if (Mono.class.isAssignableFrom(returnType)) {
            return allowed.flatMap(ok -> ok
                    ? Mono.defer(() -> {
                        try {
                            return (Mono<?>) joinPoint.proceed();
                        } catch (Throwable e) {
                            log.error("执行目标方法失败: {}", e.getMessage(), e);
                            return Mono.error(e);
                        }
                    })
                    : Mono.error(new BusinessException(ResultCode.FORBIDDEN)));
        }

        if (Flux.class.isAssignableFrom(returnType)) {
            return allowed.flatMapMany(ok -> ok
                    ? Flux.defer(() -> {
                        try {
                            return (Flux<?>) joinPoint.proceed();
                        } catch (Throwable e) {
                            log.error("执行目标方法失败: {}", e.getMessage(), e);
                            return Flux.error(e);
                        }
                    })
                    : Flux.error(new BusinessException(ResultCode.FORBIDDEN)));
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            log.error("执行目标方法失败: {}", e.getMessage(), e);
            return Mono.error(e);
        }
    }

    /**
     * 校验当前用户是否拥有指定权限
     *
     * @param requirePermission 权限注解
     * @return true-有权限，false-无权限
     */
    private Mono<Boolean> isAllowed(RequirePermission requirePermission) {
        List<String> requiredCodes = parseCodes(requirePermission.value());
        if (requiredCodes.isEmpty()) {
            return Mono.just(true);
        }

        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(Objects::nonNull)
                .map(Authentication::getName)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED)))
                .flatMap(userService::getByOpenId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED)))
                .flatMap(user -> {
                    return hasAdminRole().flatMap(isAdmin -> {
                        if (isAdmin) {
                            return Mono.just(true);
                        }
                        return permissionService
                                .getPermissionsByUserId(user.getId())
                                .map(permissions -> permissions.stream()
                                        .map(p -> p.getCode() == null ? "" : p.getCode())
                                        .collect(Collectors.toSet()))
                                .map(userCodes ->
                                        checkPermission(userCodes, requiredCodes, requirePermission.logical()));
                    });
                });
    }

    /**
     * 检查当前用户是否拥有管理员角色（ROLE_ADMIN）。
     *
     * <p>从 {@link ReactiveSecurityContextHolder} 中提取认证信息，
     * 遍历用户权限集合查找 {@code ROLE_ADMIN} 标识。</p>
     *
     * @return 是否为管理员，认证信息为空时返回 false
     */
    private Mono<Boolean> hasAdminRole() {
        return ReactiveSecurityContextHolder.getContext().map(ctx -> {
            Authentication auth = ctx.getAuthentication();
            if (auth == null) {
                return false;
            }
            return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
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
