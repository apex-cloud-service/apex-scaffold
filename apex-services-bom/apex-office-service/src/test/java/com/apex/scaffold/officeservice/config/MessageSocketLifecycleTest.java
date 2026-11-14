package com.apex.scaffold.officeservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.officeservice.interfaces.security.AuthenticatedUser;
import com.apex.scaffold.officeservice.interfaces.security.MessageSocketRegistry;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;

/**
 * 使用真实网络帧验证心跳、帧上限与连接释放，不依赖生产秒级等待。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
class MessageSocketLifecycleTest {
    /** 1024 字节文本帧可继续收取心跳，1025 字节帧被协议层拒绝。 */
    @Test
    void enforcesActualFrameByteBoundary() {
        run(Duration.ofSeconds(10), Duration.ofSeconds(20), uri -> {
            new ReactorNettyWebSocketClient()
                    .execute(
                            uri,
                            session -> Mono.when(
                                    session.send(Mono.just(session.textMessage("界".repeat(341) + "x"))),
                                    session.receive()
                                            .next()
                                            .switchIfEmpty(Mono.error(new AssertionError("1024 字节帧后必须收到心跳")))
                                            .doOnNext(frame -> assertThat(frame.getPayloadAsText())
                                                    .contains("ping"))
                                            .then()))
                    .block(Duration.ofSeconds(5));
            new ReactorNettyWebSocketClient()
                    .execute(uri, session -> session.send(Mono.just(session.textMessage("界".repeat(341) + "xy")))
                            .then(session.receive().then())
                            .then(Mono.defer(session::closeStatus)
                                    .switchIfEmpty(Mono.error(new AssertionError("必须收到关闭状态")))
                                    .doOnNext(status ->
                                            assertThat(status.getCode()).isEqualTo(1009))
                                    .then()))
                    .block(Duration.ofSeconds(5));
        });
    }

    /** 不发送应答时按策略关闭，回送 pong 时跨过超时窗口仍存活。 */
    @Test
    void requiresPongAndExpiresResponsiveConnections() {
        run(Duration.ofMillis(600), Duration.ofSeconds(3), uri -> {
            var pings = new AtomicInteger();
            new ReactorNettyWebSocketClient()
                    .execute(uri, session -> session.receive()
                            .doOnNext(frame -> pings.incrementAndGet())
                            .then()
                            .then(Mono.defer(session::closeStatus)
                                    .switchIfEmpty(Mono.error(new AssertionError("失活连接必须收到关闭状态")))
                                    .doOnNext(status -> assertThat(status).isEqualTo(CloseStatus.POLICY_VIOLATION))
                                    .then()))
                    .block(Duration.ofSeconds(5));
            assertThat(pings.get()).isPositive();
            var replies = new AtomicInteger();
            new ReactorNettyWebSocketClient()
                    .execute(uri, session -> session.send(session.receive().map(frame -> {
                                replies.incrementAndGet();
                                return session.textMessage("pong");
                            }))
                            .then(Mono.defer(session::closeStatus)
                                    .switchIfEmpty(Mono.error(new AssertionError("寿命到期必须收到关闭状态")))
                                    .doOnNext(status -> assertThat(status).isEqualTo(CloseStatus.NORMAL))
                                    .then()))
                    .block(Duration.ofSeconds(5));
            assertThat(replies.get()).isGreaterThan(8);
        });
    }

    /** 在真实连接内集中写满提示队列，验证溢出关闭及连接释放。 */
    @Test
    void closesSocketWhenNotificationQueueOverflows() {
        run(Duration.ofSeconds(10), Duration.ofSeconds(20), (uri, registry) -> new ReactorNettyWebSocketClient()
                .execute(uri, session -> Mono.defer(
                                () -> registry.connectionCount() == 1 ? Mono.just(true) : Mono.empty())
                        .repeatWhenEmpty(repeat -> repeat.delayElements(Duration.ofMillis(10)))
                        .then(Mono.defer(() -> {
                            synchronized (registry) {
                                for (long id = 1; id <= 65; id++) {
                                    registry.publish("a", 7L, id);
                                }
                            }
                            return Mono.when(
                                    session.receive().then(),
                                    session.closeStatus()
                                            .switchIfEmpty(Mono.error(new AssertionError("必须收到关闭状态")))
                                            .doOnNext(status ->
                                                    assertThat(status.getCode()).isEqualTo(1008))
                                            .then());
                        })))
                .block(Duration.ofSeconds(5)));
    }

    /** 在独立 Netty 服务执行场景并检查所有连接最终释放。 */
    private void run(Duration timeout, Duration lifetime, java.util.function.Consumer<URI> scenario) {
        run(timeout, lifetime, (uri, registry) -> scenario.accept(uri));
    }

    /** 装配独立服务并向容量场景提供连接注册表。 */
    private void run(
            Duration timeout, Duration lifetime, java.util.function.BiConsumer<URI, MessageSocketRegistry> scenario) {
        try (var context = new AnnotationConfigApplicationContext()) {
            var registry = new MessageSocketRegistry();
            context.register(WebConfig.class);
            context.registerBean(MessageSocketRegistry.class, () -> registry);
            context.registerBean(
                    MessageWebSocketConfig.class,
                    () -> new MessageWebSocketConfig(Duration.ofMillis(100), timeout, lifetime));
            context.registerBean(
                    WebFilter.class,
                    () -> (exchange, chain) -> chain.filter(exchange.mutate()
                            .principal(Mono.just(new UsernamePasswordAuthenticationToken(
                                    new AuthenticatedUser(7L, null, "a"), null, List.of())))
                            .build()));
            context.refresh();
            var server = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .handle(new ReactorHttpHandlerAdapter(
                            WebHttpHandlerBuilder.applicationContext(context).build()))
                    .bindNow();
            try {
                scenario.accept(URI.create("ws://127.0.0.1:" + server.port() + "/api/v1/messages/ws"), registry);
                Mono.defer(() -> registry.connectionCount() == 0 ? Mono.just(true) : Mono.empty())
                        .repeatWhenEmpty(repeat -> repeat.delayElements(Duration.ofMillis(10)))
                        .block(Duration.ofSeconds(3));
                assertThat(registry.connectionCount()).isZero();
            } finally {
                server.disposeNow();
            }
        }
    }

    /**
     * 仅装配生产 WebFlux 协议处理器，身份过滤器在场景中明确安装。
     * @author Apex Team
     * @version 1.3.1
     * @since 1.3.1
     */
    @Configuration
    @EnableWebFlux
    static class WebConfig {}
}
