package com.apex.scaffold.interfaces.monitor.dashboard;

import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.apex.scaffold.interfaces.dto.monitor.MonitorBusinessResponse;
import com.apex.scaffold.interfaces.dto.monitor.MonitorGovernanceResponse;
import com.apex.scaffold.interfaces.dto.monitor.MonitorMiddlewareResponse;
import com.apex.scaffold.interfaces.dto.monitor.MonitorOverviewResponse;
import com.apex.scaffold.interfaces.dto.monitor.MonitorSystemResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.net.NetworkInterface;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * 监控大盘指标采集器。
 *
 * <p>在进程内聚合 Micrometer 指标、OS 资源、Sentinel 规则、Resilience4j 熔断器、
 * MySQL/Redis/消息中间件/Zipkin 状态，为监控大盘提供统一 JSON 数据源。</p>
 *
 * <h2>数据来源</h2>
 * <ul>
 *   <li>JVM/CPU/线程/GC/类加载：Micrometer jvm.* / system.* / process.* 与 MXBean</li>
 *   <li>磁盘：{@link FileStore}；网络：{@link NetworkInterface} + Linux /proc/net/dev</li>
 *   <li>MySQL：druid.* / hikaricp.* 连接池指标；Redis：redisson.* 与 apex.security.*</li>
 *   <li>消息中间件：RocketMQTemplate Bean 存在性 + 配置</li>
 *   <li>链路追踪：Brave Tracing + AsyncReporter 待上报 Span</li>
 *   <li>治理：Sentinel 规则管理器 + apex.security.* 计数器 + apex.gateway.* / apex.api.*</li>
 *   <li>核心业务：apex.business.* 计数器（近 10 分钟按分钟聚合趋势）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Component
@ConditionalOnClass({CircuitBreakerRegistry.class, RocketMQTemplate.class})
public class MonitorMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(MonitorMetricsCollector.class);

    /** 业务趋势保留分钟数。 */
    private static final int TREND_WINDOW_MINUTES = 10;

    /** 时间格式化。 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /* ==================== 指标名常量 ==================== */

    private static final String M_JVM_MEMORY_USED = "jvm.memory.used";
    private static final String M_JVM_MEMORY_COMMITTED = "jvm.memory.committed";
    private static final String M_JVM_MEMORY_MAX = "jvm.memory.max";
    private static final String M_JVM_THREADS_LIVE = "jvm.threads.live";
    private static final String M_JVM_THREADS_PEAK = "jvm.threads.peak";
    private static final String M_JVM_THREADS_DAEMON = "jvm.threads.daemon";
    private static final String M_JVM_GC_PAUSE = "jvm.gc.pause";
    private static final String M_JVM_CLASSES_LOADED = "jvm.classes.loaded";
    private static final String M_JVM_CLASSES_UNLOADED = "jvm.classes.unloaded";
    private static final String M_JVM_BUFFER_MEMORY = "jvm.buffer.memory.used";
    private static final String M_SYSTEM_CPU = "system.cpu.usage";
    private static final String M_PROCESS_CPU = "process.cpu.usage";
    private static final String M_SYSTEM_CPU_COUNT = "system.cpu.count";
    private static final String M_PROCESS_UPTIME = "process.uptime";
    private static final String M_PROCESS_START_TIME = "process.start.time";
    private static final String M_PROCESS_FILES_OPEN = "process.files.open.files";
    private static final String M_PROCESS_FILES_MAX = "process.files.max.files";
    private static final String M_PROCESS_RESIDENT = "process.resident.memory.bytes";
    private static final String M_PROCESS_VIRTUAL = "process.virtual.memory.bytes";

    private static final String M_DRUID_ACTIVE = "druid.connections.active";
    private static final String M_DRUID_MAX = "druid.connections.max";
    private static final String M_DRUID_IDLE = "druid.connections.idle";
    private static final String M_HIKARI_ACTIVE = "hikaricp.connections.active";
    private static final String M_HIKARI_MAX = "hikaricp.connections.max";
    private static final String M_HIKARI_IDLE = "hikaricp.connections.idle";

    private static final String M_REDISSON_NODES = "redisson.nodes.total";
    private static final String M_REDISSON_HEALTHY = "redisson.healthy";
    private static final String M_REDIS_UP = "apex.security.redis.metrics.up";
    private static final String M_REDIS_LAST_SUCCESS = "apex.security.redis.metrics.last.success.timestamp";
    private static final String M_ACTIVE_SESSIONS = "apex.security.active.sessions";
    private static final String M_IP_BLACKLIST = "apex.security.ip.blacklist.size";
    private static final String M_TOKEN_BLACKLIST = "apex.security.token.blacklist.size";

    private static final String M_RATE_LIMIT_REJECTED = "apex.security.rate.limit.rejected";
    private static final String M_CIRCUIT_OPENED = "apex.security.circuit.breaker.opened";
    private static final String M_IP_BLOCKED = "apex.security.ip.blocked";
    private static final String M_LOGIN_FAILED = "apex.security.login.failed";
    private static final String M_XSS_INTERCEPTED = "apex.security.xss.intercepted";
    private static final String M_ANTI_REPLAY = "apex.security.anti.replay.rejected";
    private static final String M_REPEAT_SUBMIT = "apex.security.repeat.submit.rejected";

    private static final String M_API_REQUEST_COUNT = "apex.api.request.count";
    private static final String M_API_RESPONSE_TIME = "apex.api.response.time";
    private static final String M_GATEWAY_REQUEST_COUNT = "apex.gateway.request.count";
    private static final String M_GATEWAY_RESPONSE_TIME = "apex.gateway.response.time";

    private static final String M_BUSINESS_LOGIN_SUCCESS = "apex.business.login.success";
    private static final String M_BUSINESS_REGISTER = "apex.business.register";
    private static final String M_BUSINESS_UPLOAD = "apex.business.upload";
    private static final String M_BUSINESS_CREDIT_CHANGE = "apex.business.credit.change";

    private static final String POOL_TYPE_JDBC = "jdbc";
    private static final String POOL_TYPE_CACHE = "cache";
    private static final String POOL_TYPE_STORAGE = "storage";
    private static final String POOL_TYPE_EVENT = "event";
    private static final String POOL_TYPE_REMOTE = "remote";
    private static final String POOL_TYPE_GENERAL = "general";

    private static final double MIN_SAMPLE_INTERVAL_SECONDS = 0.5;
    private static final double MAX_SAMPLE_INTERVAL_SECONDS = 60;
    private static final long MILLISECONDS_PER_MINUTE = 60_000;

    /* ==================== 依赖与配置 ==================== */

    private final MeterRegistry meterRegistry;

    /** 熔断器注册表（Resilience4j，可能未配置）。 */
    private final ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistryProvider;

    /** RocketMQ 模板（可能未接入）。 */
    private final ObjectProvider<RocketMQTemplate> rocketMqTemplateProvider;

    @Value("${spring.application.name:apex-scaffold}")
    private String applicationName;

    @Value("${apex.version:1.2.0}")
    private String applicationVersion;

    @Value("${trace.zipkin-url:}")
    private String zipkinUrl;

    @Value("${trace.sampling-rate:1.0}")
    private float samplingRate;

    @Value("${rocketmq.name-server:}")
    private String rocketMqNameServer;

    /** 业务趋势聚合：指标键 → 最近 10 分钟每分钟累计值（key=epoch 分钟）。 */
    private final Map<String, Deque<BusinessBucket>> businessTrends = new ConcurrentHashMap<>();

    /** 业务指标上次读取值（用于分钟窗口增量）。 */
    private final Map<String, Double> businessLastValue = new ConcurrentHashMap<>();

    /** 网络速率上次采样（Linux /proc/net/dev）。 */
    private volatile Map<String, long[]> lastNetSample = Collections.emptyMap();

    private volatile long lastNetSampleNanos;

    /** 每分钟业务聚合桶。 */
    private record BusinessBucket(String key, String name, String unit, long minute, double value, String time) {}

    /**
     * 创建监控指标采集器。
     *
     * @param meterRegistry Micrometer 指标注册表
     * @param circuitBreakerRegistryProvider 熔断器注册表提供器
     * @param rocketMqTemplateProvider RocketMQ 模板提供器
     */
    public MonitorMetricsCollector(
            MeterRegistry meterRegistry,
            ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistryProvider,
            ObjectProvider<RocketMQTemplate> rocketMqTemplateProvider) {
        this.meterRegistry = meterRegistry;
        this.circuitBreakerRegistryProvider = circuitBreakerRegistryProvider;
        this.rocketMqTemplateProvider = rocketMqTemplateProvider;
    }

    /* ==================== 概览 ==================== */

    /**
     * 采集监控概览。
     *
     * @return 概览响应
     */
    public MonitorOverviewResponse overview() {
        double heapUsed = sumGauges(M_JVM_MEMORY_USED, "area", "heap");
        double heapMax = sumGauges(M_JVM_MEMORY_MAX, "area", "heap");
        double p95 = timerPercentile(M_API_RESPONSE_TIME, 0.95);
        double p99 = timerPercentile(M_API_RESPONSE_TIME, 0.99);
        return new MonitorOverviewResponse(
                applicationName,
                applicationVersion,
                healthStatus(),
                (long) gauge(M_PROCESS_UPTIME),
                formatTimestamp(gauge(M_PROCESS_START_TIME)),
                System.currentTimeMillis(),
                heapUsed,
                heapMax,
                safeRatio(heapUsed, heapMax),
                sumGauges(M_JVM_MEMORY_USED, "area", "nonheap"),
                gauge(M_PROCESS_CPU),
                gauge(M_SYSTEM_CPU),
                gauge(M_JVM_THREADS_LIVE),
                gauge(M_ACTIVE_SESSIONS),
                counter(M_API_REQUEST_COUNT),
                apiErrorCount(4),
                apiErrorCount(5),
                timerAverage(M_API_RESPONSE_TIME),
                p95,
                p99,
                counter(M_GATEWAY_REQUEST_COUNT),
                counter(M_RATE_LIMIT_REJECTED),
                counter(M_CIRCUIT_OPENED),
                counter(M_LOGIN_FAILED),
                counter(M_BUSINESS_LOGIN_SUCCESS),
                counter(M_BUSINESS_REGISTER),
                counter(M_BUSINESS_UPLOAD));
    }

    /**
     * 综合健康判定。
     *
     * <p>依据 MySQL 连接池、Redis 健康探针与熔断器状态综合评估服务健康度：
     * 任一关键中间件不可用或存在熔断器处于 OPEN 状态时判定为 {@code DEGRADED}，
     * 全部正常时返回 {@code UP}。避免单一指标缺失导致误报——Gauge 为 NaN
     * （未采集到数据）时不参与判定。</p>
     *
     * @return UP / DEGRADED
     */
    private String healthStatus() {
        boolean mysqlOk = isAvailable(gauge(M_DRUID_ACTIVE), gauge(M_DRUID_MAX))
                || isAvailable(gauge(M_HIKARI_ACTIVE), gauge(M_HIKARI_MAX));
        boolean redisOk = isAvailable(gauge(M_REDISSON_NODES), gauge(M_REDISSON_HEALTHY)) || gauge(M_REDIS_UP) >= 1;
        boolean circuitOpen = circuitBreakerStates().stream().anyMatch(state -> "OPEN".equals(state.state()));
        if (!mysqlOk || !redisOk || circuitOpen) {
            return "DEGRADED";
        }
        return "UP";
    }

    /**
     * 判断指标是否有效（非 NaN 且为正）。
     *
     * @param values 指标值
     * @return 存在有效值返回 true
     */
    private static boolean isAvailable(double... values) {
        for (double v : values) {
            if (!Double.isNaN(v) && v > 0) {
                return true;
            }
        }
        return false;
    }

    /* ==================== 系统资源 ==================== */

    /**
     * 采集系统资源（内存/CPU/磁盘/网络/JVM/线程/GC）。
     *
     * @return 系统监控响应
     */
    public MonitorSystemResponse system() {
        MemoryMXBean memoryMx = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryMx.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMx.getNonHeapMemoryUsage();

        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        double loadAverage = os.getSystemLoadAverage();

        MonitorSystemResponse.JvmStats jvm = new MonitorSystemResponse.JvmStats(
                heap.getUsed(),
                heap.getCommitted(),
                Math.max(heap.getMax(), heap.getCommitted()),
                safeRatio(heap.getUsed(), Math.max(heap.getMax(), heap.getCommitted())),
                nonHeap.getUsed(),
                nonHeap.getCommitted(),
                timerTotalTime(M_JVM_GC_PAUSE),
                timerCount(M_JVM_GC_PAUSE),
                (long) gauge(M_JVM_CLASSES_LOADED),
                (long) gauge(M_JVM_CLASSES_UNLOADED),
                sumGauges(M_JVM_BUFFER_MEMORY));

        MonitorSystemResponse.CpuStats cpu = new MonitorSystemResponse.CpuStats(
                gauge(M_PROCESS_CPU),
                gauge(M_SYSTEM_CPU),
                gauge(M_SYSTEM_CPU_COUNT),
                loadAverage < 0 ? -1 : loadAverage);

        MonitorSystemResponse.ProcessStats process = new MonitorSystemResponse.ProcessStats(
                (long) gauge(M_PROCESS_UPTIME),
                formatTimestamp(gauge(M_PROCESS_START_TIME)),
                gauge(M_PROCESS_RESIDENT),
                gauge(M_PROCESS_VIRTUAL),
                gauge(M_PROCESS_FILES_OPEN),
                gauge(M_PROCESS_FILES_MAX));

        MonitorSystemResponse.ThreadStats threadStats = new MonitorSystemResponse.ThreadStats(
                gauge(M_JVM_THREADS_LIVE), gauge(M_JVM_THREADS_PEAK), gauge(M_JVM_THREADS_DAEMON));

        return new MonitorSystemResponse(
                System.currentTimeMillis(),
                jvm,
                cpu,
                process,
                threadStats,
                diskUsage(),
                networkUsage(),
                blockingIoPools());
    }

    /* ==================== 中间件 ==================== */

    /**
     * 采集中间件监控（MySQL/Redis/MQ/Zipkin）。
     *
     * @return 中间件监控响应
     */
    public MonitorMiddlewareResponse middleware() {
        return new MonitorMiddlewareResponse(
                System.currentTimeMillis(), mysqlStats(), redisStats(), mqStats(), tracingStats());
    }

    /* ==================== 治理 ==================== */

    /**
     * 采集治理监控（Sentinel/熔断器/网关流量）。
     *
     * @return 治理监控响应
     */
    public MonitorGovernanceResponse governance() {
        return new MonitorGovernanceResponse(
                System.currentTimeMillis(),
                new MonitorGovernanceResponse.SentinelStats(
                        sentinelEnabled(), flowRules(), degradeRules(), authorityRules(), paramFlowRules()),
                new MonitorGovernanceResponse.SecurityCounters(
                        counter(M_RATE_LIMIT_REJECTED),
                        counter(M_CIRCUIT_OPENED),
                        counter(M_IP_BLOCKED),
                        counter(M_LOGIN_FAILED),
                        counter(M_XSS_INTERCEPTED),
                        counter(M_ANTI_REPLAY),
                        counter(M_REPEAT_SUBMIT)),
                circuitBreakerStates(),
                gatewayStats());
    }

    /* ==================== 核心业务 ==================== */

    /**
     * 采集核心业务指标与趋势。
     *
     * @return 业务监控响应
     */
    public MonitorBusinessResponse business() {
        List<MonitorBusinessResponse.MetricPoint> metrics = List.of(
                metric("loginSuccess", "登录成功", counter(M_BUSINESS_LOGIN_SUCCESS), "次"),
                metric("register", "注册成功", counter(M_BUSINESS_REGISTER), "次"),
                metric("upload", "文件上传", counter(M_BUSINESS_UPLOAD), "次"),
                metric("creditChange", "信用变动", counter(M_BUSINESS_CREDIT_CHANGE), "次"),
                metric("loginFailed", "登录失败", counter(M_LOGIN_FAILED), "次"),
                metric("api5xx", "接口 5xx", apiErrorCount(5), "次"));

        // 记录本次采样点到趋势序列，供下方组装趋势数据
        metrics.forEach(m -> recordTrend(m.key(), m.name(), m.unit(), m.value()));

        List<MonitorBusinessResponse.BusinessTrend> trends = businessTrends.entrySet().stream()
                .map(entry -> new MonitorBusinessResponse.BusinessTrend(
                        entry.getValue().peekLast() == null
                                ? entry.getKey()
                                : entry.getValue().peekLast().name(),
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(b -> new MonitorBusinessResponse.TrendPoint(b.time(), b.value()))
                                .toList()))
                .toList();

        return new MonitorBusinessResponse(System.currentTimeMillis(), metrics, trends);
    }

    /* ==================== 系统资源实现 ==================== */

    /**
     * 采集磁盘使用。
     *
     * @return 磁盘使用列表
     */
    private List<MonitorSystemResponse.DiskUsage> diskUsage() {
        List<MonitorSystemResponse.DiskUsage> list = new ArrayList<>();
        try {
            for (FileStore store : FileSystems.getDefault().getFileStores()) {
                long total = store.getTotalSpace();
                long usable = store.getUsableSpace();
                long free = store.getUnallocatedSpace();
                list.add(new MonitorSystemResponse.DiskUsage(
                        store.name(), store.type(), total, free, usable, safeRatio(total - usable, total)));
            }
        } catch (Exception e) {
            log.debug("采集磁盘指标失败: {}", e.getMessage());
        }
        return list;
    }

    /**
     * 采集网络使用（Linux 读取 /proc/net/dev 计算速率，其他平台仅网卡状态）。
     *
     * @return 网络使用列表
     */
    private List<MonitorSystemResponse.NetworkUsage> networkUsage() {
        List<MonitorSystemResponse.NetworkUsage> list = new ArrayList<>();
        boolean linux = System.getProperty("os.name", "").toLowerCase().contains("linux");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface net = interfaces.nextElement();
                if (!net.isUp() || net.isLoopback() || net.isVirtual()) {
                    continue;
                }
                long rx = 0;
                long tx = 0;
                if (linux) {
                    long[] sample = linuxNetDev(net.getName());
                    if (sample != null) {
                        rx = sample[0];
                        tx = sample[1];
                    }
                }
                long speed = -1;
                list.add(new MonitorSystemResponse.NetworkUsage(net.getName(), net.isUp(), speed, rx, tx, 0, 0, false));
            }
            if (linux && !list.isEmpty()) {
                fillNetRates(list);
            }
        } catch (Exception e) {
            log.debug("采集网络指标失败: {}", e.getMessage());
        }
        return list;
    }

    /**
     * 读取 Linux /proc/net/dev 的累计收发字节。
     *
     * @param name 网卡名
     * @return [rxBytes, txBytes]
     */
    private long[] linuxNetDev(String name) {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/net/dev"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.startsWith(name + ":")) {
                    continue;
                }
                String[] parts = trimmed.substring(name.length() + 1).trim().split("\\s+");
                if (parts.length >= 9) {
                    return new long[] {Long.parseLong(parts[0]), Long.parseLong(parts[8])};
                }
            }
        } catch (Exception e) {
            log.debug("读取 /proc/net/dev 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 基于两次采样差值计算网络收发速率。
     *
     * @param usages 当前网络使用列表（原地更新速率字段）
     */
    private void fillNetRates(List<MonitorSystemResponse.NetworkUsage> usages) {
        Map<String, long[]> current = new LinkedHashMap<>();
        usages.forEach(u -> current.put(u.name(), new long[] {u.rxBytes(), u.txBytes()}));
        long now = System.nanoTime();
        if (!lastNetSample.isEmpty() && lastNetSampleNanos > 0) {
            double seconds = (now - lastNetSampleNanos) / 1_000_000_000.0;
            if (seconds > MIN_SAMPLE_INTERVAL_SECONDS && seconds < MAX_SAMPLE_INTERVAL_SECONDS) {
                List<MonitorSystemResponse.NetworkUsage> updated = new ArrayList<>();
                for (MonitorSystemResponse.NetworkUsage usage : usages) {
                    long[] prev = lastNetSample.get(usage.name());
                    if (prev != null) {
                        double rxRate = Math.max(0, (usage.rxBytes() - prev[0]) / seconds);
                        double txRate = Math.max(0, (usage.txBytes() - prev[1]) / seconds);
                        updated.add(new MonitorSystemResponse.NetworkUsage(
                                usage.name(),
                                usage.up(),
                                usage.speedMbps(),
                                usage.rxBytes(),
                                usage.txBytes(),
                                rxRate,
                                txRate,
                                true));
                    } else {
                        updated.add(usage);
                    }
                }
                lastNetSample = current;
                lastNetSampleNanos = now;
                usages.clear();
                usages.addAll(updated);
                return;
            }
        }
        lastNetSample = current;
        lastNetSampleNanos = now;
    }

    /**
     * 采集阻塞 IO 线程池快照（读取 apex.blocking.io.* 指标）。
     *
     * @return 池名 → 快照
     */
    private Map<String, MonitorSystemResponse.BlockingIoPool> blockingIoPools() {
        Map<String, MonitorSystemResponse.BlockingIoPool> pools = new LinkedHashMap<>();
        double up = gauge("apex.blocking.io.up", "type", "scheduler_query");
        for (String pool : List.of(
                POOL_TYPE_JDBC,
                POOL_TYPE_CACHE,
                POOL_TYPE_STORAGE,
                POOL_TYPE_EVENT,
                POOL_TYPE_REMOTE,
                POOL_TYPE_GENERAL)) {
            pools.put(
                    pool,
                    new MonitorSystemResponse.BlockingIoPool(
                            (int) gauge("apex.blocking.io.max.threads", "pool", pool),
                            (int) gauge("apex.blocking.io.queue.capacity", "pool", pool),
                            (int) gauge("apex.blocking.io.active.tasks", "pool", pool),
                            (int) gauge("apex.blocking.io.queued.tasks", "pool", pool),
                            (long) gauge("apex.blocking.io.completed.tasks", "pool", pool),
                            gauge("apex.blocking.io.saturation", "pool", pool),
                            up >= 1));
        }
        return pools;
    }

    /* ==================== 中间件实现 ==================== */

    /**
     * 采集 MySQL 连接池指标（Druid 优先，Hikari 兜底）。
     *
     * @return MySQL 指标
     */
    private MonitorMiddlewareResponse.MysqlStats mysqlStats() {
        double active = gauge(M_DRUID_ACTIVE);
        double max = gauge(M_DRUID_MAX);
        double idle = gauge(M_DRUID_IDLE);
        String driver = "Druid";
        if (Double.isNaN(active) && Double.isNaN(max)) {
            active = gauge(M_HIKARI_ACTIVE);
            max = gauge(M_HIKARI_MAX);
            idle = gauge(M_HIKARI_IDLE);
            driver = "HikariCP";
        }
        boolean available = !Double.isNaN(active) || !Double.isNaN(max);
        return new MonitorMiddlewareResponse.MysqlStats(
                available,
                available ? driver : null,
                active,
                max,
                idle,
                safeRatio(active, max),
                counter("druid.statistics.connect"),
                counter("druid.statistics.error"),
                gauge("druid.statistics.slow.sql.count"));
    }

    /**
     * 采集 Redis 指标（Redisson 探针 + 派生键数）。
     *
     * @return Redis 指标
     */
    private MonitorMiddlewareResponse.RedisStats redisStats() {
        double nodes = gauge(M_REDISSON_NODES);
        double healthy = gauge(M_REDISSON_HEALTHY);
        double up = gauge(M_REDIS_UP);
        boolean available = !Double.isNaN(nodes) || !Double.isNaN(healthy) || up >= 1;
        return new MonitorMiddlewareResponse.RedisStats(
                available,
                healthy >= 1,
                nodes,
                up >= 1,
                gauge(M_ACTIVE_SESSIONS),
                gauge(M_IP_BLACKLIST),
                gauge(M_TOKEN_BLACKLIST),
                gauge(M_REDIS_LAST_SUCCESS));
    }

    /**
     * 采集消息中间件（RocketMQ）状态。
     *
     * @return 消息中间件指标
     */
    private MonitorMiddlewareResponse.MqStats mqStats() {
        RocketMQTemplate template = rocketMqTemplateProvider.getIfAvailable();
        if (template == null || template.getProducer() == null) {
            return new MonitorMiddlewareResponse.MqStats(false, "RocketMQ", rocketMqNameServer, null, "未接入", 0);
        }
        return new MonitorMiddlewareResponse.MqStats(
                true, "RocketMQ", rocketMqNameServer, template.getProducer().getProducerGroup(), "RUNNING", 0);
    }

    /**
     * 采集链路追踪（Zipkin/Brave）状态。
     *
     * <p>当前提供配置级信息（上报地址、采样率）；zipkin-reporter 3.x 的
     * AsyncReporter 不暴露待上报队列读取 API，Span 积压/丢弃计数暂置 0。</p>
     *
     * @return 链路追踪指标
     */
    private MonitorMiddlewareResponse.TracingStats tracingStats() {
        boolean available = zipkinUrl != null && !zipkinUrl.isBlank();
        return new MonitorMiddlewareResponse.TracingStats(
                available, "Brave/Zipkin", available ? zipkinUrl : null, samplingRate, 0, 0);
    }

    /* ==================== 治理实现 ==================== */

    /**
     * Sentinel 是否启用（存在任意规则即视为启用）。
     *
     * @return 是否启用
     */
    private boolean sentinelEnabled() {
        try {
            return !FlowRuleManager.getRules().isEmpty()
                    || !DegradeRuleManager.getRules().isEmpty()
                    || !AuthorityRuleManager.getRules().isEmpty()
                    || !ParamFlowRuleManager.getRules().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 采集 Sentinel 限流规则。
     *
     * @return 规则列表
     */
    private List<MonitorGovernanceResponse.SentinelRule> flowRules() {
        try {
            return FlowRuleManager.getRules().stream()
                    .map(rule -> new MonitorGovernanceResponse.SentinelRule(
                            rule.getResource(),
                            "限流",
                            rule.getCount(),
                            flowStrategyText(rule),
                            flowControlBehaviorText(rule),
                            null,
                            true))
                    .toList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 采集 Sentinel 熔断降级规则。
     *
     * @return 规则列表
     */
    private List<MonitorGovernanceResponse.SentinelRule> degradeRules() {
        try {
            return DegradeRuleManager.getRules().stream()
                    .map(rule -> new MonitorGovernanceResponse.SentinelRule(
                            rule.getResource(),
                            "熔断降级",
                            rule.getCount(),
                            degradeGradeText(rule),
                            null,
                            rule.getTimeWindow(),
                            true))
                    .toList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 采集 Sentinel 授权规则。
     *
     * @return 规则列表
     */
    private List<MonitorGovernanceResponse.SentinelRule> authorityRules() {
        try {
            return AuthorityRuleManager.getRules().stream()
                    .map(rule -> new MonitorGovernanceResponse.SentinelRule(
                            rule.getResource(), "授权", rule.getStrategy(), rule.getLimitApp(), null, null, true))
                    .toList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 采集 Sentinel 热点参数规则。
     *
     * @return 规则列表
     */
    private List<MonitorGovernanceResponse.SentinelRule> paramFlowRules() {
        try {
            return ParamFlowRuleManager.getRules().stream()
                    .map(rule -> new MonitorGovernanceResponse.SentinelRule(
                            rule.getResource(),
                            "热点参数",
                            rule.getCount(),
                            "参数索引 " + rule.getParamIdx(),
                            null,
                            null,
                            true))
                    .toList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 采集 Resilience4j 熔断器状态。
     *
     * @return 熔断器状态列表
     */
    private List<MonitorGovernanceResponse.CircuitBreakerState> circuitBreakerStates() {
        CircuitBreakerRegistry registry = circuitBreakerRegistryProvider.getIfAvailable();
        if (registry == null) {
            return Collections.emptyList();
        }
        return registry.getAllCircuitBreakers().stream()
                .map(cb -> {
                    CircuitBreaker.Metrics metrics = cb.getMetrics();
                    return new MonitorGovernanceResponse.CircuitBreakerState(
                            cb.getName(),
                            cb.getState().name(),
                            metrics.getFailureRate() < 0 ? Double.NaN : metrics.getFailureRate() / 100.0,
                            metrics.getSlowCallRate() < 0 ? Double.NaN : metrics.getSlowCallRate() / 100.0,
                            metrics.getNumberOfBufferedCalls(),
                            metrics.getNumberOfFailedCalls(),
                            metrics.getNumberOfNotPermittedCalls());
                })
                .toList();
    }

    /**
     * 采集网关/API 流量（含 TOP 路径）。
     *
     * @return 网关流量统计
     */
    private MonitorGovernanceResponse.GatewayStats gatewayStats() {
        double total = counter(M_GATEWAY_REQUEST_COUNT);
        double error4xx = gatewayErrorCount(4);
        double error5xx = gatewayErrorCount(5);

        // 按 path 聚合请求数与耗时
        Map<String, PathAggregate> byPath = new LinkedHashMap<>();
        for (Meter meter : meterRegistry.find(M_GATEWAY_REQUEST_COUNT).meters()) {
            Map<String, String> tags = meterTags(meter);
            String path = tags.getOrDefault("path", "/unknown");
            String method = tags.getOrDefault("method", "");
            int status = parseIntSafe(tags.get("status"));
            PathAggregate agg = byPath.computeIfAbsent(path + "|" + method, key -> new PathAggregate(path, method));
            if (meter instanceof io.micrometer.core.instrument.Counter c) {
                agg.count += c.count();
                if (status >= 400) {
                    agg.errorCount += c.count();
                }
            }
        }
        // 耗时聚合
        for (Timer timer : meterRegistry.find(M_GATEWAY_RESPONSE_TIME).timers()) {
            Map<String, String> tags = timerTags(timer);
            String path = tags.getOrDefault("path", "/unknown");
            String method = tags.getOrDefault("method", "");
            PathAggregate agg = byPath.computeIfAbsent(path + "|" + method, key -> new PathAggregate(path, method));
            agg.avgTime = timer.mean(TimeUnit.SECONDS);
            agg.maxTime = Math.max(agg.maxTime, timer.max(TimeUnit.SECONDS));
        }

        List<MonitorGovernanceResponse.PathTraffic> topPaths = byPath.values().stream()
                .sorted(Comparator.comparingDouble((PathAggregate agg) -> agg.count)
                        .reversed())
                .limit(10)
                .map(agg -> new MonitorGovernanceResponse.PathTraffic(
                        agg.path,
                        agg.method,
                        agg.count,
                        agg.avgTime,
                        agg.maxTime,
                        agg.errorCount,
                        agg.count <= 0 ? 0 : agg.errorCount / agg.count))
                .toList();

        double avgTime = timerAverage(M_GATEWAY_RESPONSE_TIME);
        double maxTime = timerMax(M_GATEWAY_RESPONSE_TIME);
        return new MonitorGovernanceResponse.GatewayStats(total, error4xx, error5xx, avgTime, maxTime, topPaths);
    }

    /** 路径流量聚合结构。 */
    private static final class PathAggregate {
        private final String path;
        private final String method;
        private double count;
        private double errorCount;
        private double avgTime;
        private double maxTime;

        private PathAggregate(String path, String method) {
            this.path = path;
            this.method = method;
        }
    }

    /* ==================== 核心业务实现 ==================== */

    /**
     * 记录业务指标趋势（按分钟桶聚合增量）。
     *
     * @param key 指标键
     * @param name 指标名称
     * @param unit 单位
     * @param current 当前累计值
     */
    private void recordTrend(String key, String name, String unit, double current) {
        long minute = System.currentTimeMillis() / MILLISECONDS_PER_MINUTE;
        double previous = businessLastValue.getOrDefault(key, Double.NaN);
        double delta = Double.isNaN(previous) ? 0 : Math.max(0, current - previous);
        businessLastValue.put(key, current);

        Deque<BusinessBucket> buckets = businessTrends.computeIfAbsent(key, k -> new ArrayDeque<>());
        BusinessBucket last = buckets.peekLast();
        String time = LocalTime.now().format(TIME_FORMATTER);
        if (last != null && last.minute() == minute) {
            buckets.removeLast();
            buckets.addLast(new BusinessBucket(key, name, unit, minute, last.value() + delta, time));
        } else {
            buckets.addLast(new BusinessBucket(key, name, unit, minute, delta, time));
        }
        // 清理超过窗口的桶
        while (buckets.size() > TREND_WINDOW_MINUTES) {
            buckets.pollFirst();
        }
    }

    /**
     * 构造业务指标点。
     *
     * @param key 指标键
     * @param name 指标名称
     * @param value 指标值
     * @param unit 单位
     * @return 指标点
     */
    private MonitorBusinessResponse.MetricPoint metric(String key, String name, double value, String unit) {
        return new MonitorBusinessResponse.MetricPoint(key, name, Double.isNaN(value) ? 0 : value, unit);
    }

    /* ==================== Micrometer 读取工具 ==================== */

    /**
     * 读取 Gauge 值（找不到返回 NaN）。
     *
     * @param name 指标名
     * @param tags 标签键值对
     * @return 指标值
     */
    private double gauge(String name, String... tags) {
        Gauge gauge = meterRegistry.find(name).tags(tags).gauge();
        return gauge == null ? Double.NaN : gauge.value();
    }

    /**
     * 对同名 Gauge 求和（不过滤标签）。
     *
     * @param name 指标名
     * @return 求和结果
     */
    private double sumGauges(String name) {
        return meterRegistry.find(name).gauges().stream()
                .mapToDouble(Gauge::value)
                .sum();
    }

    /**
     * 对同名 Gauge 按标签过滤求和。
     *
     * @param name 指标名
     * @param tagKey 标签键
     * @param tagValue 标签值
     * @return 求和结果
     */
    private double sumGauges(String name, String tagKey, String tagValue) {
        return meterRegistry.find(name).tag(tagKey, tagValue).gauges().stream()
                .mapToDouble(Gauge::value)
                .sum();
    }

    /**
     * 读取 Counter 值（找不到返回 0）。
     *
     * @param name 指标名
     * @return 计数值
     */
    private double counter(String name) {
        return meterRegistry.find(name).counters().stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count)
                .sum();
    }

    /**
     * 统计 API 指标中指定状态码类的错误数（apex.api.request.count status 前缀匹配）。
     *
     * @param statusClass 4 或 5
     * @return 错误数
     */
    private double apiErrorCount(int statusClass) {
        return meterRegistry.find(M_API_REQUEST_COUNT).meters().stream()
                .filter(meter -> meterTags(meter).getOrDefault("status", "").startsWith(String.valueOf(statusClass)))
                .filter(meter -> meter instanceof io.micrometer.core.instrument.Counter)
                .mapToDouble(meter -> ((io.micrometer.core.instrument.Counter) meter).count())
                .sum();
    }

    /**
     * 统计网关指标中指定状态码类的错误数。
     *
     * @param statusClass 4 或 5
     * @return 错误数
     */
    private double gatewayErrorCount(int statusClass) {
        return meterRegistry.find(M_GATEWAY_REQUEST_COUNT).meters().stream()
                .filter(meter ->
                        meterTags(meter).getOrDefault("statusClass", "").startsWith(String.valueOf(statusClass)))
                .filter(meter -> meter instanceof io.micrometer.core.instrument.Counter)
                .mapToDouble(meter -> ((io.micrometer.core.instrument.Counter) meter).count())
                .sum();
    }

    /**
     * 读取 Timer 平均耗时（秒）。
     *
     * @param name 指标名
     * @return 平均耗时（秒），无数据时 0
     */
    private double timerAverage(String name) {
        java.util.Collection<Timer> timers = meterRegistry.find(name).timers();
        if (timers.isEmpty()) {
            return 0;
        }
        return timers.stream()
                .mapToDouble(timer -> timer.mean(TimeUnit.SECONDS))
                .filter(Double::isFinite)
                .average()
                .orElse(0);
    }

    /**
     * 读取 Timer 最大耗时（秒）。
     *
     * @param name 指标名
     * @return 最大耗时（秒），无数据时 0
     */
    private double timerMax(String name) {
        return meterRegistry.find(name).timers().stream()
                .mapToDouble(timer -> timer.max(TimeUnit.SECONDS))
                .filter(Double::isFinite)
                .max()
                .orElse(0);
    }

    /**
     * 读取 Timer 分位数耗时（秒）。
     *
     * <p>依赖 Timer 注册时启用 {@code publishPercentileHistogram()}（直方图分位数），
     * 未启用或样本不足时返回 0。多个 Timer（不同路径标签）取最大分位值。</p>
     *
     * @param name 指标名
     * @param percentile 分位数（0~1），如 0.95 / 0.99
     * @return 分位耗时（秒），无数据时 0
     */
    private double timerPercentile(String name, double percentile) {
        return meterRegistry.find(name).timers().stream()
                .mapToDouble(timer -> timer.percentile(percentile, TimeUnit.SECONDS))
                .filter(Double::isFinite)
                .max()
                .orElse(0);
    }

    /**
     * 读取 Timer 总耗时（毫秒）。
     *
     * @param name 指标名
     * @return 总耗时（毫秒）
     */
    private double timerTotalTime(String name) {
        return meterRegistry.find(name).timers().stream()
                .mapToDouble(timer -> timer.totalTime(TimeUnit.MILLISECONDS))
                .sum();
    }

    /**
     * 读取 Timer 调用次数。
     *
     * @param name 指标名
     * @return 调用次数
     */
    private long timerCount(String name) {
        return meterRegistry.find(name).timers().stream()
                .mapToLong(Timer::count)
                .sum();
    }

    /**
     * 提取 Meter 标签为 Map。
     *
     * @param meter 计量器
     * @return 标签 Map
     */
    private static Map<String, String> meterTags(Meter meter) {
        return meter.getId().getTags().stream()
                .collect(Collectors.toMap(
                        io.micrometer.core.instrument.Tag::getKey,
                        io.micrometer.core.instrument.Tag::getValue,
                        (a, b) -> a));
    }

    /**
     * 从 Meter 提取标签 Map（Timer 专用重载）。
     *
     * @param timer 计时器
     * @return 标签 Map
     */
    private static Map<String, String> timerTags(Timer timer) {
        return timer.getId().getTags().stream()
                .collect(Collectors.toMap(
                        io.micrometer.core.instrument.Tag::getKey,
                        io.micrometer.core.instrument.Tag::getValue,
                        (a, b) -> a));
    }

    /* ==================== 辅助工具 ==================== */

    /**
     * 计算安全比例（除数为 0 或非法时返回 NaN）。
     *
     * @param numerator 分子
     * @param denominator 分母
     * @return 比例 0~1
     */
    private static double safeRatio(double numerator, double denominator) {
        if (Double.isNaN(numerator) || Double.isNaN(denominator) || denominator <= 0) {
            return Double.NaN;
        }
        return Math.min(1, Math.max(0, numerator / denominator));
    }

    /**
     * 格式化 Unix 秒时间戳。
     *
     * @param epochSecond Unix 秒
     * @return 本地时间字符串
     */
    private static String formatTimestamp(double epochSecond) {
        if (Double.isNaN(epochSecond) || epochSecond <= 0) {
            return null;
        }
        return java.time.Instant.ofEpochMilli((long) epochSecond * 1000)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 安全解析整数。
     *
     * @param value 字符串值
     * @return 整数值，解析失败返回 -1
     */
    private static int parseIntSafe(String value) {
        if (value == null) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 限流规则策略描述。
     *
     * @param rule 限流规则
     * @return 策略描述
     */
    private static String flowStrategyText(FlowRule rule) {
        return switch (rule.getStrategy()) {
            case 0 -> "直接";
            case 1 -> "关联";
            case 2 -> "链路";
            default -> String.valueOf(rule.getStrategy());
        };
    }

    /**
     * 限流规则控制行为描述。
     *
     * @param rule 限流规则
     * @return 控制行为描述
     */
    private static String flowControlBehaviorText(FlowRule rule) {
        return switch (rule.getControlBehavior()) {
            case 0 -> "快速失败";
            case 1 -> "Warm Up";
            case 2 -> "匀速排队";
            default -> String.valueOf(rule.getControlBehavior());
        };
    }

    /**
     * 熔断降级规则维度描述。
     *
     * @param rule 降级规则
     * @return 维度描述
     */
    private static String degradeGradeText(DegradeRule rule) {
        return switch (rule.getGrade()) {
            case 0 -> "RT 慢调用";
            case 1 -> "异常比例";
            case 2 -> "异常数";
            default -> String.valueOf(rule.getGrade());
        };
    }
}
