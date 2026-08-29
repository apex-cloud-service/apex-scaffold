package com.apex.scaffold.interfaces.config;

import static com.apex.scaffold.common.scheduler.BlockingIoSchedulers.cache;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers.SchedulerPoolSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Mono;

/**
 * Prometheus指标监控配置类
 *
 * <p>配置 JVM 内存、线程、CPU 等监控指标，以及安全相关的监控指标。
 * Redis 派生 Gauge 通过定时 SCAN 获取真实快照，并以 {@code up} 与最近成功时间标记采集状态；
 * Prometheus 抓取线程只读取原子快照，不执行阻塞 Redis 查询。
 *
 * <h2>安全监控指标</h2>
 * <ul>
 *   <li>rateLimitRejected - 限流拒绝请求计数</li>
 *   <li>ipBlocked - IP封禁计数</li>
 *   <li>loginFailed - 登录失败计数</li>
 *   <li>xssIntercepted - XSS攻击拦截计数</li>
 *   <li>antiReplayRejected - 防重放拒绝计数</li>
 *   <li>repeatSubmitRejected - HTTP幂等重复提交拒绝计数</li>
 *   <li>IP黑名单大小 - 通过 Redis SCAN blacklist:ip:* 统计</li>
 *   <li>Token黑名单大小 - 通过 Redis SCAN token:blacklist:* 统计</li>
 *   <li>活跃会话数 - 通过 Redis SCAN token:access:* 统计</li>
 * </ul>
 *
 * <h2>线程模型</h2>
 * <p>Redis SCAN 扫描操作使用 {@link com.apex.scaffold.common.scheduler.BlockingIoSchedulers#cache()}
 * 专用池承载同步 RedisTemplate 阻塞调用，避免占用 Reactor 全局 boundedElastic 导致核心业务被挤占。</p>
 *
 * <h2>阻塞 IO 线程池指标</h2>
 * <p>通过 {@code BlockingIoSchedulers#snapshotAll()} 定时采集 6 个专用阻塞池（jdbc/cache/storage/event/remote/general）
 * 的运行态快照，每池注册 7 个 Micrometer Gauge：max_threads、queue_capacity、active_tasks、queued_tasks、
 * completed_tasks、saturation（饱和度 0~1）与 up（采集可用性）。
 * 池名称作为 Prometheus 标签，面板中可用 {@code apex.blocking.io.*{pool="jdbc"}} 方式筛选。
 * 刷新周期由 {@code apex.blocking-io.snapshot-refresh-ms} 控制，默认 3 秒。</p>
 *
 * @author Apex Team
 * @version 2.3.0
 * @since 1.0.0
 */
@Configuration
public class MetricsConfig {

    private static final Logger log = LoggerFactory.getLogger(MetricsConfig.class);

    /** Redis IP 黑名单键前缀，与 {@code GlobalGuardWebFilter} 保持一致 */
    private static final String IP_BLACKLIST_KEY_PATTERN = "blacklist:ip:*";

    /** Redis Token 黑名单键前缀，与 {@code TokenBlacklistServiceImpl} 保持一致 */
    private static final String TOKEN_BLACKLIST_KEY_PATTERN = "token:blacklist:*";

    /** Redis 活跃会话键前缀，与 {@code TokenServiceImpl} 中 {@code token:access:*} 保持一致 */
    private static final String ACTIVE_SESSION_KEY_PATTERN = "token:access:*";

    /** 响应式 Redis 字符串模板，用于查询 Redis 键数量 */
    private final ReactiveStringRedisTemplate redisTemplate;

    /** Redis IP 黑名单数量的最近一次成功快照；未成功采集时为 NaN，不伪装成 0。 */
    private final AtomicReference<Double> ipBlacklistSize = new AtomicReference<>(Double.NaN);

    /** Redis Token 黑名单数量的最近一次成功快照；未成功采集时为 NaN，不伪装成 0。 */
    private final AtomicReference<Double> tokenBlacklistSize = new AtomicReference<>(Double.NaN);

    /** Redis 活跃会话数量的最近一次成功快照；未成功采集时为 NaN，不伪装成 0。 */
    private final AtomicReference<Double> activeSessionCount = new AtomicReference<>(Double.NaN);

    /** Redis 指标查询状态：1 表示最近一次刷新成功，0 表示尚未成功或刷新失败。 */
    private final AtomicLong redisMetricsUp = new AtomicLong();

    /** 最近一次成功刷新时间（Unix 秒）。 */
    private final AtomicLong redisMetricsLastSuccess = new AtomicLong();

    /** 防止 Redis 扫描较慢时多个定时刷新重叠。 */
    private final AtomicBoolean refreshInProgress = new AtomicBoolean();

    /** 阻塞 IO 线程池最近一次成功快照；初始化为空 Map，采集成功后替换为 snapshotAll() 返回值。 */
    private final AtomicReference<Map<String, SchedulerPoolSnapshot>> schedulerLatestSnapshot =
            new AtomicReference<>(Collections.emptyMap());

    /** Scheduler 指标刷新状态：1 表示最近一次成功，0 表示尚未成功或失败。 */
    private final AtomicLong schedulerMetricsUp = new AtomicLong();

    /** Scheduler 指标最近一次成功刷新时间（Unix 秒）。 */
    private final AtomicLong schedulerMetricsLastSuccess = new AtomicLong();

    /** 防止多个 Scheduler 定时刷新重叠（与 Redis 刷新独立）。 */
    private final AtomicBoolean schedulerRefreshInProgress = new AtomicBoolean();

    /**
     * 构造 Prometheus 指标监控配置类。
     *
     * <p>通过构造注入获取 {@link ReactiveStringRedisTemplate} 实例，
     * 用于 Gauge 指标的实时 Redis 数据查询。
     *
     * @param redisTemplate 响应式 Redis 字符串模板，由 Spring Boot 自动配置提供
     */
    public MetricsConfig(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 注册 JVM 内存指标。
     *
     * <p>返回 {@link JvmMemoryMetrics} 作为 Spring Bean，Micrometer 会自动将其绑定到
     * {@link MeterRegistry}。registry 参数用于触发/确保注入注册器，但在此实现中无需直接使用。
     *
     * @param registry Micrometer 指标注册器
     * @return JVM 内存指标采集器
     */
    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics(MeterRegistry registry) {
        return new JvmMemoryMetrics();
    }

    /**
     * 注册 JVM 线程指标。
     *
     * <p>返回 {@link JvmThreadMetrics} 作为 Spring Bean，Micrometer 会自动将其绑定到
     * {@link MeterRegistry}。registry 参数用于触发/确保注入注册器，但在此实现中无需直接使用。
     *
     * @param registry Micrometer 指标注册器
     * @return JVM 线程指标采集器
     */
    @Bean
    public JvmThreadMetrics jvmThreadMetrics(MeterRegistry registry) {
        return new JvmThreadMetrics();
    }

    /**
     * 注册处理器（CPU）指标。
     *
     * <p>返回 {@link ProcessorMetrics} 作为 Spring Bean，Micrometer 会自动将其绑定到
     * {@link MeterRegistry}。registry 参数用于触发/确保注入注册器，但在此实现中无需直接使用。
     *
     * @param registry Micrometer 指标注册器
     * @return 处理器指标采集器
     */
    @Bean
    public ProcessorMetrics processorMetrics(MeterRegistry registry) {
        return new ProcessorMetrics();
    }

    /**
     * 注册进程内存指标。
     *
     * <p>返回 {@link ProcessMemoryMetrics} 作为 Spring Bean，Micrometer 会自动将其绑定到
     * {@link MeterRegistry}。micrometer 不提供进程内存指标，由该类读取
     * {@code /proc/self/statm} 补齐（非 Linux 环境不注册），供 Grafana 进程内存面板使用。
     *
     * @param registry Micrometer 指标注册器
     * @return 进程内存指标绑定器
     */
    @Bean
    public MeterBinder processMemoryMetrics(MeterRegistry registry) {
        return new ProcessMemoryMetrics();
    }

    /**
     * 注册磁盘与网络指标。
     *
     * <p>补齐磁盘容量/使用率与网卡状态指标（{@code apex.disk.*}、{@code apex.network.*}），
     * 供 Grafana 大盘与监控聚合接口使用。</p>
     *
     * @param registry Micrometer 指标注册器
     * @return 磁盘/网络指标绑定器
     */
    @Bean
    public MeterBinder systemResourceMetrics(MeterRegistry registry) {
        return new SystemResourceMetrics();
    }

    /**
     * 注册限流拒绝请求计数器
     *
     * <p>统计被 Sentinel 限流规则拒绝的请求数量。
     *
     * @param registry Micrometer 指标注册器
     * @return 限流拒绝计数器
     */
    @Bean
    public Counter rateLimitRejectedCounter(MeterRegistry registry) {
        return Counter.builder("apex.security.rate.limit.rejected")
                .description("限流拒绝请求次数")
                .tags(Tags.of("type", "rate_limit"))
                .register(registry);
    }

    /**
     * 注册 IP 封禁计数器
     *
     * <p>统计因异常行为被自动封禁的 IP 数量。
     *
     * @param registry Micrometer 指标注册器
     * @return IP 封禁计数器
     */
    @Bean
    public Counter ipBlockedCounter(MeterRegistry registry) {
        return Counter.builder("apex.security.ip.blocked")
                .description("IP封禁次数")
                .tags(Tags.of("type", "ip_block"))
                .register(registry);
    }

    /**
     * 注册登录失败计数器
     *
     * <p>统计登录失败的次数，用于监控暴力破解攻击。
     *
     * @param registry Micrometer 指标注册器
     * @return 登录失败计数器
     */
    @Bean
    public Counter loginFailedCounter(MeterRegistry registry) {
        return Counter.builder("apex.security.login.failed")
                .description("登录失败次数")
                .tags(Tags.of("type", "login_fail"))
                .register(registry);
    }

    /**
     * 注册 XSS 拦截计数器
     *
     * <p>统计被 WAF 或过滤器拦截的 XSS 攻击请求数量。
     *
     * @param registry Micrometer 指标注册器
     * @return XSS 拦截计数器
     */
    @Bean
    public Counter xssInterceptedCounter(MeterRegistry registry) {
        return Counter.builder("apex.security.xss.intercepted")
                .description("XSS攻击拦截次数")
                .tags(Tags.of("type", "xss"))
                .register(registry);
    }

    /**
     * 注册防重放拒绝计数器
     *
     * <p>统计被防重放机制拒绝的重复请求数量。
     *
     * @param registry Micrometer 指标注册器
     * @return 防重放拒绝计数器
     */
    @Bean
    public Counter antiReplayRejectedCounter(MeterRegistry registry) {
        return Counter.builder("apex.security.anti.replay.rejected")
                .description("防重放拒绝次数")
                .tags(Tags.of("type", "anti_replay"))
                .register(registry);
    }

    /**
     * 注册幂等重复提交拒绝计数器
     *
     * <p>统计被 {@code @IdempotentApi} 幂等去重机制拒绝的重复写请求数量。
     *
     * @param registry Micrometer 指标注册器
     * @return 幂等重复提交拒绝计数器
     */
    @Bean
    public Counter repeatSubmitRejectedCounter(MeterRegistry registry) {
        return Counter.builder("apex.security.repeat.submit.rejected")
                .description("HTTP幂等重复提交拒绝次数")
                .tags(Tags.of("type", "repeat_submit"))
                .register(registry);
    }

    /**
     * 注册熔断器打开计数器。
     *
     * <p>统计 Sentinel 熔断降级和 resilience4j CircuitBreaker 触发 OPEN 状态的次数。
     *
     * @param registry Micrometer 指标注册器
     * @return 熔断器打开计数器
     */
    @Bean
    public Counter circuitBreakerOpenedCounter(MeterRegistry registry) {
        return Counter.builder("apex.security.circuit.breaker.opened")
                .description("熔断器打开次数")
                .tags(Tags.of("type", "circuit_breaker"))
                .register(registry);
    }

    /**
     * 注册安全指标收集器
     *
     * <p>通过 {@link MeterBinder} 注册 IP 黑名单大小、Token 黑名单大小和
     * 活跃会话数等 Gauge 指标。Gauge 读取最近一次异步刷新快照，不在 Prometheus 抓取线程中执行
     * Redis 阻塞查询；刷新失败时返回 NaN 并单独暴露查询可用性。
     *
     * <h2>Redis 键模式</h2>
     * <ul>
     *   <li>IP 黑名单：{@code blacklist:ip:*} — 由 {@code GlobalGuardWebFilter} 维护</li>
     *   <li>Token 黑名单：{@code token:blacklist:*} — 由 {@code TokenBlacklistServiceImpl} 维护</li>
     *   <li>活跃会话：{@code token:access:*} — 由 {@code TokenServiceImpl} 维护</li>
     * </ul>
     *
     * @return 安全指标绑定器
     */
    @Bean
    public MeterBinder securityMetrics() {
        return registry -> {
            io.micrometer.core.instrument.Gauge.builder(
                            "apex.security.ip.blacklist.size", ipBlacklistSize, AtomicReference::get)
                    .description("IP黑名单当前大小")
                    .tags(Tags.of("type", "ip_blacklist"))
                    .strongReference(true)
                    .register(registry);

            io.micrometer.core.instrument.Gauge.builder(
                            "apex.security.token.blacklist.size", tokenBlacklistSize, AtomicReference::get)
                    .description("Token黑名单当前大小")
                    .tags(Tags.of("type", "token_blacklist"))
                    .strongReference(true)
                    .register(registry);

            io.micrometer.core.instrument.Gauge.builder(
                            "apex.security.active.sessions", activeSessionCount, AtomicReference::get)
                    .description("当前活跃会话数")
                    .tags(Tags.of("type", "session"))
                    .strongReference(true)
                    .register(registry);

            io.micrometer.core.instrument.Gauge.builder(
                            "apex.security.redis.metrics.up", redisMetricsUp, AtomicLong::get)
                    .description("Redis 安全派生指标最近一次刷新是否成功")
                    .tags(Tags.of("type", "redis_query"))
                    .strongReference(true)
                    .register(registry);

            io.micrometer.core.instrument.Gauge.builder(
                            "apex.security.redis.metrics.last.success.timestamp",
                            redisMetricsLastSuccess,
                            AtomicLong::get)
                    .description("Redis 安全派生指标最近一次成功刷新的 Unix 时间戳")
                    .tags(Tags.of("type", "redis_query"))
                    .strongReference(true)
                    .register(registry);
        };
    }

    /**
     * 异步刷新 Redis 安全派生指标。
     *
     * <p>使用 Redis SCAN 统计键数量，不使用 KEYS；刷新运行在独立调度线程上，Prometheus 抓取只读取
     * 原子快照。刷新周期由 {@code apex.security.metrics-refresh-interval-ms} 控制，默认 5 秒；
     * Redis 不可用、超时或本次刷新仍在执行时，分别保持明确的失败状态或跳过重叠刷新，不把异常伪装成零值。</p>
     */
    @Scheduled(
            fixedDelayString = "${apex.security.metrics-refresh-interval-ms:5000}",
            initialDelayString = "${apex.security.metrics-initial-delay-ms:1000}")
    public void refreshSecurityMetrics() {
        if (!refreshInProgress.compareAndSet(false, true)) {
            return;
        }
        Mono.defer(() -> Mono.zip(
                        scanKeyCount(IP_BLACKLIST_KEY_PATTERN),
                        scanKeyCount(TOKEN_BLACKLIST_KEY_PATTERN),
                        scanKeyCount(ACTIVE_SESSION_KEY_PATTERN)))
                .timeout(Duration.ofSeconds(4))
                .subscribeOn(cache())
                .subscribe(
                        counts -> {
                            ipBlacklistSize.set(counts.getT1().doubleValue());
                            tokenBlacklistSize.set(counts.getT2().doubleValue());
                            activeSessionCount.set(counts.getT3().doubleValue());
                            redisMetricsUp.set(1L);
                            redisMetricsLastSuccess.set(System.currentTimeMillis() / 1000L);
                            refreshInProgress.set(false);
                        },
                        error -> {
                            ipBlacklistSize.set(Double.NaN);
                            tokenBlacklistSize.set(Double.NaN);
                            activeSessionCount.set(Double.NaN);
                            redisMetricsUp.set(0L);
                            refreshInProgress.set(false);
                            log.warn("刷新 Redis 安全派生指标失败: {}", error.getMessage());
                        });
    }

    /**
     * 注册阻塞 IO 线程池指标绑定器。
     *
     * <p>为 JDBC、CACHE、STORAGE、EVENT、REMOTE、GENERAL 6 个专用池各注册 7 个 Gauge：
     * <ul>
     *   <li>{@code apex.blocking.io.max.threads}       — 配置的最大工作线程数</li>
     *   <li>{@code apex.blocking.io.queue.capacity}    — 任务队列最大容量</li>
     *   <li>{@code apex.blocking.io.active.tasks}      — 当前正在执行的任务数（0 ~ max_threads）</li>
     *   <li>{@code apex.blocking.io.queued.tasks}      — 队列中等待的任务数（0 ~ queue_capacity）</li>
     *   <li>{@code apex.blocking.io.completed.tasks}   — 累计完成任务数（Counter 风格 Gauge）</li>
     *   <li>{@code apex.blocking.io.saturation}        — 池饱和度 active/max（0.0 ~ 1.0），> 0.85 建议扩容</li>
     *   <li>{@code apex.blocking.io.up}                — 本池最近一次采集是否成功（1 / 0）</li>
     * </ul>
     * 所有指标统一带 {@code pool} 标签（值为 jdbc/cache/storage/event/remote/general）。
     * Prometheus 抓取线程仅读取 {@link #schedulerLatestSnapshot} 原子引用，不触发任何阻塞调用。
     *
     * @return 阻塞 IO 线程池指标绑定器
     */
    @Bean
    public MeterBinder blockingIoSchedulerMetrics() {
        return registry -> {
            // 函数式 forEach：6 池 × 6 指标 = 36 个 Gauge，避免增强 for 循环样板
            java.util.stream.Stream.of("jdbc", "cache", "storage", "event", "remote", "general")
                    .forEach(pool -> {
                        io.micrometer.core.instrument.Gauge.builder(
                                        "apex.blocking.io.max.threads",
                                        schedulerLatestSnapshot,
                                        ref -> snapshotField(ref.get(), pool, SchedulerPoolSnapshot::maxThreads))
                                .description("阻塞 IO 专用池最大工作线程数")
                                .tags(Tags.of("pool", pool))
                                .strongReference(true)
                                .register(registry);

                        io.micrometer.core.instrument.Gauge.builder(
                                        "apex.blocking.io.queue.capacity",
                                        schedulerLatestSnapshot,
                                        ref -> snapshotField(ref.get(), pool, SchedulerPoolSnapshot::queueCapacity))
                                .description("阻塞 IO 专用池队列最大容量")
                                .tags(Tags.of("pool", pool))
                                .strongReference(true)
                                .register(registry);

                        io.micrometer.core.instrument.Gauge.builder(
                                        "apex.blocking.io.active.tasks",
                                        schedulerLatestSnapshot,
                                        ref -> snapshotField(ref.get(), pool, SchedulerPoolSnapshot::activeTasks))
                                .description("阻塞 IO 专用池当前执行中任务数")
                                .tags(Tags.of("pool", pool))
                                .strongReference(true)
                                .register(registry);

                        io.micrometer.core.instrument.Gauge.builder(
                                        "apex.blocking.io.queued.tasks",
                                        schedulerLatestSnapshot,
                                        ref -> snapshotField(ref.get(), pool, SchedulerPoolSnapshot::queuedTasks))
                                .description("阻塞 IO 专用池队列中等待的任务数")
                                .tags(Tags.of("pool", pool))
                                .strongReference(true)
                                .register(registry);

                        io.micrometer.core.instrument.Gauge.builder(
                                        "apex.blocking.io.completed.tasks",
                                        schedulerLatestSnapshot,
                                        ref -> snapshotLongField(
                                                ref.get(), pool, SchedulerPoolSnapshot::completedTasks))
                                .description("阻塞 IO 专用池累计完成任务数")
                                .tags(Tags.of("pool", pool))
                                .strongReference(true)
                                .register(registry);

                        io.micrometer.core.instrument.Gauge.builder(
                                        "apex.blocking.io.saturation", schedulerLatestSnapshot, ref -> {
                                            Map<String, SchedulerPoolSnapshot> snap = ref.get();
                                            SchedulerPoolSnapshot s = snap == null ? null : snap.get(pool);
                                            return s == null ? Double.NaN : s.saturation();
                                        })
                                .description("阻塞 IO 专用池饱和度（0.0~1.0，>0.85 建议扩容或降级）")
                                .tags(Tags.of("pool", pool))
                                .strongReference(true)
                                .register(registry);
                    });

            io.micrometer.core.instrument.Gauge.builder("apex.blocking.io.up", schedulerMetricsUp, AtomicLong::get)
                    .description("阻塞 IO 专用池快照最近一次刷新是否成功")
                    .tags(Tags.of("type", "scheduler_query"))
                    .strongReference(true)
                    .register(registry);

            io.micrometer.core.instrument.Gauge.builder(
                            "apex.blocking.io.last.success.timestamp", schedulerMetricsLastSuccess, AtomicLong::get)
                    .description("阻塞 IO 专用池快照最近一次成功刷新的 Unix 时间戳")
                    .tags(Tags.of("type", "scheduler_query"))
                    .strongReference(true)
                    .register(registry);
        };
    }

    /**
     * 从快照 Map 中按池名读取 int 字段；无快照时返回 NaN，不伪装成零。
     *
     * @param map  最近一次快照（非空，可能是空 Map）
     * @param pool 池名称
     * @param ext  字段提取函数
     * @return 字段值的 double 表现；池中不存在返回 {@link Double#NaN}
     */
    private static double snapshotField(
            Map<String, SchedulerPoolSnapshot> map,
            String pool,
            java.util.function.ToIntFunction<SchedulerPoolSnapshot> ext) {
        SchedulerPoolSnapshot s = map.get(pool);
        return s == null ? Double.NaN : ext.applyAsInt(s);
    }

    /**
     * 从快照 Map 中按池名读取 long 字段（累计完成任务数）；无快照时返回 NaN。
     *
     * @param map  最近一次快照（非空，可能是空 Map）
     * @param pool 池名称
     * @param ext  long 字段提取函数
     * @return 字段值的 double 表现；池中不存在返回 {@link Double#NaN}
     */
    private static double snapshotLongField(
            Map<String, SchedulerPoolSnapshot> map,
            String pool,
            java.util.function.ToLongFunction<SchedulerPoolSnapshot> ext) {
        SchedulerPoolSnapshot s = map.get(pool);
        return s == null ? Double.NaN : (double) ext.applyAsLong(s);
    }

    /**
     * 异步刷新阻塞 IO 线程池运行态快照。
     *
     * <p>调用 {@code BlockingIoSchedulers#snapshotAll()} 通过反射读取各 BoundedElasticScheduler
     * 内部统计。刷新周期由 {@code apex.blocking-io.snapshot-refresh-ms} 控制，默认 3 秒；
     * 超时或重叠刷新时分别保持明确失败状态或跳过。本方法本身为轻量反射读取，不阻塞 Reactor。
     */
    @Scheduled(
            fixedDelayString = "${apex.blocking-io.snapshot-refresh-ms:3000}",
            initialDelayString = "${apex.blocking-io.snapshot-initial-delay-ms:1500}")
    public void refreshSchedulerMetrics() {
        if (!schedulerRefreshInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            Map<String, SchedulerPoolSnapshot> snap = BlockingIoSchedulers.snapshotAll();
            schedulerLatestSnapshot.set(snap);
            schedulerMetricsUp.set(1L);
            schedulerMetricsLastSuccess.set(System.currentTimeMillis() / 1000L);
        } catch (RuntimeException error) {
            schedulerMetricsUp.set(0L);
            log.warn("刷新阻塞 IO 线程池快照失败: {}", error.getMessage());
        } finally {
            schedulerRefreshInProgress.set(false);
        }
    }

    /**
     * 使用 Redis SCAN 统计指定模式的键数量。
     *
     * <p>SCAN 结果是本次刷新期间看到的键数量，不承诺跨多个刷新周期的事务快照；这符合
     * Gauge 的实时近似语义。Redis 出错时由上层将值置为 NaN，而不是把故障伪装成零。</p>
     *
     * @param pattern Redis glob 匹配模式
     * @return 匹配键数量的响应式结果
     */
    private Mono<Long> scanKeyCount(String pattern) {
        return redisTemplate
                .scan(ScanOptions.scanOptions().match(pattern).count(1000).build())
                .count()
                .switchIfEmpty(Mono.just(0L));
    }
}
