package com.apex.scaffold.interfaces.aspect;

import com.apex.scaffold.common.annotation.DataSource;
import com.apex.scaffold.common.context.DataSourceContext;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 数据源动态切换切面（自定义 {@code @DataSource} 注解装配端）。
 *
 * <p>拦截标注 {@link DataSource} 的方法（或类），在方法执行期间将数据源切换到注解指定名称，
 * 结束/终止后自动释放：</p>
 *
 * <ol>
 *   <li><b>切换</b>：同时写入本脚手架 {@link DataSourceContext}（切换栈，供日志/埋点/读取）
 *       与 Baomidou {@link DynamicDataSourceContextHolder}（驱动 {@code DynamicRoutingDataSource}
 *       实际选库）；</li>
 *   <li><b>优先级</b>：方法级注解优先于类级注解；数据源名称为空/空白时跳过切换（使用主库）；</li>
 *   <li><b>响应式适配</b>：返回 {@link Mono}/{@link Flux} 的方法在 <em>订阅时</em>
 *       （{@code doFirst}）切换、<em>终止时</em>（{@code doFinally}）释放；
 *       同步方法沿用 try/finally；</li>
 *   <li><b>埋点</b>：每次切换上报 {@code apex.datasource.switch.count{target=数据源名}}
 *       （注册表不存在时静默跳过）。</li>
 * </ol>
 *
 * <p><b>执行顺序</b>：{@link Order#HIGHEST_PRECEDENCE} 保证先于事务切面执行，
 * 使连接从正确的数据源获取。</p>
 *
 * <p><b>响应式线程模型</b>：切换基于线程上下文，要求查询与切换同线程执行；
 * 经 {@code BlockingIoSchedulers.jdbc()} 切换线程的阻塞查询需要显式装配或线程上下文透明传播，
 * 详见 {@code docs/DATA_SOURCE_SWITCH_USAGE.md}。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 * @see DataSource
 * @see DataSourceContext
 */
@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class DataSourceSwitchAspect {

    /** 数据源切换指标名（按数据源名打标签） */
    private static final String SWITCH_METRIC = "apex.datasource.switch.count";

    /** Micrometer 注册表（可选：上下文无注册表时静默跳过埋点） */
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    /**
     * 构造数据源切换切面。
     *
     * @param meterRegistryProvider Micrometer 注册表提供者（可为空）
     */
    public DataSourceSwitchAspect(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    /** 类级注解切入点 */
    @Pointcut("@within(com.apex.scaffold.common.annotation.DataSource)")
    public void classLevelDataSource() {
        // 命名切入点
    }

    /** 方法级注解切入点 */
    @Pointcut("@annotation(com.apex.scaffold.common.annotation.DataSource)")
    public void methodLevelDataSource() {
        // 命名切入点
    }

    /**
     * 环绕通知：切换/释放数据源上下文。
     *
     * @param joinPoint 连接点（{@code @DataSource} 标注方法或类中的方法）
     * @return 方法执行结果（响应式方法原样返回 Mono/Flux）
     * @throws Throwable 方法异常原样抛出
     */
    @Around("classLevelDataSource() || methodLevelDataSource()")
    public Object switchDataSource(ProceedingJoinPoint joinPoint) throws Throwable {
        DataSource annotation = resolveAnnotation(joinPoint);
        String dataSourceName = annotation == null ? "" : annotation.value();
        if (dataSourceName == null || dataSourceName.isBlank()) {
            // 未指定数据源 → 使用主库，不执行切换
            return joinPoint.proceed();
        }

        Class<?> returnType = ((MethodSignature) joinPoint.getSignature()).getReturnType();
        if (Mono.class.isAssignableFrom(returnType)) {
            return ((Mono<?>) joinPoint.proceed())
                    .doFirst(() -> switchTo(dataSourceName, joinPoint))
                    .doFinally(signalType -> restore(dataSourceName));
        }
        if (Flux.class.isAssignableFrom(returnType)) {
            return ((Flux<?>) joinPoint.proceed())
                    .doFirst(() -> switchTo(dataSourceName, joinPoint))
                    .doFinally(signalType -> restore(dataSourceName));
        }
        switchTo(dataSourceName, joinPoint);
        try {
            return joinPoint.proceed();
        } finally {
            restore(dataSourceName);
        }
    }

    /**
     * 解析生效注解：方法级优先，其次类级。
     *
     * @param joinPoint 连接点
     * @return 注解实例（未找到时返回 {@code null}）
     */
    private DataSource resolveAnnotation(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        DataSource methodAnnotation = method.getAnnotation(DataSource.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        return joinPoint.getTarget().getClass().getAnnotation(DataSource.class);
    }

    /**
     * 执行切换：写入本脚手架上下文 + Baomidou 切换栈 + 埋点。
     */
    private void switchTo(String dataSourceName, ProceedingJoinPoint joinPoint) {
        DataSourceContext.push(dataSourceName);
        DynamicDataSourceContextHolder.push(dataSourceName);
        recordSwitch(dataSourceName);
        log.debug("数据源切换: method={}, target={}", joinPoint.getSignature().getName(), dataSourceName);
    }

    /**
     * 释放切换：弹出两处切换栈。
     */
    private void restore(String dataSourceName) {
        DataSourceContext.pop();
        DynamicDataSourceContextHolder.poll();
    }

    /**
     * 上报切换埋点（注册表不存在时静默跳过）。
     */
    private void recordSwitch(String dataSourceName) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter(SWITCH_METRIC, "target", dataSourceName).increment();
        }
    }
}
