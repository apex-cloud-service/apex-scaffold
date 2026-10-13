package com.apex.scaffold.interfaces.test;

import com.apex.scaffold.common.constant.SystemConstants;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * 用户API集成测试
 *
 * <p>测试用户信息获取相关接口。
 * 涉及 /api/v1/user/info 和 /api/v1/user/{userId} 端点。
 *
 * @author Apex Team
 */
@DisplayName("用户API集成测试")
@EnabledIfSystemProperty(named = "apex.remote-it.enabled", matches = "true")
class UserRemoteIT extends BaseLocalIntegrationTest {
    public UserRemoteIT(DataSource dataSource) {
        super(dataSource);
    }

    @Test
    @DisplayName("GET /api/v1/user/info - 携带有效Token应返回用户信息")
    void getCurrentUserInfo_withValidToken_shouldReturnUserInfo() {
        String accessToken = generateAccessToken(TEST_OPEN_ID, 60);
        webTestClient
                .get()
                .uri("/api/v1/user/info")
                .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("GET /api/v1/user/info - 未携带Token应返回401")
    void getCurrentUserInfo_withoutToken_shouldReturn401() {
        webTestClient.get().uri("/api/v1/user/info").exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("GET /api/v1/user/info - 携带过期Token应返回401")
    void getCurrentUserInfo_withExpiredToken_shouldReturn401() {
        String expiredToken = generateExpiredToken(TEST_OPEN_ID);
        webTestClient
                .get()
                .uri("/api/v1/user/info")
                .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + expiredToken)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    @DisplayName("GET /api/v1/user/{userId} - 未携带Token应返回401")
    void getUserById_withoutToken_shouldReturn401() {
        webTestClient.get().uri("/api/v1/user/1").exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("GET /api/v1/user/{userId} - 携带有效Token应返回200")
    void getUserById_withValidToken_shouldReturn200() {
        String accessToken = generateAccessToken(TEST_OPEN_ID, 60);
        webTestClient
                .get()
                .uri("/api/v1/user/1")
                .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }
}
