package com.apex.scaffold.interfaces.monitor;

import com.apex.scaffold.common.exception.AlertEvent;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.util.LogSafetyUtil;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.service.AlertService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 异常监控器
 *
 * <p>用于监控系统中的异常情况，当异常达到阈值时发送告警。该类实时统计各类型异常的发生次数，
 * 在异常数量超过配置阈值或遇到系统级异常时触发告警机制，确保系统故障能够及时被发现和处理。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>异常统计：按异常类型统计发生次数</li>
 *   <li>告警触发：当异常数量达到阈值时自动发送告警</li>
 *   <li>系统异常识别：自动识别并优先告警系统级异常</li>
 *   <li>统计重置：支持清空所有异常统计数据</li>
 *   <li>过期清理：支持清理过期的异常记录（每 5 分钟自动执行）</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>CRITICAL：系统异常（如空指针、内存溢出、栈溢出等）</li>
 *   <li>ERROR：普通业务异常</li>
 *   <li>WARNING：业务异常（如自定义业务异常）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see AlertService
 * @see AlertEvent
 */
@Slf4j
@Component
public class ExceptionMonitor {

    /** 告警服务 */
    private final AlertService alertService;

    /** Micrometer 指标注册表 */
    private final MeterRegistry meterRegistry;

    /** 异常计数器，按异常类型统计 */
    private final ConcurrentHashMap<String, AtomicInteger> exceptionCounter = new ConcurrentHashMap<>();

    /** 异常时间戳，记录每种异常的最近发生时间 */
    private final ConcurrentHashMap<String, AtomicLong> exceptionTimestamp = new ConcurrentHashMap<>();

    /** 异常总数（供 Prometheus Gauge 原子读取） */
    private final AtomicLong totalExceptionCount = new AtomicLong();

    /** 告警次数（供 Prometheus Gauge 原子读取） */
    private final AtomicLong totalAlertCount = new AtomicLong();

    /** 异常告警阈值，超过该次数触发告警 */
    private final int exceptionThreshold;

    /** 告警窗口时间（分钟），在该时间窗口内统计异常次数 */
    private final int alertWindowMinutes;

    /** 最大监控的异常类型数量，防止内存泄露 */
    private final int maxExceptionTypes;

    /** 告警服务名（读配置，兜底默认值与应用名 apex-scaffold 保持一致） */
    private final String serviceName;

    /**
     * 构造函数
     *
     * @param alertService 告警服务
     * @param meterRegistry Micrometer 指标注册表
     * @param exceptionThreshold 异常告警阈值（次），默认 10
     * @param alertWindowMinutes 告警窗口时间（分钟），默认 5
     * @param maxExceptionTypes 最大监控的异常类型数量，默认 100
     * @param serviceName 告警服务名，默认 apex-scaffold
     */
    public ExceptionMonitor(
            AlertService alertService,
            MeterRegistry meterRegistry,
            @Value("${alert.exception.threshold:10}") int exceptionThreshold,
            @Value("${alert.exception.window:5}") int alertWindowMinutes,
            @Value("${alert.exception.max-types:100}") int maxExceptionTypes,
            @Value("${spring.application.name:apex-scaffold}") String serviceName) {
        this.alertService = alertService;
        this.meterRegistry = meterRegistry;
        this.exceptionThreshold = exceptionThreshold;
        this.alertWindowMinutes = alertWindowMinutes;
        this.maxExceptionTypes = maxExceptionTypes;
        this.serviceName = serviceName;
        registerMetrics();
    }

    /**
     * 注册异常监控指标。
     *
     * <p>注册 {@code apex.exception.count} 计数器（按异常类型打标签）与
     * {@code apex.exception.total}、{@code apex.exception.alert.count} 两个 Gauge，
     * 供 Prometheus/Grafana 查询异常趋势与告警次数。</p>
     */
    private void registerMetrics() {
        meterRegistry.gauge("apex.exception.total", totalExceptionCount, AtomicLong::get);
        meterRegistry.gauge("apex.exception.alert.count", totalAlertCount, AtomicLong::get);
    }

    /**
     * 监控异常
     *
     * <p>记录异常类型和次数，当达到阈值时发送告警。
     * 告警通过 {@link AlertService#sendAlert(AlertEvent)} 异步发送，
     * 使用完整的 {@code subscribe} 回调形式处理成功和失败情况。
     *
     * @param ex 异常对象
     */
    public void monitorException(Exception ex) {
        String exceptionType = ex.getClass().getName();
        String traceId = LogSafetyUtil.text(TraceIdUtil.getTraceId(), 64);

        if (exceptionCounter.size() >= maxExceptionTypes && !exceptionCounter.containsKey(exceptionType)) {
            log.warn("超过最大异常类型监控数量({})，丢弃新类型异常监控: {}", maxExceptionTypes, exceptionType);
            return;
        }

        AtomicInteger count = exceptionCounter.computeIfAbsent(exceptionType, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();
        totalExceptionCount.incrementAndGet();

        exceptionTimestamp
                .computeIfAbsent(exceptionType, k -> new AtomicLong(0))
                .set(System.currentTimeMillis());

        // 记录异常计数器（Prometheus 标签为异常类型与告警级别，供 Grafana 趋势查询）
        Counter.builder("apex.exception.count")
                .description("按类型统计的异常发生次数")
                .tags(List.of(
                        Tag.of("exception", simpleExceptionName(exceptionType)),
                        Tag.of("level", determineAlertLevel(ex).name())))
                .register(meterRegistry)
                .increment();

        if (shouldAlert(currentCount, exceptionType)) {
            totalAlertCount.incrementAndGet();
            AlertEvent alertEvent = AlertEvent.builder()
                    .exceptionType(exceptionType)
                    .message(ex.getClass().getSimpleName())
                    .traceId(traceId)
                    .count(currentCount)
                    .serviceName(serviceName)
                    .level(determineAlertLevel(ex))
                    .build();

            alertService
                    .sendAlert(alertEvent)
                    .subscribe(
                            unused -> {
                                count.set(0);
                                log.debug("发送告警成功, exceptionType: {}, traceId: {}", exceptionType, traceId);
                            },
                            error -> {
                                log.error(
                                        "发送告警失败, exceptionType: {}, error: {}, traceId: {}",
                                        exceptionType,
                                        error.getClass().getSimpleName(),
                                        traceId);
                            });
        }
    }

    /**
     * 判断是否需要告警
     *
     * @param currentCount 当前异常计数
     * @param exceptionType 异常类型
     * @return 是否需要告警
     */
    private boolean shouldAlert(int currentCount, String exceptionType) {
        if (currentCount >= exceptionThreshold) {
            return true;
        }
        return isSystemException(exceptionType);
    }

    /**
     * 判断是否为系统异常
     *
     * @param exceptionType 异常类型
     * @return 是否为系统异常
     */
    private boolean isSystemException(String exceptionType) {
        return exceptionType.contains("NullPointerException")
                || exceptionType.contains("OutOfMemoryError")
                || exceptionType.contains("StackOverflowError")
                || exceptionType.contains("DatabaseException")
                || exceptionType.contains("RedisConnectionFailure");
    }

    /**
     * 确定告警级别
     *
     * @param ex 异常对象
     * @return 告警级别
     */
    private AlertEvent.AlertLevel determineAlertLevel(Exception ex) {
        if (ex instanceof BusinessException) {
            return AlertEvent.AlertLevel.WARNING;
        }
        if (isSystemException(ex.getClass().getName())) {
            return AlertEvent.AlertLevel.CRITICAL;
        }
        return AlertEvent.AlertLevel.ERROR;
    }

    /**
     * 获取异常统计信息
     *
     * @return 异常计数器
     */
    public ConcurrentHashMap<String, AtomicInteger> getExceptionStats() {
        return exceptionCounter;
    }

    /**
     * 重置异常统计信息
     *
     * <p>清空按异常类型累计的计数与时间戳映射，并将总异常计数归零；不影响告警发送器等
     * 其他状态，用于运维手动复位或测试清理。
     */
    public void resetExceptionStats() {
        exceptionCounter.clear();
        exceptionTimestamp.clear();
        totalExceptionCount.set(0);
    }

    /**
     * 提取异常类型的简单类名，用于指标标签。
     *
     * @param exceptionType 全限定异常类型
     * @return 简单类名
     */
    private static String simpleExceptionName(String exceptionType) {
        int idx = exceptionType.lastIndexOf('.');
        return idx >= 0 ? exceptionType.substring(idx + 1) : exceptionType;
    }

    /**
     * 清理过期的记录
     *
     * <p>定时任务，每 5 分钟自动清理超出告警窗口时间的过期异常记录，防止内存泄漏。
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredRecords() {
        long currentTime = System.currentTimeMillis();
        long windowMillis = alertWindowMinutes * 60 * 1000L;

        exceptionTimestamp.entrySet().removeIf(entry -> {
            boolean expired = currentTime - entry.getValue().get() > windowMillis;
            if (expired) {
                exceptionCounter.remove(entry.getKey());
            }
            return expired;
        });
    }
}
