package com.apex.scaffold.common.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RateLimiter 接口限流注解测试
 *
 * <p>验证 RateLimiter 注解各属性的默认值是否与预期一致
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimiter 接口限流注解测试")
class RateLimiterTest {
    @Test
    @DisplayName("各属性应具有正确的默认值")
    void shouldHaveDefaultValues() throws Exception {
        Method valueMethod = RateLimiter.class.getMethod("value");
        Method limitMethod = RateLimiter.class.getMethod("limit");
        Method windowMethod = RateLimiter.class.getMethod("window");
        Method messageMethod = RateLimiter.class.getMethod("message");

        assertThat(valueMethod.getDefaultValue()).isEqualTo("");
        assertThat(limitMethod.getDefaultValue()).isEqualTo(100);
        assertThat(windowMethod.getDefaultValue()).isEqualTo(1);
        assertThat(messageMethod.getDefaultValue()).isEqualTo("请求过于频繁，请稍后再试");
    }
}
