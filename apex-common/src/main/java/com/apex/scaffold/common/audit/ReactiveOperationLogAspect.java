package com.apex.scaffold.common.audit;

import com.apex.scaffold.common.annotation.OperationLog;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 四个独立服务的响应式操作审计消费者，仅输出白名单元数据。
 *
 * <p>不采集身份、正文、地址、请求头或异常消息。响应就绪不代表业务成功、事务提交或响应送达。
 * 空完成与取消不产生响应就绪记录；日志输出不提供可靠持久化保证。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Aspect
public class ReactiveOperationLogAspect {
    /**
     * 延迟调用目标方法，每次订阅只订阅原发布者一次，并保留原响应与异常。
     *
     * @param point 四个目标服务中带操作日志注解的方法连接点
     * @return 附加审计观察的原始响应式信号
     */
    @Around("@annotation(com.apex.scaffold.common.annotation.OperationLog) && ("
            + "execution(reactor.core.publisher.Mono com.apex.scaffold.authservice..*(..)) || "
            + "execution(reactor.core.publisher.Mono com.apex.scaffold.fileservice..*(..)) || "
            + "execution(reactor.core.publisher.Mono com.apex.scaffold.gateway..*(..)) || "
            + "execution(reactor.core.publisher.Mono com.apex.scaffold.maskingservice..*(..)))")
    public Object intercept(ProceedingJoinPoint point) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        OperationLog operation = method.getAnnotation(OperationLog.class);
        return Mono.defer(() -> invoke(point))
                .doOnNext(response -> record(operation, method, point.getArgs(), response, null))
                .doOnError(error -> record(operation, method, point.getArgs(), null, error));
    }

    /**
     * 将同步调用异常转换为携带同一异常对象的响应式错误。
     *
     * @param point 返回 Mono 的目标方法连接点
     * @return 原发布者或原异常
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
     * 记录固定操作声明、类方法、HTTP 方法及状态或错误类型，隔离日志设施异常。
     *
     * @param operation 静态注解声明，保存开关仅控制 HTTP 元数据
     * @param method 目标类与方法
     * @param args 仅检查请求类型并读取 HTTP 方法
     * @param response 就绪响应对象，不访问响应正文
     * @param error 原异常，仅提取异常类名
     */
    private void record(OperationLog operation, Method method, Object[] args, Object response, Throwable error) {
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("module", operation.module());
            details.put("description", operation.description());
            if (operation.saveRequest()) {
                for (Object arg : args) {
                    if (arg instanceof ServerRequest request) {
                        details.put("httpMethod", request.method().name());
                    } else if (arg instanceof ServerWebExchange exchange) {
                        details.put(
                                "httpMethod", exchange.getRequest().getMethod().name());
                    }
                }
            }
            if (operation.saveResponse() && response instanceof ServerResponse http) {
                details.put("httpStatus", http.statusCode().value());
            }
            if (error != null) {
                details.put("errorType", error.getClass().getSimpleName());
            }
            log.info(
                    "OPERATION_AUDIT resource={}.{} result={} details={}",
                    method.getDeclaringClass().getSimpleName(),
                    method.getName(),
                    error == null ? "HTTP_RESPONSE_READY" : "ERROR",
                    details);
        } catch (RuntimeException loggingFailure) {
            // 本地日志设施故障不能改变接口返回值或覆盖原业务异常。
        }
    }
}
