package com.apex.scaffold.interfaces.test;

import com.apex.scaffold.common.constant.SystemConstants;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.MediaType;

/**
 * 认证流程集成测试
 *
 * <p>测试登录、Token 刷新、Token 校验等认证相关功能。
 * 涉及 /api/v1/auth/refresh 和带 Authorization 头访问需认证接口。
 *
 * @author Apex Team
 */
@DisplayName("认证流程集成测试")
@EnabledIfSystemProperty(named = "apex.remote-it.enabled", matches = "true")
class AuthRemoteIT extends BaseLocalIntegrationTest {
    public AuthRemoteIT(DataSource dataSource) {
        super(dataSource);
    }

    @Test
    @DisplayName("访问需认证接口 - 未携带Token应返回401")
    void authenticatedEndpoint_withoutToken_shouldReturn401() {
        webTestClient.get().uri("/api/v1/user/info").exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("访问需认证接口 - 携带无效Token应返回401")
    void authenticatedEndpoint_withInvalidToken_shouldReturn401() {
        webTestClient
                .get()
                .uri("/api/v1/user/info")
                .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + "invalid_token_value")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    @DisplayName("访问需认证接口 - 携带过期Token应返回401")
    void authenticatedEndpoint_withExpiredToken_shouldReturn401() {
        String expiredToken = generateExpiredToken(TEST_OPEN_ID);
        webTestClient
                .get()
                .uri("/api/v1/user/info")
                .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + expiredToken)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    /**
     * 刷新{@code token_withValidToken_shouldReturnNewToken}。
     */
    @Test
    @DisplayName("POST /api/v1/auth/refresh - 使用有效RefreshToken应返回新的Token对")
    void refreshToken_withValidToken_shouldReturnNewToken() {
        String refreshToken = generateRefreshToken(TEST_OPEN_ID, 60);
        webTestClient
                .post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", refreshToken))
                .exchange()
                .expectStatus()
                .isEqualTo(401);
    }

    /**
     * 刷新{@code token_withoutToken_shouldReturn400}。
     */
    @Test
    @DisplayName("POST /api/v1/auth/refresh - 不传RefreshToken应返回400错误")
    void refreshToken_withoutToken_shouldReturn400() {
        webTestClient
                .post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of())
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(400);
    }

    /**
     * 刷新{@code token_withExpiredToken_shouldReturn401}。
     */
    @Test
    @DisplayName("POST /api/v1/auth/refresh - 使用过期RefreshToken应返回401")
    void refreshToken_withExpiredToken_shouldReturn401() {
        String expiredToken = generateExpiredToken(TEST_OPEN_ID);
        webTestClient
                .post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", expiredToken))
                .exchange()
                .expectStatus()
                .isEqualTo(401);
    }

    /**
     * 刷新{@code token_withEmptyToken_shouldReturn400}。
     */
    @Test
    @DisplayName("POST /api/v1/auth/refresh - 使用空字符串RefreshToken应返回400")
    void refreshToken_withEmptyToken_shouldReturn400() {
        webTestClient
                .post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", ""))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(400);
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout - 未携带Token应返回401")
    void logout_withoutToken_shouldReturn401() {
        webTestClient
                .post()
                .uri("/api/v1/auth/logout")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout - 携带有效Token应返回200")
    void logout_withValidToken_shouldReturn200() {
        String accessToken = generateAccessToken(TEST_OPEN_ID, 60);
        webTestClient
                .post()
                .uri("/api/v1/auth/logout")
                .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("GET /api/v1/auth/roles/my - 未携带Token应返回401")
    void getMyRoles_withoutToken_shouldReturn401() {
        webTestClient
                .get()
                .uri("/api/v1/auth/roles/my")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}
