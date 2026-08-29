package com.apex.scaffold.interfaces.aspect;

import com.apex.scaffold.common.annotation.DataScope;
import com.apex.scaffold.common.context.DataScopeConfig;
import com.apex.scaffold.common.context.DataScopeContext;
import com.apex.scaffold.common.context.DataScopeInfo;
import com.apex.scaffold.common.context.DataScopeProvider;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 数据权限切面（自定义数据权限装配端）。
 *
 * <p>拦截标注 {@link DataScope} 的 Service 方法，在方法执行期间将当前用户的数据权限信息
 * （{@link DataScopeInfo}，由 {@link DataScopeProvider} 解析）与注入配置
 * （{@link DataScopeConfig}，由注解转换）装配到 {@link DataScopeContext}，
 * 供 {@code DataScopeInnerInterceptor}（MyBatis-Plus 拦截器）在查询 SQL 执行前
 * 自动注入过滤条件；方法执行结束后释放上下文。</p>
 *
 * <p><b>响应式适配</b>：返回 {@link Mono}/{@link Flux} 的方法在 <em>订阅时</em>
 * （{@code doFirst}）装配、<em>终止时</em>（{@code doFinally}）释放，保证上下文
 * 在异步执行期间始终存活；同步方法沿用 try/finally 装配释放。
 * 阻塞查询经 {@code BlockingIoSchedulers.jdbc()} 切换线程执行时，
 * 需保证上下文在查询执行线程可见（详见 {@code docs/DATA_SCOPE.md}）。</p>
 *
 * <p><b>放行语义</b>：无法解析当前用户数据权限时记录 WARN 并放行（不做数据权限过滤），
 * 与旧版"MDC 无 deptId 时放行"保持一致。</p>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.2.0
 * @see DataScope
 * @see DataScopeContext
 * @see DataScopeProvider
 */
@Slf4j
@Aspect
@Component
public class DataScopeAspect {

    /** 数据权限装配器（解析当前用户的数据可见范围） */
    private final DataScopeProvider dataScopeProvider;

    /**
     * 构造数据权限切面。
     *
     * @param dataScopeProvider 数据权限装配器
     */
    public DataScopeAspect(DataScopeProvider dataScopeProvider) {
        this.dataScopeProvider = dataScopeProvider;
    }

    /**
     * 环绕通知：装配/释放数据权限上下文。
     *
     * @param joinPoint 连接点（{@code @DataScope} 标注方法）
     * @param dataScope {@code @DataScope} 注解实例
     * @return 方法执行结果（响应式方法原样返回 Mono/Flux）
     * @throws Throwable 方法异常原样抛出
     */
    @Around("@annotation(dataScope)")
    public Object applyDataScope(ProceedingJoinPoint joinPoint, DataScope dataScope) throws Throwable {
        Class<?> returnType = ((MethodSignature) joinPoint.getSignature()).getReturnType();
        if (Mono.class.isAssignableFrom(returnType)) {
            return proceedReactive(joinPoint, dataScope, true);
        }
        if (Flux.class.isAssignableFrom(returnType)) {
            return proceedReactive(joinPoint, dataScope, false);
        }
        return proceedSynchronous(joinPoint, dataScope);
    }

    /**
     * 响应式方法：订阅时装配、终止时释放。
     */
    private Object proceedReactive(ProceedingJoinPoint joinPoint, DataScope dataScope, boolean isMono) throws Throwable {
        Object result = joinPoint.proceed();
        if (isMono) {
            return ((Mono<?>) result)
                    .doFirst(() -> assemble(dataScope, joinPoint))
                    .doFinally(signalType -> DataScopeContext.clear());
        }
        return ((Flux<?>) result)
                .doFirst(() -> assemble(dataScope, joinPoint))
                .doFinally(signalType -> DataScopeContext.clear());
    }

    /**
     * 同步方法：执行前装配、finally 释放。
     */
    private Object proceedSynchronous(ProceedingJoinPoint joinPoint, DataScope dataScope) throws Throwable {
        assemble(dataScope, joinPoint);
        try {
            return joinPoint.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }

    /**
     * 装配数据权限上下文。
     *
     * @param dataScope 注解实例（用于转换注入配置）
     * @param joinPoint 连接点（仅用于日志）
     */
    private void assemble(DataScope dataScope, ProceedingJoinPoint joinPoint) {
        dataScopeProvider.resolve().ifPresentOrElse(
                info -> {
                    DataScopeContext.setDataScope(info, DataScopeConfig.from(dataScope));
                    log.debug("数据权限装配: method={}, type={}, deptId={}, userId={}, deptIds={}",
                            joinPoint.getSignature().getName(),
                            info.type(),
                            info.deptId(),
                            info.userId(),
                            info.deptIds());
                },
                () -> log.warn("数据权限未装配（无法解析当前用户数据权限）: method={}，将不做数据过滤",
                        joinPoint.getSignature().getName()));
    }
}
