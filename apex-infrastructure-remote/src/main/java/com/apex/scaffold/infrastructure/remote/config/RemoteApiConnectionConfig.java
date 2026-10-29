package com.apex.scaffold.infrastructure.remote.config;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.resources.ConnectionProvider;

/**
 * 远程调用 HTTP 连接池与网络层配置。
 *
 * <p>为 {@link com.apex.scaffold.infrastructure.remote.service.RemoteApiClient} 提供共享的
 * Reactor Netty {@link ConnectionProvider}，实现：
 * <ul>
 *   <li><b>连接复用</b> — 多服务共享同一个连接池，避免每次请求 TCP/TLS 握手开销</li>
 *   <li><b>高并发保护</b> — 通过 pendingAcquire 上限防止连接风暴</li>
 *   <li><b>资源回收</b> — 空闲超时 + 最大生命周期自动回收过期连接</li>
 * </ul>
 *
 * <p>配置通过 {@link ExternalHttpClientPoolProperties} 读取 YAML 中 {@code remote.api.pool-*} 值。
 *
 * <h2>设计说明</h2>
 * <table>
 *   <tr><th>参数</th><th>默认值</th><th>说明</th></tr>
 *   <tr><td>maxConnections</td><td>100</td><td>连接池最大连接数，按需调大以支持更高并发</td></tr>
 *   <tr><td>pendingAcquireMaxCount</td><td>500</td><td>等待获取连接的最大请求数</td></tr>
 *   <tr><td>maxIdleTime</td><td>30s</td><td>连接最大空闲时间</td></tr>
 *   <tr><td>maxLifeTime</td><td>5min</td><td>连接最大生命周期</td></tr>
 * </table>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.1.0
 */
@Configuration
@EnableConfigurationProperties(ExternalHttpClientPoolProperties.class)
@Slf4j
public class RemoteApiConnectionConfig {

    /** 外部 HTTP 连接池 Bean 名称，供其他模块按名称注入 */
    public static final String REMOTE_API_CONNECTION_PROVIDER = "remoteApiConnectionProvider";

    /**
     * 创建用于远程 API 调用的共享连接池。
     *
     * <p>连接池名称 {@code remote-api-pool} 用于 Netty 指标监控。
     * 配置从 {@link ExternalHttpClientPoolProperties} 读取，若容器中已存在同名 Bean 则不覆盖。
     *
     * <h2>设计说明</h2>
     * <ul>
     *   <li>{@code evictInBackground(Duration.ofSeconds(30))} — 独立后台线程每 30s 扫描一次，
     *       主动关闭超过 maxIdleTime/maxLifeTime 的空闲连接，防止过期连接长期占用池配额</li>
     *   <li>{@code lifo()} — 使用 LIFO 策略分配连接，复用最近释放的热连接（TCP 状态最优），
     *       减少 DNS 重解析和 TLS 重新协商概率，降低延迟抖动</li>
     * </ul>
     *
     * @param poolProperties 连接池配置属性
     * @return 共享连接池实例
     */
    @Bean(name = REMOTE_API_CONNECTION_PROVIDER)
    @ConditionalOnMissingBean(name = REMOTE_API_CONNECTION_PROVIDER)
    public ConnectionProvider remoteApiConnectionProvider(ExternalHttpClientPoolProperties poolProperties) {
        int maxConnections = poolProperties.getPoolMaxConnections();
        int pendingAcquireMax = poolProperties.getPoolPendingAcquireMaxCount();
        int maxIdleSeconds = poolProperties.getPoolMaxIdleTimeSeconds();
        int maxLifeMinutes = poolProperties.getPoolMaxLifeTimeMinutes();

        ConnectionProvider provider = ConnectionProvider.builder("remote-api-pool")
                .maxConnections(maxConnections)
                .pendingAcquireMaxCount(pendingAcquireMax)
                .maxIdleTime(Duration.ofSeconds(maxIdleSeconds))
                .maxLifeTime(Duration.ofMinutes(maxLifeMinutes))
                .evictInBackground(Duration.ofSeconds(30))
                .lifo()
                .build();

        log.info(
                "共享远程调用连接池已创建: name=remote-api-pool, maxConnections={}, pendingAcquireMax={}, "
                        + "maxIdleTime={}s, maxLifeTime={}min, evictInBackground=30s, lifo=true",
                maxConnections,
                pendingAcquireMax,
                maxIdleSeconds,
                maxLifeMinutes);
        return provider;
    }
}
