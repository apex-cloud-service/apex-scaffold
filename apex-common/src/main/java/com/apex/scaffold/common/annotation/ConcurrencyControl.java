package com.apex.scaffold.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 并发控制注解
 *
 * <p>用于方法级别的并发控制，通过信号量（Semaphore）机制限制同一方法的最大并发调用数。
 * 当并发调用数达到 {@link #maxConcurrent} 上限时，新的请求将在 {@link #timeout} 时间内
 * 等待获取许可；超时后返回 {@link #message} 指定的错误提示，避免系统因过载而崩溃。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>资源密集型方法的并发保护（如大数据量导出）</li>
 *   <li>第三方服务调用配额受限时的并发控制</li>
 *   <li>防止瞬时高并发冲垮后端服务</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * @ConcurrencyControl(maxConcurrent = 5, timeout = 3000, message = "导出任务繁忙，请稍后再试")
 * public Mono<Void> exportData(ExportRequest request) { ... }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConcurrencyControl {

    /**
     * 最大并发数
     *
     * @return 最大并发数
     */
    int maxConcurrent() default 10;

    /**
     * 等待超时时间（毫秒）
     *
     * @return 等待超时时间
     */
    long timeout() default 5000;

    /**
     * 超时后的错误消息
     *
     * @return 错误消息
     */
    String message() default "系统繁忙，请稍后再试";
}
