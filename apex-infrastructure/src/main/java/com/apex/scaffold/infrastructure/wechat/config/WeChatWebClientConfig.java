package com.apex.scaffold.infrastructure.wechat.config;

import com.apex.scaffold.infrastructure.remote.filter.ApiCallLogFilter;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

/**
 * 微信生态 WebClient 配置。
 *
 * <p>为微信API调用提供统一的、连接池化的 WebClient Bean。
 * 包括微信公众号API、微信支付API和企业微信API三个独立的 WebClient 实例，
 * 共享同一个底层连接池，避免每次调用都创建新的连接实例，提升连接复用率和性能。
 *
 * <h2>优化说明（Netty/线程池/IO 性能优化方案 P2-1）</h2>
 * <p>原有微信服务存在两种客户端路径混用：
 * <ul>
 *   <li>本配置类的 3 个独立 Bean（wechat-api-pool / wechat-pay-pool / wework-pool）</li>
 *   <li>微信服务中通过 {@code ManagedWebClientFactory.create()} 创建的客户端（共用 external-http 池）</li>
 * </ul>
 * 混用导致 external-http 池额外承担约 100 个微信连接，且 wechat-api-pool 大量空闲。
 * 优化后将所有微信服务统一使用本配置类的 {@code @Qualifier} Bean：
 * <ul>
 *   <li>weChatApiWebClient — 微信公众号/小程序 API，baseUrl {@code https://api.weixin.qq.com}</li>
 *   <li>weChatPayWebClient — 微信支付 API，baseUrl {@code https://api.mch.weixin.qq.com}</li>
 *   <li>weWorkWebClient — 企业微信 API，baseUrl {@code https://qyapi.weixin.qq.com}</li>
 * </ul>
 * 新增 {@link ApiCallLogFilter} 使统一后的 WebClient 具备 API 调用日志能力，
 * 与 {@code ManagedWebClientFactory} 保持一致。</p>
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
 * @version 1.1.0
 * @since 1.0.0
 * @see WebClient
 * @see ConnectionProvider
 */
@Configuration
public class WeChatWebClientConfig {
    private static final Logger log = LoggerFactory.getLogger(WeChatWebClientConfig.class);

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
     * 微信公众号/小程序 API WebClient。
     *
     * <p>用于调用微信公众号、小程序等大多数服务的API接口。
     * 通过 {@link ObjectProvider} 延迟获取 {@link ApiCallLogService}，
     * 避免与日志基础设施之间的启动顺序依赖。
     *
     * @param logServiceProvider API 调用日志服务的延迟提供者
     * @return 配置好的 WebClient 实例，baseUrl 为 https://api.weixin.qq.com
     */
    @Bean("weChatApiWebClient")
    public WebClient weChatApiWebClient(ObjectProvider<ApiCallLogService> logServiceProvider) {
        return buildWebClient("https://api.weixin.qq.com", "wechat-api-pool", logServiceProvider);
    }

    /**
     * 微信支付 API WebClient。
     *
     * <p>用于调用微信支付相关的API接口。
     *
     * @param logServiceProvider API 调用日志服务的延迟提供者
     * @return 配置好的 WebClient 实例，baseUrl 为 https://api.mch.weixin.qq.com
     */
    @Bean("weChatPayWebClient")
    public WebClient weChatPayWebClient(ObjectProvider<ApiCallLogService> logServiceProvider) {
        return buildWebClient("https://api.mch.weixin.qq.com", "wechat-pay-pool", logServiceProvider);
    }

    /**
     * 企业微信 API WebClient。
     *
     * <p>用于调用企业微信相关的API接口。
     *
     * @param logServiceProvider API 调用日志服务的延迟提供者
     * @return 配置好的 WebClient 实例，baseUrl 为 https://qyapi.weixin.qq.com
     */
    @Bean("weWorkWebClient")
    public WebClient weWorkWebClient(ObjectProvider<ApiCallLogService> logServiceProvider) {
        return buildWebClient("https://qyapi.weixin.qq.com", "wework-pool", logServiceProvider);
    }

    /**
     * 构建带连接池、超时、压缩配置和 API 调用日志过滤器的 WebClient。
     *
     * <h2>优化说明（Netty/线程池/IO 性能优化方案 P1-1/P1-2）</h2>
     * <ul>
     *   <li>{@code evictInBackground(Duration.ofSeconds(30))} — 独立后台线程每 30s 扫描一次，
     *       主动关闭超过 maxIdleTime/maxLifeTime 的空闲连接，防止过期连接长期占用池配额</li>
     *   <li>{@code lifo()} — 使用 LIFO 策略分配连接，复用最近释放的热连接，
     *       减少 DNS 重解析和 TLS 重新协商概率，降低延迟抖动</li>
     * </ul>
     *
     * <h2>API 调用日志（P2-1 优化新增）</h2>
     * <p>通过 {@link ObjectProvider} 获取 {@link ApiCallLogService}，
     * 若可用则附加 {@link ApiCallLogFilter}，实现与 {@code ManagedWebClientFactory} 一致的
     * API 调用日志记录能力。</p>
     *
     * @param baseUrl 基础URL
     * @param poolName 连接池名称
     * @param logServiceProvider API 调用日志服务的延迟提供者
     * @return 配置好的 WebClient 实例
     */
    private WebClient buildWebClient(
            String baseUrl, String poolName, ObjectProvider<ApiCallLogService> logServiceProvider) {
        ConnectionProvider connectionProvider = ConnectionProvider.builder(poolName)
                .maxConnections(MAX_CONNECTIONS)
                .pendingAcquireMaxCount(PENDING_ACQUIRE_MAX_COUNT)
                .maxIdleTime(Duration.ofSeconds(MAX_IDLE_TIME_SECONDS))
                .maxLifeTime(Duration.ofSeconds(MAX_LIFE_TIME_SECONDS))
                .evictInBackground(Duration.ofSeconds(30))
                .lifo()
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int)
                        Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS).toMillis())
                .doOnConnected(
                        conn -> conn.addHandlerFirst(new ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                                .addHandlerFirst(new WriteTimeoutHandler(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)))
                .compress(true);

        log.info(
                "微信WebClient初始化完成, {} -> {}, maxConnections: {}, connectTimeout: {}s, readTimeout: {}s",
                poolName,
                baseUrl,
                MAX_CONNECTIONS,
                CONNECT_TIMEOUT_SECONDS,
                READ_TIMEOUT_SECONDS);

        WebClient.Builder builder =
                WebClient.builder().baseUrl(baseUrl).clientConnector(new ReactorClientHttpConnector(httpClient));

        ApiCallLogService logService = logServiceProvider.getIfAvailable();
        if (logService != null) {
            builder.filter(ApiCallLogFilter.create(logService));
        }

        return builder.build();
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
                            "微信API重试耗尽, totalRetries: {}, failure: {}",
                            retrySignal.totalRetries(),
                            retrySignal.failure().getMessage());
                    return retrySignal.failure();
                });
    }
}
