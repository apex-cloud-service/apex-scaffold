package com.apex.scaffold.maskingservice;

import com.apex.scaffold.common.annotation.MaskType;
import com.apex.scaffold.common.masking.JsonFieldMasker;
import com.apex.scaffold.maskingservice.config.MaskingProperties;
import com.apex.scaffold.maskingservice.config.MaskingSecurityConfig;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import reactor.test.StepVerifier;

/** 使用本机临时 JWKS 和真实 RSA 签名校验信任边界。 */
class MaskingJwtTest {
    @Test
    void verifiesSignatureIssuerAudienceAndExpiry() throws Exception {
        RSAKey trusted = new RSAKeyGenerator(2048).keyID("test-key").generate();
        RSAKey untrusted = new RSAKeyGenerator(2048).keyID("test-key").generate();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] keys = new JWKSet(trusted.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, keys.length);
            try (var output = exchange.getResponseBody()) {
                output.write(keys);
            }
        });
        server.start();
        try {
            var properties = new MaskingProperties(
                    "https://issuer.example",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks",
                    "masking",
                    Map.of(
                            "v1",
                            new MaskingProperties.Policy(
                                    Set.of("tenant-a"),
                                    Map.of("phone", new JsonFieldMasker.Rule(MaskType.PHONE, null)))));
            var decoder = new MaskingSecurityConfig().maskingJwtDecoder(properties);
            Instant expiry = Instant.now().plusSeconds(300);
            StepVerifier.create(decoder.decode(token(trusted, properties.issuer(), "masking", expiry)))
                    .expectNextMatches(jwt -> "tenant-a".equals(jwt.getClaimAsString("tenant_id")))
                    .verifyComplete();
            StepVerifier.create(decoder.decode(token(untrusted, properties.issuer(), "masking", expiry)))
                    .expectError(JwtException.class)
                    .verify();
            StepVerifier.create(decoder.decode(token(trusted, "https://wrong.example", "masking", expiry)))
                    .expectError(JwtException.class)
                    .verify();
            StepVerifier.create(decoder.decode(token(trusted, properties.issuer(), "wrong", expiry)))
                    .expectError(JwtException.class)
                    .verify();
            StepVerifier.create(decoder.decode(token(
                            trusted,
                            properties.issuer(),
                            "masking",
                            Instant.now().minusSeconds(120))))
                    .expectError(JwtException.class)
                    .verify();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void acceptsLoginFamilyTokenAndRejectsUnknownFamily() throws Exception {
        RSAKey trusted = new RSAKeyGenerator(2048).keyID("test-key").generate();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] keys = new JWKSet(trusted.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, keys.length);
            try (var output = exchange.getResponseBody()) {
                output.write(keys);
            }
        });
        server.start();
        try {
            var properties = new MaskingProperties(
                    "https://issuer.example",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks",
                    "masking",
                    Map.of(
                            "v1",
                            new MaskingProperties.Policy(
                                    Set.of("tenant-a"),
                                    Map.of("phone", new JsonFieldMasker.Rule(MaskType.PHONE, null)))));
            var decoder = new MaskingSecurityConfig().maskingJwtDecoder(properties);
            Instant expiry = Instant.now().plusSeconds(300);
            // 登录家族：无 iss/aud，tokenType=ACCESS 且带租户声明
            var loginClaims = new JWTClaimsSet.Builder()
                    .subject("1001")
                    .claim("tokenType", "ACCESS")
                    .claim("tenantId", "tenant-a")
                    .expirationTime(Date.from(expiry))
                    .build();
            StepVerifier.create(decoder.decode(sign(trusted, loginClaims)))
                    .expectNextMatches(jwt -> "tenant-a".equals(jwt.getClaimAsString("tenantId")))
                    .verifyComplete();
            // 未识别家族：无 token_use 也无 tokenType
            var unknownClaims = new JWTClaimsSet.Builder()
                    .subject("1001")
                    .expirationTime(Date.from(expiry))
                    .build();
            StepVerifier.create(decoder.decode(sign(trusted, unknownClaims)))
                    .expectError(JwtException.class)
                    .verify();
        } finally {
            server.stop(0);
        }
    }

    /** 使用指定声明集合签名测试令牌。 */
    private String sign(RSAKey key, JWTClaimsSet claims) throws Exception {
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(JOSEObjectType.JWT)
                        .keyID(key.getKeyID())
                        .build(),
                claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    /** 创建仅用于测试的 OAuth2 家族签名令牌。 */
    private String token(RSAKey key, String issuer, String audience, Instant expiry) throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject("test")
                .claim("token_use", "access")
                .claim("tenant_id", "tenant-a")
                .expirationTime(Date.from(expiry))
                .build();
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(JOSEObjectType.JWT)
                        .keyID(key.getKeyID())
                        .build(),
                claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
