package com.apex.scaffold.workflowservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.workflowservice.config.WorkflowSecurityConfig;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
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

/** 工作流服务真实 JWT 验签与安全链测试。 */
class IdentityRelayAuthenticationFilterTest {
    private static final String SECRET = "workflow-test-signing-key-at-least-32-bytes";
    private static final KeyPair RSA = Jwts.SIG.RS512.keyPair().build();
    private static final KeyPair OTHER_RSA = Jwts.SIG.RS256.keyPair().build();

    private IdentityRelayAuthenticationFilter configured(String publicKey, String secret, boolean legacy) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource(
                            "jwt-test",
                            Map.of(
                                    "workflow.security.jwt-public-key-base64", publicKey,
                                    "workflow.security.jwt-secret", secret,
                                    "workflow.security.jwt-hmac-enabled", legacy)));
            context.register(IdentityRelayAuthenticationFilter.class);
            context.refresh();
            return context.getBean(IdentityRelayAuthenticationFilter.class);
        }
    }

    private IdentityRelayAuthenticationFilter rsaFilter() {
        return configured(Base64.getEncoder().encodeToString(RSA.getPublic().getEncoded()), SECRET, true);
    }

    private JwtBuilder claims(Object user, Object tenant, String type, Instant expiration) {
        return Jwts.builder()
                .subject("7")
                .claim("userId", user)
                .claim("tenantId", tenant)
                .claim("tokenType", type)
                .claim("clientType", "PC_ADMIN")
                .expiration(expiration == null ? null : Date.from(expiration));
    }

    private String accessToken() {
        return claims(7L, "42", "ACCESS", Instant.now().plusSeconds(300))
                .signWith(RSA.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String hmacToken() {
        return claims(7L, "42", "ACCESS", Instant.now().plusSeconds(300))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private CapturedContexts capture(IdentityRelayAuthenticationFilter filter, MockServerHttpRequest request) {
        AtomicReference<Authentication> authentication = new AtomicReference<>();
        AtomicReference<String> tenant = new AtomicReference<>();
        AtomicReference<String> threadTenant = new AtomicReference<>();
        StepVerifier.create(filter.filter(
                        MockServerWebExchange.from(request),
                        exchange -> Mono.deferContextual(ctx -> {
                            tenant.set(ctx.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null));
                            threadTenant.set(TenantContext.currentTenantId().orElse(null));
                            return ReactiveSecurityContextHolder.getContext()
                                    .doOnNext(context -> authentication.set(context.getAuthentication()))
                                    .then();
                        })))
                .verifyComplete();
        return new CapturedContexts(authentication.get(), tenant.get(), threadTenant.get());
    }

    private MockServerHttpRequest request(String token) {
        return MockServerHttpRequest.get("/api/v1/workflow/probe")
                .header("Authorization", "Bearer " + token)
                .header("X-User-Id", "999")
                .header("X-Tenant-Id", "forged")
                .header("X-Client-Type", "forged")
                .build();
    }

    @Test
    void acceptsRs256AndBridgesOnlySignedIdentity() {
        var captured = capture(rsaFilter(), request(accessToken()));
        assertThat(captured.authentication()).isNotNull();
        assertThat(captured.authentication().getPrincipal()).isEqualTo(new AuthenticatedUser(7L, null, "42"));
        assertThat(captured.authentication().getDetails()).isEqualTo("PC_ADMIN");
        assertThat(captured.tenant()).isEqualTo("42");
        assertThat(captured.threadTenant()).isEqualTo("42");
        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void rejectsForgedHeadersAndMissingBearer() {
        for (var request : new MockServerHttpRequest[] {
            MockServerHttpRequest.get("/x").build(),
            MockServerHttpRequest.get("/x")
                    .header("X-User-Id", "7")
                    .header("X-Tenant-Id", "42")
                    .build(),
            MockServerHttpRequest.get("/x")
                    .header("Authorization", accessToken())
                    .build()
        }) {
            assertThat(capture(rsaFilter(), request)).isEqualTo(new CapturedContexts(null, null, null));
        }
    }

    @Test
    void rejectsInvalidSignatureAndAlgorithms() {
        for (String token : new String[] {
            "malformed",
            claims(7L, "42", "ACCESS", Instant.now().plusSeconds(300))
                    .signWith(OTHER_RSA.getPrivate(), Jwts.SIG.RS256)
                    .compact(),
            claims(7L, "42", "ACCESS", Instant.now().plusSeconds(300))
                    .signWith(RSA.getPrivate(), Jwts.SIG.RS512)
                    .compact(),
            claims(7L, "42", "ACCESS", Instant.now().plusSeconds(300)).compact(),
            hmacToken()
        }) {
            assertThat(capture(rsaFilter(), request(token)).authentication()).isNull();
        }
    }

    @Test
    void requiresExpirationAndAccessType() {
        for (String type : new String[] {"ACCESS", "REFRESH", "access", null}) {
            Instant expiration = "ACCESS".equals(type) ? null : Instant.now().plusSeconds(300);
            String token = claims(7L, "42", type, expiration)
                    .signWith(RSA.getPrivate(), Jwts.SIG.RS256)
                    .compact();
            assertThat(capture(rsaFilter(), request(token)).authentication()).isNull();
        }
        String expired = claims(7L, "42", "ACCESS", Instant.now().minusSeconds(60))
                .signWith(RSA.getPrivate(), Jwts.SIG.RS256)
                .compact();
        assertThat(capture(rsaFilter(), request(expired)).authentication()).isNull();
    }

    @Test
    void requiresPositiveUserAndStringTenant() {
        for (Object user : new Object[] {null, 0L, -1L, "abc", 1.5, "9223372036854775808"}) {
            String token = claims(user, "42", "ACCESS", Instant.now().plusSeconds(300))
                    .signWith(RSA.getPrivate(), Jwts.SIG.RS256)
                    .compact();
            assertThat(capture(rsaFilter(), request(token)).authentication()).isNull();
        }
        for (Object tenant : new Object[] {null, "", "  ", 42L}) {
            String token = claims(7L, tenant, "ACCESS", Instant.now().plusSeconds(300))
                    .signWith(RSA.getPrivate(), Jwts.SIG.RS256)
                    .compact();
            assertThat(capture(rsaFilter(), request(token)).authentication()).isNull();
        }
    }

    @Test
    void missingKeyNeverAuthenticates() {
        assertThat(capture(configured("", "", false), request(accessToken())).authentication())
                .isNull();
        assertThat(capture(configured("", "", true), request(hmacToken())).authentication())
                .isNull();
    }

    @Test
    void hmacRequiresExplicitOptInAndNeverOverridesRsa() {
        assertThat(capture(configured("", SECRET, false), request(hmacToken())).authentication())
                .isNull();
        assertThat(capture(configured("", SECRET, true), request(hmacToken())).authentication())
                .isNotNull();
        assertThat(capture(rsaFilter(), request(hmacToken())).authentication()).isNull();
        assertThatThrownBy(() -> configured("invalid-rsa", SECRET, true)).isInstanceOf(RuntimeException.class);
    }

    private WebTestClient client() {
        var security = new WorkflowSecurityConfig().securityWebFilterChain(ServerHttpSecurity.http());
        return WebTestClient.bindToWebHandler(exchange -> {
                    exchange.getResponse().setStatusCode(HttpStatus.OK);
                    return exchange.getResponse().setComplete();
                })
                .webFilter(rsaFilter(), new WebFilterChainProxy(security))
                .build();
    }

    @Test
    void actualSecurityChainAcceptsSignedToken() {
        client().get()
                .uri("/api/v1/workflow/probe")
                .headers(headers -> headers.addAll(request(accessToken()).getHeaders()))
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void actualSecurityChainRejectsForgedHeaders() {
        client().get()
                .uri("/api/v1/workflow/probe")
                .header("X-User-Id", "7")
                .header("X-Tenant-Id", "42")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void actualSecurityChainRejectsInvalidBearerEvenWithHeaders() {
        client().get()
                .uri("/api/v1/workflow/probe")
                .headers(headers -> headers.addAll(request("invalid").getHeaders()))
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    private WebTestClient internalClient() {
        var security = new WorkflowSecurityConfig().securityWebFilterChain(ServerHttpSecurity.http());
        return WebTestClient.bindToRouterFunction(
                        org.springframework.web.reactive.function.server.RouterFunctions.route(
                                org.springframework.web.reactive.function.server.RequestPredicates.GET("/probe"),
                                request -> org.springframework.web.reactive.function.server.ServerResponse.ok()
                                        .build()))
                .webFilter(rsaFilter(), new WebFilterChainProxy(security))
                .build();
    }

    @Test
    void internalRouteRejectsAnonymous() {
        internalClient()
                .get()
                .uri("/internal/v1/instances/1/status")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void internalRouteRejectsForgedHeaders() {
        internalClient()
                .get()
                .uri("/internal/v1/instances/1/status")
                .header("X-User-Id", "7")
                .header("X-Tenant-Id", "42")
                .header("X-Client-Type", "PC_ADMIN")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void internalRouteRejectsInvalidBearer() {
        internalClient()
                .get()
                .uri("/internal/v1/instances/1/status")
                .headers(headers -> headers.addAll(request("invalid").getHeaders()))
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void signedTokenReachesUnimplementedInternalRoute() {
        internalClient()
                .get()
                .uri("/internal/v1/instances/1/status")
                .headers(headers -> headers.addAll(request(accessToken()).getHeaders()))
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void actualSecurityChainKeepsHealthAndDocumentationAnonymous() {
        for (String path : new String[] {
            "/actuator/health", "/v3/api-docs", "/swagger-ui.html", "/swagger-ui/index.html", "/webjars/probe.js"
        }) {
            client().get().uri(path).exchange().expectStatus().isOk();
        }
    }

    private record CapturedContexts(Authentication authentication, String tenant, String threadTenant) {}
}
