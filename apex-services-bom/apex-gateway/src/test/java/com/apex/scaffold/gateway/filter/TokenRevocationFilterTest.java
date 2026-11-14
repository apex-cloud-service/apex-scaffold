package com.apex.scaffold.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.gateway.security.RevocationCacheService;
import com.apex.scaffold.gateway.security.RevocationProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * {@link TokenRevocationFilter} 单元测试（统一认证收口 A3）。
 *
 * <p>覆盖：撤销命中 401、未撤销放行、未启用放行、无 Claims 属性放行。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("TokenRevocationFilter 网关撤销过滤器测试")
class TokenRevocationFilterTest {

    private RevocationCacheService enabledService() {
        RevocationProperties properties = new RevocationProperties();
        properties.setEnabled(true);
        properties.setUrl("http://auth.internal/internal/v1/tokens/revocations");
        RevocationCacheService service = new RevocationCacheService(properties);
        service.consume(
                "{\"code\":0,\"data\":{\"revocations\":[{\"jti\":\"revoked-jti\",\"revokedAt\":1}],\"cursor\":1}}");
        return service;
    }

    private String tokenWithJti(String jti) {
        return Jwts.builder()
                .header()
                .keyId("kid")
                .and()
                .id(jti)
                .subject("1")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(
                        "gateway-revocation-test-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private MockServerWebExchange exchangeWithClaims(String jti) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/user/profile").build());
        exchange.getAttributes()
                .put(
                        JwtSignatureFilter.ATTR_CLAIMS,
                        Jwts.claims().subject("1").id(jti).build());
        return exchange;
    }

    @Test
    @DisplayName("命中撤销缓存收敛为 401 且不进入后续链")
    void rejectsRevokedJti() {
        RevocationCacheService service = enabledService();
        TokenRevocationFilter filter = new TokenRevocationFilter(service);
        AtomicBoolean chained = new AtomicBoolean(false);
        MockServerWebExchange exchange = exchangeWithClaims("revoked-jti");

        filter.filter(exchange, e -> {
                    chained.set(true);
                    return Mono.empty();
                })
                .block();

        assertThat(chained.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("未撤销 jti 正常放行")
    void passesNonRevokedJti() {
        RevocationCacheService service = enabledService();
        TokenRevocationFilter filter = new TokenRevocationFilter(service);
        AtomicBoolean chained = new AtomicBoolean(false);
        MockServerWebExchange exchange = exchangeWithClaims("live-jti");

        filter.filter(exchange, e -> {
                    chained.set(true);
                    return Mono.empty();
                })
                .block();

        assertThat(chained.get()).isTrue();
    }

    @Test
    @DisplayName("撤销流未启用或无 Claims 属性时放行")
    void passesWhenDisabledOrNoClaims() {
        TokenRevocationFilter filter =
                new TokenRevocationFilter(new RevocationCacheService(new RevocationProperties()));
        AtomicBoolean chained = new AtomicBoolean(false);
        MockServerWebExchange disabled =
                MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());
        filter.filter(disabled, e -> {
                    chained.set(true);
                    return Mono.empty();
                })
                .block();
        assertThat(chained.get()).isTrue();

        AtomicBoolean chained2 = new AtomicBoolean(false);
        MockServerWebExchange noClaims =
                MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());
        new TokenRevocationFilter(enabledService())
                .filter(noClaims, e -> {
                    chained2.set(true);
                    return Mono.empty();
                })
                .block();
        assertThat(chained2.get()).isTrue();
        assertThat(((ServerWebExchange) noClaims).getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
