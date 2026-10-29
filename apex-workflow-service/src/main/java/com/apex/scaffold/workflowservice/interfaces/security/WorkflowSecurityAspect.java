package com.apex.scaffold.workflowservice.interfaces.security;

import com.apex.scaffold.common.annotation.Logical;
import com.apex.scaffold.common.annotation.RequirePermission;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 工作流接口本地权限接线：每次订阅时解析可信身份并查询授权快照，通过后才调用 Handler。
 *
 * <p>仅处理 {@link RequirePermission}，操作日志由 {@link WorkflowOperationLogAspect} 独立消费，
 * 避免同一次调用重复写入审计。权限码只从方法声明读取，不根据角色名称或通配符放行。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Aspect
@Component
@Order(0)
public class WorkflowSecurityAspect {
    private final WorkflowPermissionClient permissions;

    /**
     * 注入按可信用户及租户查询授权快照的适配器。
     *
     * @param permissions 授权快照查询适配器，配置无效或远程失败时返回统一拒绝
     */
    public WorkflowSecurityAspect(WorkflowPermissionClient permissions) {
        this.permissions = permissions;
    }

    /**
     * 在订阅时授权，通过后延迟调用目标方法；异步错误由原发布者传播。
     *
     * @param point 带权限注解且返回 Mono 的接口方法连接点
     * @return 延迟执行的 {@link Mono}，传播授权或目标调用错误
     * @throws AccessDeniedException 目标返回类型不是 Mono，无法使用响应式授权边界
     */
    @Around("@annotation(com.apex.scaffold.common.annotation.RequirePermission) && "
            + "execution(* com.apex.scaffold.workflowservice.interfaces..*(..))")
    public Object intercept(ProceedingJoinPoint point) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        RequirePermission required = method.getAnnotation(RequirePermission.class);
        if (!Mono.class.isAssignableFrom(method.getReturnType())) {
            throw new AccessDeniedException("Forbidden");
        }
        return identity().flatMap(user -> authorize(user, required).then(Mono.defer(() -> invoke(point))));
    }

    /**
     * 从 Reactor 安全上下文提取已认证的本地用户，不接受请求头提供的身份。
     *
     * @return 具有正数用户 ID 和非空白租户 ID 的主体；缺失或无效时发出访问拒绝异常
     */
    private Mono<AuthenticatedUser> identity() {
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(context -> Mono.justOrEmpty(context.getAuthentication()))
                .filter(authentication -> authentication.isAuthenticated())
                .map(authentication -> authentication.getPrincipal())
                .ofType(AuthenticatedUser.class)
                .filter(user -> user.userId() != null
                        && user.userId() > 0
                        && user.tenantId() != null
                        && !user.tenantId().isBlank())
                .switchIfEmpty(Mono.error(new AccessDeniedException("Forbidden")));
    }

    /**
     * 按注解的 AND 或 OR 关系匹配精确权限码，不提供角色名称或通配符放行。
     *
     * <p>逗号分隔的权限码会修剪、去空及去重；存在注解但未声明有效权限码时拒绝。
     *
     * @param user 已通过身份校验的认证主体
     * @param required 方法权限注解，为 null 时无需查询授权中心
     * @return 无注解或匹配通过时完成；权限不足、声明为空或快照查询失败时发出访问拒绝异常
     */
    private Mono<Void> authorize(AuthenticatedUser user, RequirePermission required) {
        if (required == null) {
            return Mono.empty();
        }
        List<String> codes = Arrays.stream(required.value().split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .distinct()
                .toList();
        return permissions
                .permissions(user)
                .filter(owned -> !codes.isEmpty()
                        && (required.logical() == Logical.AND
                                ? codes.stream().allMatch(owned::contains)
                                : codes.stream().anyMatch(owned::contains)))
                .switchIfEmpty(Mono.error(new AccessDeniedException("Forbidden")))
                .then();
    }

    /**
     * 在授权完成后调用目标方法，将目标同步抛出的异常转换为响应式错误。
     *
     * @param point 返回 Mono 的目标方法连接点
     * @return 目标发布者，或携带同步调用异常的错误发布者
     */
    @SuppressWarnings("unchecked")
    private Mono<Object> invoke(ProceedingJoinPoint point) {
        try {
            return (Mono<Object>) point.proceed();
        } catch (Throwable error) {
            return Mono.error(error);
        }
    }
}
