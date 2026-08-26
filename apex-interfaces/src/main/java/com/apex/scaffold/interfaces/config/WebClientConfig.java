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
 * <p>配置用于 HTTP 调用的 WebClient，支持超时、连接池等设置。
 * 超时参数通过 {@code server.webclient.*} 配置项管理。
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
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
