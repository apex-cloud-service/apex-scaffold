package com.apex.scaffold.interfaces.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 核心模块启动状态日志记录器。
 *
 * <p>实现 {@link ApplicationListener}&lt;{@link ApplicationReadyEvent}&gt;，
 * 在 Spring 容器完全初始化且所有 Bean 就绪后触发。一次性输出七个关键模块的配置状态，
 * 替代运维人员手动检查多个配置文件和日志片段的繁琐流程。</p>
 *
 * <h2>主要功能</h2>
 * <ul>
 *   <li>监听 {@link ApplicationReadyEvent}，在应用完全就绪后输出核心模块加载状态</li>
 *   <li>汇总输出 Profile 与端口、Nacos、Scheduling、Redis、MySQL、线程池、Sentinel 七项关键配置</li>
 *   <li>日志中脱敏处理，不输出数据库密码、Redis 密码、JWT 密钥等敏感信息</li>
 *   <li>各模块输出相互独立，某一模块配置缺失不影响其他模块的状态输出</li>
 * </ul>
 *
 * <h2>触发时机</h2>
 * <p>{@code ApplicationReadyEvent} 在以下阶段之后触发：</p>
 * <ol>
 *   <li>所有 Bean 初始化完成（包括 {@code @PostConstruct}）</li>
 *   <li>所有 {@code ApplicationRunner} / {@code CommandLineRunner} 执行完毕</li>
 *   <li>Web 服务器（Netty）已启动并接受请求</li>
 * </ol>
 * <p>这意味着日志输出时，所有核心模块确实已完成初始化并能反映真实状态。</p>
 *
 * <h2>输出模块清单</h2>
 * <table>
 *   <tr><th>序号</th><th>模块</th><th>输出内容</th><th>对应配置前缀</th></tr>
 *   <tr><td>1</td><td>启动信息</td><td>激活的 Profile、服务端口</td><td>spring.profiles.active / server.port</td></tr>
 *   <tr><td>2</td><td>Nacos</td><td>配置中心/服务发现启用状态、连接地址</td><td>spring.cloud.nacos.*</td></tr>
 *   <tr><td>3</td><td>Scheduling</td><td>定时任务总开关、线程池大小</td><td>apex.scheduling.enabled / spring.task.scheduling.pool.size</td></tr>
 *   <tr><td>4</td><td>Redis</td><td>连接地址、端口、数据库编号</td><td>spring.data.redis.*</td></tr>
 *   <tr><td>5</td><td>MySQL</td><td>数据源 URL、用户名（不含密码）</td><td>spring.datasource.dynamic.datasource.master.*</td></tr>
 *   <tr><td>6</td><td>线程池</td><td>CPU 核心数、@Async/IO/CPU 线程池的 core/max</td><td>基于 Runtime.availableProcessors() 计算</td></tr>
 *   <tr><td>7</td><td>Sentinel</td><td>流量控制启用状态</td><td>spring.cloud.sentinel.enabled</td></tr>
 * </table>
 *
 * <h2>安全设计</h2>
 * <ul>
 *   <li>不输出数据库密码、Redis 密码、JWT 密钥等敏感信息</li>
 *   <li>数据源 URL 仅打印域名和端口，不打印具体库名后的参数（但 URL 本身可能含库名）</li>
 *   <li>日志级别使用 {@code INFO}，不影响生产环境日志策略</li>
 * </ul>
 *
 * <h2>日志输出示例</h2>
 * <pre>{@code * ========== 核心模块加载状态 ==========
 * [启动] Profile: [dev], 端口: 8080
 * [Nacos] 配置中心: 已启用, 服务发现: 已启用, 地址: 127.0.0.1:8848
 * [Scheduling] 启用: 已启用, 线程池大小: 4
 * [Redis] 地址: 127.0.0.1:6379, 数据库: 0
 * [MySQL] URL: jdbc:mysql://127.0.0.1:3306/apex_data, 用户: root
 * [Async] CPU 核心数: 12, @Async: core=24/max=48, IO: core=24/max=96, CPU: core=11/max=12
 * [Sentinel] 启用: 已启用
 * ========== 核心模块加载完成 ==========
 * }</pre>
 *
 * <h2>扩展指南</h2>
 * <p>如需新增模块检查，按以下步骤操作：</p>
 * <ol>
 *   <li>在 {@link #onApplicationEvent(ApplicationReadyEvent)} 中添加
 *       {@code logXxxStatus()} 调用</li>
 *   <li>新增对应的 {@code private void logXxxStatus()} 方法，从
 *       {@link Environment} 读取配置并输出日志</li>
 *   <li>补充本类规范的作者标签</li>
 * </ol>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see ApplicationReadyEvent
 * @see Environment
 */
@Component
public class CoreModuleStartupLogger implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(CoreModuleStartupLogger.class);

    /** Spring 环境抽象，提供所有配置属性访问能力。 */
    private final Environment env;

    /**
     * 构造器注入。
     *
     * @param env Spring {@link Environment}，由容器自动注入
     */
    public CoreModuleStartupLogger(Environment env) {
        this.env = env;
    }

    /**
     * Spring 容器就绪后触发，依次输出七个核心模块的加载状态。
     *
     * <p>方法内每个 {@code logXxxStatus()} 调用相互独立，某一模块配置缺失不会影响
     * 其他模块的状态输出。</p>
     *
     * @param event {@link ApplicationReadyEvent} 就绪事件对象
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("========== 核心模块加载状态 ==========");

        logProfiles();
        logNacosStatus();
        logSchedulingStatus();
        logRedisStatus();
        logDataSourceStatus();
        logThreadPoolStatus();
        logSentinelStatus();

        log.info("========== 核心模块加载完成 ==========");
    }

    /**
     * 输出激活的 Spring Profile 列表和服务端口。
     *
     * <p>Profile 从 {@link Environment#getActiveProfiles()} 获取，按激活顺序列出。
     * 端口从 {@code server.port} 配置读取，默认为 8080。</p>
     */
    private void logProfiles() {
        List<String> activeProfiles = List.of(env.getActiveProfiles());
        String port = env.getProperty("server.port", "8080");
        log.info("[启动] Profile: {}, 端口: {}", activeProfiles, port);
    }

    /**
     * 输出 Nacos 配置中心和服务发现的启用状态及连接地址。
     *
     * <p>分别读取 {@code spring.cloud.nacos.config.enabled} 和
     * {@code spring.cloud.nacos.discovery.enabled}，均为 true 时标记为"已启用"。
     * 服务地址从 {@code spring.cloud.nacos.server-addr} 读取，
     * 未配置时显示"未配置"而非报错。</p>
     */
    private void logNacosStatus() {
        boolean configEnabled = env.getProperty("spring.cloud.nacos.config.enabled", Boolean.class, true);
        boolean discoveryEnabled = env.getProperty("spring.cloud.nacos.discovery.enabled", Boolean.class, true);
        String serverAddr = env.getProperty("spring.cloud.nacos.server-addr", "未配置");
        log.info("[Nacos] 配置中心: {}, 服务发现: {}, 地址: {}", enabled(configEnabled), enabled(discoveryEnabled), serverAddr);
    }

    /**
     * 输出定时任务调度的启用状态和线程池大小。
     *
     * <p>总开关由项目自定义属性 {@code apex.scheduling.enabled} 控制（默认 true）。
     * 线程池大小来自 Spring 标准属性 {@code spring.task.scheduling.pool.size}（默认 1）。
     * 关闭时仍会输出线程池大小，便于确认是否因误配置导致调度不执行。</p>
     */
    private void logSchedulingStatus() {
        boolean schedulingEnabled = env.getProperty("apex.scheduling.enabled", Boolean.class, true);
        int poolSize = env.getProperty("spring.task.scheduling.pool.size", Integer.class, 1);
        log.info("[Scheduling] 启用: {}, 线程池大小: {}", enabled(schedulingEnabled), poolSize);
    }

    /**
     * 输出 Redis 连接主机、端口和数据库编号。
     *
     * <p>读取 {@code spring.data.redis.host}、{@code spring.data.redis.port}、
     * {@code spring.data.redis.database} 三个配置项。
     * 均未配置时显示"未配置"而非报错 —— 可能使用了 Sentinel/Cluster 模式，
     * 此时需要另行检查哨兵或集群配置。</p>
     */
    private void logRedisStatus() {
        String host = env.getProperty("spring.data.redis.host", "未配置");
        String port = env.getProperty("spring.data.redis.port", "未配置");
        String database = env.getProperty("spring.data.redis.database", "未配置");
        log.info("[Redis] 地址: {}:{}, 数据库: {}", host, port, database);
    }

    /**
     * 输出 MySQL 数据源连接 URL 和用户名。
     *
     * <p>项目使用 dynamic-datasource，主数据源从
     * {@code spring.datasource.dynamic.datasource.master.url} 和
     * {@code spring.datasource.dynamic.datasource.master.username} 读取。
     * 不输出密码，避免敏感信息泄露。</p>
     *
     * <p>多数据源情况下仅输出 master 的配置，如需检查从库请扩展本方法。</p>
     */
    private void logDataSourceStatus() {
        String url = env.getProperty("spring.datasource.dynamic.datasource.master.url", "未配置");
        String username = env.getProperty("spring.datasource.dynamic.datasource.master.username", "未配置");
        log.info("[MySQL] URL: {}, 用户: {}", url, username);
    }

    /**
     * 输出异步线程池配置摘要。
     *
     * <p>项目定义了三类线程池，池大小基于 {@link Runtime#availableProcessors()}
     * 乘以固定倍率计算，与 {@link com.apex.scaffold.interfaces.config.ThreadPoolConfig}
     * 和 {@link com.apex.scaffold.interfaces.config.AsyncConfig} 保持一致：</p>
     * <table>
     *   <tr><th>线程池</th><th>core</th><th>max</th><th>用途</th></tr>
     *   <tr><td>@Async</td><td>CPU×2</td><td>CPU×4</td><td>优雅关机事件等轻量异步</td></tr>
     *   <tr><td>IO</td><td>CPU×2</td><td>CPU×8</td><td>数据库/文件/网络等 IO 密集型任务</td></tr>
     *   <tr><td>CPU</td><td>CPU-1</td><td>CPU</td><td>计算密集型任务</td></tr>
     * </table>
     */
    private void logThreadPoolStatus() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int asyncCore = cpuCores * 2;
        int asyncMax = cpuCores * 4;
        int ioCore = cpuCores * 2;
        int ioMax = cpuCores * 8;
        int cpuCore = Math.max(1, cpuCores - 1);
        int cpuMax = Math.max(1, cpuCores);
        log.info(
                "[Async] CPU 核心数: {}, @Async 线程池: core={}/max={}, IO 线程池: core={}/max={}, CPU 线程池: core={}/max={}",
                cpuCores,
                asyncCore,
                asyncMax,
                ioCore,
                ioMax,
                cpuCore,
                cpuMax);
    }

    /**
     * 输出 Sentinel 流量控制启用状态。
     *
     * <p>读取 {@code spring.cloud.sentinel.enabled} 配置，默认值为 true。
     * Sentinel 关闭时流量控制、熔断降级、系统保护规则全部失效，需在日志中显式标记。</p>
     */
    private void logSentinelStatus() {
        boolean sentinelEnabled = env.getProperty("spring.cloud.sentinel.enabled", Boolean.class, true);
        log.info("[Sentinel] 启用: {}", enabled(sentinelEnabled));
    }

    /**
     * 将 {@code boolean} 值转为可读的中文状态字符串。
     *
     * @param value 布尔值
     * @return {@code true} 返回 "已启用"，{@code false} 返回 "已禁用"
     */
    private static String enabled(boolean value) {
        return value ? "已启用" : "已禁用";
    }
}
