package com.apex.scaffold.license;

import com.apex.scaffold.common.annotation.FeatureGate;
import com.apex.scaffold.common.error.ErrorCode;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import java.lang.reflect.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 功能开关AOP切面
 *
 * <p>拦截标注了@FeatureGate注解的方法,自动检查License授权。
 * <ul>
 *   <li>仅当 {@code apex.license.enabled=true} 时激活,与{@link LicenseAutoConfiguration}
 *       保持一致,避免未启用License时因缺少LicenseValidator Bean导致应用启动失败。</li>
 *   <li>兼容WebFlux响应式方法: 拦截方法返回{@link Mono}/{@link Flux}时,
 *       以{@code Mono.error}/{@code Flux.error}抛出{@link BusinessException},
 *       由全局异常处理器转换为统一响应格式。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "apex.license.enabled", havingValue = "true", matchIfMissing = true)
public class FeatureGateAspect {

    private final LicenseValidator licenseValidator;

    @Around("@annotation(featureGate)")
    public Object around(ProceedingJoinPoint joinPoint, FeatureGate featureGate) throws Throwable {
        // 功能已授权,继续执行
        if (licenseValidator.isFeatureEnabled(featureGate.value())) {
            return joinPoint.proceed();
        }

        log.warn("功能未授权: {}, 方法: {}", featureGate.value(), joinPoint.getSignature());
        BusinessException exception =
                new BusinessException(ErrorCode.PERMISSION_DENIED.getCode(), featureGate.message());

        // 响应式方法: 通过 Mono.error/Flux.error 传递,交给全局异常处理器输出统一格式
        Method method = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> returnType = method.getReturnType();
        if (Mono.class.isAssignableFrom(returnType)) {
            return Mono.error(exception);
        }
        if (Flux.class.isAssignableFrom(returnType)) {
            return Flux.error(exception);
        }

        // 同步方法: 直接返回统一错误结果
        return Result.error(ErrorCode.PERMISSION_DENIED.getCode(), featureGate.message());
    }
}
