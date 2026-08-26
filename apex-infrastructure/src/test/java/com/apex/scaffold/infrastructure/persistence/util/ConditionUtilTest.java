package com.apex.scaffold.infrastructure.persistence.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ConditionUtil} 的单元测试。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@DisplayName("ConditionUtil 单元测试")
class ConditionUtilTest {

    /**
     * 执行 {@code ifPresent_shouldRunConsumerOnlyWhenValuePresent} 操作。
     */
    @Test
    @DisplayName("ifPresent - 值存在时执行 consumer，值为空时跳过")
    void ifPresent_shouldRunConsumerOnlyWhenValuePresent() {
        AtomicInteger count = new AtomicInteger(0);
        var wrapper = ConditionUtil.<Object>lambda();
        ConditionUtil.ifPresent(wrapper, null, value -> count.incrementAndGet());
        ConditionUtil.ifPresent(wrapper, 1, value -> count.incrementAndGet());

        assertThat(count.get()).isEqualTo(1);
    }

    /**
     * 执行 {@code ifPresentStr_shouldSkipBlankAndRunWhenHasText} 操作。
     */
    @Test
    @DisplayName("ifPresentStr - 空白字符串跳过，非空字符串执行 consumer")
    void ifPresentStr_shouldSkipBlankAndRunWhenHasText() {
        AtomicInteger count = new AtomicInteger(0);
        var wrapper = ConditionUtil.<Object>lambda();
        ConditionUtil.ifPresentStr(wrapper, "  ", value -> count.incrementAndGet());
        ConditionUtil.ifPresentStr(wrapper, "ok", value -> count.incrementAndGet());

        assertThat(count.get()).isEqualTo(1);
    }

    /**
     * 执行 {@code ifTrue_shouldRunWhenConditionTrue} 操作。
     */
    @Test
    @DisplayName("ifTrue - 条件为 true 执行 consumer")
    void ifTrue_shouldRunWhenConditionTrue() {
        AtomicInteger count = new AtomicInteger(0);
        var wrapper = ConditionUtil.<Object>lambda();
        ConditionUtil.ifTrue(wrapper, false, () -> count.incrementAndGet());
        ConditionUtil.ifTrue(wrapper, true, () -> count.incrementAndGet());

        assertThat(count.get()).isEqualTo(1);
    }
}
