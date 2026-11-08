package com.apex.scaffold.interfaces.config;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Netty 服务器配置类
 *
 * <p>配置 Netty 服务器参数，优化高并发处理能力与网络传输性能，同时防护 Slowloris 慢速攻击。
 * 端口由 {@code server.port} 配置项控制，此处仅配置 TCP 参数不覆盖端口。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>响应压缩：启用 Gzip/Brotli 压缩，减少网络传输数据量</li>
 *   <li>连接队列：SO_BACKLOG 提升至 2048，应对瞬时高并发突发</li>
 *   <li>TCP 参数：启用 SO_KEEPALIVE 和 TCP_NODELAY，减少空闲连接堆积和传输延迟</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>连接超时：CONNECT_TIMEOUT_MILLIS 降至 5000ms，避免慢速连接耗尽连接池</li>
 *   <li>读写超时：默认 ReadTimeoutHandler 为 30s、WriteTimeoutHandler 为 60s，并支持按运行场景覆盖</li>
 *   <li>空闲检测：默认检测 20s 内无读操作的连接，并支持按运行场景覆盖</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.1.0
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class NettyConfig {

    /** 响应压缩默认最小阈值：1KB */
    private static final int DEFAULT_MIN_COMPRESSION_SIZE = 1024;

    /**
     * 是否启用响应压缩
     *
     * <p>通过 {@code server.netty.compression.enabled} 配置，默认开启。
     * 生产环境建议保持开启以减少带宽消耗。
     */
    @Value("${server.netty.compression.enabled:true}")
    private boolean compressionEnabled;

    /**
     * 响应压缩级别（0-9）
     *
     * <p>通过 {@code server.netty.compression.level} 配置，默认 6。
     * <ul>
     *   <li>0：不压缩</li>
     *   <li>1-3：速度优先，压缩率较低</li>
     *   <li>4-6：平衡模式（推荐）</li>
     *   <li>7-9：压缩率优先，消耗更多CPU</li>
     * </ul>
     */
    @Value("${server.netty.compression.level:6}")
    private int compressionLevel;

    /**
     * 触发压缩的最小响应体大小（字节）
     *
     * <p>通过 {@code server.netty.compression.min-response-size} 配置，默认 1KB。
     * 小于此值的响应不进行压缩，避免对小响应产生无意义的压缩开销。
     */
    @Value("${server.netty.compression.min-response-size:1024}")
    private int minCompressionResponseSize;

    /**
     * 连接无入站数据时允许等待的秒数。
     *
     * <p>默认值保持历史的 30 秒。真实数据库诊断或管理模拟接口可能在完整请求体到达后执行
     * 较长时间，此时可通过 {@code server.netty.read-timeout-seconds} 临时增大窗口，避免 Netty
     * 在业务响应生成前关闭连接。</p>
     */
    @Value("${server.netty.read-timeout-seconds:30}")
    private int readTimeoutSeconds;

    /**
     * 单次响应写入允许等待的秒数。
     *
     * <p>通过 {@code server.netty.write-timeout-seconds} 配置，默认保持历史值 60 秒。</p>
     */
    @Value("${server.netty.write-timeout-seconds:60}")
    private int writeTimeoutSeconds;

    /**
     * 连接读空闲事件的检测秒数。
     *
     * <p>通过 {@code server.netty.idle-timeout-seconds} 配置，默认保持历史值 20 秒。</p>
     */
    @Value("${server.netty.idle-timeout-seconds:20}")
    private int idleTimeoutSeconds;

    /** Netty 服务器端连接建立超时（毫秒），从配置中读取，默认 5000 */
    @Value("${server.netty.connect-timeout-millis:5000}")
    private int connectTimeoutMillis;

    /** 构造 Netty 配置类 */
    public NettyConfig() {}

    /**
     * 配置 Netty 服务器参数
     *
     * <p>应用 TCP 优化参数并启用响应压缩功能。压缩仅对大于 {@code minCompressionResponseSize}
     * 的响应生效，避免对小响应产生额外的 CPU 开销。
     *
     * <h2>设计说明</h2>
     * <ul>
     *   <li>Netty 自动协商压缩算法（优先 Brotli > Gzip）</li>
     *   <li>仅对 text/*、application/json 等文本型 MIME 类型进行压缩</li>
     *   <li>已压缩的二进制格式（图片、视频等）不会被重复压缩</li>
     * </ul>
     *
     * @return WebServerFactoryCustomizer 自定义器
     */
    @Bean
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyServerCustomizer() {
        return factory -> {
            int processors = Runtime.getRuntime().availableProcessors();
            int actualMinSize = Math.max(minCompressionResponseSize, DEFAULT_MIN_COMPRESSION_SIZE);
            int actualReadTimeoutSeconds = Math.max(1, readTimeoutSeconds);
            int actualWriteTimeoutSeconds = Math.max(1, writeTimeoutSeconds);
            int actualIdleTimeoutSeconds = Math.max(1, idleTimeoutSeconds);

            factory.addServerCustomizers(server -> {
                // 配置 TCP 参数与空闲连接管理
                return server.option(ChannelOption.SO_BACKLOG, 2048)
                        .option(ChannelOption.SO_KEEPALIVE, true)
                        .option(ChannelOption.TCP_NODELAY, true)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                        .doOnConnection(conn -> {
                            conn.addHandlerLast(new IdleStateHandler(actualIdleTimeoutSeconds, 0, 0, TimeUnit.SECONDS));
                            // 读超时仅用于 Slowloris 防护（慢请求头/慢请求体）。SSE 等长驻响应
                            // 在请求读入后长时间不再有入站数据，固定读超时会在首个响应事件前
                            // 关闭长连接 —— 故请求读取完成（入站最后一块请求体）时即解除读超时，
                            // 此后由业务层响应超时与写超时兜底。
                            ReadTimeoutHandler readTimeoutHandler =
                                    new ReadTimeoutHandler(actualReadTimeoutSeconds, TimeUnit.SECONDS);
                            conn.addHandlerLast(readTimeoutHandler);
                            conn.addHandlerLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg)
                                        throws Exception {
                                    if (msg instanceof LastHttpContent) {
                                        try {
                                            ctx.pipeline().remove(readTimeoutHandler);
                                        } catch (java.util.NoSuchElementException ignored) {
                                            // 已移除：仅解除一次
                                        }
                                    }
                                    ctx.fireChannelRead(msg);
                                }
                            });
                            conn.addHandlerLast(new WriteTimeoutHandler(actualWriteTimeoutSeconds, TimeUnit.SECONDS));
                        });
            });

            // 启用响应压缩（Gzip/Brotli）
            if (compressionEnabled) {
                factory.addServerCustomizers(server -> {
                    log.info("Netty 响应压缩已启用, 级别: {}, 最小压缩大小: {}B", compressionLevel, actualMinSize);
                    return server.compress(compressionLevel);
                });
            } else {
                log.info("Netty 响应压缩未启用");
            }

            log.info(
                    "Netty 服务器配置完成, processors: {}, backlog: {}, "
                            + "readTimeout: {}s, writeTimeout: {}s, idleTimeout: {}s, compression: {}",
                    processors,
                    2048,
                    actualReadTimeoutSeconds,
                    actualWriteTimeoutSeconds,
                    actualIdleTimeoutSeconds,
                    compressionEnabled);
        };
    }
}
