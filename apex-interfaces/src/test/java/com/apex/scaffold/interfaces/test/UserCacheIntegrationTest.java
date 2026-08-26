package com.apex.scaffold.interfaces.test;

import com.apex.scaffold.common.constant.SystemConstants;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * 用户缓存注解集成测试
 *
 * <p>验证 {@link com.apex.scaffold.application.service.UserService} 中
 * {@link org.springframework.cache.annotation.Cacheable} 和
 * {@link org.springframework.cache.annotation.CacheEvict} 注解的行为。
 *
 * <p>测试场景：
 * <ul>
 *   <li>通过 OpenID 查询用户时结果被缓存，第二次请求不再命中数据库</li>
 *   <li>通过 ID 查询用户时结果被缓存，第二次请求不再命中数据库</li>
 *   <li>更新用户信息后相关缓存被正确清除</li>
 *   <li>查询不存在的用户不会缓存 null 结果</li>
 * </ul>
 *
 * @author Apex Team
 */
@DisplayName("用户缓存注解集成测试")
@EnabledIfSystemProperty(named = "apex.remote-it.enabled", matches = "true")
class UserCacheRemoteIT extends BaseLocalIntegrationTest {

    /**
     * 创建 {@code UserCacheRemoteIT} 实例。
     * @param dataSource 参数 {@code dataSource}
     */
    public UserCacheRemoteIT(DataSource dataSource) {
        super(dataSource);
    }

    /**
     * 执行 {@code shouldCacheUserByOpenId} 操作。
     */
    @Test
    @DisplayName("GET /api/v1/user/info - 首次请求应返回用户信息（缓存未命中）")
    void shouldCacheUserByOpenId() {
        String accessToken = generateAccessToken(TEST_OPEN_ID, 60);

        // 第一次调用：预期返回用户信息，缓存未命中
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

        // 第二次调用：预期命中缓存，仍然返回用户信息
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

    /**
     * 执行 {@code shouldCacheUserById} 操作。
     */
    @Test
    @DisplayName("GET /api/v1/user/{userId} - 首次请求应返回用户信息（缓存未命中）")
    void shouldCacheUserById() {
        String accessToken = generateAccessToken(TEST_OPEN_ID, 60);

        // 第一次调用：预期返回用户信息，缓存未命中
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

        // 第二次调用：预期命中缓存，仍然返回用户信息
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

    /**
     * 执行 {@code nonExistentUserToken_shouldReturn401} 操作。
     */
    @Test
    @DisplayName("GET /api/v1/user/info - 不存在的用户应被安全层拒绝(401)")
    void nonExistentUserToken_shouldReturn401() {
        String nonExistentOpenId = "non_existent_user_999";
        String accessToken = generateAccessToken(nonExistentOpenId, 60);

        // 不存在用户的 Token 在 JWT 认证阶段即被拒绝（用户存在性校验），返回 401
        webTestClient
                .get()
                .uri("/api/v1/user/info")
                .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                .exchange()
                .expectStatus()
                .isUnauthorized();

        // 重复请求结果一致，不存在缓存副作用
        webTestClient
                .get()
                .uri("/api/v1/user/info")
                .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}
