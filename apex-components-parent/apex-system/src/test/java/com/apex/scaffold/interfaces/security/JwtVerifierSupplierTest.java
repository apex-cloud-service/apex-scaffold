package com.apex.scaffold.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.config.JwksFetchProperties;
import com.apex.scaffold.common.security.JwksFetcher;
import com.apex.scaffold.common.security.MultiKeyJwtVerifier;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link JwtVerifierSupplier} 统一令牌验签器测试（统一认证收口 A1/A2）。
 *
 * <p>覆盖静态公钥解码、RS256（认证中心）与 HS256（主应用）双算法解析、
 * JWKS 拉取快照对静态公钥的同 kid 覆盖。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("JwtVerifierSupplier 统一令牌验签器测试")
class JwtVerifierSupplierTest {

    private static final String HMAC_SECRET = "system-verifier-test-secret-0123456789abcdef";

    private final KeyPair centerKey = Jwts.SIG.RS256.keyPair().build();

    private String centerToken(String kid, long ttlSeconds) {
        return Jwts.builder()
                .header()
                .keyId(kid)
                .and()
                .subject("95302")
                .claim("tokenType", "ACCESS")
                .claim("accountId", 95302L)
                .claim("tenantId", "default")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(ttlSeconds)))
                .signWith(centerKey.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String mainAppToken(long ttlSeconds) {
        return Jwts.builder()
                .subject("open-id")
                .claim("type", "access")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(ttlSeconds)))
                .signWith(
                        io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                                HMAC_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
    }

    private Map<String, PublicKey> staticKeys() {
        return JwtVerifierSupplier.decodeConfiguredKeys(Map.of(
                "key-test-0001",
                Base64.getEncoder().encodeToString(centerKey.getPublic().getEncoded())));
    }

    @Test
    @DisplayName("静态公钥配置解码与非法配置快速失败")
    void decodesConfiguredKeys() {
        assertThat(staticKeys()).containsOnlyKeys("key-test-0001");
        assertThat(JwtVerifierSupplier.decodeConfiguredKeys(Map.of())).isEmpty();
        assertThatThrownBy(() -> JwtVerifierSupplier.decodeConfiguredKeys(Map.of("kid", "not-base64")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtVerifierSupplier.decodeConfiguredKeys(Map.of("", "AAA")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("认证中心 RS256 令牌与主应用 HS256 令牌均验签通过")
    void parsesBothTokenFamilies() {
        JwtVerifierSupplier supplier = new JwtVerifierSupplier(staticKeys(), null, HMAC_SECRET, true);
        Claims center = supplier.parse(centerToken("key-test-0001", 300));
        assertThat(center.get("tokenType", String.class)).isEqualTo("ACCESS");
        assertThat(center.get("accountId", Long.class)).isEqualTo(95302L);

        Claims mainApp = supplier.parse(mainAppToken(300));
        assertThat(mainApp.get("type", String.class)).isEqualTo("access");
    }

    @Test
    @DisplayName("JWKS 拉取快照覆盖同 kid 静态公钥（轮换后的新公钥即时生效）")
    void fetcherSnapshotOverridesStaticKeys() {
        KeyPair rotated = Jwts.SIG.RS256.keyPair().build();
        JwksFetchProperties properties = new JwksFetchProperties();
        properties.setRemoteUrl("http://auth.example/.well-known/jwks.json");
        // 替身拉取客户端：快照携带轮换后的同 kid 新公钥
        JwksFetcher fetcher = new JwksFetcher(properties) {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public Map<String, PublicKey> snapshot() {
                return Map.of("key-test-0001", rotated.getPublic());
            }
        };
        JwtVerifierSupplier supplier = new JwtVerifierSupplier(staticKeys(), fetcher, HMAC_SECRET, true);
        // 轮换新私钥签发的令牌（旧静态公钥验不过）必须通过
        String rotatedToken = Jwts.builder()
                .header()
                .keyId("key-test-0001")
                .and()
                .subject("95302")
                .claim("tokenType", "ACCESS")
                .claim("accountId", 95302L)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(rotated.getPrivate(), Jwts.SIG.RS256)
                .compact();
        assertThat(supplier.parse(rotatedToken).getSubject()).isEqualTo("95302");
        // 主应用 HMAC 路径不受影响
        assertThat(supplier.parse(mainAppToken(300)).getSubject()).isEqualTo("open-id");
    }

    @Test
    @DisplayName("禁用 JWKS 拉取时仅用静态公钥，未知 kid 拒绝")
    void fallsBackToStaticKeysWhenFetcherDisabled() {
        JwksFetcher disabledFetcher = new JwksFetcher(new JwksFetchProperties());
        JwtVerifierSupplier supplier = new JwtVerifierSupplier(staticKeys(), disabledFetcher, HMAC_SECRET, true);
        assertThat(disabledFetcher.enabled()).isFalse();
        assertThatThrownBy(() -> supplier.parse(centerToken("key-unknown", 300)))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
        assertThat(supplier.parse(centerToken("key-test-0001", 300)).getSubject())
                .isEqualTo("95302");
        // MultiKeyJwtVerifier 的共享语义保持：主应用 HS256 令牌走 HMAC 过渡
        assertThat(MultiKeyJwtVerifier.of(Map.of(), HMAC_SECRET, true)
                        .parse(mainAppToken(300))
                        .getSubject())
                .isEqualTo("open-id");
    }
}
