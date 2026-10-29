package com.apex.scaffold.common.config;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
@Getter
@Setter
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
     * <p>平台线程模式（{@code virtualThreads=false}）下，字段语义与 Reactor
     * {@code Schedulers.newBoundedElastic(int, int, String, boolean, long)}
     * 参数一一对应：线程数（parallelism 上限）、队列容量、线程创建公平模式、
     * 任务 TTL（毫秒，-1 表示无限）。</p>
     *
     * <p>虚拟线程模式（{@code virtualThreads=true}）下，{@code threads} 的语义变为
     * "默认最大并发任务数"（可被 {@code maxConcurrency} 覆盖），{@code queueCapacity}
     * 语义不变。虚拟线程不可池化，并发控制改由信号量闸门承担，详见
     * {@link com.apex.scaffold.common.scheduler.VirtualThreadBoundedExecutor}。</p>
     *
     * @author Apex Team
     * @version 1.1.0
     * @since 1.0.0
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class PoolConfig {

        /** 最大工作线程数（平台线程模式）或默认最大并发任务数（虚拟线程模式）。 */
        private int threads;

        /** 队列容量，线程/闸门全忙时缓冲任务；满后提交者线程会阻塞（非 Reject）。 */
        private int queueCapacity;

        /** 是否启用公平模式（按 FIFO 分配线程，吞吐量略低但防饥饿）。 */
        private boolean fair;

        /** 任务最大存活时间（毫秒），-1 表示不强制过期。 */
        private long taskTtlMs;

        /**
         * 是否使用虚拟线程（JDK 21）承接本池任务。
         *
         * <p>默认关闭，保持与既有平台线程池行为一致；仅建议在"等待时间远大于计算时间"
         * 的池（jdbc / storage / remote / general）开启。cache 池任务典型 &lt; 5ms，
         * 虚拟线程带来的创建与卸载开销相对收益不明显，一般无需开启。</p>
         */
        private boolean virtualThreads;

        /**
         * 虚拟线程模式下的最大并发任务数；{@code <= 0} 表示沿用 {@code threads}。
         *
         * <p>该值是真正的容量闸门，须与下游稀缺资源对齐：JDBC 池应与数据库连接池
         * 最大连接数匹配，远程调用池应与下游配额匹配，不能仅按 CPU 核数放大。</p>
         */
        private int maxConcurrency;

        /**
         * 以平台线程模式参数构造（保留既有 4 参签名，虚拟线程相关字段取默认值）。
         *
         * @param threads       最大工作线程数
         * @param queueCapacity 队列容量
         * @param fair          公平模式
         * @param taskTtlMs     任务 TTL 毫秒
         */
        public PoolConfig(int threads, int queueCapacity, boolean fair, long taskTtlMs) {
            this.threads = threads;
            this.queueCapacity = queueCapacity;
            this.fair = fair;
            this.taskTtlMs = taskTtlMs;
        }

        /**
         * 返回虚拟线程模式下实际生效的最大并发任务数。
         *
         * @return {@code maxConcurrency} 为正时取该值，否则回退到 {@code threads}
         */
        public int effectiveMaxConcurrency() {
            return maxConcurrency > 0 ? maxConcurrency : threads;
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
        if (cfg.isVirtualThreads()) {
            // 负数无意义；0 表示"沿用 threads"，是合法缺省
            if (cfg.getMaxConcurrency() < 0) {
                throw new IllegalStateException("apex.blocking-io." + name + " 配置非法：maxConcurrency="
                        + cfg.getMaxConcurrency() + "（启用虚拟线程时必须 >= 0，0 表示沿用 threads）");
            }
            if (cfg.effectiveMaxConcurrency() <= 0) {
                throw new IllegalStateException(
                        "apex.blocking-io." + name + " 配置非法：虚拟线程并发上限必须 > 0（maxConcurrency 或 threads）");
            }
        }
    }
}
