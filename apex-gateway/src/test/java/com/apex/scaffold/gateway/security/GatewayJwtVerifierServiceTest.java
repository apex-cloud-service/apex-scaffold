package com.apex.scaffold.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link GatewayJwtVerifierService} 单元测试.
 *
 * <p>覆盖迁移方案 §16 风险 #1 缓解的「双 kid 并行验证」：
 * HMAC 过渡密钥验签、RSA 非对称公钥验签、未知 kid、签名无效、Token 过期等异常收敛为 {@code null}。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("GatewayJwtVerifierService JWKS 验签服务测试")
class GatewayJwtVerifierServiceTest {

    /** 测试用 HMAC 密钥（满足 HS256 长度要求） */
    private static final String HMAC_SECRET = "apex-verifier-test-secret-key-0123456789abcdef";

    /**
     * 构造仅含 HMAC 过渡密钥的配置.
     */
    private JwksProperties hmacOnlyProperties() {
        JwksProperties properties = new JwksProperties();
        properties.setHmacSecret(HMAC_SECRET);
        return properties;
    }

    /**
     * 生成携带 header kid 的 HMAC Token.
     *
     * @param kid     密钥 ID
     * @param subject 主体
     * @param ttlMillis 有效期（毫秒，可为负表示已过期）
     * @return JWT 字符串
     */
    private String hmacToken(String kid, String subject, long ttlMillis) {
        Date now = new Date();
        return Jwts.builder()
                .header()
                .keyId(kid)
                .and()
                .subject(subject)
                .claim("userId", subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(Keys.hmacShaKeyFor(HMAC_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Nested
    @DisplayName("HMAC 过渡密钥验签")
    class HmacVerifyTests {

        @Test
        @DisplayName("合法 HMAC Token 应验签成功并返回声明")
        void shouldVerifyValidHmacToken() {
            GatewayJwtVerifierService service = new GatewayJwtVerifierService(hmacOnlyProperties());

            Claims claims = service.verifyAndParse(hmacToken("hmac-legacy", "user-1", 60_000));

            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo("user-1");
        }

        @Test
        @DisplayName("kid 未显式声明但算法为 HS 时应回退到过渡密钥")
        void shouldFallbackToHmacKeyForHsAlg() {
            GatewayJwtVerifierService service = new GatewayJwtVerifierService(hmacOnlyProperties());

            // kid 使用任意值（非 hmac-legacy），alg 为 HS256 → 走兜底过渡密钥
            Claims claims = service.verifyAndParse(hmacToken("unknown-kid", "user-2", 60_000));

            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo("user-2");
        }

        @Test
        @DisplayName("已过期 HMAC Token 应返回 null")
        void shouldRejectExpiredToken() {
            GatewayJwtVerifierService service = new GatewayJwtVerifierService(hmacOnlyProperties());

            assertThat(service.verifyAndParse(hmacToken("hmac-legacy", "user-3", -10_000)))
                    .isNull();
        }

        @Test
        @DisplayName("签名被篡改的 Token 应返回 null")
        void shouldRejectTamperedToken() {
            GatewayJwtVerifierService service = new GatewayJwtVerifierService(hmacOnlyProperties());
            String token = hmacToken("hmac-legacy", "user-4", 60_000);

            // 篡改签名段
            String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "AAAAbrokenSignature";

            assertThat(service.verifyAndParse(tampered)).isNull();
        }
    }

    @Nested
    @DisplayName("RSA 非对称验签")
    class RsaVerifyTests {

        @Test
        @DisplayName("由 X.509 公钥签发的 Token 应验签成功")
        void shouldVerifyRsaToken() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            PrivateKey privateKey = keyPair.getPrivate();
            String publicKeyBase64 =
                    Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

            JwksProperties properties = new JwksProperties();
            properties.setKs(List.of(new JwksProfile("rsa-2026-01", "rsa256", publicKeyBase64, null)));
            GatewayJwtVerifierService service = new GatewayJwtVerifierService(properties);

            String token = Jwts.builder()
                    .header()
                    .keyId("rsa-2026-01")
                    .and()
                    .subject("user-rsa")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(privateKey)
                    .compact();

            Claims claims = service.verifyAndParse(token);

            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo("user-rsa");
        }
    }

    @Nested
    @DisplayName("异常输入收敛")
    class InvalidInputTests {

        @Test
        @DisplayName("null / 空 Token 应返回 null")
        void shouldRejectNullOrBlankToken() {
            GatewayJwtVerifierService service = new GatewayJwtVerifierService(hmacOnlyProperties());

            assertThat(service.verifyAndParse(null)).isNull();
            assertThat(service.verifyAndParse("")).isNull();
            assertThat(service.verifyAndParse("   ")).isNull();
        }

        @Test
        @DisplayName("无任何密钥时任意 Token 应返回 null")
        void shouldRejectWhenNoKeys() {
            GatewayJwtVerifierService service = new GatewayJwtVerifierService(new JwksProperties());

            assertThat(service.keyCount()).isZero();
            assertThat(service.verifyAndParse(hmacToken("hmac-legacy", "user-5", 60_000)))
                    .isNull();
        }

        @Test
        @DisplayName("格式非法的 Token 应返回 null")
        void shouldRejectMalformedToken() {
            GatewayJwtVerifierService service = new GatewayJwtVerifierService(hmacOnlyProperties());

            assertThat(service.verifyAndParse("not-a-jwt")).isNull();
        }
    }

    @Nested
    @DisplayName("密钥缓存")
    class KeyCacheTests {

        @Test
        @DisplayName("refreshKeys 后应加载 HMAC 过渡密钥")
        void shouldLoadHmacKeyOnRefresh() {
            GatewayJwtVerifierService service = new GatewayJwtVerifierService(hmacOnlyProperties());

            assertThat(service.keyCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("远程 JWKS 拉取合并（统一认证收口 A2）")
    class RemoteJwksTests {

        private JwksProperties remoteProperties(String url) {
            JwksProperties properties = hmacOnlyProperties();
            properties.setRemoteUrl(url);
            return properties;
        }

        private java.security.PublicKey remotePublicKey() {
            return Jwts.SIG.RS256.keyPair().build().getPublic();
        }

        private String rsaToken(java.security.KeyPair pair, String kid) {
            Date now = new Date();
            return Jwts.builder()
                    .header()
                    .keyId(kid)
                    .and()
                    .subject("center-user")
                    .issuedAt(now)
                    .expiration(new Date(now.getTime() + 60_000))
                    .signWith(pair.getPrivate(), Jwts.SIG.RS256)
                    .compact();
        }

        @Test
        @DisplayName("远程公钥合并进缓存并可验签认证中心令牌")
        void shouldMergeRemoteKeys() {
            java.security.KeyPair pair = Jwts.SIG.RS256.keyPair().build();
            String kid = "key-remote-0001";
            java.util.Map<String, java.security.PublicKey> remote = java.util.Map.of(kid, pair.getPublic());
            GatewayJwtVerifierService service = new GatewayJwtVerifierService(
                    remoteProperties("http://auth.example/.well-known/jwks.json"),
                    url -> reactor.core.publisher.Mono.just(remote));

            assertThat(service.keyCount()).isEqualTo(2); // hmac-legacy + 远程公钥
            assertThat(service.verifyAndParse(rsaToken(pair, kid))).isNotNull();
        }

        @Test
        @DisplayName("远程拉取失败时保留静态密钥，令牌验签不受影响")
        void shouldKeepStaticKeysWhenRemoteFails() {
            GatewayJwtVerifierService service = new GatewayJwtVerifierService(
                    remoteProperties("http://auth.example/.well-known/jwks.json"),
                    url -> reactor.core.publisher.Mono.error(new IllegalStateException("auth unreachable")));

            assertThat(service.keyCount()).isEqualTo(1);
            assertThat(service.verifyAndParse(hmacToken("hmac-legacy", "user-6", 60_000)))
                    .isNotNull();
        }

        @Test
        @DisplayName("远程公钥覆盖同 kid 静态条目（轮换公钥即时生效）")
        void shouldOverrideSameKidStaticEntry() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            java.security.KeyPair stale = generator.generateKeyPair();
            java.security.KeyPair rotated = generator.generateKeyPair();
            JwksProperties properties = new JwksProperties();
            properties.setKs(List.of(new JwksProfile(
                    "key-center",
                    "rsa256",
                    Base64.getEncoder().encodeToString(stale.getPublic().getEncoded()),
                    null)));
            properties.setRemoteUrl("http://auth.example/.well-known/jwks.json");
            GatewayJwtVerifierService service = new GatewayJwtVerifierService(
                    properties,
                    url -> reactor.core.publisher.Mono.just(java.util.Map.of("key-center", rotated.getPublic())));

            assertThat(service.verifyAndParse(rsaToken(stale, "key-center"))).isNull();
            assertThat(service.verifyAndParse(rsaToken(rotated, "key-center"))).isNotNull();
        }
    }
}
