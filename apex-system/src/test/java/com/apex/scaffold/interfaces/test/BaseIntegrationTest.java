package com.apex.scaffold.interfaces.test;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 集成测试基类
 *
 * <p>所有集成测试应继承此类。提供用户创建和 JWT Token 生成等通用工具方法。
 *
 * <p>工具方法委托至以下辅助类：
 * <ul>
 *   <li>{@link TestTokenHelper} — JWT Token 生成</li>
 *   <li>{@link TestDataFactory} — 测试数据常量</li>
 *   <li>{@link TestMockHelper} — 认证辅助和响应解析</li>
 * </ul>
 *
 * @author Apex Team
 */
@SpringBootTest(classes = IntegrationTestApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ItProfileMockRedisConfiguration.class)
public abstract class BaseIntegrationTest {

    // ==================== 测试数据常量（委托至 TestDataFactory） ====================

    protected static final String TEST_OPEN_ID = TestDataFactory.TEST_OPEN_ID;
    protected static final String TEST_NICK_NAME = TestDataFactory.TEST_NICK_NAME;
    protected static final String TEST_AVATAR_URL = TestDataFactory.TEST_AVATAR_URL;

    // ==================== Spring 注入 ====================

    protected WebTestClient webTestClient;

    // ==================== Mock 辅助（实例化委托） ====================

    protected TestMockHelper mockHelper;

    /**
     * 构造器注入 WebTestClient。
     *
     * @param webTestClient Spring Boot 测试自动配置的 WebTestClient
     */
    public BaseIntegrationTest(WebTestClient webTestClient) {
        this.webTestClient = webTestClient;
    }

    // ==================== 生命周期回调 ====================

    /**
     * 设置{@code upBase}。
     */
    @BeforeAll
    void setUpBase() {
        webTestClient =
                webTestClient.mutate().responseTimeout(Duration.ofSeconds(30)).build();
        mockHelper = new TestMockHelper(webTestClient);
    }

    // ==================== Token 方法（委托至 TestTokenHelper） ====================

    /**
     * 生成测试用 JWT accessToken
     *
     * @param openId  用户 OpenID
     * @param minutes 过期时间（分钟）
     * @return JWT Token 字符串
     */
    protected String generateAccessToken(String openId, int minutes) {
        return TestTokenHelper.generateAccessToken(openId, minutes);
    }

    /**
     * 生成测试用 JWT refreshToken
     *
     * @param openId  用户 OpenID
     * @param minutes 过期时间（分钟）
     * @return JWT Token 字符串
     */
    protected String generateRefreshToken(String openId, int minutes) {
        return TestTokenHelper.generateRefreshToken(openId, minutes);
    }

    /**
     * 生成已过期的 JWT accessToken
     *
     * @param openId 用户 OpenID
     * @return 已过期的 JWT Token 字符串
     */
    protected String generateExpiredToken(String openId) {
        return TestTokenHelper.generateExpiredToken(openId);
    }

    // ==================== WebTestClient 辅助方法（委托至 TestMockHelper） ====================

    /**
     * 获取带认证头的 WebTestClient 构建器
     *
     * @param token JWT Token
     * @return WebTestClient.RequestHeadersSpec 构建器
     */
    protected WebTestClient.RequestHeadersSpec<?> withAuth(String token) {
        return mockHelper.withAuth(token);
    }

    /**
     * 解析响应体中的 Result 对象
     *
     * @param responseBody 原始响应体字符串
     * @return 解析后的 Result 对象
     */
    protected Map<String, Object> parseResult(String responseBody) {
        return TestMockHelper.parseResult(responseBody);
    }
}
