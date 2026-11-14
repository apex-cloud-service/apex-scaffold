package com.apex.scaffold.interfaces.monitor.health;

import com.apex.scaffold.interfaces.dto.monitor.HealthReportResponse;
import com.apex.scaffold.interfaces.dto.monitor.MonitorGovernanceResponse;
import com.apex.scaffold.interfaces.dto.monitor.MonitorMiddlewareResponse;
import com.apex.scaffold.interfaces.dto.monitor.MonitorOverviewResponse;
import com.apex.scaffold.interfaces.dto.monitor.MonitorSystemResponse;
import com.apex.scaffold.interfaces.monitor.dashboard.MonitorMetricsCollector;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * 管理员体检报告组装器。
 *
 * <p>复用 {@link MonitorMetricsCollector} 的五类采集结果，转换为可解释的检查项：
 * 每项都给出状态、范围、采样时间、来源、证据与建议，并按
 * 「应用存活 / 依赖就绪 / 业务风险 / 容量」四组呈现。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>Gauge 缺失（采集器返回 {@code NaN}）判为 {@code UNKNOWN}，不计为健康</li>
 *   <li>组件未接入（如消息中间件）判为 {@code NOT_CONFIGURED}，不参与总体结论</li>
 *   <li>依赖不可用判为 {@code DOWN}；阈值类风险判为 {@code DEGRADED}</li>
 *   <li>总体结论优先级：{@code DOWN > DEGRADED > UNKNOWN > UP}，
 *       仅当全部检查项均为 {@code NOT_CONFIGURED} 时总体为 {@code UNKNOWN}</li>
 * </ul>
 *
 * <p><b>范围约束</b>：数据仅在当前进程内采集，报告明确标注 {@code INSTANCE}
 * 范围，不对外宣称跨实例或跨服务健康。磁盘证据只输出容量与使用率，
 * 不输出主机挂载路径。</p>
 *
 * <p><b>阻塞隔离</b>：磁盘/文件描述符等同步系统调用由调用方在
 * {@code BlockingIoSchedulers.general()} 上执行，本类不做线程切换。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see MonitorMetricsCollector
 */
@Component
@ConditionalOnClass({CircuitBreakerRegistry.class, RocketMQTemplate.class})
@Slf4j
public class HealthReportAssembler {

    /** 判定规则版本，规则调整时递增。 */
    public static final String RULE_VERSION = "health-report-v1";

    /** 采集范围：当前实例。 */
    public static final String SCOPE_INSTANCE = "INSTANCE";

    /** 范围说明文案。 */
    static final String SCOPE_NOTICE = "仅覆盖当前服务实例的进程内采集结果，不代表集群其他实例或其他服务的健康状态";

    /** 进程正常运行时启动时间必然早于采样时间，最多容忍 60 秒的时钟偏差或启动竞态。 */
    private static final long START_TIME_TOLERANCE_SECONDS = 60L;

    private static final String CATEGORY_LIVENESS = "LIVENESS";
    private static final String CATEGORY_DEPENDENCY = "DEPENDENCY";
    private static final String CATEGORY_BUSINESS_RISK = "BUSINESS_RISK";
    private static final String CATEGORY_CAPACITY = "CAPACITY";

    private static final String STATUS_UP = HealthReportResponse.STATUS_UP;
    private static final String STATUS_DEGRADED = HealthReportResponse.STATUS_DEGRADED;
    private static final String STATUS_DOWN = HealthReportResponse.STATUS_DOWN;
    private static final String STATUS_UNKNOWN = HealthReportResponse.STATUS_UNKNOWN;
    private static final String STATUS_NOT_CONFIGURED = HealthReportResponse.STATUS_NOT_CONFIGURED;

    /** 堆内存使用率降级阈值。 */
    private static final double HEAP_DEGRADED_THRESHOLD = 0.85;
    /** 堆内存使用率失败阈值。 */
    private static final double HEAP_DOWN_THRESHOLD = 0.95;
    /** 磁盘使用率降级阈值。 */
    private static final double DISK_DEGRADED_THRESHOLD = 0.85;
    /** 磁盘使用率失败阈值。 */
    private static final double DISK_DOWN_THRESHOLD = 0.95;
    /** 文件描述符使用率降级阈值。 */
    private static final double FILE_DESCRIPTOR_DEGRADED_THRESHOLD = 0.8;
    /** 数据库连接池使用率降级阈值。 */
    private static final double POOL_DEGRADED_THRESHOLD = 0.8;
    /** 阻塞池饱和度降级阈值。 */
    private static final double BLOCKING_POOL_DEGRADED_THRESHOLD = 0.8;
    /** 业务风险：5xx 占比降级阈值。 */
    private static final double ERROR_RATE_DEGRADED_THRESHOLD = 0.05;

    private final MonitorMetricsCollector collector;

    private final String serviceName;

    private final String instanceId;

    /**
     * 创建体检报告组装器。
     *
     * @param collector 监控指标采集器
     */
    public HealthReportAssembler(MonitorMetricsCollector collector) {
        this.collector = collector;
        this.serviceName = ManagementFactory.getRuntimeMXBean().getName();
        this.instanceId = buildInstanceId();
    }

    /**
     * 生成即时体检报告。
     *
     * <p>会依次触发概览、中间件与系统资源采集；其中系统资源包含同步磁盘与
     * 文件描述符读取，调用方必须在阻塞池上执行本方法。</p>
     *
     * @return 体检报告
     */
    public HealthReportResponse report() {
        Instant sampledAt = Instant.now();
        MonitorOverviewResponse overview = collector.overview();
        MonitorMiddlewareResponse middleware = collector.middleware();
        MonitorSystemResponse system = collector.system();
        List<MonitorGovernanceResponse.CircuitBreakerState> circuitBreakers =
                collector.governance().circuitBreakers();

        List<HealthReportResponse.HealthGroup> groups = List.of(
                group(CATEGORY_LIVENESS, "应用存活", livenessItems(overview, sampledAt)),
                group(CATEGORY_DEPENDENCY, "依赖就绪", dependencyItems(middleware, sampledAt)),
                group(CATEGORY_BUSINESS_RISK, "业务风险", businessRiskItems(overview, circuitBreakers, sampledAt)),
                group(CATEGORY_CAPACITY, "容量", capacityItems(overview, system, sampledAt)));

        List<HealthReportResponse.HealthCheckItem> items =
                groups.stream().flatMap(g -> g.items().stream()).toList();
        String overallStatus = overallStatus(items);
        return new HealthReportResponse(
                sampledAt.toString(),
                serviceName,
                instanceId,
                SCOPE_INSTANCE,
                SCOPE_NOTICE,
                RULE_VERSION,
                overallStatus,
                summary(overallStatus, items),
                counts(items),
                groups);
    }

    /* ==================== 分组组装 ==================== */

    private List<HealthReportResponse.HealthCheckItem> livenessItems(
            MonitorOverviewResponse overview, Instant sampledAt) {
        List<HealthReportResponse.HealthCheckItem> items = new ArrayList<>();
        items.add(processAliveItem(overview, sampledAt));
        double liveThreads = overview.threadCount();
        items.add(item(
                "process.threads",
                "线程可用",
                Double.isNaN(liveThreads) || liveThreads <= 0 ? STATUS_UNKNOWN : STATUS_UP,
                sampledAt,
                "Micrometer jvm.threads.live",
                metricEvidence("liveThreads", liveThreads),
                Double.isNaN(liveThreads) || liveThreads <= 0 ? "未采集到存活线程数，检查 Micrometer 绑定" : null));
        return items;
    }

    /**
     * 判定进程存活。
     *
     * <p>能执行到本次采集本身即说明 JVM 存活，因此不以 {@code uptime=0} 或
     * 采集器状态字段判失败；改用启动时间是否早于采样时间识别「指标失联」，
     * 这类情况属于证据不足，判为 {@code UNKNOWN}。</p>
     */
    private HealthReportResponse.HealthCheckItem processAliveItem(MonitorOverviewResponse overview, Instant sampledAt) {
        String source = "Micrometer process.uptime";
        String evidence = "uptime=" + value(overview.uptimeSeconds()) + "s, startTime=" + overview.startTime()
                + ", status=" + overview.status();
        Instant startTime = parseInstant(overview.startTime());
        if (startTime == null) {
            return item(
                    "process.alive",
                    "进程存活",
                    STATUS_UNKNOWN,
                    sampledAt,
                    source,
                    evidence + ", startTimeParsed=false",
                    "未采集到可解析的进程启动时间，检查 Micrometer process.start.time 绑定");
        }
        if (startTime.isAfter(sampledAt.plusSeconds(START_TIME_TOLERANCE_SECONDS))) {
            return item(
                    "process.alive",
                    "进程存活",
                    STATUS_UNKNOWN,
                    sampledAt,
                    source,
                    evidence,
                    "进程启动时间晚于采样时间，指标可能来自过期快照，请复核采集链路");
        }
        return item("process.alive", "进程存活", STATUS_UP, sampledAt, source, evidence, null);
    }

    private List<HealthReportResponse.HealthCheckItem> dependencyItems(
            MonitorMiddlewareResponse middleware, Instant sampledAt) {
        List<HealthReportResponse.HealthCheckItem> items = new ArrayList<>();
        items.add(mysqlItem(middleware.mysql(), sampledAt));
        items.add(redisItem(middleware.redis(), sampledAt));
        items.add(mqItem(middleware.mq(), sampledAt));
        items.add(tracingItem(middleware.tracing(), sampledAt));
        return items;
    }

    private HealthReportResponse.HealthCheckItem mysqlItem(
            MonitorMiddlewareResponse.MysqlStats mysql, Instant sampledAt) {
        String source = "Micrometer druid.*/hikaricp.*";
        String evidence = "driver=" + mysql.driver() + ", active=" + value(mysql.activeConnections()) + ", max="
                + value(mysql.maxConnections()) + ", idle=" + value(mysql.idleConnections()) + ", ratio="
                + ratio(mysql.activeConnectionRatio()) + ", errors=" + value(mysql.totalErrors()) + ", slowSql="
                + value(mysql.slowSqlCount());
        if (!mysql.available()) {
            return item(
                    "dependency.mysql",
                    "数据库连接池",
                    STATUS_UNKNOWN,
                    sampledAt,
                    source,
                    evidence + ", collected=false",
                    "未采集到连接池指标，确认数据源与连接池指标已绑定，或核对 MySQL 连通性");
        }
        double ratio = mysql.activeConnectionRatio();
        if (!Double.isNaN(ratio) && ratio >= 1.0) {
            return item(
                    "dependency.mysql", "数据库连接池", STATUS_DOWN, sampledAt, source, evidence, "连接池已耗尽，排查长事务或连接泄漏并扩容连接数");
        }
        if (!Double.isNaN(ratio) && ratio >= POOL_DEGRADED_THRESHOLD) {
            return item(
                    "dependency.mysql",
                    "数据库连接池",
                    STATUS_DEGRADED,
                    sampledAt,
                    source,
                    evidence,
                    "连接池使用率偏高，关注慢 SQL 与并发峰值");
        }
        if (Double.isNaN(ratio)) {
            return item(
                    "dependency.mysql",
                    "数据库连接池",
                    STATUS_UNKNOWN,
                    sampledAt,
                    source,
                    evidence,
                    "连接池使用率不可计算（缺 active 或 max 指标），无法判断容量风险");
        }
        return item("dependency.mysql", "数据库连接池", STATUS_UP, sampledAt, source, evidence, null);
    }

    private HealthReportResponse.HealthCheckItem redisItem(
            MonitorMiddlewareResponse.RedisStats redis, Instant sampledAt) {
        String source = "Micrometer redisson.* / apex.security.redis.metrics.up";
        String evidence = "healthy=" + redis.healthy() + ", up=" + redis.up() + ", nodes=" + value(redis.nodes())
                + ", activeSessions=" + value(redis.activeSessions());
        if (!redis.available()) {
            return item(
                    "dependency.redis",
                    "缓存",
                    STATUS_UNKNOWN,
                    sampledAt,
                    source,
                    evidence + ", collected=false",
                    "未采集到 Redis 探针指标，确认探针已注册，或核对 Redis 连通性");
        }
        if (redis.up()) {
            return item("dependency.redis", "缓存", STATUS_UP, sampledAt, source, evidence, null);
        }
        return item("dependency.redis", "缓存", STATUS_DOWN, sampledAt, source, evidence, "Redis 派生指标采集失败，检查连接与主从状态");
    }

    private HealthReportResponse.HealthCheckItem mqItem(MonitorMiddlewareResponse.MqStats mq, Instant sampledAt) {
        String source = "RocketMQTemplate Bean 存在性 + rocketmq.name-server 配置";
        if (!mq.available()) {
            return item(
                    "dependency.mq",
                    "消息中间件",
                    STATUS_NOT_CONFIGURED,
                    sampledAt,
                    source,
                    "producerStatus=" + mq.producerStatus(),
                    "如业务需要异步消息，请配置 RocketMQ 并接入生产者");
        }
        String evidence =
                "type=" + mq.type() + ", producerGroup=" + mq.producerGroup() + ", status=" + mq.producerStatus();
        return item("dependency.mq", "消息中间件", STATUS_UP, sampledAt, source, evidence, null);
    }

    private HealthReportResponse.HealthCheckItem tracingItem(
            MonitorMiddlewareResponse.TracingStats tracing, Instant sampledAt) {
        String source = "skywalking.collector-backend / trace.sampling-rate 配置";
        if (!tracing.available()) {
            return item(
                    "dependency.tracing",
                    "链路追踪",
                    STATUS_NOT_CONFIGURED,
                    sampledAt,
                    source,
                    "未配置 OAP 后端地址",
                    "如需链路追踪，请配置 skywalking.collector-backend");
        }
        String evidence = "type=" + tracing.type() + ", samplingRate=" + tracing.samplingRate();
        return item("dependency.tracing", "链路追踪", STATUS_UP, sampledAt, source, evidence, null);
    }

    private List<HealthReportResponse.HealthCheckItem> businessRiskItems(
            MonitorOverviewResponse overview,
            List<MonitorGovernanceResponse.CircuitBreakerState> circuitBreakers,
            Instant sampledAt) {
        List<HealthReportResponse.HealthCheckItem> items = new ArrayList<>();
        items.add(apiErrorRateItem(overview, sampledAt));
        items.add(circuitBreakerItem(circuitBreakers, sampledAt));
        return items;
    }

    private HealthReportResponse.HealthCheckItem apiErrorRateItem(MonitorOverviewResponse overview, Instant sampledAt) {
        String source = "Micrometer apex.api.request.count / api 状态计数";
        double total = overview.totalRequests();
        double error5xx = overview.error5xx();
        String evidence = "totalRequests=" + value(total) + ", error4xx=" + value(overview.error4xx()) + ", error5xx="
                + value(error5xx);
        if (Double.isNaN(total) || total <= 0) {
            return item(
                    "business.api_error_rate",
                    "接口错误率",
                    STATUS_UNKNOWN,
                    sampledAt,
                    source,
                    evidence,
                    "当前实例尚无 API 流量，不能据此判定接口健康");
        }
        double errorRate = Double.isNaN(error5xx) ? Double.NaN : error5xx / total;
        if (Double.isNaN(errorRate)) {
            return item(
                    "business.api_error_rate",
                    "接口错误率",
                    STATUS_UNKNOWN,
                    sampledAt,
                    source,
                    evidence,
                    "缺少 5xx 计数指标，检查 API 观测埋点");
        }
        if (errorRate >= ERROR_RATE_DEGRADED_THRESHOLD) {
            return item(
                    "business.api_error_rate",
                    "接口错误率",
                    STATUS_DEGRADED,
                    sampledAt,
                    source,
                    evidence + ", errorRate=" + ratio(errorRate),
                    "5xx 占比超过阈值，检查近期异常与依赖失败");
        }
        return item(
                "business.api_error_rate",
                "接口错误率",
                STATUS_UP,
                sampledAt,
                source,
                evidence + ", errorRate=" + ratio(errorRate),
                null);
    }

    private HealthReportResponse.HealthCheckItem circuitBreakerItem(
            List<MonitorGovernanceResponse.CircuitBreakerState> circuitBreakers, Instant sampledAt) {
        String source = "Resilience4j CircuitBreakerRegistry";
        if (circuitBreakers.isEmpty()) {
            return item(
                    "business.circuit_breaker",
                    "熔断器",
                    STATUS_NOT_CONFIGURED,
                    sampledAt,
                    source,
                    "未注册熔断器",
                    "如依赖外部服务，建议配置熔断策略");
        }
        List<String> open = circuitBreakers.stream()
                .filter(state -> "OPEN".equals(state.state()) || "FORCED_OPEN".equals(state.state()))
                .map(MonitorGovernanceResponse.CircuitBreakerState::name)
                .toList();
        String evidence = "total=" + circuitBreakers.size() + ", open=" + open;
        if (!open.isEmpty()) {
            return item(
                    "business.circuit_breaker",
                    "熔断器",
                    STATUS_DEGRADED,
                    sampledAt,
                    source,
                    evidence,
                    "存在打开的熔断器，检查下游失败原因与恢复条件");
        }
        return item("business.circuit_breaker", "熔断器", STATUS_UP, sampledAt, source, evidence, null);
    }

    private List<HealthReportResponse.HealthCheckItem> capacityItems(
            MonitorOverviewResponse overview, MonitorSystemResponse system, Instant sampledAt) {
        List<HealthReportResponse.HealthCheckItem> items = new ArrayList<>();
        items.add(heapItem(overview, sampledAt));
        items.add(diskItem(system.disk(), sampledAt));
        items.add(fileDescriptorItem(system.process(), sampledAt));
        items.addAll(blockingPoolItems(system.blockingIo(), sampledAt));
        return items;
    }

    private HealthReportResponse.HealthCheckItem heapItem(MonitorOverviewResponse overview, Instant sampledAt) {
        String source = "Micrometer jvm.memory.used/max (area=heap)";
        double percent = overview.heapUsedPercent();
        String evidence = "heapUsed=" + value(overview.heapUsed()) + ", heapMax=" + value(overview.heapMax())
                + ", usedPercent=" + ratio(percent);
        if (Double.isNaN(percent) || overview.heapMax() <= 0) {
            return item("capacity.heap", "堆内存", STATUS_UNKNOWN, sampledAt, source, evidence, "堆上限不可用，无法评估内存容量");
        }
        if (percent >= HEAP_DOWN_THRESHOLD) {
            return item(
                    "capacity.heap",
                    "堆内存",
                    STATUS_DOWN,
                    sampledAt,
                    source,
                    evidence,
                    "堆内存接近上限，存在 OOM 风险，立即排查内存占用与 GC 行为");
        }
        if (percent >= HEAP_DEGRADED_THRESHOLD) {
            return item("capacity.heap", "堆内存", STATUS_DEGRADED, sampledAt, source, evidence, "堆内存使用率偏高，关注 GC 频率并评估扩容");
        }
        return item("capacity.heap", "堆内存", STATUS_UP, sampledAt, source, evidence, null);
    }

    private HealthReportResponse.HealthCheckItem diskItem(
            List<MonitorSystemResponse.DiskUsage> disks, Instant sampledAt) {
        String source = "FileStore 容量采集（不输出主机挂载路径）";
        if (disks.isEmpty()) {
            return item("capacity.disk", "磁盘容量", STATUS_UNKNOWN, sampledAt, source, "未采集到文件系统容量", "确认运行环境允许读取文件系统容量");
        }
        double worst = disks.stream()
                .map(MonitorSystemResponse.DiskUsage::usedPercent)
                .filter(v -> !Double.isNaN(v))
                .max(Comparator.naturalOrder())
                .orElse(Double.NaN);
        if (Double.isNaN(worst)) {
            return item(
                    "capacity.disk",
                    "磁盘容量",
                    STATUS_UNKNOWN,
                    sampledAt,
                    source,
                    "filesystems=" + disks.size() + ", maxUsedPercent=unknown",
                    "确认文件系统容量采集是否受容器限制");
        }
        String evidence = "filesystems=" + disks.size() + ", maxUsedPercent=" + ratio(worst);
        if (worst >= DISK_DOWN_THRESHOLD) {
            return item("capacity.disk", "磁盘容量", STATUS_DOWN, sampledAt, source, evidence, "磁盘空间接近耗尽，清理日志或扩容存储");
        }
        if (worst >= DISK_DEGRADED_THRESHOLD) {
            return item("capacity.disk", "磁盘容量", STATUS_DEGRADED, sampledAt, source, evidence, "磁盘使用率偏高，规划日志轮转与容量扩容");
        }
        return item("capacity.disk", "磁盘容量", STATUS_UP, sampledAt, source, evidence, null);
    }

    private HealthReportResponse.HealthCheckItem fileDescriptorItem(
            MonitorSystemResponse.ProcessStats process, Instant sampledAt) {
        String source = "Micrometer process.files.open.files/process.files.max.files";
        double open = process.openFiles();
        double max = process.maxFiles();
        if (Double.isNaN(open) || Double.isNaN(max) || max <= 0) {
            return item(
                    "capacity.file_descriptor",
                    "文件描述符",
                    STATUS_UNKNOWN,
                    sampledAt,
                    source,
                    "open=" + value(open) + ", max=" + value(max),
                    "当前平台未暴露文件描述符上限，无法评估");
        }
        double percent = open / max;
        String evidence = "open=" + value(open) + ", max=" + value(max) + ", usedPercent=" + ratio(percent);
        if (percent >= FILE_DESCRIPTOR_DEGRADED_THRESHOLD) {
            return item(
                    "capacity.file_descriptor",
                    "文件描述符",
                    STATUS_DEGRADED,
                    sampledAt,
                    source,
                    evidence,
                    "文件描述符使用率偏高，检查连接与文件句柄泄漏");
        }
        return item("capacity.file_descriptor", "文件描述符", STATUS_UP, sampledAt, source, evidence, null);
    }

    private List<HealthReportResponse.HealthCheckItem> blockingPoolItems(
            Map<String, MonitorSystemResponse.BlockingIoPool> pools, Instant sampledAt) {
        if (pools.isEmpty()) {
            return List.of(item(
                    "capacity.blocking_pool",
                    "阻塞 IO 线程池",
                    STATUS_UNKNOWN,
                    sampledAt,
                    "Micrometer apex.blocking.io.*",
                    "未采集到阻塞池指标",
                    "确认 BlockingIoSchedulers 指标已注册"));
        }
        return pools.entrySet().stream()
                .map(entry -> blockingPoolItem(entry.getKey(), entry.getValue(), sampledAt))
                .toList();
    }

    private HealthReportResponse.HealthCheckItem blockingPoolItem(
            String pool, MonitorSystemResponse.BlockingIoPool stats, Instant sampledAt) {
        String source = "Micrometer apex.blocking.io.* (pool=" + pool + ")";
        String evidence = "maxThreads=" + stats.maxThreads() + ", active=" + stats.activeTasks() + ", queued="
                + stats.queuedTasks() + ", saturation=" + ratio(stats.saturation()) + ", up=" + stats.up();
        if (!stats.up() && stats.maxThreads() <= 0) {
            return item(
                    "capacity.blocking_pool." + pool,
                    "阻塞 IO 线程池-" + pool,
                    STATUS_UNKNOWN,
                    sampledAt,
                    source,
                    evidence,
                    "未采集到该池的线程与队列指标");
        }
        if (stats.saturation() >= BLOCKING_POOL_DEGRADED_THRESHOLD) {
            return item(
                    "capacity.blocking_pool." + pool,
                    "阻塞 IO 线程池-" + pool,
                    STATUS_DEGRADED,
                    sampledAt,
                    source,
                    evidence,
                    "阻塞池饱和度偏高，检查阻塞任务耗时与线程上限");
        }
        if (stats.saturation() < 0) {
            return item(
                    "capacity.blocking_pool." + pool,
                    "阻塞 IO 线程池-" + pool,
                    STATUS_UNKNOWN,
                    sampledAt,
                    source,
                    evidence,
                    "饱和度不可计算，无法评估该池的排队风险");
        }
        return item(
                "capacity.blocking_pool." + pool, "阻塞 IO 线程池-" + pool, STATUS_UP, sampledAt, source, evidence, null);
    }

    /* ==================== 结论计算 ==================== */

    private static HealthReportResponse.HealthGroup group(
            String category, String title, List<HealthReportResponse.HealthCheckItem> items) {
        String status = items.stream()
                .map(HealthReportResponse.HealthCheckItem::status)
                .max(Comparator.comparingInt(HealthReportAssembler::severity))
                .orElse(STATUS_UNKNOWN);
        return new HealthReportResponse.HealthGroup(category, title, status, List.copyOf(items));
    }

    private static String overallStatus(List<HealthReportResponse.HealthCheckItem> items) {
        String worst = items.stream()
                .map(HealthReportResponse.HealthCheckItem::status)
                .max(Comparator.comparingInt(HealthReportAssembler::severity))
                .orElse(STATUS_UNKNOWN);
        // NOT_CONFIGURED 不参与总体结论：全部组件未配置等价于无可用证据
        return STATUS_NOT_CONFIGURED.equals(worst) ? STATUS_UNKNOWN : worst;
    }

    private static int severity(String status) {
        return switch (status) {
            case STATUS_DOWN -> 5;
            case STATUS_DEGRADED -> 4;
            case STATUS_UNKNOWN -> 3;
            case STATUS_UP -> 2;
            default -> 1;
        };
    }

    private static HealthReportResponse.StatusCounts counts(List<HealthReportResponse.HealthCheckItem> items) {
        Map<String, Long> byStatus = items.stream()
                .collect(Collectors.groupingBy(HealthReportResponse.HealthCheckItem::status, Collectors.counting()));
        return new HealthReportResponse.StatusCounts(
                byStatus.getOrDefault(STATUS_UP, 0L).intValue(),
                byStatus.getOrDefault(STATUS_DEGRADED, 0L).intValue(),
                byStatus.getOrDefault(STATUS_DOWN, 0L).intValue(),
                byStatus.getOrDefault(STATUS_UNKNOWN, 0L).intValue(),
                byStatus.getOrDefault(STATUS_NOT_CONFIGURED, 0L).intValue());
    }

    private static String summary(String overallStatus, List<HealthReportResponse.HealthCheckItem> items) {
        List<String> abnormal = items.stream()
                .filter(i -> severity(i.status()) >= severity(STATUS_UNKNOWN))
                .map(i -> i.name() + "=" + i.status())
                .toList();
        String base =
                switch (overallStatus) {
                    case STATUS_DOWN -> "存在失败项，当前实例关键能力不可用";
                    case STATUS_DEGRADED -> "存在降级项，当前实例可服务但存在风险";
                    case STATUS_UNKNOWN -> "证据不足，存在未知项，不能判定为健康";
                    default -> "当前实例检查项均正常";
                };
        return abnormal.isEmpty() ? base : base + "；" + String.join(", ", abnormal);
    }

    /* ==================== 工具方法 ==================== */

    private HealthReportResponse.HealthCheckItem item(
            String id,
            String name,
            String status,
            Instant sampledAt,
            String source,
            String evidence,
            String recommendation) {
        return new HealthReportResponse.HealthCheckItem(
                id,
                name,
                status,
                SCOPE_INSTANCE,
                serviceName,
                instanceId,
                sampledAt.toString(),
                source,
                evidence,
                recommendation,
                RULE_VERSION);
    }

    private static String metricEvidence(String name, double raw) {
        return name + "=" + value(raw);
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String value(double raw) {
        return Double.isNaN(raw) || Double.isInfinite(raw) ? "unknown" : String.format("%.2f", raw);
    }

    private static String ratio(double raw) {
        return Double.isNaN(raw) || Double.isInfinite(raw) ? "unknown" : String.format("%.2f%%", raw * 100);
    }

    private static String buildInstanceId() {
        String pid = ManagementFactory.getRuntimeMXBean().getName();
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            log.debug("获取主机名失败，使用占位主机名: {}", e.getMessage());
            host = "unknown-host";
        }
        return pid + "@" + host;
    }
}
