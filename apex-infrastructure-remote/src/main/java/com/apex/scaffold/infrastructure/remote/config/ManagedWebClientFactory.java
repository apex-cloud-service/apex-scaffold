package com.apex.scaffold.infrastructure.remote.config;

import com.apex.scaffold.infrastructure.remote.filter.ApiCallLogFilter;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * 容器级外部 HTTP 客户端工厂。
 *
 * <p>在构造时应用已绑定的配置并创建连接池；同一容器中的客户端共享该池，
 * 容器销毁时同步释放连接池。业务重试仍由适配器按幂等语义决定。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public final class ManagedWebClientFactory implements AutoCloseable {
    private static final Duration POOL_EVICTION_INTERVAL = Duration.ofSeconds(30);
    private static final Duration POOL_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final ConnectionProvider provider;
    private final ReactorClientHttpConnector connector;
    private final ApiCallLogService logService;

    /**
     * 在首个消费者创建客户端之前固定连接、读写超时及连接池配置。
     *
     * @param properties 外部 HTTP 连接与连接池配置
     * @param logService 可选的 API 调用日志服务，为 null 时不注册日志过滤器
     */
    public ManagedWebClientFactory(ExternalHttpClientPoolProperties properties, ApiCallLogService logService) {
        this.logService = logService;
        provider = ConnectionProvider.builder("external-http")
                .maxConnections(properties.getPoolMaxConnections())
                .pendingAcquireMaxCount(properties.getPoolPendingAcquireMaxCount())
                .maxIdleTime(Duration.ofSeconds(properties.getPoolMaxIdleTimeSeconds()))
                .maxLifeTime(Duration.ofMinutes(properties.getPoolMaxLifeTimeMinutes()))
                .evictInBackground(POOL_EVICTION_INTERVAL)
                .lifo()
                .build();
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMillis())
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(properties.getIoTimeoutSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(properties.getIoTimeoutSeconds(), TimeUnit.SECONDS)))
                .compress(true);
        connector = new ReactorClientHttpConnector(httpClient);
    }

    /**
     * 创建共享本容器连接池的客户端，不预设基础地址。
     *
     * @return 使用受管连接池及可选日志过滤器的客户端
     */
    public WebClient create() {
        WebClient.Builder builder = WebClient.builder().clientConnector(connector);
        if (logService != null) {
            builder.filter(ApiCallLogFilter.create(logService));
        }
        return builder.build();
    }

    /**
     * 创建固定基础地址客户端，保留相同的超时、压缩及日志策略。
     *
     * @param baseUrl 客户端请求使用的基础地址
     * @return 使用指定基础地址及受管连接池的客户端
     */
    public WebClient create(String baseUrl) {
        return create().mutate().baseUrl(baseUrl).build();
    }

    /** 仅在同步容器销毁边界等待连接池释放，不占用请求处理线程。 */
    @Override
    public void close() {
        provider.disposeLater().block(POOL_SHUTDOWN_TIMEOUT);
    }
}
