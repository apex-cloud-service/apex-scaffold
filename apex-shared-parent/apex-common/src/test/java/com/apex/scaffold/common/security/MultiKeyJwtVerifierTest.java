package com.apex.scaffold.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link MultiKeyJwtVerifier} 双算法验签单元测试（统一认证收口 A1）。
 *
 * <p>覆盖 RS256 按 kid 选钥、HS 系列过渡验签开关、算法混淆拒绝、过期与签名错误传播。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("MultiKeyJwtVerifier 双算法验签测试")
class MultiKeyJwtVerifierTest {

    private static final String HMAC_SECRET = "apex-multi-key-verifier-test-secret-0123456789abcdef";
    private static final String CENTER_KID = "key-test-0001";

    private record RsaKeyMaterial(KeyPair pair, String kid, String publicKeyBase64) {}

    private RsaKeyMaterial rsaKey(String kid) {
        KeyPair pair = Jwts.SIG.RS256.keyPair().build();
        return new RsaKeyMaterial(
                pair, kid, Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
    }

    private PublicKey decode(String base64) throws Exception {
        return java.security.KeyFactory.getInstance("RSA")
                .generatePublic(new java.security.spec.X509EncodedKeySpec(
                        Base64.getDecoder().decode(base64)));
    }

    private String signRs256(RsaKeyMaterial key, String kid, long ttlSeconds) {
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
                .signWith(key.pair().getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String signHs256(long ttlSeconds) {
        return Jwts.builder()
                .subject("open-id")
                .claim("type", "access")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(ttlSeconds)))
                .signWith(Keys.hmacShaKeyFor(HMAC_SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    @Test
    @DisplayName("RS256 令牌按 kid 选公钥验签通过")
    void parseRs256ByKid() throws Exception {
        RsaKeyMaterial active = rsaKey(CENTER_KID);
        RsaKeyMaterial grace = rsaKey("key-test-0000");
        MultiKeyJwtVerifier verifier = MultiKeyJwtVerifier.of(
                Map.of(active.kid(), decode(active.publicKeyBase64()), grace.kid(), decode(grace.publicKeyBase64())),
                HMAC_SECRET,
                true);
        var claims = verifier.parse(signRs256(grace, grace.kid(), 300));
        assertThat(claims.getSubject()).isEqualTo("95302");
        assertThat(claims.get("tokenType", String.class)).isEqualTo("ACCESS");
    }

    @Test
    @DisplayName("未知 kid、缺失 kid 与未配置公钥均拒绝")
    void rejectsUnknownOrMissingKid() throws Exception {
        RsaKeyMaterial active = rsaKey(CENTER_KID);
        MultiKeyJwtVerifier verifier =
                MultiKeyJwtVerifier.of(Map.of(active.kid(), decode(active.publicKeyBase64())), "", false);
        assertThatThrownBy(() -> verifier.parse(signRs256(active, "key-unknown", 300)))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("key-unknown");
        // 缺失 kid 的 RS256 令牌：header 无 kid → 公钥定位失败
        String kidLess = Jwts.builder()
                .subject("95302")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(active.pair().getPrivate(), Jwts.SIG.RS256)
                .compact();
        assertThatThrownBy(() -> verifier.parse(kidLess)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("HS256 在开关开启时走 HMAC 过渡验签，关闭时拒绝")
    void hmacToggle() {
        MultiKeyJwtVerifier enabled = MultiKeyJwtVerifier.of(Map.of(), HMAC_SECRET, true);
        assertThat(enabled.parse(signHs256(300)).getSubject()).isEqualTo("open-id");

        MultiKeyJwtVerifier disabled = MultiKeyJwtVerifier.of(Map.of(), HMAC_SECRET, false);
        assertThatThrownBy(() -> disabled.parse(signHs256(300)))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("HMAC");
    }

    @Test
    @DisplayName("算法混淆与缺失 alg 一律拒绝")
    void rejectsAlgConfusion() throws Exception {
        RsaKeyMaterial active = rsaKey(CENTER_KID);
        MultiKeyJwtVerifier verifier =
                MultiKeyJwtVerifier.of(Map.of(active.kid(), decode(active.publicKeyBase64())), HMAC_SECRET, true);
        // RS512 冒充 RS256 公钥验签：算法不在白名单
        String rs512 = Jwts.builder()
                .header()
                .keyId(CENTER_KID)
                .and()
                .subject("95302")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(active.pair().getPrivate(), Jwts.SIG.RS512)
                .compact();
        assertThatThrownBy(() -> verifier.parse(rs512))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("不支持的签名算法");
        // 非 JWT 结构
        assertThatThrownBy(() -> verifier.parse("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("过期与签名错误原样传播 jjwt 异常")
    void propagatesExpiredAndBadSignature() throws Exception {
        RsaKeyMaterial active = rsaKey(CENTER_KID);
        RsaKeyMaterial other = rsaKey("key-other");
        MultiKeyJwtVerifier verifier =
                MultiKeyJwtVerifier.of(Map.of(active.kid(), decode(active.publicKeyBase64())), "", false);
        assertThatThrownBy(() -> verifier.parse(signRs256(active, CENTER_KID, -60)))
                .isInstanceOf(ExpiredJwtException.class);
        // 用其他密钥私钥签名、冒充活跃 kid
        assertThatThrownBy(() -> verifier.parse(signRs256(other, CENTER_KID, 300)))
                .isInstanceOf(JwtException.class);
    }
}
