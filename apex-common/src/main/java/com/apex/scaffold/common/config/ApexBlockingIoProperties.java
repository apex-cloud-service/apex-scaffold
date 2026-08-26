package com.apex.scaffold.common.config;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阻塞 I/O 专用调度器参数配置。
 *
 * <p>将 {@link com.apex.scaffold.common.scheduler.BlockingIoSchedulers} 中
 * 六大专用池（JDBC / CACHE / STORAGE / EVENT / REMOTE / GENERAL）的线程数、
 * 队列容量、公平模式、任务 TTL 等参数全部从外部配置注入，替代原先散落各处的
 * {@code @Value} 硬编码。所有参数均支持通过环境变量覆盖，便于生产灰度与弹性伸缩。</p>
 *
 * <h2>专用池语义与选择规则</h2>
 * <ol>
 *   <li>{@code jdbc} —— 所有 MyBatis-Plus Mapper 调用、Spring JDBC、TransactionTemplate：
 *       典型阻塞 I/O，单次任务 ~1ms~200ms，线程上限 = 连接池 × 0.8。</li>
 *   <li>{@code cache} —— Redisson 同步 API、本地缓存锁、Redis 分布式事务：
 *       单次任务 &lt; 1ms ~ 5ms，少量线程足够抗高 QPS。</li>
 *   <li>{@code storage} —— 对象存储 SDK（MinIO/TOS）同步上传/下载、本地文件 IO：
 *       单次任务 ~10ms~2s，允许更大并发但受网络带宽限制。</li>
 *   <li>{@code event} —— Outbox 写入、API 调用日志入库、领域事件发布回调：
 *       允许排队队列较长，低 QPS 但抗峰值风暴。</li>
 *   <li>{@code remote} —— 同步外部 HTTP（SDK 封装的创蓝/火山引擎修图/微信支付等）：
 *       单任务慢（50ms~5s）且波动大，必须独立池隔离，避免拖死 JDBC/CACHE。</li>
 *   <li>{@code general} —— 启动预热 Runner、后台批量处理、补偿调度、一次性运维任务：
 *       不请求核心业务路径，但也不能占用全局共享池。</li>
 * </ol>
 *
 * <h2>禁止项</h2>
 * <p>任何生产代码<strong>不得直接使用 {@code Schedulers.boundedElastic()}</strong>，
 * 必须从上述 6 类中选择最贴近的语义。ArchUnit 分层测试应同步新增该门禁。</p>
 *
 * <h2>选型决策树（快速查找）</h2>
 * <pre>{@code * 是否阻塞式调用（不是 WebFlux/WebClient 异步）？
 * ├─ 否：不用 subscribeOn / publishOn，留在 Netty EventLoop
 * └─ 是：下面哪个场景最贴近？
 *     ├─ MySQL / MyBatis / JDBC / 事务 → jdbc()
 *     ├─ Redis / Redisson / 本地 Caffeine 同步 → cache()
 *     ├─ 对象存储 / 本地文件读写 / IO 流 → storage()
 *     ├─ 写 Outbox / 写 API 日志 / 发领域事件 → event()
 *     ├─ 第三方 SDK（微信/创蓝/火山/短信/人脸）→ remote()
 *     └─ 启动预热 / 后台批量 / 补偿任务 / 运维一次性 → general()
 * }</pre>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "apex.blocking-io")
public class ApexBlockingIoProperties {

    /** JDBC 专用池配置。 */
    private PoolConfig jdbc = new PoolConfig(32, 128, false, -1L);

    /** 缓存/Redis 专用池配置。 */
    private PoolConfig cache = new PoolConfig(4, 512, false, -1L);

    /** 对象存储与文件 I/O 专用池配置。 */
    private PoolConfig storage = new PoolConfig(8, 1024, false, -1L);

    /** 事件/日志/Outbox 专用池配置。 */
    private PoolConfig event = new PoolConfig(2, 2048, false, -1L);

    /** 外部 HTTP/SDK 远程调用专用池配置。 */
    private PoolConfig remote = new PoolConfig(16, 1024, false, -1L);

    /** 通用（启动预热、后台批量）专用池配置。 */
    private PoolConfig general = new PoolConfig(16, 1024, false, -1L);

    /** Scheduler 运行态快照刷新周期（毫秒）；Grafana 面板从此快照读取各池实时状态。 */
    private long snapshotRefreshMs = 3000L;

    /**
     * 单个专用池的参数结构。
     *
     * <p>字段语义与 Reactor {@code Schedulers.newBoundedElastic(int, int, String, boolean, long)}
     * 参数一一对应：线程数（parallelism 上限）、队列容量、线程创建公平模式、
     * 任务 TTL（毫秒，-1 表示无限）。</p>
     *
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    public static class PoolConfig {

        /** 最大工作线程数（boundedElastic 的 parallelism 参数）。 */
        private int threads;

        /** 队列容量，线程全忙时缓冲任务；满后提交者线程会阻塞（非 Reject）。 */
        private int queueCapacity;

        /** 是否启用公平模式（按 FIFO 分配线程，吞吐量略低但防饥饿）。 */
        private boolean fair;

        /** 任务最大存活时间（毫秒），-1 表示不强制过期。 */
        private long taskTtlMs;

        /**
         * 默认构造器（Spring Boot 配置绑定使用）。
         */
        public PoolConfig() {}

        /**
         * 便捷构造器，用于声明默认值与单元测试构造。
         *
         * @param threads       最大工作线程数（必须 &gt; 0）
         * @param queueCapacity 任务队列容量（必须 &gt; 0）
         * @param fair          是否启用 FIFO 公平模式
         * @param taskTtlMs     任务 TTL 毫秒，-1 表示无限制
         */
        public PoolConfig(int threads, int queueCapacity, boolean fair, long taskTtlMs) {
            this.threads = threads;
            this.queueCapacity = queueCapacity;
            this.fair = fair;
            this.taskTtlMs = taskTtlMs;
        }

        /**
         * 返回最大工作线程数。
         *
         * @return 线程数上限（boundedElastic parallelism 参数）
         */
        public int getThreads() {
            return threads;
        }

        /**
         * 设置最大工作线程数。
         *
         * @param threads 线程数上限（必须 &gt; 0）
         */
        public void setThreads(int threads) {
            this.threads = threads;
        }

        /**
         * 返回任务队列容量。
         *
         * @return 队列容量，线程全忙时允许缓冲的任务数量
         */
        public int getQueueCapacity() {
            return queueCapacity;
        }

        /**
         * 设置任务队列容量。
         *
         * @param queueCapacity 队列容量（必须 &gt; 0）
         */
        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        /**
         * 返回是否启用公平模式。
         *
         * @return true 表示 FIFO 分配（防饥饿），false 表示抢占（高吞吐）
         */
        public boolean isFair() {
            return fair;
        }

        /**
         * 设置是否启用公平模式。
         *
         * @param fair true 表示 FIFO，false 表示抢占
         */
        public void setFair(boolean fair) {
            this.fair = fair;
        }

        /**
         * 返回任务最大存活时间。
         *
         * @return TTL 毫秒；-1 表示不强制过期
         */
        public long getTaskTtlMs() {
            return taskTtlMs;
        }

        /**
         * 设置任务最大存活时间。
         *
         * @param taskTtlMs TTL 毫秒；-1 表示不强制过期
         */
        public void setTaskTtlMs(long taskTtlMs) {
            this.taskTtlMs = taskTtlMs;
        }
    }

    /**
     * 构造后自检：保证所有参数合法。
     *
     * <p>生产环境若注入非法值（threads &lt;= 0 或 queueCapacity &lt;= 0），
     * 启动阶段直接抛出异常以避免"静默用错参数"。</p>
     *
     * @throws IllegalStateException threads 或 queueCapacity 非法
     */
    @PostConstruct
    public void validate() {
        // 函数式 Stream 批量校验，避免 6 行 validatePool 重复样板调用
        Stream.of(
                        Map.entry("jdbc", jdbc),
                        Map.entry("cache", cache),
                        Map.entry("storage", storage),
                        Map.entry("event", event),
                        Map.entry("remote", remote),
                        Map.entry("general", general))
                .forEach(e -> validatePool(e.getKey(), e.getValue()));
    }

    /**
     * 校验单个专用池参数。
     *
     * @param name 池名称（用于异常提示）
     * @param cfg 待校验的池参数
     * @throws IllegalStateException 当线程数或队列容量非法时
     */
    private void validatePool(String name, PoolConfig cfg) {
        if (cfg.getThreads() <= 0 || cfg.getQueueCapacity() <= 0) {
            throw new IllegalStateException("apex.blocking-io." + name + " 配置非法：threads=" + cfg.getThreads()
                    + ", queueCapacity=" + cfg.getQueueCapacity()
                    + "（均必须 > 0）");
        }
    }

    /**
     * 返回 JDBC 专用池配置。
     *
     * @return JDBC/MyBatis/事务等阻塞 DB 操作专用池参数
     */
    public PoolConfig getJdbc() {
        return jdbc;
    }

    /**
     * 设置 JDBC 专用池配置。
     *
     * @param jdbc JDBC 专用池参数
     */
    public void setJdbc(PoolConfig jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 返回缓存/Redis 专用池配置。
     *
     * @return Redisson/本地缓存同步阻塞操作专用池参数
     */
    public PoolConfig getCache() {
        return cache;
    }

    /**
     * 设置缓存/Redis 专用池配置。
     *
     * @param cache 缓存/Redis 专用池参数
     */
    public void setCache(PoolConfig cache) {
        this.cache = cache;
    }

    /**
     * 返回对象存储/文件 I/O 专用池配置。
     *
     * @return MinIO/TOS/本地文件读写专用池参数
     */
    public PoolConfig getStorage() {
        return storage;
    }

    /**
     * 设置对象存储/文件 I/O 专用池配置。
     *
     * @param storage 存储/文件专用池参数
     */
    public void setStorage(PoolConfig storage) {
        this.storage = storage;
    }

    /**
     * 返回事件/日志/Outbox 专用池配置。
     *
     * @return Outbox 写入/API 日志入库/领域事件回调专用池参数
     */
    public PoolConfig getEvent() {
        return event;
    }

    /**
     * 设置事件/日志/Outbox 专用池配置。
     *
     * @param event 事件/日志专用池参数
     */
    public void setEvent(PoolConfig event) {
        this.event = event;
    }

    /**
     * 返回外部 HTTP/SDK 远程调用专用池配置。
     *
     * @return 微信/创蓝/火山引擎修图等同步外部 SDK 专用池参数
     */
    public PoolConfig getRemote() {
        return remote;
    }

    /**
     * 设置外部 HTTP/SDK 远程调用专用池配置。
     *
     * @param remote 远程调用专用池参数
     */
    public void setRemote(PoolConfig remote) {
        this.remote = remote;
    }

    /**
     * 返回通用后台处理专用池配置。
     *
     * @return 启动预热/后台批量/补偿/运维专用池参数
     */
    public PoolConfig getGeneral() {
        return general;
    }

    /**
     * 设置通用后台处理专用池配置。
     *
     * @param general 通用专用池参数
     */
    public void setGeneral(PoolConfig general) {
        this.general = general;
    }

    /**
     * 返回 Scheduler 运行态快照刷新周期。
     *
     * @return 刷新周期毫秒；默认 3000
     */
    public long getSnapshotRefreshMs() {
        return snapshotRefreshMs;
    }

    /**
     * 设置 Scheduler 运行态快照刷新周期。
     *
     * @param snapshotRefreshMs 刷新周期毫秒
     */
    public void setSnapshotRefreshMs(long snapshotRefreshMs) {
        this.snapshotRefreshMs = snapshotRefreshMs;
    }
}
