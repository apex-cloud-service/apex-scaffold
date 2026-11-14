package com.apex.scaffold.officeservice.config;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.officeservice.interfaces.security.AuthenticatedUser;
import com.apex.scaffold.officeservice.interfaces.security.MessageSocketRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springdoc.core.annotations.RouterOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 站内信真实 WebFlux WebSocket 与浏览器握手票据端点。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
public class MessageWebSocketConfig {
    /** 站内信 WebSocket 握手路径，握手鉴权与处理器映射必须引用同一常量。 */
    public static final String MESSAGE_SOCKET_PATH = "/api/v1/messages/ws";

    /** 服务端心跳探测帧，客户端需按协议回送 {@link #PONG_FRAME}。 */
    private static final String PING_FRAME = "{\"type\":\"ping\"}";

    /** 客户端心跳应答帧，用于刷新连接活跃时间。 */
    private static final String PONG_FRAME = "pong";

    private final Duration heartbeatInterval;
    private final Duration pongTimeout;
    private final Duration lifetime;

    /** 使用生产心跳间隔、失活期限与连接寿命创建配置。 */
    public MessageWebSocketConfig() {
        this(Duration.ofSeconds(20), Duration.ofSeconds(60), Duration.ofHours(1));
    }

    /**
     * 使用指定时间参数创建配置，供包内生命周期测试控制时间尺度。
     * @param heartbeatInterval 心跳发送间隔
     * @param pongTimeout 应答失活期限
     * @param lifetime 连接最大寿命
     */
    MessageWebSocketConfig(Duration heartbeatInterval, Duration pongTimeout, Duration lifetime) {
        this.heartbeatInterval = heartbeatInterval;
        this.pongTimeout = pongTimeout;
        this.lifetime = lifetime;
    }

    /**
     * 注册受认证保护的一次性票据签发端点。
     * @param registry 票据与连接表
     * @return 函数式路由
     */
    @Bean
    @RouterOperation(
            path = "/api/v1/messages/ws-ticket",
            method = RequestMethod.POST,
            operation =
                    @io.swagger.v3.oas.annotations.Operation(
                            operationId = "issueMessageSocketTicket",
                            summary = "签发三十秒有效的一次性站内信握手票据"))
    public RouterFunction<ServerResponse> messageTicketRoutes(MessageSocketRegistry registry) {
        return RouterFunctions.route()
                .POST("/api/v1/messages/ws-ticket", request -> request.principal()
                        .cast(Authentication.class)
                        .map(Authentication::getPrincipal)
                        .cast(AuthenticatedUser.class)
                        .flatMap(user -> ServerResponse.ok()
                                .header("Cache-Control", "no-store")
                                .bodyValue(Result.success(Map.of("ticket", registry.issue(user), "expiresIn", 30)))))
                .build();
    }

    /**
     * 优先匹配 WebSocket 路径，避免被消息详情路径捕获。
     * @param registry 连接表
     * @return WebSocket 映射
     */
    @Bean
    public SimpleUrlHandlerMapping messageSocketMapping(MessageSocketRegistry registry) {
        WebSocketHandler handler = session -> session.getHandshakeInfo()
                .getPrincipal()
                .cast(Authentication.class)
                .map(Authentication::getPrincipal)
                .cast(AuthenticatedUser.class)
                .flatMap(user -> Mono.defer(() -> {
                    var connection = registry.connect(user);
                    AtomicLong lastPong = new AtomicLong(System.nanoTime());
                    Flux<String> notifications = Flux.interval(Duration.ofMillis(100))
                            .handle((tick, sink) -> {
                                if (connection.overflow()) {
                                    sink.error(new IllegalStateException("发送队列已满"));
                                } else {
                                    String next = connection.queue().poll();
                                    if (next != null) {
                                        sink.next(next);
                                    }
                                }
                            });
                    Flux<String> heartbeat = Flux.interval(heartbeatInterval).map(tick -> {
                        if (Duration.ofNanos(System.nanoTime() - lastPong.get()).compareTo(pongTimeout) > 0) {
                            throw new IllegalStateException("心跳超时");
                        }
                        return PING_FRAME;
                    });
                    var closeStatus = new java.util.concurrent.atomic.AtomicReference<>(CloseStatus.NORMAL);
                    Mono<Void> outbound = session.send(Flux.merge(notifications, heartbeat)
                                    .onBackpressureBuffer(64)
                                    .takeUntilOther(Mono.delay(lifetime))
                                    .onErrorResume(error -> {
                                        closeStatus.set(CloseStatus.POLICY_VIOLATION);
                                        return Flux.empty();
                                    })
                                    .map(session::textMessage))
                            .then(Mono.defer(() -> session.close(closeStatus.get())));
                    Mono<Void> inbound = session.receive()
                            .doOnNext(message -> {
                                if (PONG_FRAME.equals(message.getPayloadAsText())) {
                                    lastPong.set(System.nanoTime());
                                }
                            })
                            .then();
                    return Mono.firstWithSignal(inbound, outbound)
                            .onErrorResume(error -> session.close(CloseStatus.POLICY_VIOLATION))
                            .then(Mono.defer(() -> session.close(CloseStatus.NORMAL)))
                            .doFinally(signal -> registry.disconnect(connection));
                }));
        return new SimpleUrlHandlerMapping(Map.of(MESSAGE_SOCKET_PATH, handler), -2);
    }

    /**
     * 装配响应式 WebSocket 升级适配器。
     * @return WebSocket 适配器
     */
    @Bean
    public WebSocketHandlerAdapter messageSocketAdapter() {
        var strategy = new org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy(
                () -> reactor.netty.http.server.WebsocketServerSpec.builder().maxFramePayloadLength(1024));
        var adapter = new WebSocketHandlerAdapter(
                new org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService(strategy));
        // 优先于 WebFlux 自带适配器，确保实际握手应用上述帧大小限制。
        adapter.setOrder(1);
        return adapter;
    }
}
