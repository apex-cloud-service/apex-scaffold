package com.apex.scaffold.officeservice.interfaces.security;

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
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 办公接口本地注解接线：订阅时授权后才调用 Handler，并记录有限的 HTTP 审计元数据。
 *
 * <p>Handler 可能吞错或返回 Result.success(false)，响应生成不代表业务成功或响应已发送。
 * 不读取请求、响应正文，也不记录 URI、查询参数、身份头及异常消息，避免泄露敏感数据。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Aspect
@Component
@Order(0)
public class OfficeSecurityAspect {
    private final OfficePermissionClient permissions;
    private final AuditLogService audit;

    /**
     * 注入本地授权适配器及公共审计契约实现。
     *
     * @param permissions 按可信用户及租户查询授权快照的适配器
     * @param audit 接收白名单元数据的审计实现
     */
    public OfficeSecurityAspect(OfficePermissionClient permissions, AuditLogService audit) {
        this.permissions = permissions;
        this.audit = audit;
    }

    /**
     * 统一拦截现有方法注解，在每次订阅时解析身份、授权并延迟调用目标方法。
     *
     * <p>权限码只从方法声明读取。有操作日志注解时，响应生成或错误传播前等待审计完成；
     * 审计失败会传播为调用失败，不视为业务回滚。身份无效时尚未获得可信审计主体，不写操作日志。
     * 取消订阅或目标空完成不会产生响应就绪日志。
     *
     * @param point 被注解标记的目标方法连接点
     * @return 延迟执行的 {@link Mono}，传播授权、目标调用或审计错误
     * @throws AccessDeniedException 目标返回类型不是 Mono，无法使用响应式授权边界
     */
    @Around("@annotation(com.apex.scaffold.common.annotation.RequirePermission) || "
            + "@annotation(com.apex.scaffold.common.annotation.OperationLog)")
    public Object intercept(ProceedingJoinPoint point) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        RequirePermission required = method.getAnnotation(RequirePermission.class);
        OperationLog operation = method.getAnnotation(OperationLog.class);
        if (!Mono.class.isAssignableFrom(method.getReturnType())) {
            throw new AccessDeniedException("Forbidden");
        }
        if (required == null
                && method.getDeclaringClass()
                        == com.apex.scaffold.officeservice.interfaces.handler.OfficeHandler.class) {
            return Mono.defer(() -> invoke(point))
                    .flatMap(response -> record(null, operation, method, point.getArgs(), response, null)
                            .thenReturn(response))
                    .onErrorResume(error -> record(null, operation, method, point.getArgs(), null, error)
                            .then(Mono.error(error)));
        }
        return identity().flatMap(user -> {
            Mono<Object> invocation = authorize(user, required).then(Mono.defer(() -> invoke(point)));
            if (operation == null) {
                return invocation;
            }
            return invocation
                    .flatMap(response -> record(user, operation, method, point.getArgs(), response, null)
                            .thenReturn(response))
                    .onErrorResume(error -> record(user, operation, method, point.getArgs(), null, error)
                            .then(Mono.error(error)));
        });
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
     * @return 目标发布者，或携带同步调用异常的错误发布者；异步错误由原发布者传播
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
     * 记录操作声明与允许采集的 HTTP 元数据，不检查正文中的业务结果。
     *
     * <p>saveRequest、saveResponse 仅控制 HTTP 方法和状态码的采集；异常仅记录类型。
     * HTTP_RESPONSE_READY 表示响应对象已生成，ERROR 表示调用链出错，均不承诺响应已送达。
     *
     * @param user 已验证的审计主体及租户
     * @param operation 方法的操作日志声明
     * @param method 用于生成稳定资源名称的目标方法
     * @param args 目标方法实参，仅提取 ServerRequest 的 HTTP 方法
     * @param response 目标响应对象，错误路径为 null
     * @param error 调用链错误，响应生成路径为 null
     * @return 审计完成信号；审计实现失败时传播错误，不转换为成功
     */
    private Mono<Void> record(
            AuthenticatedUser user,
            OperationLog operation,
            Method method,
            Object[] args,
            Object response,
            Throwable error) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("module", operation.module());
        details.put("description", operation.description());
        // 即使注解允许正文，也只采集非敏感白名单元数据；false 时不添加对应请求/响应字段。
        if (operation.saveRequest()) {
            for (Object arg : args) {
                if (arg instanceof ServerRequest request) {
                    details.put("httpMethod", request.method().name());
                }
            }
        }
        if (operation.saveResponse() && response instanceof ServerResponse http) {
            details.put("httpStatus", http.statusCode().value());
        }
        if (error != null) {
            details.put("errorType", error.getClass().getSimpleName());
        }
        return audit.log(
                user == null ? null : user.userId().toString(),
                user == null ? null : user.tenantId(),
                operation.type().name(),
                method.getDeclaringClass().getSimpleName() + "." + method.getName(),
                error == null ? "HTTP_RESPONSE_READY" : "ERROR",
                details);
    }
}
