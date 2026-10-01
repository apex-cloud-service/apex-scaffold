package com.apex.scaffold.infrastructure.office.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * 办公平台WebClient配置
 *
 * <p>配置用于调用飞书、钉钉、企业微信等办公平台API的响应式WebClient,
 * 包含连接超时、读写超时和内存缓冲区大小的优化配置。
 *
 * <h2>配置特性</h2>
 * <ul>
 *   <li><strong>连接超时</strong>:30秒,适用于办公平台API的建立连接阶段</li>
 *   <li><strong>响应超时</strong>:30秒,适用于办公平台API的整体响应时间</li>
 *   <li><strong>读写超时</strong>:30秒,适用于办公平台API的数据传输阶段</li>
 *   <li><strong>内存缓冲区</strong>:16MB,适用于处理办公平台的大文件上传/下载</li>
 * </ul>
 *
 * <h2>超时配置说明</h2>
 * <table border="1">
 *   <caption>超时类型对比</caption>
 *   <tr>
 *     <th>超时类型</th>
 *     <th>配置值</th>
 *     <th>作用阶段</th>
 *   </tr>
 *   <tr>
 *     <td>连接超时(CONNECT_TIMEOUT)</td>
 *     <td>30秒</td>
 *     <td>建立TCP连接</td>
 *   </tr>
 *   <tr>
 *     <td>响应超时(responseTimeout)</td>
 *     <td>30秒</td>
 *     <td>从发起请求到收到响应</td>
 *   </tr>
 *   <tr>
 *     <td>读超时(ReadTimeout)</td>
 *     <td>30秒</td>
 *     <td>读取响应数据</td>
 *   </tr>
 *   <tr>
 *     <td>写超时(WriteTimeout)</td>
 *     <td>30秒</td>
 *     <td>发送请求数据</td>
 *   </tr>
 * </table>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 示例1: 注入officeWebClient并调用飞书API（构造器注入）
 * @Service
 * @RequiredArgsConstructor
 * public class FeishuService {
 *
 *     private final WebClient officeWebClient;
 *
 *     public Mono<String> getFeishuUserInfo(String accessToken) {
 *         return officeWebClient
 *             .get()
 *             .uri("https://open.feishu.cn/open-apis/authen/v1/user_info")
 *             .header("Authorization", "Bearer " + accessToken)
 *             .retrieve()
 *             .bodyToMono(String.class);
 *     }
 * }
 *
 * // 示例2: 上传文件到钉钉(16MB内存缓冲区支持)
 * public Mono<String> uploadToDingtalk(byte[] fileData) {
 *     return officeWebClient
 *         .post()
 *         .uri("https://oapi.dingtalk.com/media/upload")
 *         .bodyValue(fileData)
 *         .retrieve()
 *         .bodyToMono(String.class);
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.0.0
 */
@Configuration
public class OfficeWebClientConfig {

    /**
     * 创建办公平台专用的WebClient Bean
     *
     * <p>配置包括:
     * <ul>
     *   <li>30秒连接超时</li>
     *   <li>30秒响应超时</li>
     *   <li>30秒读写超时</li>
     *   <li>16MB内存缓冲区</li>
     * </ul>
     *
     * @return 配置好的WebClient实例
     */
    @Bean("officeWebClient")
    public WebClient officeWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }
}
