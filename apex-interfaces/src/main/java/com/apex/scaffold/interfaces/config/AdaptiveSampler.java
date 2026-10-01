package com.apex.scaffold.interfaces.config;

import brave.sampler.Sampler;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * 自适应链路追踪采样器
 *
 * <p>根据系统 QPS 动态调整链路追踪采样率，在高流量时降低采样率以减少性能开销，
 * 在低流量时提高采样率以获得更完整的链路数据。
 *
 * <h2>采样策略</h2>
 * <table>
 *   <caption>QPS 区间与采样率对应关系</caption>
 *   <tr><th>QPS 区间</th><th>采样率</th><th>说明</th></tr>
 *   <tr><td>0 - 100</td><td>100%</td><td>低流量，全量采样</td></tr>
 *   <tr><td>100 - 500</td><td>50%</td><td>中流量，半量采样</td></tr>
 *   <tr><td>500 - 1000</td><td>30%</td><td>中高流量，适度采样</td></tr>
 *   <tr><td>1000+</td><td>10%</td><td>高流量，最小采样</td></tr>
 * </table>
 *
 * <h2>QPS 计算</h2>
 * <p>基于 Micrometer 的 HTTP 请求指标计算过去 1 分钟的平均 QPS：
 * <pre>
 * QPS = rate(http_server_requests_total[1m])
 * </pre>
 *
 * <h2>配置说明</h2>
 * <pre>
 * trace:
 *   adaptive-sampling:
 *     enabled: true
 *     low-qps-threshold: 100      # 低 QPS 阈值
 *     medium-qps-threshold: 500   # 中 QPS 阈值
 *     high-qps-threshold: 1000    # 高 QPS 阈值
 *     low-qps-rate: 1.0           # 低 QPS 采样率
 *     medium-qps-rate: 0.5        # 中 QPS 采样率
 *     high-qps-rate: 0.3          # 高 QPS 采样率
 *     very-high-qps-rate: 0.1     # 极高 QPS 采样率
 * </pre>
 *
 * <h2>优势</h2>
 * <ul>
 *   <li>自动适应流量变化，无需手动调整</li>
 *   <li>高流量时减少性能开销（CPU、内存、网络）</li>
 *   <li>低流量时获得完整链路数据，便于排查问题</li>
 *   <li>减少 Zipkin 存储压力</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>QPS 统计基于过去 1 分钟，存在一定延迟</li>
 *   <li>启动初期 QPS 为 0，会使用 100% 采样率</li>
 *   <li>不同实例独立计算 QPS，无跨实例协调</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Slf4j
public class AdaptiveSampler extends Sampler {

    private final MeterRegistry meterRegistry;

    /**
     * 低 QPS 阈值(QPS 低于该值时使用低 QPS 采样率)
     */
    private final long lowQpsThreshold;

    /**
     * 中 QPS 阈值(QPS 在低和中之间时使用中 QPS 采样率)
     */
    private final long mediumQpsThreshold;

    /**
     * 高 QPS 阈值(QPS 在中和高之间时使用高 QPS 采样率)
     */
    private final long highQpsThreshold;

    /**
     * 低 QPS 采样率
     */
    private final float lowQpsRate;

    /**
     * 中 QPS 采样率
     */
    private final float mediumQpsRate;

    /**
     * 高 QPS 采样率
     */
    private final float highQpsRate;

    /**
     * 极高 QPS 采样率
     */
    private final float veryHighQpsRate;

    /**
     * 最近一次 QPS 检查时间戳
     */
    private final AtomicLong lastQpsCheckTime = new AtomicLong(0);

    private volatile float cachedSamplingRate = 1.0f;

    /** QPS 检查间隔时间(毫秒) */
    private static final long QPS_CHECK_INTERVAL_MS = 10_000;

    /** 采样率变化阈值 */
    private static final double SAMPLING_RATE_CHANGE_THRESHOLD = 0.01;

    /** JVM 最小运行时间(秒) */
    private static final long MIN_UPTIME_SECONDS = 60;

    /**
     * 构造自适应采样器
     *
     * @param meterRegistry Micrometer 指标注册表
     * @param lowQpsThreshold 低 QPS 阈值
     * @param mediumQpsThreshold 中 QPS 阈值
     * @param highQpsThreshold 高 QPS 阈值
     * @param lowQpsRate 低 QPS 采样率
     * @param mediumQpsRate 中 QPS 采样率
     * @param highQpsRate 高 QPS 采样率
     * @param veryHighQpsRate 极高 QPS 采样率
     */
    public AdaptiveSampler(
            MeterRegistry meterRegistry,
            long lowQpsThreshold,
            long mediumQpsThreshold,
            long highQpsThreshold,
            float lowQpsRate,
            float mediumQpsRate,
            float highQpsRate,
            float veryHighQpsRate) {
        this.meterRegistry = meterRegistry;
        this.lowQpsThreshold = lowQpsThreshold;
        this.mediumQpsThreshold = mediumQpsThreshold;
        this.highQpsThreshold = highQpsThreshold;
        this.lowQpsRate = lowQpsRate;
        this.mediumQpsRate = mediumQpsRate;
        this.highQpsRate = highQpsRate;
        this.veryHighQpsRate = veryHighQpsRate;

        log.info(
                "自适应采样器已初始化 - QPS阈值: [{}, {}, {}], 采样率: [{}, {}, {}, {}]",
                lowQpsThreshold,
                mediumQpsThreshold,
                highQpsThreshold,
                lowQpsRate,
                mediumQpsRate,
                highQpsRate,
                veryHighQpsRate);
    }

    /**
     * 判断是否采样
     *
     * @param traceId 链路追踪 ID
     * @return true 表示采样，false 表示不采样
     */
    @Override
    public boolean isSampled(long traceId) {
        // 定期更新采样率
        updateSamplingRateIfNeeded();

        // 根据 traceId 的哈希值决定是否采样（保证采样的均匀性）
        return Math.abs(traceId) % 100 < (cachedSamplingRate * 100);
    }

    /**
     * 如果需要，更新采样率
     */
    private void updateSamplingRateIfNeeded() {
        long now = System.currentTimeMillis();
        long lastCheck = lastQpsCheckTime.get();

        // 距离上次检查超过 10 秒，重新计算
        if (now - lastCheck > QPS_CHECK_INTERVAL_MS) {
            if (lastQpsCheckTime.compareAndSet(lastCheck, now)) {
                try {
                    long currentQps = getCurrentQps();
                    float newRate = calculateSamplingRate(currentQps);

                    if (Math.abs(newRate - cachedSamplingRate) > SAMPLING_RATE_CHANGE_THRESHOLD) {
                        log.debug("更新采样率: {} -> {}, 当前 QPS: {}", cachedSamplingRate, newRate, currentQps);
                        cachedSamplingRate = newRate;
                    }
                } catch (Exception e) {
                    log.warn("计算 QPS 失败，使用默认采样率", e);
                }
            }
        }
    }

    /**
     * 获取当前 QPS
     *
     * <p>从 Micrometer 获取 HTTP 请求指标，计算过去 1 分钟的平均 QPS
     *
     * @return 当前 QPS
     */
    private long getCurrentQps() {
        try {
            // 获取 HTTP 请求总数指标
            // 注意：不同的 Spring Boot 版本，指标名称可能不同
            // Spring Boot 2.x: http.server.requests
            // Spring Boot 3.x: http.server.requests
            double totalRequests = meterRegistry.find("http.server.requests").counters().stream()
                    .mapToDouble(counter -> counter.count())
                    .sum();

            // 简化计算：假设应用已运行超过 1 分钟
            // 更精确的实现可以记录启动时间并计算运行时长
            // 这里使用简单的方式：总请求数 / 60（过去 1 分钟的平均值）
            // 注意：这是一个近似值，实际 QPS 可能会有波动

            // 如果无法获取到指标，返回 0
            if (totalRequests == 0) {
                return 0;
            }

            // 从计数器的创建时间到现在的总请求数,除以运行时间(秒)
            // 这里简化处理,假设应用刚启动时 QPS 较低会使用高采样率
            long uptimeSeconds = getUptimeSeconds();
            if (uptimeSeconds < MIN_UPTIME_SECONDS) {
                // 启动不足 1 分钟,返回 0(使用最高采样率)
                return 0;
            }

            // 计算平均 QPS
            return (long) (totalRequests / uptimeSeconds);
        } catch (Exception e) {
            log.warn("获取 QPS 指标失败", e);
            return 0;
        }
    }

    /**
     * 获取应用运行时间(秒)
     *
     * @return 运行时间(秒)
     */
    private long getUptimeSeconds() {
        try {
            // 从 JVM 运行时间获取
            return java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        } catch (Exception e) {
            // 默认假设已运行 60 秒
            return MIN_UPTIME_SECONDS;
        }
    }

    /**
     * 根据 QPS 计算采样率
     *
     * @param qps 当前 QPS
     * @return 采样率（0.0 - 1.0）
     */
    private float calculateSamplingRate(long qps) {
        if (qps < lowQpsThreshold) {
            return lowQpsRate;
        } else if (qps < mediumQpsThreshold) {
            return mediumQpsRate;
        } else if (qps < highQpsThreshold) {
            return highQpsRate;
        } else {
            return veryHighQpsRate;
        }
    }

    @Override
    public String toString() {
        return String.format("AdaptiveSampler{currentRate=%.2f}", cachedSamplingRate);
    }
}
