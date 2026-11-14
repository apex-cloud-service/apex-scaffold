package com.apex.scaffold.domain.model.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 图片审核领域结果单元测试。
 *
 * @author weiqiang.ling
 */
class ImageModerationResultTest {

    /**
     * 验证 {@code aggregatesUsingHighestSeverity} 场景。
     */
    @Test
    void aggregatesUsingHighestSeverity() {
        ImageModerationResult result = ImageModerationResult.aggregate(
                "ALIYUN_SCAN_IMAGE",
                "request-1",
                "task-1",
                List.of(
                        new ModerationFinding("ad", "pass", "normal", BigDecimal.valueOf(99)),
                        new ModerationFinding("porn", "review", "sexy", BigDecimal.valueOf(80)),
                        new ModerationFinding("live", "block", "drug", BigDecimal.valueOf(95))));

        assertThat(result.decision()).isEqualTo(ModerationDecision.BLOCK);
    }

    /**
     * 验证 {@code rejectsUnknownSuggestion} 场景。
     */
    @Test
    void rejectsUnknownSuggestion() {
        assertThatThrownBy(() -> ImageModerationResult.aggregate(
                        "ALIYUN_SCAN_IMAGE",
                        "request-1",
                        "task-1",
                        List.of(new ModerationFinding("porn", "unknown", "normal", BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证 {@code rejectsEmptyFindings} 场景。
     */
    @Test
    void rejectsEmptyFindings() {
        assertThatThrownBy(() -> ImageModerationResult.aggregate("ALIYUN_SCAN_IMAGE", "request-1", "task-1", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
