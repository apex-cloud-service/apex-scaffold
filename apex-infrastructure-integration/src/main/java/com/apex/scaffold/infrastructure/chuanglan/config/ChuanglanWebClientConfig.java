package com.apex.scaffold.infrastructure.chuanglan.config;

import com.apex.scaffold.infrastructure.remote.filter.ApiCallLogFilter;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

/**
 * 创蓝平台 WebClient 配置
 *
 * <p>为创蓝API调用提供统一的、连接池化的 WebClient Bean。
 * 所有创蓝API调用（短信发送、余额查询、闪验）共享此 WebClient 实例，
 * 避免每次调用都创建新的连接实例，提升连接复用率和性能。
 *
 * <h2>连接池配置</h2>
 * <ul>
 *   <li><strong>最大连接数</strong>：{@value #MAX_CONNECTIONS}，控制同时打开的TCP连接数上限</li>
 *   <li><strong>等待获取连接上限</strong>：{@value #PENDING_ACQUIRE_MAX_COUNT}，
 *       当连接池满载时，最多等待该数量的请求排队获取连接</li>
 *   <li><strong>连接超时</strong>：{@value #CONNECT_TIMEOUT_SECONDS} 秒</li>
 *   <li><strong>读超时</strong>：{@value #READ_TIMEOUT_SECONDS} 秒</li>
 *   <li><strong>写超时</strong>：{@value #WRITE_TIMEOUT_SECONDS} 秒</li>
 * </ul>
 *
 * <h2>重试配置</h2>
 * <p>最多重试 {@value #MAX_RETRY_ATTEMPTS} 次，采用指数退避策略：
 * 首次重试间隔 200ms，之后每次翻倍（200ms → 400ms → 800ms），添加 ±50% 随机抖动。</p>
 *
 * <h2>连接生命周期</h2>
 * <ul>
 *   <li><strong>空闲超时</strong>：60 秒无流量后关闭连接</li>
 *   <li><strong>最大生命周期</strong>：5 分钟后强制回收连接（防止服务端长连接老化）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see WebClient
 * @see ConnectionProvider
 */
@Slf4j
@Configuration
public class ChuanglanWebClientConfig {

    /** 连接池最大连接数 */
    private static final int MAX_CONNECTIONS = 100;

    /** 等待获取连接的最大排队数 */
    private static final int PENDING_ACQUIRE_MAX_COUNT = 500;

    /** 连接超时（秒） */
    private static final long CONNECT_TIMEOUT_SECONDS = 5;

    /** 读超时（秒） */
    private static final long READ_TIMEOUT_SECONDS = 10;

    /** 写超时（秒） */
    private static final long WRITE_TIMEOUT_SECONDS = 10;

    /** 空闲超时（秒） */
    private static final long MAX_IDLE_TIME_SECONDS = 60;

    /** 最大生命周期（秒） */
    private static final long MAX_LIFE_TIME_SECONDS = 300;

    /** 最大重试次数 */
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /** 首次重试间隔（毫秒） */
    private static final long INITIAL_RETRY_BACKOFF_MS = 200;

    /** 重试退避倍数 */
    private static final int BACKOFF_MULTIPLIER = 2;

    /** 重试 jitter 因子 */
    private static final double RETRY_JITTER_FACTOR = 0.5;

    /**
     * 创建创蓝平台专用的 WebClient Bean
     *
     * <p>配置连接池、超时、重试策略和API调用日志记录，
     * 供所有创蓝API调用共享使用。
     *
     * <h2>优化说明（Netty/线程池/IO 性能优化方案 P1-1/P1-2）</h2>
     * <ul>
     *   <li>{@code evictInBackground(Duration.ofSeconds(30))} — 独立后台线程每 30s 扫描一次，
     *       主动关闭超过 maxIdleTime/maxLifeTime 的空闲连接，防止过期连接长期占用池配额</li>
     *   <li>{@code lifo()} — 使用 LIFO 策略分配连接，复用最近释放的热连接，
     *       减少 DNS 重解析和 TLS 重新协商概率，降低延迟抖动</li>
     * </ul>
     *
     * @param logService API 调用日志服务
     * @return 配置好的 WebClient 实例
     */
    @Bean("chuanglanWebClient")
    public WebClient chuanglanWebClient(ApiCallLogService logService) {
        // 配置连接池
        ConnectionProvider connectionProvider = ConnectionProvider.builder("chuanglan-pool")
                .maxConnections(MAX_CONNECTIONS)
                .pendingAcquireMaxCount(PENDING_ACQUIRE_MAX_COUNT)
                .maxIdleTime(Duration.ofSeconds(MAX_IDLE_TIME_SECONDS))
                .maxLifeTime(Duration.ofSeconds(MAX_LIFE_TIME_SECONDS))
                .evictInBackground(Duration.ofSeconds(30))
                .lifo()
                .build();

        // 配置 HTTP 客户端（超时设置）
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int)
                        Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS).toMillis())
                .doOnConnected(
                        conn -> conn.addHandlerFirst(new ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                                .addHandlerFirst(new WriteTimeoutHandler(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)))
                .compress(true);

        log.info(
                "创蓝WebClient初始化完成, maxConnections: {}, connectTimeout: {}s, readTimeout: {}s, writeTimeout: {}s",
                MAX_CONNECTIONS,
                CONNECT_TIMEOUT_SECONDS,
                READ_TIMEOUT_SECONDS,
                WRITE_TIMEOUT_SECONDS);

        return WebClient.builder()
                .filter(ApiCallLogFilter.create(logService))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * 创建指数退避重试策略
     *
     * <p>重试策略：最多重试 {@value #MAX_RETRY_ATTEMPTS} 次，
     * 首次重试延迟 {@value #INITIAL_RETRY_BACKOFF_MS}ms，
     * 之后每次延迟加倍，并添加 ±50% 随机抖动防止重试风暴。
     *
     * <h3>重试条件</h3>
     * <p>对 {@link java.io.IOException} 和 5xx 服务器错误进行重试，
     * 4xx 客户端错误不重试（因为客户端错误重试无意义）。
     *
     * @return Retry 策略实例
     */
    public static Retry createRetry() {
        return reactor.util.retry.Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofMillis(INITIAL_RETRY_BACKOFF_MS))
                .maxBackoff(Duration.ofSeconds(5))
                .jitter(RETRY_JITTER_FACTOR)
                .filter(throwable -> {
                    if (throwable instanceof java.io.IOException) {
                        return true;
                    }
                    if (throwable
                            instanceof
                            org.springframework.web.reactive.function.client.WebClientResponseException
                            wcre) {
                        return wcre.getStatusCode().is5xxServerError();
                    }
                    return false;
                })
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                    log.error(
                            "创蓝API重试耗尽, totalRetries: {}, failure: {}",
                            retrySignal.totalRetries(),
                            retrySignal.failure().getMessage());
                    return retrySignal.failure();
                });
    }
}
