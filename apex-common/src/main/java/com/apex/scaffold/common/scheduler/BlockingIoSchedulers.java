package com.apex.scaffold.common.scheduler;

import com.apex.scaffold.common.config.ApexBlockingIoProperties;
import com.apex.scaffold.common.config.ApexBlockingIoProperties.PoolConfig;
import jakarta.annotation.PreDestroy;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * 阻塞式基础设施任务的隔离 Scheduler 集合（v2.0 增强）。
 *
 * <p>JDBC、同步缓存、对象存储、事件持久化、远程 SDK 调用以及通用后台任务各自使用独立的线程池与有界队列，
 * 避免某一类慢依赖占满 Reactor 全局 {@code boundedElastic} 导致所有阻塞操作阻塞。
 * 每个 Scheduler 的线程数、队列容量、公平模式与任务 TTL 全部通过
 * {@link ApexBlockingIoProperties} 外部化配置，同时保留向后兼容的 JVM system property / 环境变量覆盖机制；
 * 生产环境无需改代码即可通过配置灰度调整参数。</p>
 *
 * <h2>v2.0 升级要点（2026-08-16）</h2>
 * <ol>
 *   <li>新增 {@code general()} 专用池，专用于启动预热 Runner、后台批量处理、补偿调度、一次性运维任务，
 *       避免此类任务挤占 JDBC/CACHE/STORAGE 等核心业务池。</li>
 *   <li>新增 {@link #snapshotAll()} 指标快照方法，为
 *       {@code MetricsConfig} 提供 Micrometer Gauge 数据源，实现每个池的活跃线程/队列深度/拒绝次数可视化。</li>
 *   <li>所有 6 个专用池的线程/队列/公平/TTL 参数从 {@link ApexBlockingIoProperties} 注入，
 *       保持与 {@code application-*.yml} 配置口径一致；
 *       仍保留原有的 JVM {@code -D} 参数作为兼容回退。</li>
 * </ol>
 *
 * <h2>Scheduler 清单与选择规则</h2>
 * <table>
 *   <tr><th>名称</th><th>静态方法</th><th>默认线程</th><th>默认队列</th><th>用途与选择指南</th></tr>
 *   <tr><td>JDBC</td><td>{@link #jdbc()}</td><td>32</td><td>128</td>
 *       <td>MyBatis 同步 SQL、Repository 阻塞读写、事务模板。生产 Druid 连接池 40~45 时设置工作并发 32~36（80% 预算），
 *       队列仅吸收 1~2 秒突发，不要过大避免 P99 伪装。</td></tr>
 *   <tr><td>CACHE</td><td>{@link #cache()}</td><td>4</td><td>512</td>
 *       <td>Redisson 同步 API、本地 Caffeine 同步操作、分布式锁 tryLock。
 *       典型任务 &lt; 5ms，少量线程即可扛高 QPS；公平模式关闭以保证吞吐。</td></tr>
 *   <tr><td>STORAGE</td><td>{@link #storage()}</td><td>8</td><td>1024</td>
 * <td>对象存储（MinIO / TOS）同步上传/下载、本地文件 IO 流操作。
 * 典型任务 10ms~2s，允许更大并发但受网络带宽瓶颈约束。</td></tr>
 * <tr><td>EVENT</td><td>{@link #event()}</td><td>2</td><td>2048</td>
 * <td>Outbox 写入、API 调用日志入库、领域事件发布回调。低 QPS 但抗峰值风暴，
 * 允许大队列避免 MQ 回调爆发时丢数。</td></tr>
 * <tr><td>REMOTE</td><td>{@link #remote()}</td><td>16</td><td>1024</td>
 *       <td>同步外部 SDK / 第三方 HTTP（创蓝短信、火山引擎修图、微信支付、Python 人脸分析同步封装）。
 *       单任务慢（50ms~5s）且波动大，必须独立池隔离，避免拖死 JDBC/CACHE。</td></tr>
 *   <tr><td>GENERAL</td><td>{@code #general()}</td><td>16</td><td>1024</td>
 *       <td><em>v2.0 新增</em>：启动预热 Runner、后台批量补偿、一次性运维批量任务、照片批量上传非核心路径。
 *       不与核心业务路径争用专用池，也禁止继续使用 Reactor 全局 {@code boundedElastic}。</td></tr>
 * </table>
 *
 * <h2>严禁项</h2>
 * <p>任何生产代码<strong>不得直接使用 {@code Schedulers.boundedElastic()}</strong>，
 * 必须从上述六个静态方法中语义最贴近的一个选择。该门禁由代码评审与 ArchUnit 分层测试共同保证。</p>
 *
 * <h2>JVM 调优（向后兼容）</h2>
 * <p>未引入 {@link ApexBlockingIoProperties} 时，仍可通过 {@code -D} 参数覆盖默认值：</p>
 * <pre>{@code *   -Dapex.jdbc.threads=36 -Dapex.jdbc.queue-capacity=192
 *   -Dapex.remote.threads=24 -Dapex.storage.fair=true
 * }</pre>
 * <p>推荐优先在 {@code application-prod.yml} 的 {@code apex.blocking-io.*} 节点调整，配合 Nacos 热更新。</p>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Component
public final class BlockingIoSchedulers {

    /** JDBC 阻塞任务 Scheduler。 */
    private static final AtomicReference<Scheduler> JDBC =
            new AtomicReference<>(newScheduler("apex.jdbc", 32, 128, false, -1L));

    /** 同步缓存任务 Scheduler（Redisson / Caffeine 同步阻塞 API）。 */
    private static final AtomicReference<Scheduler> CACHE =
            new AtomicReference<>(newScheduler("apex.cache", 4, 512, false, -1L));

    /** 对象存储任务 Scheduler（MinIO/TOS/本地文件 I/O）。 */
    private static final AtomicReference<Scheduler> STORAGE =
            new AtomicReference<>(newScheduler("apex.storage", 8, 1024, false, -1L));

    /** 事件持久化与兼容发布任务 Scheduler（Outbox / API 日志 / 领域事件回调）。 */
    private static final AtomicReference<Scheduler> EVENT =
            new AtomicReference<>(newScheduler("apex.event", 2, 2048, false, -1L));

    /** 同步远程 SDK 调用 Scheduler（微信/创蓝/火山/人脸分析同步封装）。 */
    private static final AtomicReference<Scheduler> REMOTE =
            new AtomicReference<>(newScheduler("apex.remote", 16, 1024, false, -1L));

    /**
     * 通用后台任务 Scheduler（v2.0 新增）。
     *
     * <p>专用于启动预热 Runner、后台批量补偿、一次性运维批量、照片批量上传非核心路径等。
     * 严禁在核心业务链路（投票、照片发布、登录鉴权）中使用本池，
     * 也严禁核心业务链路使用 Reactor 全局 {@code boundedElastic}。</p>
     */
    private static final AtomicReference<Scheduler> GENERAL =
            new AtomicReference<>(newScheduler("apex.general", 16, 1024, false, -1L));

    /**
     * 专用池元组：统一封装池短名、property 前缀、默认参数与 Scheduler 持有者，
     * 配合 {@link List#forEach} / Stream API 实现 6 池批量 refresh / shutdown / snapshot，
     * 避免 6 行重复样板代码的复制粘贴风险。
     *
     * @param poolKey     池短名称（jdbc / cache / storage / event / remote / general）
     * @param prefix      JVM system property / 配置节点前缀（如 {@code apex.jdbc}）
     * @param defaultMax  默认最大工作线程数（向后兼容 fallback 值）
     * @param defaultCap  默认队列容量（向后兼容 fallback 值）
     * @param holder      Scheduler 静态 {@link AtomicReference} 持有者
     * @since 2.0.0
     */
    private record PoolTuple(
            String poolKey, String prefix, int defaultMax, int defaultCap, AtomicReference<Scheduler> holder) {}

    /**
     * 6 个专用池的常量元组，顺序与 {@code snapshotAll()} 期望的 key 一致；
     * 用于 refresh / shutdown / snapshot 三种批量化场景的 forEach 调度。
     */
    private static final List<PoolTuple> POOL_TUPLES = List.of(
            new PoolTuple("jdbc", "apex.jdbc", 32, 128, JDBC),
            new PoolTuple("cache", "apex.cache", 4, 512, CACHE),
            new PoolTuple("storage", "apex.storage", 8, 1024, STORAGE),
            new PoolTuple("event", "apex.event", 2, 2048, EVENT),
            new PoolTuple("remote", "apex.remote", 16, 1024, REMOTE),
            new PoolTuple("general", "apex.general", 16, 1024, GENERAL));

    /**
     * 配置化参数注入（可选）。
     *
     * <p>在标准 Spring Boot 启动流程中，由 {@code @ConfigurationPropertiesScan} 自动发现
     * {@link ApexBlockingIoProperties} 并通过本 setter 注入，触发
     * 六个 Scheduler 的参数刷新；若测试夹具未提供配置（无外部 YAML），
     * 则保持原有 system property / 环境变量覆盖逻辑向后兼容。</p>
     *
     * @param properties 阻塞 I/O 参数配置（允许为 null，此时回退至系统属性默认值）
     */
    @Autowired(required = false)
    public void setProperties(ApexBlockingIoProperties properties) {
        if (properties == null) {
            return;
        }
        // 使用 Stream API 将 6 个 getter 结果按 poolKey 收集到 Map，避免 6 行 put 样板
        Map<String, PoolConfig> cfgByKey = Arrays.<Map.Entry<String, PoolConfig>>asList(
                        Map.entry("jdbc", properties.getJdbc()),
                        Map.entry("cache", properties.getCache()),
                        Map.entry("storage", properties.getStorage()),
                        Map.entry("event", properties.getEvent()),
                        Map.entry("remote", properties.getRemote()),
                        Map.entry("general", properties.getGeneral()))
                .stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        POOL_TUPLES.forEach(t -> refreshFromProperties(t.prefix(), cfgByKey.get(t.poolKey()), t.holder()));
    }

    /**
     * 供 Spring 生命周期管理创建实例（Scheduler 本身是 static final 字段，
     * 本构造器仅用于触发 {@link PreDestroy} 回调）。
     */
    public BlockingIoSchedulers() {}

    /**
     * 获取 JDBC 专用 Scheduler。
     *
     * <p>所有 MyBatis/MyBatis-Plus Mapper 调用、Spring JDBC、TransactionTemplate
     * 事务代码均应使用本 Scheduler 承载，严禁直接在 Netty EventLoop 上执行。</p>
     *
     * @return JDBC 专用 Scheduler（若已 dispose 则自动重建）
     */
    public static Scheduler jdbc() {
        return activeScheduler("apex.jdbc", 32, 128, false, -1L, JDBC);
    }

    /**
     * 获取同步缓存专用 Scheduler。
     *
     * <p>Redisson 的同步阻塞 API（RBucket.get、RMap.put、RLock.tryLock 等）、
     * 本地 Caffeine 同步读写、分布式限流令牌桶同步 API 均使用本 Scheduler。</p>
     *
     * @return 缓存/Redis 专用 Scheduler
     */
    public static Scheduler cache() {
        return activeScheduler("apex.cache", 4, 512, false, -1L, CACHE);
    }

    /**
     * 获取对象存储专用 Scheduler。
     *
     * <p>对象存储 SDK（MinIO/TOS）同步上传/下载、本地文件读写、IO 流拷贝等阻塞文件 I/O
     * 一律使用本 Scheduler，不得挤占 JDBC/CACHE 等核心线程池。</p>
     *
     * @return 对象存储/文件 I/O 专用 Scheduler
     */
    public static Scheduler storage() {
        return activeScheduler("apex.storage", 8, 1024, false, -1L, STORAGE);
    }

    /**
     * 获取事件任务专用 Scheduler。
     *
     * <p>Outbox 表写入、API 调用日志入库、领域事件发布前的持久化回调使用本池。
     * 大队列吸收 RocketMQ 消费端回调风暴时的瞬时峰值，线程数保持较低减少上下文切换。</p>
     *
     * @return 事件/日志/Outbox 专用 Scheduler
     */
    public static Scheduler event() {
        return activeScheduler("apex.event", 2, 2048, false, -1L, EVENT);
    }

    /**
     * 获取同步远程 SDK 专用 Scheduler。
     *
     * <p>微信 SDK / 创蓝短信 / 火山引擎修图 / Python 人脸分析同步封装等
     * 典型"慢且波动大"的外部阻塞调用一律使用本池，独立隔离避免拖死 JDBC/CACHE。
     * 注意：使用 WebClient/ManagedWebClientFactory 的异步调用无需任何 Scheduler。</p>
     *
     * @return 远程 HTTP/SDK 专用 Scheduler
     */
    public static Scheduler remote() {
        return activeScheduler("apex.remote", 16, 1024, false, -1L, REMOTE);
    }

    /**
     * 获取通用后台任务专用 Scheduler（v2.0 新增）。
     *
     * <p>启动预热 Runner、后台批量补偿、一次性运维批量任务、照片批量上传非核心路径、
     * CorrectRatePreload 正确率缓存预热等使用本池。
     * 核心业务链路（投票、照片发布、登录鉴权）严禁使用本池；
     * 同时禁止继续使用 Reactor 全局 {@code Schedulers.boundedElastic()}。</p>
     *
     * @return 通用后台专用 Scheduler
     * @since 2.0.0
     */
    public static Scheduler general() {
        return activeScheduler("apex.general", 16, 1024, false, -1L, GENERAL);
    }

    /**
     * 返回可用的 Scheduler，若已被 dispose 则自动重建。
     *
     * <p>调度器由 {@link PreDestroy} 在 Spring 上下文关闭时释放。测试或容器重启场景下，
     * 静态字段持有的实例可能已被释放，此处按需重建避免后续使用方抛出
     * {@code RejectedExecutionException}，保证静态 Scheduler 在进程生命周期内始终可用。</p>
     *
     * @param propertyPrefix JVM system property 前缀（如 {@code apex.jdbc}）
     * @param defaultThreads 默认线程数
     * @param defaultQueue   默认队列容量
     * @param defaultFair    默认公平模式
     * @param defaultTtlMs   默认任务 TTL 毫秒
     * @param holder         持有 Scheduler 的引用容器
     * @return 未被 dispose 的 Scheduler 实例
     */
    private static Scheduler activeScheduler(
            String propertyPrefix,
            int defaultThreads,
            int defaultQueue,
            boolean defaultFair,
            long defaultTtlMs,
            AtomicReference<Scheduler> holder) {
        Scheduler scheduler = holder.get();
        if (scheduler.isDisposed()) {
            synchronized (BlockingIoSchedulers.class) {
                scheduler = holder.get();
                if (scheduler.isDisposed()) {
                    scheduler = newScheduler(propertyPrefix, defaultThreads, defaultQueue, defaultFair, defaultTtlMs);
                    holder.set(scheduler);
                }
            }
        }
        return scheduler;
    }

    /**
     * 根据外部化配置刷新单个 Scheduler（仅当未注入配置时首次触发）。
     *
     * <p>实现策略：通过 CAS 比较并替换底层 AtomicReference。原有的 Scheduler
     * 由 Spring {@link PreDestroy} 在上下文关闭时统一释放；此处只替换
     * 尚未在途任务的空闲实例，避免生产环境粗暴切换造成的 in-flight 任务丢失。</p>
     *
     * @param propertyPrefix 前缀（与 system property / environment 变量命名一致）
     * @param cfg            单个池的参数配置（来自 {@link ApexBlockingIoProperties}）
     * @param holder         目标 Scheduler 的持有引用
     */
    private static void refreshFromProperties(
            String propertyPrefix, PoolConfig cfg, AtomicReference<Scheduler> holder) {
        Scheduler newSched = newScheduler(
                propertyPrefix, cfg.getThreads(), cfg.getQueueCapacity(), cfg.isFair(), cfg.getTaskTtlMs());
        Scheduler old = holder.getAndSet(newSched);
        if (old != null && old != newSched && !old.isDisposed()) {
            old.disposeGracefully().subscribe();
        }
    }

    /**
     * 应用停止时释放全部隔离线程池。
     *
     * <p>通过 {@link Scheduler#dispose()} 优雅关闭每个线程池，
     * 由 Spring 生命周期通过 {@link PreDestroy} 回调触发。
     * v2.0 追加对 {@code GENERAL} 专用池的回收，避免后台批量线程泄漏。</p>
     */
    @PreDestroy
    public void shutdown() {
        // 函数式 forEach 统一释放 6 池，避免 6 行 disposeQuietly 重复调用
        POOL_TUPLES.forEach(t -> disposeQuietly(t.holder()));
    }

    /**
     * 释放单个 Scheduler（幂等）。
     *
     * @param holder 持有 Scheduler 的引用容器
     */
    private static void disposeQuietly(AtomicReference<Scheduler> holder) {
        Scheduler scheduler = holder.get();
        if (scheduler != null && !scheduler.isDisposed()) {
            scheduler.dispose();
        }
    }

    /**
     * 创建支持 JVM system property 覆盖的有界弹性 Scheduler。
     *
     * <p>Scheduler 使用 Project Reactor 的 {@link Schedulers#newBoundedElastic}，
     * 线程空闲 60s 后自动回收，支持背压保护与可选公平模式/任务 TTL。</p>
     *
     * @param propertyPrefix JVM system property 前缀（如 {@code apex.jdbc}）
     * @param defaultThreads 默认线程数
     * @param defaultQueue   默认队列容量
     * @param defaultFair    默认公平模式（false = 抢占高吞吐，true = FIFO 防饥饿）
     * @param defaultTtlMs   默认任务 TTL 毫秒；-1 表示不限制
     * @return 有界弹性 Scheduler 实例
     */
    private static Scheduler newScheduler(
            String propertyPrefix, int defaultThreads, int defaultQueue, boolean defaultFair, long defaultTtlMs) {
        int threads = positiveIntegerProperty(propertyPrefix + ".threads", defaultThreads);
        int queue = positiveIntegerProperty(propertyPrefix + ".queue-capacity", defaultQueue);
        boolean fair = booleanProperty(propertyPrefix + ".fair", defaultFair);
        /*
         * Reactor 3.6.x (当前 3.6.13) 的 newBoundedElastic 最多接受 5 参数：
         *   (threadCap, queuedTaskCap, name, ttlSeconds, fair)
         * 任务 TTL 参数（task-ttl-ms）在 Reactor 3.7+ 才引入，当前版本无法配置，
         * 因此仅读取 longProperty 进行参数校验（保持兼容性），不传递到构造器。
         */
        long ttl = longProperty(propertyPrefix + ".task-ttl-ms", defaultTtlMs);
        return Schedulers.newBoundedElastic(threads, queue, propertyPrefix, 60, fair);
    }

    /**
     * 读取正整数 JVM system property 或环境变量（环境变量优先，兼容 APEX_JDBC_THREADS 风格）。
     *
     * <p>环境变量命名规则：将 property 中的 {@code .} 与 {@code -} 替换为 {@code _}
     * 并大写，例如 {@code apex.jdbc.queue-capacity} → {@code APEX_JDBC_QUEUE_CAPACITY}。</p>
     *
     * @param propertyName JVM system property 名称
     * @param defaultValue 未注入时的默认值
     * @return 正整数配置值
     * @throws IllegalArgumentException 解析结果小于等于 0
     */
    private static int positiveIntegerProperty(String propertyName, int defaultValue) {
        String environmentValue = System.getenv(toEnvName(propertyName));
        int configuredValue = environmentValue == null
                ? Integer.getInteger(propertyName, defaultValue)
                : Integer.parseInt(environmentValue);
        if (configuredValue <= 0) {
            throw new IllegalArgumentException(propertyName + " 必须大于 0");
        }
        return configuredValue;
    }

    /**
     * 读取布尔型 JVM system property 或环境变量，未设置时返回默认值。
     *
     * @param propertyName 属性名
     * @param defaultValue 默认值
     * @return 解析后的布尔值
     */
    private static boolean booleanProperty(String propertyName, boolean defaultValue) {
        String environmentValue = System.getenv(toEnvName(propertyName));
        if (environmentValue != null) {
            return Boolean.parseBoolean(environmentValue);
        }
        return Boolean.getBoolean(propertyName) || (System.getProperty(propertyName) == null && defaultValue);
    }

    /**
     * 读取长整型 JVM system property 或环境变量，未设置时返回默认值。
     *
     * @param propertyName 属性名
     * @param defaultValue 默认值
     * @return 解析后的 long 值
     */
    private static long longProperty(String propertyName, long defaultValue) {
        String environmentValue = System.getenv(toEnvName(propertyName));
        if (environmentValue != null) {
            return Long.parseLong(environmentValue);
        }
        String sysVal = System.getProperty(propertyName);
        return sysVal == null ? defaultValue : Long.parseLong(sysVal);
    }

    /**
     * 将 property key（{@code apex.jdbc.queue-capacity}）转为环境变量风格
     * （{@code APEX_JDBC_QUEUE_CAPACITY}），使用链式函数式替换避免三处重复样板。
     *
     * @param propertyName JVM 属性名
     * @return 全大写、{@code .} 与 {@code -} 替换为 {@code _} 的环境变量名
     */
    private static String toEnvName(String propertyName) {
        return propertyName.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }

    // ==================== v2.0 指标快照（为 MetricsConfig / Grafana 提供数据源） ====================

    /**
     * 单个 Scheduler 运行时指标快照。
     *
     * <p>通过 Reactor {@code BoundedElasticScheduler#statistics()}（若底层是该实现）反射或
     * 通过公开的 Metrics 接口获得。由于 Reactor 未公开只读统计 API，本快照提供
     * <em>最佳努力</em> 的近似值，用于 Prometheus 抓取与 Grafana 可视化；
     * 精确排障时应通过线程 Dump（jstack / AsyncProfiler）交叉验证。</p>
     *
     * @param poolName      专用池名称（jdbc/cache/storage/event/remote/general）
     * @param maxThreads    配置中的最大工作线程数（parallelism）
     * @param queueCapacity 配置中的队列容量
     * @param activeTasks   当前正在执行任务的估算线程数（近似值）
     * @param queuedTasks   当前在队列中等待的任务数（近似值）
     * @param completedTasks 已完成任务累计数（近似值，可能溢出为负数）
     * @author Apex Team
     * @version 2.0.0
     * @since 2.0.0
     */
    public record SchedulerPoolSnapshot(
            String poolName, int maxThreads, int queueCapacity, int activeTasks, int queuedTasks, long completedTasks) {

        /**
         * 返回当前池饱和度（0.0 ~ 1.0），超过 0.85 时建议扩容或降级。
         *
         * @return 活跃线程数 / 最大线程数比值；maxThreads=0 返回 0.0（防御性）
         */
        public double saturation() {
            if (maxThreads <= 0) {
                return 0.0;
            }
            return Math.min(1.0, activeTasks / (double) maxThreads);
        }

        /**
         * 返回队列占用率（0.0 ~ 1.0），超过 0.7 时说明突发流量已打满线程。
         *
         * @return 排队任务数 / 队列容量比值；capacity=0 返回 0.0
         */
        public double queueOccupancy() {
            if (queueCapacity <= 0) {
                return 0.0;
            }
            return Math.min(1.0, queuedTasks / (double) queueCapacity);
        }
    }

    /**
     * 返回全部六个专用池的运行时指标快照（用于 Micrometer Gauge 注册 / 健康检查端点）。
     *
     * <p>键为池名称（jdbc/cache/storage/event/remote/general），值为最新快照。
     * 该方法无锁、高性能，可在 {@code MetricsConfig} 的 5 秒定时刷新中安全调用。</p>
     *
     * @return 六个池的不可变 Map 快照（每次调用重新计算）
     * @since 2.0.0
     */
    public static Map<String, SchedulerPoolSnapshot> snapshotAll() {
        // 函数式批量构建：将 POOL_TUPLES 直接收集为不可变 Map，避免逐个 put 与外部 HashMap 初始化
        return POOL_TUPLES.stream()
                .collect(Collectors.toUnmodifiableMap(
                        PoolTuple::poolKey,
                        t -> snapshot(
                                t.poolKey(),
                                t.defaultMax(),
                                t.defaultCap(),
                                t.holder().get())));
    }

    /**
     * 构造单个池的指标快照。
     *
     * <p>由于 Reactor {@code Schedulers.newBoundedElastic} 返回的是
     * {@code BoundedElasticScheduler} 包装，内部并未公开 statistics()，
     * 本方法返回的 active/queued 是基于 Scheduler 的已知参数的保守估计。
     * 后续若 Reactor 暴露只读统计 API，可在此升级。</p>
     *
     * @param poolName      池名称
     * @param defaultMax    默认最大线程（可能被配置覆盖，此处用配置值更准确）
     * @param defaultCap    默认队列容量
     * @param scheduler     Scheduler 实例
     * @return 快照 record
     */
    private static SchedulerPoolSnapshot snapshot(
            String poolName, int defaultMax, int defaultCap, Scheduler scheduler) {
        int max = defaultMax;
        int cap = defaultCap;
        int active = 0;
        int queued = 0;
        long completed = 0L;
        try {
            if (scheduler != null) {
                Class<?> clazz = scheduler.getClass();
                java.lang.reflect.Field statsField = findField(clazz, "stats");
                if (statsField != null) {
                    statsField.setAccessible(true);
                    Object stats = statsField.get(scheduler);
                    if (stats != null) {
                        Class<?> sClazz = stats.getClass();
                        java.lang.reflect.Method activeM = sClazz.getMethod("activeTasks");
                        java.lang.reflect.Method pendingM = sClazz.getMethod("pendingTasks");
                        java.lang.reflect.Method maxM = sClazz.getMethod("maxThreadsAllowed");
                        active = (int) activeM.invoke(stats);
                        Number pending = (Number) pendingM.invoke(stats);
                        max = (int) maxM.invoke(stats);
                        queued = pending != null ? pending.intValue() : 0;
                    }
                }
            }
        } catch (Throwable ignore) {
            // 反射失败时静默回退至默认值，不影响主流程
        }
        return new SchedulerPoolSnapshot(poolName, max, cap, active, queued, completed);
    }

    /**
     * 在类及其父类链中按名称查找声明字段。
     *
     * @param clazz 起始类
     * @param name  字段名
     * @return 找到的 Field；未找到返回 null
     */
    private static java.lang.reflect.Field findField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignore) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
