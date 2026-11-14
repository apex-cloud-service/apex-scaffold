package com.apex.scaffold.interfaces.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * WebClient 配置类
 *
 * <p>配置用于 HTTP 调用的 WebClient，基于 Reactor Netty 构建响应式 HTTP 客户端，
 * 支持连接超时、读取超时、写入超时等设置。超时参数通过 {@code server.webclient.*} 配置项管理。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>构建 {@link WebClient.Builder} Bean，底层使用 Reactor Netty {@link HttpClient}</li>
 *   <li>设置连接超时（CONNECT_TIMEOUT_MILLIS），避免 TCP 握手长时间阻塞</li>
 *   <li>在连接建立后注册读取/写入超时处理器，防止对端响应慢或请求体发送卡住</li>
 *   <li>通过 {@link ReactorClientHttpConnector} 将 Netty HttpClient 接入 WebClient</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code server.webclient.connect-timeout-millis} — 连接超时（毫秒），默认 5000</li>
 *   <li>{@code server.webclient.read-timeout-millis} — 读取超时（毫秒），默认 10000</li>
 *   <li>{@code server.webclient.write-timeout-millis} — 写入超时（毫秒），默认 10000</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see WebClient
 * @see HttpClient
 */
@Slf4j
@Configuration
public class WebClientConfig {

    /** 连接超时时间（毫秒），从配置中读取，默认 5000 */
    @Value("${server.webclient.connect-timeout-millis:5000}")
    private int connectTimeout;

    /** 读取超时时间（毫秒），从配置中读取，默认 10000 */
    @Value("${server.webclient.read-timeout-millis:10000}")
    private int readTimeout;

    /** 写入超时时间（毫秒），从配置中读取，默认 10000 */
    @Value("${server.webclient.write-timeout-millis:10000}")
    private int writeTimeout;

    /**
     * 创建 WebClient.Builder
     *
     * @return WebClient.Builder实例
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        log.info(
                "初始化 WebClient 配置，connectTimeout: {}ms, readTimeout: {}ms, writeTimeout: {}ms",
                connectTimeout,
                readTimeout,
                writeTimeout);

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeout, TimeUnit.MILLISECONDS)));

        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
