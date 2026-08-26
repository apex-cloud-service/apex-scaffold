package com.apex.scaffold.interfaces.config;

import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.spi.Spi;
import com.alibaba.csp.sentinel.transport.HeartbeatSender;
import com.alibaba.csp.sentinel.transport.config.TransportConfig;
import com.alibaba.csp.sentinel.transport.endpoint.Endpoint;
import com.alibaba.csp.sentinel.transport.heartbeat.HeartbeatMessage;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 基于 JDK HTTP 客户端的 Sentinel 心跳发送器。
 *
 * <p>Sentinel 1.8.9 内置的简单 Socket 客户端在有效的 Dashboard 响应时可能返回 null 响应并引发空指针，
 * 本实现使用 JDK HttpClient 处理标准 HTTP 响应帧，并显式绕过进程级代理设置，确保控制面连接的稳定性。</p>
 *
 * <h2>核心特性</h2>
 * <ul>
 *   <li>使用 JDK HttpClient 替代默认 Socket 实现，获得更好的 HTTP 协议处理能力</li>
 *   <li>绕过系统代理设置，直连 Sentinel Dashboard 控制平面</li>
 *   <li>支持 Dashboard 端点轮询，实现高可用心跳发送</li>
 *   <li>5 秒请求超时，避免心跳阻塞</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Spi(order = Spi.ORDER_HIGHEST)
public final class JdkHttpHeartbeatSender implements HeartbeatSender {

    private static final int OK_STATUS = 200;
    private static final long DEFAULT_INTERVAL_MS = 10_000L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HeartbeatMessage heartbeatMessage;
    private final HttpClient httpClient;
    private final List<Endpoint> dashboardEndpoints;
    private int currentEndpointIndex;

    /**
     * 默认构造函数。
     *
     * <p>使用默认心跳消息、直连代理选择器和系统默认的 Dashboard 端点列表创建实例。
     */
    public JdkHttpHeartbeatSender() {
        this(
                new HeartbeatMessage(),
                HttpClient.newBuilder()
                        .connectTimeout(REQUEST_TIMEOUT)
                        .proxy(new DirectProxySelector())
                        .build(),
                TransportConfig.getConsoleServerList());
    }

    /**
     * 构造心跳发送器。
     *
     * <p>使用指定的心跳消息、HTTP 客户端和 Dashboard 端点列表创建实例。
     *
     * @param heartbeatMessage 心跳消息载体
     * @param httpClient       用于发送心跳的 JDK HTTP 客户端
     * @param dashboardEndpoints Dashboard 服务器端点列表
     */
    JdkHttpHeartbeatSender(
            HeartbeatMessage heartbeatMessage, HttpClient httpClient, List<Endpoint> dashboardEndpoints) {
        this.heartbeatMessage = heartbeatMessage;
        this.httpClient = httpClient;
        this.dashboardEndpoints = new ArrayList<>(dashboardEndpoints);
        if (this.dashboardEndpoints.isEmpty()) {
            RecordLog.warn("[JdkHttpHeartbeatSender] Dashboard server address is not configured");
        } else {
            RecordLog.info("[JdkHttpHeartbeatSender] Dashboard address list retrieved: {}", this.dashboardEndpoints);
        }
    }

    /**
     * 发送心跳消息到 Sentinel Dashboard。
     *
     * <p>使用轮询策略选择 Dashboard 端点，将心跳消息以 HTTP POST 方式发送。
     * 发送失败或超时会记录警告日志并切换到下一个端点。
     *
     * @return 心跳发送成功返回 {@code true}，失败返回 {@code false}
     */
    @Override
    public boolean sendHeartbeat() {
        if (TransportConfig.getRuntimePort() <= 0 || dashboardEndpoints.isEmpty()) {
            return false;
        }

        Endpoint endpoint = dashboardEndpoints.get(currentEndpointIndex % dashboardEndpoints.size());
        try {
            HttpRequest request = HttpRequest.newBuilder(buildHeartbeatUri(endpoint))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(encodeForm(heartbeatMessage.generateCurrentMessage())))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == OK_STATUS) {
                return true;
            }
            RecordLog.warn(
                    "[JdkHttpHeartbeatSender] Dashboard rejected heartbeat, endpoint={}, status={}",
                    endpoint,
                    response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            RecordLog.warn("[JdkHttpHeartbeatSender] Heartbeat interrupted for " + endpoint, exception);
        } catch (Exception exception) {
            RecordLog.warn("[JdkHttpHeartbeatSender] Failed to send heartbeat to " + endpoint, exception);
        }
        currentEndpointIndex = (currentEndpointIndex + 1) % dashboardEndpoints.size();
        return false;
    }

    /**
     * 获取心跳发送间隔。
     *
     * @return 心跳间隔时间（毫秒），默认 10 秒
     */
    @Override
    public long intervalMs() {
        return DEFAULT_INTERVAL_MS;
    }

    /**
     * 构建心跳请求 URI。
     *
     * <p>根据 Dashboard 端点协议、主机、端口和心跳 API 路径拼接完整 URI。
     *
     * @param endpoint Dashboard 端点
     * @return 心跳请求 URI
     */
    static URI buildHeartbeatUri(Endpoint endpoint) {
        return URI.create(endpoint.getProtocol().getProtocol()
                + "://"
                + endpoint.getHost()
                + ":"
                + endpoint.getPort()
                + TransportConfig.getHeartbeatApiPath());
    }

    /**
     * 将表单参数编码为 URL 查询字符串。
     *
     * <p>将键值对集合编码为 {@code application/x-www-form-urlencoded} 格式，
     * 空键或空值会被跳过。
     *
     * @param values 表单参数键值对
     * @return 编码后的表单字符串
     */
    static String encodeForm(Map<String, String> values) {
        StringJoiner encoded = new StringJoiner("&");
        values.forEach((key, value) -> {
            if (key != null && value != null) {
                encoded.add(urlEncode(key) + "=" + urlEncode(value));
            }
        });
        return encoded.toString();
    }

    /**
     * URL 编码字符串值。
     *
     * <p>使用 UTF-8 字符集对字符串进行 URL 编码。
     *
     * @param value 待编码的字符串
     * @return URL 编码后的字符串
     */
    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 直连代理选择器。
     *
     * <p>绕过系统级代理设置，始终返回 {@link Proxy#NO_PROXY}，
     * 确保心跳消息直连 Sentinel Dashboard 控制平面。
     */
    private static final class DirectProxySelector extends ProxySelector {

        /**
         * 选择代理。
         *
         * <p>始终返回无代理配置，强制直连目标主机。
         *
         * @param uri 目标 URI
         * @return 包含 {@link Proxy#NO_PROXY} 的列表
         */
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        /**
         * 连接失败回调。
         *
         * <p>当直连 Dashboard 失败时记录警告日志，便于运维排查网络问题。
         *
         * @param uri     目标 URI
         * @param address 目标地址
         * @param error   连接异常
         */
        @Override
        public void connectFailed(URI uri, java.net.SocketAddress address, java.io.IOException error) {
            RecordLog.warn(
                    "[JdkHttpHeartbeatSender] Direct connection failed, uri=" + uri + ", address=" + address, error);
        }
    }
}
