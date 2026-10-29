package com.apex.scaffold.fileservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.file.application.service.FileAccessContext;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.test.StepVerifier;

/** 验证独立验签拒绝伪造中继头和失效凭证。 */
class IdentityRelayAuthenticationFilterTest {
    private static final String SECRET = "test-file-signing-key-with-at-least-32-bytes";
    private final IdentityRelayAuthenticationFilter filter = new IdentityRelayAuthenticationFilter("", SECRET);

    /** 捕获过滤器创建的认证主体。 */
    private Authentication capture(MockServerHttpRequest request) {
        AtomicReference<Authentication> auth = new AtomicReference<>();
        StepVerifier.create(filter.filter(
                        MockServerWebExchange.from(request), exchange -> ReactiveSecurityContextHolder.getContext()
                                .doOnNext(c -> auth.set(c.getAuthentication()))
                                .then()))
                .verifyComplete();
        return auth.get();
    }

    /** 创建指定期限及租户的签名凭证。 */
    private String token(String tenant, Instant expiration, String secret) {
        return Jwts.builder()
                .subject("7")
                .claim("userId", 7L)
                .claim("tenantId", tenant)
                .claim("tokenType", "ACCESS")
                .expiration(Date.from(expiration))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    /** 伪造用户与租户请求头不能建立认证。 */
    @Test
    void rejectsIdentityHeaders() {
        assertThat(capture(MockServerHttpRequest.get("/x")
                        .header("X-User-Id", "7")
                        .header("X-Tenant-Id", "42")
                        .build()))
                .isNull();
        assertThat(capture(MockServerHttpRequest.get("/x").build())).isNull();
    }

    /** 验签通过后同时提供业务可信身份。 */
    @Test
    void acceptsSignedIdentity() {
        AtomicReference<FileAccessContext> identity = new AtomicReference<>();
        String jwt = token("42", Instant.now().plusSeconds(60), SECRET);
        StepVerifier.create(filter.filter(
                        MockServerWebExchange.from(MockServerHttpRequest.get("/x")
                                .header("Authorization", "Bearer " + jwt)
                                .build()),
                        exchange -> FileAccessContext.current()
                                .doOnNext(identity::set)
                                .then()))
                .verifyComplete();
        assertThat(identity.get()).isEqualTo(new FileAccessContext("42", 7L));
        assertThat(capture(MockServerHttpRequest.get("/x")
                        .header("Authorization", "Bearer " + jwt)
                        .build()))
                .isNotNull();
    }

    /** 刷新令牌不能用于文件业务认证。 */
    @Test
    void rejectsRefreshToken() {
        String jwt = Jwts.builder()
                .subject("7")
                .claim("userId", 7L)
                .claim("tenantId", "42")
                .claim("tokenType", "REFRESH")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThat(capture(MockServerHttpRequest.get("/x")
                        .header("Authorization", "Bearer " + jwt)
                        .build()))
                .isNull();
    }

    /** 认证中心签发的完整 RS256 ACCESS 声明可以被文件服务消费，其他 RSA 算法不能冒充。 */
    @Test
    void acceptsAuthCenterRs256ContractAndRejectsOtherAlgorithms() {
        var keys = Jwts.SIG.RS512.keyPair().build();
        var rsaFilter = new IdentityRelayAuthenticationFilter(
                java.util.Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()), "");
        for (var algorithm : java.util.List.of(Jwts.SIG.RS256, Jwts.SIG.RS512)) {
            String jwt = Jwts.builder()
                    .header()
                    .keyId("active-key")
                    .and()
                    .subject("7")
                    .id("access-id")
                    .claim("userId", 7L)
                    .claim("tenantId", "42")
                    .claim("tokenType", "ACCESS")
                    .claim("deviceId", "device")
                    .claim("accountId", 8L)
                    .claim("username", "tester")
                    .claim("clientType", "WEB")
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plusSeconds(60)))
                    .signWith(keys.getPrivate(), algorithm)
                    .compact();
            if (algorithm == Jwts.SIG.RS256) {
                assertThat(Jwts.parser()
                                .verifyWith(keys.getPublic())
                                .build()
                                .parseSignedClaims(jwt)
                                .getPayload()
                                .get("tenantId", String.class))
                        .isEqualTo("42");
            }
            AtomicReference<FileAccessContext> identity = new AtomicReference<>();
            StepVerifier.create(rsaFilter.filter(
                            MockServerWebExchange.from(MockServerHttpRequest.get("/x")
                                    .header("Authorization", "Bearer " + jwt)
                                    .build()),
                            exchange -> reactor.core.publisher.Mono.deferContextual(context -> {
                                identity.set(context.getOrDefault(FileAccessContext.KEY, null));
                                return reactor.core.publisher.Mono.empty();
                            })))
                    .verifyComplete();
            if (algorithm == Jwts.SIG.RS256) {
                assertThat(identity.get()).isEqualTo(new FileAccessContext("42", 7L));
            } else {
                assertThat(identity.get()).isNull();
            }
        }
    }

    /** 过期、错误签名及缺失租户均不能建立认证。 */
    @Test
    void rejectsInvalidTokens() {
        for (String jwt : new String[] {
            token("42", Instant.now().minusSeconds(60), SECRET),
            token("42", Instant.now().plusSeconds(60), "different-signing-key-at-least-32-bytes"),
            token(null, Instant.now().plusSeconds(60), SECRET)
        }) {
            assertThat(capture(MockServerHttpRequest.get("/x")
                            .header("Authorization", "Bearer " + jwt)
                            .build()))
                    .isNull();
        }
    }
}
