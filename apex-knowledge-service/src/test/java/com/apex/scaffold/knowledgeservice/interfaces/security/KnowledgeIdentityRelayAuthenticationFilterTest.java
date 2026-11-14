package com.apex.scaffold.knowledgeservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.context.TenantContext;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 知识库独立验签过滤器单元测试：RS256 多键/kid 选择、HMAC 与刷新令牌拒绝。
 *
 * <p>断言口径：链内可见的 Spring Security 上下文是否建立（未验签成功时下游无身份）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
class KnowledgeIdentityRelayAuthenticationFilterTest {

    /** 与测试 application.yml 一致的 HMAC 过渡密钥。 */
    private static final String HMAC_SECRET = "test-knowledge-hmac-secret-32-bytes!!";

    /** 链内最近一次可见主体（null 表示无身份）。 */
    private static volatile Object lastPrincipal;

    /** 链内最近一次可见租户（withTenant 在订阅期间装配 ThreadLocal）。 */
    private static volatile String lastTenant;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
        lastPrincipal = null;
    }

    /**
     * 执行过滤器并在下游链内记录安全上下文主体。
     *
     * @param authorization Authorization 头（可空）
     * @param filter 过滤器
     * @return 链内主体（无身份时为 null）
     */
    private Mono<java.util.Optional<Object>> runFilter(
            String authorization, KnowledgeIdentityRelayAuthenticationFilter filter) {
        lastPrincipal = null;
        lastTenant = null;
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/kb/spaces")
                .header("Authorization", authorization == null ? "" : authorization)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // 链内同时捕获身份主体与租户 ThreadLocal（withTenant 在订阅期间装配，完成后恢复）
        WebFilterChain chain = filterExchange -> ReactiveSecurityContextHolder.getContext()
                .doOnNext(context -> {
                    var authentication = context.getAuthentication();
                    lastPrincipal = authentication == null ? null : authentication.getPrincipal();
                    lastTenant = TenantContext.currentTenantId().orElse(null);
                })
                .onErrorResume(error -> Mono.empty())
                .then();
        return filter.filter(exchange, chain)
                .then(Mono.fromSupplier(() -> java.util.Optional.ofNullable(lastPrincipal)));
    }

    /**
     * 构造 HMAC 过渡密钥过滤器。
     *
     * @return 过滤器实例
     */
    private KnowledgeIdentityRelayAuthenticationFilter hmacFilter() {
        return new KnowledgeIdentityRelayAuthenticationFilter("", "", HMAC_SECRET, null);
    }

    /**
     * 签发 HMAC 访问令牌。
     *
     * @param tokenType 令牌类型
     * @return 紧凑型 JWT
     */
    private String hmacToken(String tokenType) {
        SecretKey key = Keys.hmacShaKeyFor(HMAC_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claims(Map.of("userId", "7", "tenantId", "tenant-a", "tokenType", tokenType))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key)
                .compact();
    }

    @Test
    void hmacTokenEstablishesIdentityAndTenant() {
        StepVerifier.create(runFilter("Bearer " + hmacToken("ACCESS"), hmacFilter()))
                .assertNext(principal -> {
                    assertThat(principal).isPresent();
                    assertThat(((AuthenticatedUser) principal.get()).tenantId()).isEqualTo("tenant-a");
                    assertThat(((AuthenticatedUser) principal.get()).userId()).isEqualTo(7L);
                })
                .verifyComplete();
        assertThat(lastTenant).isEqualTo("tenant-a");
    }

    @Test
    void invalidHmacSignatureLeavesNoIdentity() {
        SecretKey other = Keys.hmacShaKeyFor("another-secret-key-with-32-bytes!!".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .claims(Map.of("userId", "7", "tenantId", "tenant-a", "tokenType", "ACCESS"))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(other)
                .compact();

        StepVerifier.create(runFilter("Bearer " + forged, hmacFilter()))
                .assertNext(principal -> assertThat(principal).isEmpty())
                .verifyComplete();
    }

    @Test
    void refreshTokenLeavesNoIdentity() {
        StepVerifier.create(runFilter("Bearer " + hmacToken("REFRESH"), hmacFilter()))
                .assertNext(principal -> assertThat(principal).isEmpty())
                .verifyComplete();
    }

    @Test
    void malformedAuthorizationHeaderIsIgnored() {
        StepVerifier.create(runFilter("Basic abc", hmacFilter()))
                .assertNext(principal -> assertThat(principal).isEmpty())
                .verifyComplete();
        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void missingTokenLeavesNoIdentity() {
        StepVerifier.create(runFilter(null, hmacFilter()))
                .assertNext(principal -> assertThat(principal).isEmpty())
                .verifyComplete();
    }

    @Test
    void malformedRsaConfigurationFailsFast() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new KnowledgeIdentityRelayAuthenticationFilter("bad-entry=", "", "", null));
        assertThat(true).isTrue();
    }

    @Test
    void rsaMultiKeyModeSelectsByKid() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
        String encoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        KnowledgeIdentityRelayAuthenticationFilter filter =
                new KnowledgeIdentityRelayAuthenticationFilter("kid-a=" + encoded, "", "", null);
        String token = Jwts.builder()
                .claims(new HashMap<>(Map.of("userId", "7", "tenantId", "tenant-a", "tokenType", "ACCESS")))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .header()
                .add("kid", "kid-a")
                .and()
                .signWith(pair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        StepVerifier.create(runFilter("Bearer " + token, filter))
                .assertNext(principal -> {
                    assertThat(principal).isPresent();
                    assertThat(((AuthenticatedUser) principal.get()).tenantId()).isEqualTo("tenant-a");
                })
                .verifyComplete();
    }

    @Test
    void rsaSingleKeyModeIgnoresKid() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String encoded = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        KnowledgeIdentityRelayAuthenticationFilter filter =
                new KnowledgeIdentityRelayAuthenticationFilter("", encoded, "", null);
        String token = Jwts.builder()
                .claims(new HashMap<>(Map.of("userId", "7", "tenantId", "tenant-a", "tokenType", "ACCESS")))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(pair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        StepVerifier.create(runFilter("Bearer " + token, filter))
                .assertNext(principal -> assertThat(principal.get()).isInstanceOf(AuthenticatedUser.class))
                .verifyComplete();
    }
}
