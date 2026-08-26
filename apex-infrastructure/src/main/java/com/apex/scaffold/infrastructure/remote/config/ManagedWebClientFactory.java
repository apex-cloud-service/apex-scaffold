package com.apex.scaffold.infrastructure.remote.config;

import com.apex.scaffold.infrastructure.remote.filter.ApiCallLogFilter;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * 受管外部 HTTP 客户端工厂。
 *
 * <p>为不能直接使用 Spring 命名客户端的基础设施适配器提供统一的连接池、连接超时、
 * 读写超时和响应压缩配置。所有通过本工厂创建的 {@link WebClient} 共享连接池，避免每个
 * 服务自行创建无边界客户端。业务重试仍由具体适配器按接口幂等语义决定。</p>
 *
 * <h2>运行时配置注入</h2>
 * <p>连接池参数（最大连接数、等待队列长度、空闲时间、生命周期）可通过 Spring
 * {@link ExternalHttpClientPoolProperties} 注入，在容器启动后调用
 * {@link #initialize(ExternalHttpClientPoolProperties)} 覆盖内嵌默认值。
 * 在调用 {@code initialize} 之前，工厂使用与 YAML 默认值一致的内嵌兼容值。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ManagedWebClientFactory {
    private static final Logger log = LoggerFactory.getLogger(ManagedWebClientFactory.class);

    /** 内嵌兼容默认值：连接建立超时（毫秒） */
    private static final int FALLBACK_CONNECT_TIMEOUT_MILLIS = 5_000;
    /** 内嵌兼容默认值：IO 读写超时（秒） */
    private static final int FALLBACK_IO_TIMEOUT_SECONDS = 10;
    /** 内嵌兼容默认值：连接池最大连接数 */
    private static final int FALLBACK_MAX_CONNECTIONS = 100;
    /** 内嵌兼容默认值：等待获取连接上限 */
    private static final int FALLBACK_PENDING_ACQUIRE_MAX = 500;
    /** 内嵌兼容默认值：空闲时间（秒） */
    private static final int FALLBACK_MAX_IDLE_TIME_SECONDS = 30;
    /** 内嵌兼容默认值：最大生命周期（分钟） */
    private static final int FALLBACK_MAX_LIFE_TIME_MINUTES = 5;

    /** 标记是否已通过 Spring 注入外部配置 */
    private static volatile boolean initialized = false;

    /** 当前生效的 IO 读写超时（volatile 保证可见性） */
    private static volatile int ioTimeoutSeconds = FALLBACK_IO_TIMEOUT_SECONDS;

    /** 当前生效的连接建立超时（volatile 保证可见性） */
    private static volatile int connectTimeoutMillis = FALLBACK_CONNECT_TIMEOUT_MILLIS;

    /** 当前生效的连接池配置（volatile 保证可见性） */
    private static volatile int maxConnections = FALLBACK_MAX_CONNECTIONS;

    private static volatile int pendingAcquireMax = FALLBACK_PENDING_ACQUIRE_MAX;
    private static volatile int maxIdleTimeSeconds = FALLBACK_MAX_IDLE_TIME_SECONDS;
    private static volatile int maxLifeTimeMinutes = FALLBACK_MAX_LIFE_TIME_MINUTES;

    /** API 调用日志服务（volatile 保证可见性）。null 表示不启用日志记录。 */
    private static volatile ApiCallLogService logService;

    /**
     * Connector 延迟持有者。
     *
     * <p>使用静态内部类延迟创建，确保在首次使用前通过 {@link #initialize(ExternalHttpClientPoolProperties)}
     * 注入的配置已生效。</p>
     */
    private static class ConnectorHolder {
        static final ReactorClientHttpConnector INSTANCE = buildConnector();
    }

    /**
     * 创建 {@link ManagedWebClientFactory} 实例。
     */
    private ManagedWebClientFactory() {}

    /**
     * 通过外部配置初始化连接池参数。
     *
     * <p>由 Spring 配置类在容器启动后调用，将 YAML 配置文件中的
     * {@code remote.api.pool-*} 值注入到工厂中。可在首次调用前多次调用
     * （后续调用仅更新参数，不重建连接池）。连接器实例推迟到首次
     * {@link #create()} / {@link #create(String)} 调用时延迟构建。</p>
     *
     * @param properties 外部 HTTP 连接池配置属性
     */
    public static synchronized void initialize(ExternalHttpClientPoolProperties properties) {
        if (properties == null) {
            log.warn("ManagedWebClientFactory.initialize 收到 null 参数，保持当前配置");
            return;
        }
        if (properties.getIoTimeoutSeconds() > 0) {
            ioTimeoutSeconds = properties.getIoTimeoutSeconds();
        }
        if (properties.getConnectTimeoutMillis() > 0) {
            connectTimeoutMillis = properties.getConnectTimeoutMillis();
        }
        if (properties.getPoolMaxConnections() > 0) {
            maxConnections = properties.getPoolMaxConnections();
        }
        if (properties.getPoolPendingAcquireMaxCount() > 0) {
            pendingAcquireMax = properties.getPoolPendingAcquireMaxCount();
        }
        if (properties.getPoolMaxIdleTimeSeconds() > 0) {
            maxIdleTimeSeconds = properties.getPoolMaxIdleTimeSeconds();
        }
        if (properties.getPoolMaxLifeTimeMinutes() > 0) {
            maxLifeTimeMinutes = properties.getPoolMaxLifeTimeMinutes();
        }
        initialized = true;
        log.info(
                "ManagedWebClientFactory 已应用外部连接池配置: maxConnections={}, pendingAcquireMax={}, "
                        + "maxIdleTime={}s, maxLifeTime={}min, ioTimeout={}s, connectTimeout={}ms",
                maxConnections,
                pendingAcquireMax,
                maxIdleTimeSeconds,
                maxLifeTimeMinutes,
                ioTimeoutSeconds,
                connectTimeoutMillis);
    }

    /**
     * 注入 API 调用日志服务，启用出站 HTTP 调用的自动日志记录。
     *
     * <p>由 {@link ManagedWebClientLogConfig} 在 Spring 容器启动后调用。
     * 设置后，所有通过 {@link #create()} 和 {@link #create(String)} 创建的
     * WebClient 将自动附加 {@link ApiCallLogFilter}。</p>
     *
     * @param service API 调用日志服务，为 null 则禁用日志记录
     */
    public static void enableLogFilter(ApiCallLogService service) {
        logService = service;
        if (service != null) {
            log.info("ManagedWebClientFactory 已启用 API 调用日志过滤器");
        }
    }

    /**
     * 构建连接器实例。
     *
     * <p>使用当前生效的配置参数创建链接提供者和连接器。
     *
     * <h2>优化说明（Netty/线程池/IO 性能优化方案 P1-1/P1-2）</h2>
     * <ul>
     *   <li>{@code evictInBackground(Duration.ofSeconds(30))} — 独立后台线程每 30s 扫描一次，
     *       主动关闭超过 maxIdleTime/maxLifeTime 的空闲连接，防止过期连接长期占用池配额</li>
     *   <li>{@code lifo()} — 使用 LIFO 策略分配连接，复用最近释放的热连接，
     *       减少 DNS 重解析和 TLS 重新协商概率，降低延迟抖动</li>
     * </ul>
     */
    private static ReactorClientHttpConnector buildConnector() {
        ConnectionProvider provider = ConnectionProvider.builder("external-http")
                .maxConnections(maxConnections)
                .pendingAcquireMaxCount(pendingAcquireMax)
                .maxIdleTime(Duration.ofSeconds(maxIdleTimeSeconds))
                .maxLifeTime(Duration.ofMinutes(maxLifeTimeMinutes))
                .evictInBackground(Duration.ofSeconds(30))
                .lifo()
                .build();
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(ioTimeoutSeconds, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(ioTimeoutSeconds, TimeUnit.SECONDS)))
                .compress(true);
        return new ReactorClientHttpConnector(httpClient);
    }

    /**
     * 创建没有预设基础地址的受管客户端。
     *
     * @return 共享连接池和超时策略的客户端
     */
    public static WebClient create() {
        WebClient.Builder builder = WebClient.builder().clientConnector(ConnectorHolder.INSTANCE);
        if (logService != null) {
            builder.filter(ApiCallLogFilter.create(logService));
        }
        return builder.build();
    }

    /**
     * 创建具有固定基础地址的受管客户端。
     *
     * @param baseUrl 外部服务基础地址
     * @return 共享连接池和超时策略的客户端
     */
    public static WebClient create(String baseUrl) {
        WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl).clientConnector(ConnectorHolder.INSTANCE);
        if (logService != null) {
            builder.filter(ApiCallLogFilter.create(logService));
        }
        return builder.build();
    }
}
