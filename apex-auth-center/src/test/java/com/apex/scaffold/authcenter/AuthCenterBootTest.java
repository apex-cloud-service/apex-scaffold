package com.apex.scaffold.authcenter;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.authcenter.domain.model.entity.AuthConfigEntity;
import com.apex.scaffold.authcenter.domain.repository.AuthConfigRepository;
import com.apex.scaffold.authcenter.domain.service.TokenService;
import com.apex.scaffold.common.context.TenantContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("basic")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class AuthCenterBootTest {
    private final AuthConfigRepository configs;
    private final TokenService tokens;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final WebTestClient client;
    private String password;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> UUID.randomUUID().toString() + UUID.randomUUID());
    }

    @BeforeEach
    void seed() {
        password = UUID.randomUUID().toString();
        jdbc.update("DELETE FROM auth_token");
        jdbc.update("DELETE FROM auth_user");
        jdbc.update("DELETE FROM auth_config");
        jdbc.update(
                "INSERT INTO auth_user(id,tenant_id,username,password_hash,status,failed_login_attempts) VALUES(1,'default','alice',?,1,0)",
                encoder.encode(password));
        jdbc.update(
                "INSERT INTO auth_user(id,tenant_id,username,password_hash,status,failed_login_attempts) VALUES(2,'other','bob',?,1,0)",
                encoder.encode(password));
    }

    @Test
    void startsWithRealRepositoriesAndEnforcesTenantScope() {
        AuthConfigEntity config = AuthConfigEntity.builder()
                .tenantId("default")
                .configType("SECURITY_POLICY")
                .configKey("integration")
                .configValue("{}")
                .enabled(true)
                .isDefault(false)
                .version(1)
                .build();
        AuthConfigEntity saved = configs.save(config)
                .contextWrite(TenantContext.withTenant("default"))
                .block(Duration.ofSeconds(5));
        assertThat(saved.getId()).isNotNull();
        StepVerifier.create(configs.findById(saved.getId(), "default").contextWrite(TenantContext.withTenant("other")))
                .expectError()
                .verify(Duration.ofSeconds(5));
        assertThat(configs.findById(saved.getId(), "other")
                        .contextWrite(TenantContext.withTenant("other"))
                        .block(Duration.ofSeconds(5)))
                .isNull();
    }

    @Test
    void expiredLockCanLoginButActiveLockAndDisabledCannot() {
        jdbc.update(
                "UPDATE auth_user SET status=2,failed_login_attempts=5,locked_until=? WHERE id=1",
                LocalDateTime.now().minusMinutes(1));
        login(200);
        assertThat(jdbc.queryForObject("SELECT status FROM auth_user WHERE id=1", Integer.class))
                .isEqualTo(1);
        jdbc.update(
                "UPDATE auth_user SET status=2,locked_until=? WHERE id=1",
                LocalDateTime.now().plusMinutes(5));
        login(403);
        jdbc.update(
                "UPDATE auth_user SET status=0,locked_until=? WHERE id=1",
                LocalDateTime.now().minusMinutes(1));
        login(403);
    }

    @Test
    void clientCannotChooseTrustedTenant() {
        client.post()
                .uri("/auth/v1/login")
                .header("X-Tenant-Id", "other")
                .bodyValue(Map.of("username", "bob", "password", password, "tenantId", "other"))
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    void refreshRotatesAndLogoutUsesAuthenticatedSubject() {
        Map<?, ?> pair = login(200);
        String refresh = (String) pair.get("refreshToken");
        Map<?, ?> renewed = client.post()
                .uri("/auth/v1/refresh")
                .bodyValue(Map.of("refreshToken", refresh))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        client.post()
                .uri("/auth/v1/refresh")
                .bodyValue(Map.of("refreshToken", refresh))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        String access = (String) renewed.get("accessToken");
        client.post()
                .uri("/auth/v1/logout/2?tenantId=other")
                .headers(h -> h.setBearerAuth(access))
                .exchange()
                .expectStatus()
                .isForbidden();
        client.post()
                .uri("/auth/v1/logout/1")
                .headers(h -> h.setBearerAuth(access))
                .exchange()
                .expectStatus()
                .isOk();
        client.post()
                .uri("/auth/v1/refresh")
                .bodyValue(Map.of("refreshToken", renewed.get("refreshToken")))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        StepVerifier.create(tokens.validateToken(access).contextWrite(TenantContext.withTenant("default")))
                .expectError()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void anonymousLogoutIsRejected() {
        client.post().uri("/auth/v1/logout/1").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void concurrentRefreshHasExactlyOneWinner() {
        String refresh = (String) login(200).get("refreshToken");
        java.util.List<Boolean> outcomes = Flux.range(0, 8)
                .flatMap(index -> tokens.refreshToken(refresh).map(pair -> true).onErrorReturn(false), 8)
                .collectList()
                .block(Duration.ofSeconds(10));
        assertThat(outcomes).filteredOn(Boolean::booleanValue).hasSize(1);
    }

    @Test
    void refreshCannotAuthenticateAndTrustedTenantCannotBeOverridden() {
        Map<?, ?> pair = login(200);
        String access = (String) pair.get("accessToken");
        client.post()
                .uri("/auth/v1/logout/1")
                .headers(headers -> headers.setBearerAuth((String) pair.get("refreshToken")))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        StepVerifier.create(tokens.validateToken(access).contextWrite(TenantContext.withTenant("other")))
                .expectError()
                .verify(Duration.ofSeconds(5));
        client.post()
                .uri("/auth/v1/logout/1?tenantId=other")
                .header("X-Tenant-Id", "other")
                .headers(headers -> headers.setBearerAuth(access))
                .exchange()
                .expectStatus()
                .isOk();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_token WHERE user_id=1 AND revoked=0", Integer.class))
                .isZero();
    }

    private Map<?, ?> login(int status) {
        WebTestClient.ResponseSpec response = client.post()
                .uri("/auth/v1/login")
                .bodyValue(Map.of("username", "alice", "password", password, "tenantId", "default"))
                .exchange()
                .expectStatus()
                .isEqualTo(status);
        return status == 200 ? response.expectBody(Map.class).returnResult().getResponseBody() : Map.of();
    }
}
