package com.apex.scaffold.auth.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.jsonwebtoken.JwtException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link JwtKeyMaterialService} 单元测试。
 *
 * <p>说明（2026-10-08）：微服务迁移 Phase 2.1 后，Token 签名算法由对称 HS256 升级为
 * 非对称 RS256。本测试使用<b>真实 RSA-2048 密钥对</b>对 {@code sign} / {@code parse}
 * 做端到端校验（签发→验签→解析），并验证 JWKS 公钥集合投影与各类异常分支，
 * mock 仅用于隔离底层的 {@link JwtKeyRotationService} 密钥存取。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtKeyMaterialService（RS256 密钥材料服务）测试")
class JwtKeyMaterialServiceTest {

    /** 测试使用的密钥ID。 */
    private static final String TEST_KEY_ID = "key-test-123";

    /** 测试使用的宽限期旧密钥ID（用于验证按 kid 选公钥）。 */
    private static final String OLD_KEY_ID = "key-old-001";

    @Mock
    private JwtKeyRotationService jwtKeyRotationService;

    private JwtKeyMaterialService materialService;

    /** 当前活跃密钥对（用于签发）。 */
    private KeyPair currentKeyPair;

    /** 宽限期内的旧密钥对（仅用于验签）。 */
    private KeyPair oldKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        // 通过 @InjectMocks 语义手工构造外观层，仅注入被 mock 的密钥轮换服务
        materialService = new JwtKeyMaterialService(jwtKeyRotationService);

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        currentKeyPair = keyGen.generateKeyPair();
        oldKeyPair = keyGen.generateKeyPair();
    }

    /** 构造一个携带 jti 与自定义声明的 claims。 */
    private Map<String, Object> buildClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("jti", "test-jti-001");
        claims.put("userId", 100L);
        claims.put("tenantId", "test-tenant");
        claims.put("tokenType", "ACCESS");
        return claims;
    }

    @Test
    @DisplayName("签名返回 RS256 JWT，且能被对应公钥验签解析")
    void testSign_ThenParse_RoundTrip() {
        Map<String, Object> claims = buildClaims();
        when(jwtKeyRotationService.getCurrentKeyId()).thenReturn(Mono.just(TEST_KEY_ID));
        when(jwtKeyRotationService.getCurrentPrivateKey()).thenReturn(Mono.just(currentKeyPair.getPrivate()));
        when(jwtKeyRotationService.getPublicKey(TEST_KEY_ID)).thenReturn(Mono.just(currentKeyPair.getPublic()));

        String signed = materialService.sign("userId-100", claims, 3_600_000L).block();
        assertNotNull(signed, "RS256 签发结果不应为空");
        // 生成的 Token 必须是紧凑型 JWT（含两处点分隔的三段式）
        assertEquals(2, signed.chars().filter(ch -> ch == '.').count(), "JWT 应为三段式紧凑格式");

        StepVerifier.create(materialService.parse(signed))
                .assertNext(parsed -> {
                    assertEquals("test-jti-001", parsed.get("jti", String.class));
                    assertEquals(100L, parsed.get("userId", Long.class).longValue());
                    assertEquals("test-tenant", parsed.get("tenantId", String.class));
                    assertEquals("ACCESS", parsed.get("tokenType", String.class));
                })
                .verifyComplete();

        verify(jwtKeyRotationService, atLeastOnce()).getPublicKey(TEST_KEY_ID);
    }

    @Test
    @DisplayName("无 kid 的历史 Token 回退到当前活跃公钥验签")
    void testParse_NoKid_FallbackToCurrentPublicKey() throws Exception {
        // 手动构造一段不含 kid 的 RS256 Token，使用当前私钥签发
        String token = buildTokenWithoutKid(currentKeyPair.getPrivate(), "legacy-jti");

        when(jwtKeyRotationService.getCurrentPublicKey()).thenReturn(Mono.just(currentKeyPair.getPublic()));

        StepVerifier.create(materialService.parse(token))
                .assertNext(parsed -> assertEquals("legacy-jti", parsed.get("jti", String.class)))
                .verifyComplete();

        // 未携带 kid 时不应走 getPublicKey 分支，而应走 getCurrentPublicKey
        verify(jwtKeyRotationService, never()).getPublicKey(anyString());
        verify(jwtKeyRotationService).getCurrentPublicKey();
    }

    @Test
    @DisplayName("携带 kid 时按 kid 选择对应的旧公钥验签")
    void testParse_WithKid_UsesCorrespondingPublicKey() throws Exception {
        // 用“旧密钥对”私钥签发一段携带旧 kid 的 Token
        String token = buildTokenWithKid(oldKeyPair.getPrivate(), OLD_KEY_ID, "old-jti");

        when(jwtKeyRotationService.getPublicKey(OLD_KEY_ID)).thenReturn(Mono.just(oldKeyPair.getPublic()));

        StepVerifier.create(materialService.parse(token))
                .assertNext(parsed -> assertEquals("old-jti", parsed.get("jti", String.class)))
                .verifyComplete();

        verify(jwtKeyRotationService).getPublicKey(OLD_KEY_ID);
        verify(jwtKeyRotationService, never()).getCurrentPublicKey();
    }

    @Test
    @DisplayName("kid 存在但公钥缺失时报错")
    void testParse_KidNotFoundPublicKey_Error() throws Exception {
        String token = buildTokenWithKid(currentKeyPair.getPrivate(), TEST_KEY_ID, "jti-missing-key");

        when(jwtKeyRotationService.getPublicKey(TEST_KEY_ID)).thenReturn(Mono.empty());

        StepVerifier.create(materialService.parse(token))
                .expectErrorMatches(
                        e -> e instanceof JwtException && e.getMessage().contains("未找到 kid 对应的公钥"))
                .verify();
    }

    @Test
    @DisplayName("验签失败（他人公钥）时报 JwtException")
    void testParse_SignatureMismatch_Error() throws Exception {
        // 用当前私钥签发，但用“旧公钥”验签，签名必然不匹配
        String token = buildTokenWithKid(currentKeyPair.getPrivate(), TEST_KEY_ID, "jti-mismatch");

        when(jwtKeyRotationService.getPublicKey(TEST_KEY_ID)).thenReturn(Mono.just(oldKeyPair.getPublic()));

        StepVerifier.create(materialService.parse(token))
                .expectError(JwtException.class)
                .verify();
    }

    @Test
    @DisplayName("非法 Token（无法解析头部）回退当前公钥失败时报错")
    void testParse_MalformedToken_Error() {
        // 头部无法解析出 kid，且当前公钥未初始化
        when(jwtKeyRotationService.getCurrentPublicKey()).thenReturn(Mono.empty());

        StepVerifier.create(materialService.parse("not-a-valid-jwt"))
                .expectErrorMatches(
                        e -> e instanceof JwtException && e.getMessage().contains("JWT 密钥未初始化"))
                .verify();
    }

    @Test
    @DisplayName("availablePublicKeys 返回当前活跃与旧密钥的有序投影")
    void testAvailablePublicKeys_Projection() {
        when(jwtKeyRotationService.getAllAvailableKeyIds()).thenReturn(Mono.just(List.of(TEST_KEY_ID, OLD_KEY_ID)));
        when(jwtKeyRotationService.getPublicKey(TEST_KEY_ID)).thenReturn(Mono.just(currentKeyPair.getPublic()));
        when(jwtKeyRotationService.getPublicKey(OLD_KEY_ID)).thenReturn(Mono.just(oldKeyPair.getPublic()));

        StepVerifier.create(materialService.availablePublicKeys())
                .assertNext(projection -> {
                    assertEquals(2, projection.size());
                    assertTrue(projection.containsKey(TEST_KEY_ID));
                    assertTrue(projection.containsKey(OLD_KEY_ID));
                    assertEquals(currentKeyPair.getPublic(), projection.get(TEST_KEY_ID), "当前活跃密钥应优先且内容一致");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("availablePublicKeys 在空密钥列表时返回空映射")
    void testAvailablePublicKeys_Empty() {
        when(jwtKeyRotationService.getAllAvailableKeyIds()).thenReturn(Mono.just(List.of()));

        StepVerifier.create(materialService.availablePublicKeys())
                .assertNext(projection -> assertTrue(projection.isEmpty()))
                .verifyComplete();
    }

    /** 构建一段携带 kid 头部的 RS256 Token（使用独立私钥签发并指定头）。 */
    private String buildTokenWithKid(PrivateKey privateKey, String keyId, String jti) {
        return io.jsonwebtoken.Jwts.builder()
                .header()
                .keyId(keyId)
                .and()
                .subject("subject-user")
                .claim("jti", jti)
                .claim("userId", 100L)
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(privateKey, io.jsonwebtoken.SignatureAlgorithm.RS256)
                .compact();
    }

    /** 构建一段不含 kid 的 RS256 Token（用于验证回退到当前公钥的逻辑）。 */
    private String buildTokenWithoutKid(PrivateKey privateKey, String jti) {
        return io.jsonwebtoken.Jwts.builder()
                .subject("subject-user")
                .claim("jti", jti)
                .claim("userId", 100L)
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(privateKey, io.jsonwebtoken.SignatureAlgorithm.RS256)
                .compact();
    }
}
