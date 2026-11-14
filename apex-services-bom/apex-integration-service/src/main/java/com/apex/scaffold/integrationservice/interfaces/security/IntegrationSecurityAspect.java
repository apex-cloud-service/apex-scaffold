package com.apex.scaffold.integrationservice.interfaces.security;

import com.apex.scaffold.common.annotation.Logical;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.audit.AuditLogService;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

/**
 * 集成服务接口本地注解接线：订阅时先授权，再调用 Handler，并记录受控审计元数据。
 *
 * <p>集成库的 Handler 位于 {@code com.apex.scaffold.integration..interfaces}，宿主服务的
 * 授权/审计切面此前只覆盖 service 包，导致 {@code @RequirePermission}/{@code @OperationLog}
 * 仅作为元数据存在而不执行。本切面把两类注解放进同一授权时序，权限码只从方法声明读取。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Aspect
@Component
@Order(0)
public class IntegrationSecurityAspect {

    private final IntegrationPermissionClient permissions;

    private final AuditLogService audit;

    /**
     * 注入按可信用户及租户查询授权快照的适配器与审计实现。
     *
     * @param permissions 授权快照查询适配器
     * @param audit 接收白名单元数据的审计实现
     */
    public IntegrationSecurityAspect(IntegrationPermissionClient permissions, AuditLogService audit) {
        this.permissions = permissions;
        this.audit = audit;
    }

    /**
     * 拦截集成接口方法：有权限注解时先授权，有操作日志注解时在响应前审计。
     *
     * @param point 带注解且返回 {@link Mono} 的接口方法连接点
     * @return 延迟执行的响应式结果
     * @throws AccessDeniedException 目标返回类型不是 Mono，无法使用响应式授权边界
     */
    @Around("(@annotation(com.apex.scaffold.common.annotation.RequirePermission) || "
            + "@annotation(com.apex.scaffold.common.annotation.OperationLog)) && "
            + "execution(* com.apex.scaffold.integration..interfaces..*(..))")
    public Object intercept(ProceedingJoinPoint point) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        if (!Mono.class.isAssignableFrom(method.getReturnType())) {
            throw new AccessDeniedException("Forbidden");
        }
        return execute(method, Mono.defer(() -> invoke(point)));
    }

    /**
     * 在可信主体上执行授权与审计，供切面与测试共用。
     *
     * @param method 带注解的目标方法
     * @param invocation 延迟执行的目标调用
     * @return 授权通过并审计后的结果；任何授权失败均拒绝
     */
    Mono<Object> execute(Method method, Mono<Object> invocation) {
        RequirePermission required = method.getAnnotation(RequirePermission.class);
        OperationLog operation = method.getAnnotation(OperationLog.class);
        return identity().flatMap(user -> authorize(user, required)
                .then(Mono.defer(() -> operation == null ? invocation : audit(user, operation, method, invocation))));
    }

    /**
     * 执行目标调用并在完成前写入审计；不改变原响应或原异常。
     *
     * @param user 可信主体
     * @param operation 操作声明
     * @param method 目标方法
     * @param invocation 目标调用
     * @return 审计后的原信号
     */
    private Mono<Object> audit(AuthenticatedUser user, OperationLog operation, Method method, Mono<Object> invocation) {
        return invocation.materialize().flatMap(signal -> record(user, operation, method, signal)
                .then(Mono.defer(() -> resume(signal))));
    }

    /**
     * 从 Reactor 安全上下文提取已认证本地用户，不接受请求头身份。
     *
     * @return 具有正数用户 ID 和非空白租户 ID 的主体
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
     * 按注解的 AND/OR 关系匹配精确权限码。
     *
     * @param user 已认证主体
     * @param required 权限注解；为 null 时不查询授权中心
     * @throws org.springframework.security.access.AccessDeniedException 权限码不匹配时抛出
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
     * 记录受控审计元数据；不读取请求正文、响应正文或异常消息。
     *
     * @param user 可信主体
     * @param operation 操作声明
     * @param method 目标方法
     * @param signal 物化后的调用信号
     */
    private Mono<Void> record(AuthenticatedUser user, OperationLog operation, Method method, Signal<Object> signal) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("module", operation.module());
        details.put("operation", operation.type().name());
        if (signal.isOnNext() && signal.get() instanceof ServerResponse response) {
            details.put("httpStatus", String.valueOf(response.statusCode().value()));
        }
        String result = signal.isOnError() ? "ERROR" : "HTTP_RESPONSE_READY";
        return audit.log(
                String.valueOf(user.userId()),
                user.tenantId(),
                operation.type().name(),
                method.getDeclaringClass().getSimpleName() + "#" + method.getName(),
                result,
                details);
    }

    /**
     * 延迟调用目标方法，将同步异常转换为响应式错误。
     *
     * @param point 目标连接点
     * @return 原发布者或错误发布者
     */
    @SuppressWarnings("unchecked")
    private Mono<Object> invoke(ProceedingJoinPoint point) {
        try {
            return (Mono<Object>) point.proceed();
        } catch (Throwable error) {
            return Mono.error(error);
        }
    }

    /**
     * 把物化信号还原为原响应或原异常。
     *
     * @param signal 物化结果
     * @return 原响应或错误
     */
    private Mono<Object> resume(Signal<Object> signal) {
        return signal.isOnError() ? Mono.error(signal.getThrowable()) : Mono.justOrEmpty(signal.get());
    }
}
