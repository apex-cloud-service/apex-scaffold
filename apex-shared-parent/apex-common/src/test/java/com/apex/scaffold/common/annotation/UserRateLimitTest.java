package com.apex.scaffold.common.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * UserRateLimit 用户级别限流注解测试
 *
 * <p>验证 UserRateLimit 注解各属性的默认值是否与预期一致
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserRateLimit 用户级别限流注解测试")
class UserRateLimitTest {
    @Test
    @DisplayName("各属性应具有正确的默认值")
    void shouldHaveDefaultValues() throws Exception {
        Method prefixMethod = UserRateLimit.class.getMethod("prefix");
        Method keyMethod = UserRateLimit.class.getMethod("key");
        Method rateMethod = UserRateLimit.class.getMethod("rate");
        Method rateIntervalMethod = UserRateLimit.class.getMethod("rateInterval");
        Method messageMethod = UserRateLimit.class.getMethod("message");

        assertThat(prefixMethod.getDefaultValue()).isEqualTo("");
        assertThat(keyMethod.getDefaultValue()).isEqualTo("");
        assertThat(rateMethod.getDefaultValue()).isEqualTo(1.0);
        assertThat(rateIntervalMethod.getDefaultValue()).isEqualTo(1L);
        assertThat(messageMethod.getDefaultValue()).isEqualTo("操作过于频繁，请稍后再试");
    }
}
