package com.apex.scaffold.aggregationservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.context.TenantContext;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 聚合服务认证过滤器单元测试。
 *
 * <p>验证统一待办端点所依赖的三种边界：伪造身份头不建立认证、合法 HMAC ACCESS 凭证建立
 * 认证并传播可信租户上下文（供下游内部签名使用）、刷新令牌与非 ACCESS 类型被拒绝；
 * 以及 RS256 公钥配置的验签路径与非法密钥配置的启动期拒绝。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("聚合服务认证过滤器测试")
class IdentityRelayAuthenticationFilterTest {

    private static final String SECRET = "test-aggregation-signing-key-at-least-32-bytes";

    private final IdentityRelayAuthenticationFilter filter = IdentityRelayAuthenticationFilter.forTesting("", SECRET);

    /** 捕获过滤器构建的认证主体。 */
    private Authentication capture(MockServerHttpRequest request) {
        AtomicReference<Authentication> auth = new AtomicReference<>();
        StepVerifier.create(filter.filter(
                        MockServerWebExchange.from(request), exchange -> ReactiveSecurityContextHolder.getContext()
                                .doOnNext(context -> auth.set(context.getAuthentication()))
                                .then()))
                .verifyComplete();
        return auth.get();
    }

    /** 签发指定类型与租户的 HMAC 凭证。 */
    private String token(String tenant, String tokenType, Instant expiration, String secret) {
        return Jwts.builder()
                .subject("7")
                .claim("userId", 7L)
                .claim("tenantId", tenant)
                .claim("tokenType", tokenType)
                .expiration(Date.from(expiration))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    @DisplayName("伪造 X-User-Id/X-Tenant-Id 请求头与无凭证请求不建立认证")
    void rejectsIdentityHeadersAndAnonymous() {
        assertThat(capture(MockServerHttpRequest.get("/api/v2/todo")
                        .header("X-User-Id", "7")
                        .header("X-Tenant-Id", "42")
                        .build()))
                .isNull();
        assertThat(capture(MockServerHttpRequest.get("/api/v2/todo").build())).isNull();
    }

    @Test
    @DisplayName("合法 HMAC ACCESS 凭证建立认证并传播可信租户上下文")
    void acceptsSignedAccessTokenAndPropagatesTenant() {
        String jwt = token("42", "ACCESS", Instant.now().plusSeconds(60), SECRET);
        AtomicReference<Authentication> auth = new AtomicReference<>();
        AtomicReference<String> tenant = new AtomicReference<>();

        StepVerifier.create(filter.filter(
                        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v2/todo")
                                .header("Authorization", "Bearer " + jwt)
                                .build()),
                        exchange -> Mono.deferContextual(context -> {
                            tenant.set(context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null));
                            return ReactiveSecurityContextHolder.getContext()
                                    .doOnNext(context2 -> auth.set(context2.getAuthentication()))
                                    .then();
                        })))
                .verifyComplete();

        assertThat(auth.get()).isNotNull();
        assertThat(auth.get().getPrincipal()).isInstanceOf(AuthenticatedUser.class);
        AuthenticatedUser user = (AuthenticatedUser) auth.get().getPrincipal();
        assertThat(user.userId()).isEqualTo(7L);
        assertThat(user.tenantId()).isEqualTo("42");
        assertThat(user.getName()).isEqualTo("7");
        // 可信租户进入 Reactor Context：InternalAuthExchangeFilter 据此生成 X-Internal-Tenant
        assertThat(tenant.get()).isEqualTo("42");
    }

    @Test
    @DisplayName("刷新令牌与非法声明被拒绝")
    void rejectsRefreshTokenAndInvalidClaims() {
        assertThat(capture(MockServerHttpRequest.get("/api/v2/todo")
                        .header(
                                "Authorization",
                                "Bearer " + token("42", "REFRESH", Instant.now().plusSeconds(60), SECRET))
                        .build()))
                .isNull();
        // 过期凭证
        assertThat(capture(MockServerHttpRequest.get("/api/v2/todo")
                        .header(
                                "Authorization",
                                "Bearer " + token("42", "ACCESS", Instant.now().minusSeconds(60), SECRET))
                        .build()))
                .isNull();
        // 缺租户声明
        String noTenant = Jwts.builder()
                .claim("userId", 7L)
                .claim("tokenType", "ACCESS")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThat(capture(MockServerHttpRequest.get("/api/v2/todo")
                        .header("Authorization", "Bearer " + noTenant)
                        .build()))
                .isNull();
    }

    @Test
    @DisplayName("RS256 公钥配置可验签；错误密钥签发的凭证被拒绝")
    void acceptsRs256WhenPublicKeyConfigured() {
        var keys = Jwts.SIG.RS256.keyPair().build();
        String publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        IdentityRelayAuthenticationFilter rsaFilter = IdentityRelayAuthenticationFilter.forTesting(publicKey, "");

        String valid = Jwts.builder()
                .header()
                .keyId("active-key")
                .and()
                .subject("7")
                .claim("userId", 7L)
                .claim("tenantId", "42")
                .claim("tokenType", "ACCESS")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(keys.getPrivate(), Jwts.SIG.RS256)
                .compact();
        String wrongAlg = Jwts.builder()
                .header()
                .keyId("active-key")
                .and()
                .claim("userId", 7L)
                .claim("tenantId", "42")
                .claim("tokenType", "ACCESS")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(keys.getPrivate(), Jwts.SIG.RS512)
                .compact();

        AtomicReference<Authentication> auth = new AtomicReference<>();
        StepVerifier.create(rsaFilter.filter(
                        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v2/todo")
                                .header("Authorization", "Bearer " + valid)
                                .build()),
                        exchange -> ReactiveSecurityContextHolder.getContext()
                                .doOnNext(context -> auth.set(context.getAuthentication()))
                                .then()))
                .verifyComplete();
        assertThat(auth.get()).isNotNull();

        AtomicReference<Authentication> wrongAlgAuth = new AtomicReference<>();
        StepVerifier.create(rsaFilter.filter(
                        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v2/todo")
                                .header("Authorization", "Bearer " + wrongAlg)
                                .build()),
                        exchange -> ReactiveSecurityContextHolder.getContext()
                                .doOnNext(context -> wrongAlgAuth.set(context.getAuthentication()))
                                .then()))
                .verifyComplete();
        assertThat(wrongAlgAuth.get()).isNull();
    }

    @Test
    @DisplayName("非法公钥配置在构造期即拒绝（fail-closed）")
    void rejectsInvalidPublicKeyConfiguration() {
        assertThatThrownBy(() -> IdentityRelayAuthenticationFilter.forTesting("not-a-valid-base64-key", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("聚合服务 JWT 公钥配置无效");
    }
}
