package com.apex.scaffold.officeservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.officeservice.config.MessageWebSocketConfig;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;

/**
 * 使用真实 Netty 服务和 WebSocket 客户端验证升级与票据身份。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
class MessageWebSocketTest {
    private static final String SECRET = "office-test-key-0123456789-abcdef0123456789";

    /** 验证 JWT 签发票据、真实升级、定向通知和重复票据拒绝。 */
    @Test
    void authenticatesActualWebSocketAndRejectsReplay() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var registry = new MessageSocketRegistry();
            context.register(WebConfig.class, MessageWebSocketConfig.class);
            context.registerBean(MessageSocketRegistry.class, () -> registry);
            context.registerBean(
                    IdentityRelayAuthenticationFilter.class,
                    () -> new IdentityRelayAuthenticationFilter("", SECRET, registry, List.of("https://app.example")));
            context.registerBean(SecurityWebFilterChain.class, () -> ServerHttpSecurity.http()
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
                    .build());
            context.refresh();
            var server = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .handle(new ReactorHttpHandlerAdapter(
                            WebHttpHandlerBuilder.applicationContext(context).build()))
                    .bindNow();
            try {
                var http = org.springframework.web.reactive.function.client.WebClient.create(
                        "http://127.0.0.1:" + server.port());
                String jwt = Jwts.builder()
                        .claim("userId", 7)
                        .claim("tenantId", "a")
                        .claim("tokenType", "ACCESS")
                        .expiration(Date.from(Instant.now().plusSeconds(60)))
                        .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                        .compact();
                JsonNode response = http.post()
                        .uri("/api/v1/messages/ws-ticket")
                        .headers(values -> values.setBearerAuth(jwt))
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block(Duration.ofSeconds(5));
                assertThat(response).isNotNull();
                assertThat(response.path("code").asInt()).isZero();
                String ticket = response.path("data").path("ticket").asText();
                assertThat(ticket).isNotBlank();
                assertThat(context.getBean(
                                        "messageSocketAdapter",
                                        org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
                                                .class)
                                .getOrder())
                        .isEqualTo(1);
                URI uri = URI.create("ws://127.0.0.1:" + server.port() + "/api/v1/messages/ws?ticket=" + ticket);
                HttpHeaders headers = new HttpHeaders();
                headers.setOrigin("https://app.example");
                HttpHeaders forbidden = new HttpHeaders();
                forbidden.setOrigin("https://forged.example");
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ReactorNettyWebSocketClient()
                                .execute(uri, forbidden, session -> Mono.empty())
                                .block(Duration.ofSeconds(5)))
                        .isInstanceOf(RuntimeException.class);
                AtomicReference<String> received = new AtomicReference<>();
                new ReactorNettyWebSocketClient()
                        .execute(uri, headers, session -> {
                            // 等待服务器登记连接后投递，避免握手回调与处理器订阅的竞态。
                            Mono<Void> publish = Mono.delay(Duration.ofMillis(200))
                                    .doOnNext(ignored -> {
                                        registry.publish("b", 7L, 11L);
                                        registry.publish("a", 8L, 12L);
                                        registry.publish("a", 7L, 99L);
                                    })
                                    .then();
                            return Mono.when(
                                    publish,
                                    session.receive()
                                            .next()
                                            .doOnNext(message -> received.set(message.getPayloadAsText()))
                                            .then());
                        })
                        .block(Duration.ofSeconds(10));
                assertThat(received.get()).contains("99").doesNotContain("11", "12");
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ReactorNettyWebSocketClient()
                                .execute(uri, headers, session -> Mono.empty())
                                .block(Duration.ofSeconds(5)))
                        .isInstanceOf(RuntimeException.class);
            } finally {
                server.disposeNow();
            }
        }
    }

    /**
     * 最小 WebFlux 服务器配置。
     * @author Apex Team
     * @version 1.3.1
     * @since 1.3.1
     */
    @org.springframework.context.annotation.Configuration
    @EnableWebFlux
    @org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
    static class WebConfig {}
}
