package com.apex.scaffold.authservice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "security.encryption.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "apex.security.data-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "apex.security.config-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "security.config.encryption.key=01234567890123456789012345678901"
        })
@Import(AuthCenterBasicTestConfiguration.class)
@AutoConfigureWebTestClient
@ActiveProfiles("basic")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class OAuth2BootTest extends EmbeddedRedisBootTestSupport {
    private final WebTestClient client;
    private final com.apex.scaffold.auth.application.service.OAuth2MetadataService metadata;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final org.springframework.security.crypto.password.PasswordEncoder passwords;

    @BeforeEach
    void setupClient() {
        jdbc.update("DELETE FROM oauth2_authorization");
        jdbc.update("DELETE FROM auth_user WHERE id = 95302");
        jdbc.update(
                "INSERT INTO auth_user(id,tenant_id,username,password_hash,status,failed_login_attempts) VALUES(95302,'default','oauth-user',?,1,0)",
                passwords.encode("user-secret"));
        jdbc.update("DELETE FROM auth_config WHERE id = 95301");
        String config = "{\"clientId\":\"oauth-test\",\"clientSecret\":\"" + passwords.encode("test-secret")
                + "\",\"clientAuthenticationMethods\":[\"client_secret_basic\",\"client_secret_post\"],"
                + "\"authorizationGrantTypes\":[\"client_credentials\",\"password\",\"refresh_token\",\"authorization_code\"],"
                + "\"scopes\":[\"read\"],\"redirectUris\":[\"https://client.example/callback\"]}";
        jdbc.update(
                "INSERT INTO auth_config (id, tenant_id, config_type, config_key, config_value, enabled) VALUES (?, ?, ?, ?, ?, ?)",
                95301L,
                "default",
                "OAUTH2_CLIENT",
                "oauth-test",
                config,
                true);
    }

    @Test
    void clientCredentialsIssuesPersistedRsaToken() {
        client.post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth("oauth-test", "test-secret"))
                .body(org.springframework.web.reactive.function.BodyInserters.fromFormData(
                                "grant_type", "client_credentials")
                        .with("scope", "read"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.token_type")
                .isEqualTo("Bearer")
                .jsonPath("$.access_token")
                .isNotEmpty()
                .jsonPath("$.refresh_token")
                .doesNotExist();
    }

    @Test
    void passwordRefreshConcurrencyAndRevocation() throws Exception {
        java.util.Map<?, ?> pair =
                grant(org.springframework.web.reactive.function.BodyInserters.fromFormData("grant_type", "password")
                        .with("username", "oauth-user")
                        .with("password", "user-secret")
                        .with("scope", "read"));
        String access = pair.get("access_token").toString();
        var claims = io.jsonwebtoken.Jwts.parser()
                .verifyWith(metadata.verificationKey())
                .build()
                .parseClaimsJws(access)
                .getBody();
        org.assertj.core.api.Assertions.assertThat(claims.getSubject()).isEqualTo("95302");
        String hash = java.util.HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(access.getBytes(StandardCharsets.UTF_8)));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM oauth2_authorization WHERE access_token_hash = ?", Integer.class, hash))
                .isEqualTo(1);
        String refresh = pair.get("refresh_token").toString();
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Integer> consume = () -> {
            start.await();
            return client.post()
                    .uri("/oauth2/token")
                    .headers(headers -> headers.setBasicAuth("oauth-test", "test-secret"))
                    .body(org.springframework.web.reactive.function.BodyInserters.fromFormData(
                                    "grant_type", "refresh_token")
                            .with("refresh_token", refresh))
                    .exchange()
                    .returnResult(String.class)
                    .getStatus()
                    .value();
        };
        try {
            var first = executor.submit(consume);
            var second = executor.submit(consume);
            start.countDown();
            org.assertj.core.api.Assertions.assertThat(java.util.List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(200, 400);
        } finally {
            executor.shutdownNow();
        }
        java.util.Map<?, ?> fresh =
                grant(org.springframework.web.reactive.function.BodyInserters.fromFormData("grant_type", "password")
                        .with("username", "oauth-user")
                        .with("password", "user-secret"));
        client.post()
                .uri("/oauth2/revoke")
                .headers(headers -> headers.setBasicAuth("oauth-test", "test-secret"))
                .body(org.springframework.web.reactive.function.BodyInserters.fromFormData(
                        "token", fresh.get("access_token").toString()))
                .exchange()
                .expectStatus()
                .isOk();
        client.post()
                .uri("/oauth2/introspect")
                .headers(headers -> headers.setBasicAuth("oauth-test", "test-secret"))
                .body(org.springframework.web.reactive.function.BodyInserters.fromFormData(
                        "token", fresh.get("refresh_token").toString()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.active")
                .isEqualTo(false);
    }

    @Test
    void authorizationCodeRequiresPkceAndCannotBeReplayed() throws Exception {
        java.util.Map<?, ?> login = client.post()
                .uri("/auth/v1/login")
                .bodyValue(java.util.Map.of("username", "oauth-user", "password", "user-secret", "tenantId", "default"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(java.util.Map.class)
                .returnResult()
                .getResponseBody();
        java.util.Map<?, ?> data = login;
        String verifier = "a".repeat(43);
        String challenge = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String location = client.get()
                .uri(builder -> builder.path("/oauth2/authorize")
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "oauth-test")
                        .queryParam("redirect_uri", "https://client.example/callback")
                        .queryParam("scope", "read")
                        .queryParam("code_challenge", challenge)
                        .queryParam("code_challenge_method", "S256")
                        .queryParam("state", "test-state")
                        .build())
                .headers(
                        headers -> headers.setBearerAuth(data.get("accessToken").toString()))
                .exchange()
                .expectStatus()
                .isFound()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getFirst("Location");
        String code = org.springframework.web.util.UriComponentsBuilder.fromUriString(location)
                .build()
                .getQueryParams()
                .getFirst("code");
        client.post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth("oauth-test", "test-secret"))
                .body(org.springframework.web.reactive.function.BodyInserters.fromFormData(
                                "grant_type", "authorization_code")
                        .with("code", code)
                        .with("redirect_uri", "https://client.example/callback")
                        .with("code_verifier", "b".repeat(43)))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("invalid_grant");
        grant(org.springframework.web.reactive.function.BodyInserters.fromFormData("grant_type", "authorization_code")
                .with("code", code)
                .with("redirect_uri", "https://client.example/callback")
                .with("code_verifier", verifier));
        client.post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth("oauth-test", "test-secret"))
                .body(org.springframework.web.reactive.function.BodyInserters.fromFormData(
                                "grant_type", "authorization_code")
                        .with("code", code)
                        .with("redirect_uri", "https://client.example/callback")
                        .with("code_verifier", verifier))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("invalid_grant");
    }

    private java.util.Map<?, ?> grant(
            org.springframework.web.reactive.function.BodyInserters.FormInserter<String> form) {
        return client.post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth("oauth-test", "test-secret"))
                .body(form)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(java.util.Map.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    void clientMethodScopeAndDuplicateParametersAreValidated() {
        jdbc.update(
                "UPDATE auth_config SET config_value = REPLACE(config_value, ?, ?) WHERE id=95301",
                "[\"client_secret_basic\",\"client_secret_post\"]",
                "[\"client_secret_basic\"]");
        client.post()
                .uri("/oauth2/token")
                .body(org.springframework.web.reactive.function.BodyInserters.fromFormData(
                                "grant_type", "client_credentials")
                        .with("client_id", "oauth-test")
                        .with("client_secret", "test-secret")
                        .with("_authentication_method", "client_secret_basic"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        client.post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth("oauth-test", "test-secret"))
                .body(org.springframework.web.reactive.function.BodyInserters.fromFormData(
                                "grant_type", "client_credentials")
                        .with("scope", "rea"))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("invalid_scope");
        client.post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth("oauth-test", "test-secret"))
                .body(org.springframework.web.reactive.function.BodyInserters.fromFormData(
                                "grant_type", "client_credentials")
                        .with("grant_type", "password"))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("invalid_request");
        client.post()
                .uri("/oauth2/introspect")
                .headers(headers -> headers.setBasicAuth("oauth-test", "test-secret"))
                .body(org.springframework.web.reactive.function.BodyInserters.fromFormData(
                        "token_type_hint", "access_token"))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("invalid_request");
    }

    @Test
    void expiredRefreshAndCrossTenantPasswordAreRejected() {
        java.util.Map<?, ?> pair =
                grant(org.springframework.web.reactive.function.BodyInserters.fromFormData("grant_type", "password")
                        .with("username", "oauth-user")
                        .with("password", "user-secret"));
        jdbc.update(
                "UPDATE oauth2_authorization SET refresh_token_expires_at = ?",
                java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1));
        client.post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth("oauth-test", "test-secret"))
                .body(org.springframework.web.reactive.function.BodyInserters.fromFormData(
                                "grant_type", "refresh_token")
                        .with("refresh_token", pair.get("refresh_token").toString()))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("invalid_grant");
        jdbc.update("UPDATE auth_user SET tenant_id='other' WHERE id=95302");
        client.post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth("oauth-test", "test-secret"))
                .body(org.springframework.web.reactive.function.BodyInserters.fromFormData("grant_type", "password")
                        .with("username", "oauth-user")
                        .with("password", "user-secret")
                        .with("tenant_id", "other"))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("invalid_grant");
    }

    @Test
    void invalidIntrospectionTokenReturnsInactive() {
        client.post()
                .uri("/oauth2/introspect")
                .headers(headers -> headers.setBasicAuth("oauth-test", "test-secret"))
                .body(org.springframework.web.reactive.function.BodyInserters.fromFormData("token", "missing-token"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json("{\"active\":false}");
    }

    @Test
    void unknownClientReceivesProtocolError() {
        client.post()
                .uri("/oauth2/token")
                .body(org.springframework.web.reactive.function.BodyInserters.fromFormData(
                                "grant_type", "client_credentials")
                        .with("client_id", "unknown")
                        .with("client_secret", "invalid"))
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .valueEquals("Cache-Control", "no-store")
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("invalid_client");
    }

    @Test
    void duplicateClientAuthenticationIsRejected() {
        client.post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth("unknown", "invalid"))
                .body(org.springframework.web.reactive.function.BodyInserters.fromFormData(
                                "grant_type", "client_credentials")
                        .with("client_id", "unknown")
                        .with("client_secret", "invalid"))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("invalid_request");
    }

    @Test
    void unsupportedGrantTypeReceivesProtocolError() {
        client.post()
                .uri("/oauth2/token")
                .body(org.springframework.web.reactive.function.BodyInserters.fromFormData(
                                "grant_type", "urn:example:unsupported")
                        .with("client_id", "unknown")
                        .with("client_secret", "invalid"))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .valueEquals("Cache-Control", "no-store")
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("unsupported_grant_type");
    }

    @Test
    void revokeAndIntrospectionRejectUnauthenticatedRequestsWithProtocolErrors() {
        client.post()
                .uri("/oauth2/revoke")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("invalid_client");
        client.post()
                .uri("/oauth2/introspect")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("invalid_client");
    }

    @Test
    void discoveryAndJwksArePublic() {
        client.get()
                .uri("/.well-known/openid-configuration")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.grant_types_supported.length()")
                .isEqualTo(4)
                .jsonPath("$.code_challenge_methods_supported[0]")
                .isEqualTo("S256");
        client.get()
                .uri("/.well-known/jwks.json")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.keys[0].kty")
                .isEqualTo("RSA")
                .jsonPath("$.keys[0].d")
                .doesNotExist();
    }
}
