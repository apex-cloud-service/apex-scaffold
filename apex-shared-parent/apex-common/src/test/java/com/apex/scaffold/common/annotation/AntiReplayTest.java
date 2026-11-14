package com.apex.scaffold.common.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AntiReplay 防重放攻击注解测试
 *
 * <p>验证 AntiReplay 注解各属性的默认值是否与预期一致
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AntiReplay 防重放攻击注解测试")
class AntiReplayTest {
    @Test
    @DisplayName("各属性应具有正确的默认值")
    void shouldHaveDefaultValues() throws Exception {
        Method maxTimeDiffMethod = AntiReplay.class.getMethod("maxTimeDiff");
        Method requireNonceMethod = AntiReplay.class.getMethod("requireNonce");

        assertThat(maxTimeDiffMethod.getDefaultValue()).isEqualTo(300L);
        assertThat(requireNonceMethod.getDefaultValue()).isEqualTo(true);
    }
}
