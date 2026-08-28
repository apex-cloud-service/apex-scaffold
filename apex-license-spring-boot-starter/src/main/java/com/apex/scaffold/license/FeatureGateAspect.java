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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 功能开关AOP切面
 *
 * <p>拦截标注了@FeatureGate注解的方法,自动检查License授权。
 * <ul>
 *   <li>由{@link LicenseAutoConfiguration} 以 {@code @Bean} 方式注册,
 *       不依赖宿主应用的组件扫描路径 —— 宿主应用主类无论位于什么包名下,
 *       只要引入本模块且未关闭 {@code apex.license.enabled},切面即生效。</li>
 *   <li>兼容WebFlux响应式方法: 拦截方法返回{@link Mono}/{@link Flux}时,
 *       以{@code Mono.error}/{@code Flux.error}抛出{@link BusinessException},
 *       由全局异常处理器转换为统一响应格式。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.1.0
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
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
