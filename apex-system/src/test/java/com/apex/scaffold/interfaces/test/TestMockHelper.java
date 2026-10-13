package com.apex.scaffold.interfaces.test;

import com.apex.scaffold.common.constant.SystemConstants;
import java.util.Map;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 测试 Mock 工具类
 *
 * <p>提供 WebTestClient 认证辅助和响应解析等集成测试通用工具方法。
 *
 * @author Apex Team
 */
public final class TestMockHelper {

    private final WebTestClient webTestClient;

    public TestMockHelper(WebTestClient webTestClient) {
        this.webTestClient = webTestClient;
    }

    /**
     * 获取带认证头的 WebTestClient 构建器
     *
     * @param token JWT Token
     * @return WebTestClient.RequestHeadersSpec 构建器
     */
    public WebTestClient.RequestHeadersSpec<?> withAuth(String token) {
        return webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder.path("/api/user/info").build())
                .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + token);
    }

    /**
     * 解析响应体中的 Result 对象
     *
     * @param responseBody 原始响应体字符串
     * @return 解析后的 Result 对象
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseResult(String responseBody) {
        return com.apex.scaffold.common.util.JsonUtil.fromJson(responseBody, Map.class);
    }
}
