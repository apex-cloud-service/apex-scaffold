package com.apex.scaffold.workflowservice.interfaces.security;

import com.apex.scaffold.common.annotation.OperationLog;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 工作流接口操作日志的本地消费者，仅记录可信主体及白名单元数据。
 *
 * <p>沿用既有认证语义，不提供权限判断，不从请求头补充身份或租户。响应对象生成仅表示
 * HTTP_RESPONSE_READY，不代表业务成功、事务提交或响应送达。空完成与取消不记录响应就绪。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Aspect
@Component
public class WorkflowOperationLogAspect {
    private final WorkflowAuditLogServiceImpl audit;

    /**
     * 注入本地审计输出组件。
     *
     * @param audit 仅接收白名单元数据的本地审计组件
     */
    public WorkflowOperationLogAspect(WorkflowAuditLogServiceImpl audit) {
        this.audit = audit;
    }

    /**
     * 在订阅时读取既有安全上下文并调用被注解标记的方法。
     *
     * <p>缺少本地可信主体时仍调用原方法，由既有安全链和 Handler 决定认证结果，不凭空记录主体。
     * 审计完成后传播原响应或原异常；审计输出失败会传播，不代表业务事务回滚。
     *
     * @param point 带操作日志注解且返回 Mono 的接口方法连接点
     * @return 延迟调用及审计后的响应式结果
     */
    @Around("@annotation(com.apex.scaffold.common.annotation.OperationLog) && "
            + "execution(* com.apex.scaffold.workflowservice.interfaces..*(..))")
    public Object intercept(ProceedingJoinPoint point) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        OperationLog operation = method.getAnnotation(OperationLog.class);
        return identity().flatMap(identity -> {
            Mono<Object> invocation = Mono.defer(() -> invoke(point));
            if (identity.isEmpty()) {
                return invocation;
            }
            AuthenticatedUser user = identity.get();
            return invocation.materialize().flatMap(signal -> {
                if (signal.isOnComplete()) {
                    return Mono.empty();
                }
                return record(user, operation, method, point.getArgs(), signal.get(), signal.getThrowable())
                        .then(Mono.defer(() ->
                                signal.isOnError() ? Mono.error(signal.getThrowable()) : Mono.just(signal.get())));
            });
        });
    }

    /**
     * 读取网关验签后由既有中继过滤器建立的响应式认证主体。
     *
     * @return 本地已认证主体；缺失时返回空 Optional，不改变既有认证门槛
     */
    private Mono<Optional<AuthenticatedUser>> identity() {
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(context -> Mono.justOrEmpty(context.getAuthentication()))
                .filter(authentication -> authentication.isAuthenticated())
                .map(authentication -> authentication.getPrincipal())
                .ofType(AuthenticatedUser.class)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    /**
     * 延迟调用原方法，将同步异常转换为响应式错误，保留原异常对象。
     *
     * @param point 返回 Mono 的目标方法连接点
     * @return 原方法发布者或携带原同步异常的错误发布者
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
     * 提取受控操作声明、HTTP 方法、状态码及异常类型，禁止读取正文或异常消息。
     *
     * @param user 安全上下文中的可信主体及租户，不使用其他来源补全
     * @param operation 目标方法上的操作声明
     * @param method 用于生成稳定资源名的目标方法
     * @param args 方法实参，仅提取 ServerRequest 的 HTTP 方法
     * @param response 已构建的响应，错误时为 null
     * @param error 原调用异常，正常响应时为 null
     * @return 本地审计输出完成信号
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
                user.userId(),
                user.tenantId(),
                operation.type().name(),
                method.getDeclaringClass().getSimpleName() + "." + method.getName(),
                error == null ? "HTTP_RESPONSE_READY" : "ERROR",
                details);
    }
}
