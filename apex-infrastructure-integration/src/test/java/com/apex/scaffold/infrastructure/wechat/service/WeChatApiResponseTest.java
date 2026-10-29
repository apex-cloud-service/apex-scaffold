package com.apex.scaffold.infrastructure.wechat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.infrastructure.wechat.exception.WeChatApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link WeChatApiResponse} 动态 JSON 边界适配测试。
 *
 * @author Apex Team
 * @since 3.3.0
 */
@DisplayName("微信 API 响应适配器")
class WeChatApiResponseTest {

    /**
     * 验证字符串形式的成功码和包含空值的响应都可安全转换。
     */
    @Test
    @DisplayName("成功响应应保留兼容字段和空值")
    void shouldAcceptStringSuccessCodeAndNullValue() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("errcode", "0");
        response.put("errmsg", null);
        response.put("data", Map.of("id", 1));

        WeChatApiResponse adapted = WeChatApiResponse.from(response);
        Map<String, Object> result = adapted.requireSuccess("查询标签");

        assertThat(adapted.errorMessage()).isEmpty();
        assertThat(result).containsEntry("errcode", "0").containsKey("errmsg");
        assertThatThrownBy(() -> result.put("newField", "value")).isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证非零错误码统一转为包含微信错误信息的领域异常。
     */
    @Test
    @DisplayName("业务错误码应转换为统一异常")
    void shouldRejectBusinessError() {
        assertThatThrownBy(() -> WeChatApiResponse.from(Map.of("errcode", 40013, "errmsg", "invalid appid"))
                        .requireSuccess("创建标签"))
                .isInstanceOf(WeChatApiException.class)
                .hasMessageContaining("40013")
                .hasMessageContaining("invalid appid");
    }
}
