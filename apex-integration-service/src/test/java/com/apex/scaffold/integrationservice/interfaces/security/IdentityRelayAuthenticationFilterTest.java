package com.apex.scaffold.integrationservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.config.JwksFetchProperties;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.security.JwksFetcher;
import com.apex.scaffold.integrationservice.config.IntegrationSecurityConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class IdentityRelayAuthenticationFilterTest {
    private static final KeyPair RSA = Jwts.SIG.RS256.keyPair().build();
    private IdentityRelayAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new IdentityRelayAuthenticationFilter(
                java.util.Base64.getEncoder().encodeToString(RSA.getPublic().getEncoded()), "");
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void acceptsValidAccessTokenAndBridgesSignedIdentity() {
        AtomicReference<Authentication> authentication = new AtomicReference<>();
        AtomicReference<String> reactorTenant = new AtomicReference<>();
        AtomicReference<String> threadTenant = new AtomicReference<>();

        StepVerifier.create(filter.filter(
                        exchange(validToken()),
                        ignored -> Mono.deferContextual(context -> {
                            reactorTenant.set(context.get(TenantContext.REACTOR_CONTEXT_KEY));
                            threadTenant.set(TenantContext.currentTenantId().orElse(null));
                            return ReactiveSecurityContextHolder.getContext()
                                    .doOnNext(
                                            securityContext -> authentication.set(securityContext.getAuthentication()))
                                    .then();
                        })))
                .verifyComplete();

        assertThat(authentication.get().getPrincipal()).isEqualTo(new AuthenticatedUser(7L, null, "tenant-42"));
        assertThat(reactorTenant).hasValue("tenant-42");
        assertThat(threadTenant).hasValue("tenant-42");
        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void rejectsExpiredAndMissingExpirationTokens() {
        assertThat(capture(
                        filter,
                        exchange(token(RSA, "tenant-42", "ACCESS", Instant.now().minusSeconds(1), 7L))))
                .isNull();
        assertThat(capture(filter, exchange(tokenWithoutExpiration(RSA, "tenant-42", "ACCESS", 7L))))
                .isNull();
    }

    @Test
    void rejectsInvalidAccessClaimsAndUnsupportedTokenType() {
        assertThat(capture(
                        filter,
                        exchange(
                                token(RSA, "tenant-42", "REFRESH", Instant.now().plusSeconds(300), 7L))))
                .isNull();
        assertThat(capture(
                        filter,
                        exchange(token(RSA, "tenant-42", "ACCESS", Instant.now().plusSeconds(300), 0L))))
                .isNull();
        assertThat(capture(
                        filter,
                        exchange(token(RSA, "   ", "ACCESS", Instant.now().plusSeconds(300), 7L))))
                .isNull();
        assertThat(capture(
                        filter,
                        exchange(token(RSA, "tenant-42", "ACCESS", Instant.now().plusSeconds(300), "not-a-number"))))
                .isNull();
    }

    @Test
    void acceptsConfiguredHmacAlgorithmsButDoesNotUseRsaKeyForHmac() {
        String secret = "integration-test-signing-key-at-least-32-bytes";
        var hmacFilter = new IdentityRelayAuthenticationFilter("", secret);
        String token = Jwts.builder()
                .claim("userId", 7L)
                .claim("tenantId", "tenant-42")
                .claim("tokenType", "ACCESS")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThat(capture(hmacFilter, exchange(token))).isNotNull();
        assertThat(capture(filter, exchange(token))).isNull();
    }

    @Test
    void selectsConfiguredKeyByKidAndRejectsUnknownKid() {
        KeyPair first = Jwts.SIG.RS256.keyPair().build();
        KeyPair second = Jwts.SIG.RS256.keyPair().build();
        String configuration = "first=" + encoded(first) + ",second=" + encoded(second);
        var multiKeyFilter = new IdentityRelayAuthenticationFilter(configuration, "");

        String known = tokenWithKid(second, "second");
        String unknown = tokenWithKid(second, "missing");

        assertThat(capture(multiKeyFilter, exchange(known))).isNotNull();
        assertThat(capture(multiKeyFilter, exchange(unknown))).isNull();
    }

    @Test
    void jwksSnapshotOverridesStaticKeyForSameKid() {
        KeyPair staticKey = Jwts.SIG.RS256.keyPair().build();
        KeyPair rotatedKey = Jwts.SIG.RS256.keyPair().build();
        var fetcher = new JwksFetcher(new JwksFetchProperties()) {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public Map<String, java.security.PublicKey> snapshot() {
                return Map.of("shared", rotatedKey.getPublic());
            }
        };
        ObjectProvider<JwksFetcher> provider = new ObjectProvider<>() {
            @Override
            public JwksFetcher getObject() {
                return fetcher;
            }

            @Override
            public JwksFetcher getIfAvailable() {
                return fetcher;
            }
        };
        var mergedFilter = new IdentityRelayAuthenticationFilter("shared=" + encoded(staticKey), "", "", provider);

        assertThat(capture(mergedFilter, exchange(tokenWithKid(rotatedKey, "shared"))))
                .isNotNull();
    }

    @Test
    void rejectsMalformedBearerAndTamperedSignature() {
        assertThat(capture(filter, exchange("not-a-jwt"))).isNull();
        String valid = validToken();
        // 篡改签名段的中间字符（而非末位）：RS256 签名段 Base64URL 长度为 342（342 % 4 = 2），
        // 末位字符只有低位有效，改写末位可能解码出完全相同的字节、签名仍然有效——
        // 这是 Base64 编码特性，不是验签缺陷。中间字符改动必定改变解码字节。
        int signatureStart = valid.lastIndexOf('.') + 1;
        int tamperIndex = signatureStart + (valid.length() - signatureStart) / 2;
        char original = valid.charAt(tamperIndex);
        char replacement = original == 'A' ? 'B' : 'A';
        String tampered = valid.substring(0, tamperIndex) + replacement + valid.substring(tamperIndex + 1);
        assertThat(capture(filter, exchange(tampered))).isNull();

        // 补充：篡改声明段（payload）也必须被拒
        int payloadStart = valid.indexOf('.') + 1;
        int payloadTamper = payloadStart + 1;
        char payloadOriginal = valid.charAt(payloadTamper);
        String tamperedPayload = valid.substring(0, payloadTamper)
                + (payloadOriginal == 'A' ? 'B' : 'A')
                + valid.substring(payloadTamper + 1);
        assertThat(capture(filter, exchange(tamperedPayload))).isNull();
    }

    @Test
    void rejectsInvalidKeyConfiguration() {
        assertThatThrownBy(() -> new IdentityRelayAuthenticationFilter("missing-equals", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdentityRelayAuthenticationFilter("kid=invalid", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ignoresForgedHeadersWithoutBearerToken() {
        AtomicReference<Authentication> authentication = new AtomicReference<>();

        StepVerifier.create(filter.filter(
                        MockServerWebExchange.from(MockServerHttpRequest.get("/probe")
                                .header(IdentityRelayAuthenticationFilter.HEADER_USER_ID, "999")
                                .header(IdentityRelayAuthenticationFilter.HEADER_TENANT_ID, "forged")
                                .header(IdentityRelayAuthenticationFilter.HEADER_CLIENT_TYPE, "forged")
                                .build()),
                        ignored -> ReactiveSecurityContextHolder.getContext()
                                .doOnNext(context -> authentication.set(context.getAuthentication()))
                                .then()))
                .verifyComplete();

        assertThat(authentication).hasValue(null);
        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void securityChainRejectsAnonymousAndAcceptsVerifiedJwt() {
        WebTestClient client = securityChainClient();

        client.get()
                .uri("/api/v1/integration/connectors")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        client.get()
                .uri("/api/v1/integration/connectors")
                .header(IdentityRelayAuthenticationFilter.HEADER_USER_ID, "999")
                .header(IdentityRelayAuthenticationFilter.HEADER_TENANT_ID, "forged")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        client.get()
                .uri("/api/v1/integration/connectors")
                .headers(headers -> headers.setBearerAuth("not-a-jwt"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        client.get()
                .uri("/api/v1/integration/connectors")
                .headers(headers -> headers.setBearerAuth(validToken()))
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void securityChainAllowsDocumentedAnonymousPaths() {
        WebTestClient client = securityChainClient();

        for (String path : new String[] {
            "/actuator/health",
            "/v3/api-docs",
            "/swagger-ui.html",
            "/swagger-ui/index.html",
            "/webjars/swagger-ui/index.js",
            "/internal/v1/connectors/reference",
            "/integration/v1/webhook/connector-a"
        }) {
            client.get().uri(path).exchange().expectStatus().isOk();
        }
    }

    /**
     * 用真实 {@link IntegrationSecurityConfig} 与认证过滤器组合出完整 Spring Security 链，
     * 避免只验证单独过滤器而遗漏 401、匿名白名单与有效 JWT 的组合行为。
     */
    private WebTestClient securityChainClient() {
        var security = new IntegrationSecurityConfig().securityWebFilterChain(ServerHttpSecurity.http());
        return WebTestClient.bindToWebHandler(exchange -> {
                    exchange.getResponse().setStatusCode(HttpStatus.OK);
                    return exchange.getResponse().setComplete();
                })
                .webFilter(filter, new WebFilterChainProxy(security))
                .build();
    }

    private Authentication capture(IdentityRelayAuthenticationFilter target, MockServerWebExchange exchange) {
        AtomicReference<Authentication> authentication = new AtomicReference<>();
        StepVerifier.create(target.filter(exchange, ignored -> ReactiveSecurityContextHolder.getContext()
                        .doOnNext(context -> authentication.set(context.getAuthentication()))
                        .then()))
                .verifyComplete();
        return authentication.get();
    }

    private String token(KeyPair keyPair, String tenantId, String tokenType, Instant expiration, Object userId) {
        return Jwts.builder()
                .claim("userId", userId)
                .claim("tenantId", tenantId)
                .claim("tokenType", tokenType)
                .expiration(Date.from(expiration))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String tokenWithoutExpiration(KeyPair keyPair, String tenantId, String tokenType, Object userId) {
        return Jwts.builder()
                .claim("userId", userId)
                .claim("tenantId", tenantId)
                .claim("tokenType", tokenType)
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String tokenWithKid(KeyPair keyPair, String kid) {
        return Jwts.builder()
                .claim("userId", 7L)
                .claim("tenantId", "tenant-42")
                .claim("tokenType", "ACCESS")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .header()
                .keyId(kid)
                .and()
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String encoded(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    private String validToken() {
        return Jwts.builder()
                .claim("userId", 7L)
                .claim("tenantId", "tenant-42")
                .claim("tokenType", "ACCESS")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(RSA.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private MockServerWebExchange exchange(String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/probe")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(IdentityRelayAuthenticationFilter.HEADER_USER_ID, "999")
                .header(IdentityRelayAuthenticationFilter.HEADER_TENANT_ID, "forged")
                .build());
    }
}
