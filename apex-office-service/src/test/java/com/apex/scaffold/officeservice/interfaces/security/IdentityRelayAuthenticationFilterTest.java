package com.apex.scaffold.officeservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

/**
 * 服务端身份验签与伪造中继头拒绝测试。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
class IdentityRelayAuthenticationFilterTest {
    private static final String SECRET = "office-test-key-0123456789-abcdef0123456789";
    private final IdentityRelayAuthenticationFilter filter = new IdentityRelayAuthenticationFilter(
            "", SECRET, new MessageSocketRegistry(), List.of("https://app.example"));

    /** 伪造网关身份头不能创建认证。 */
    @Test
    void rejectsForgedIdentityHeaders() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/messages/inbox")
                .header("X-User-Id", "7")
                .header("X-Tenant-Id", "other"));
        AtomicReference<Object> seen = new AtomicReference<>();
        filter.filter(exchange, ignored -> ReactiveSecurityContextHolder.getContext()
                        .doOnNext(context -> seen.set(context.getAuthentication()))
                        .then())
                .block();
        assertThat(seen.get()).isNull();
    }

    /** 有效访问 JWT 的签名声明优先于任意伪造头。 */
    @Test
    void acceptsVerifiedAccessClaimsOnly() {
        String jwt = Jwts.builder()
                .claim("userId", 7)
                .claim("tenantId", "trusted")
                .claim("tokenType", "ACCESS")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/messages/inbox")
                .header("Authorization", "Bearer " + jwt)
                .header("X-User-Id", "99")
                .header("X-Tenant-Id", "forged"));
        AtomicReference<AuthenticatedUser> seen = new AtomicReference<>();
        filter.filter(exchange, ignored -> ReactiveSecurityContextHolder.getContext()
                        .doOnNext(context -> seen.set(
                                (AuthenticatedUser) context.getAuthentication().getPrincipal()))
                        .then())
                .block();
        assertThat(seen.get()).isEqualTo(new AuthenticatedUser(7L, null, "trusted"));
    }

    /** 非 GET 升级请求必须在消费票据前拒绝。 */
    @Test
    void rejectsNonGetHandshakeWithoutConsumingTicket() {
        var registry = new MessageSocketRegistry();
        var identity = new AuthenticatedUser(7L, null, "a");
        String ticket = registry.issue(identity);
        var handshakeFilter =
                new IdentityRelayAuthenticationFilter("", SECRET, registry, List.of("https://app.example"));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/messages/ws?ticket=" + ticket)
                .header("Origin", "https://app.example")
                .header("Upgrade", "websocket"));
        handshakeFilter
                .filter(exchange, ignored -> {
                    throw new AssertionError("不应进入后续过滤链");
                })
                .block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        assertThat(registry.consume(ticket)).isEqualTo(identity);
    }

    /** 正确签名的刷新令牌或过期访问令牌仍不能建立业务身份。 */
    @Test
    void rejectsRefreshAndExpiredTokens() {
        for (String type : List.of("REFRESH", "ACCESS")) {
            Instant expiration = "ACCESS".equals(type)
                    ? Instant.now().minusSeconds(60)
                    : Instant.now().plusSeconds(60);
            String jwt = Jwts.builder()
                    .claim("userId", 7)
                    .claim("tenantId", "a")
                    .claim("tokenType", type)
                    .expiration(Date.from(expiration))
                    .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .compact();
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/messages/inbox").header("Authorization", "Bearer " + jwt));
            AtomicReference<Object> seen = new AtomicReference<>();
            filter.filter(exchange, ignored -> ReactiveSecurityContextHolder.getContext()
                            .doOnNext(context -> seen.set(context.getAuthentication()))
                            .then())
                    .block();
            assertThat(seen.get()).isNull();
        }
    }
}
