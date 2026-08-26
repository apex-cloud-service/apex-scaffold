package com.apex.scaffold.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 教育程度枚举测试。
 *
 * @author weiqiang.ling
 */
@DisplayName("EducationLevel")
class EducationLevelTest {

    /**
     * 验证 {@code parsesStableCode} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("编码稳定且可忽略大小写解析")
    void parsesStableCode() {
        assertThat(EducationLevel.BACHELOR.getCode()).isEqualTo("BACHELOR");
        assertThat(EducationLevel.fromCode(" bachelor ")).isEqualTo(EducationLevel.BACHELOR);
        assertThat(EducationLevel.BACHELOR.getDescription()).isEqualTo("本科");
    }

    /**
     * 验证 {@code invalidCodeReturnsNull} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("空值和非法编码安全返回null")
    void invalidCodeReturnsNull() {
        assertThat(EducationLevel.fromCode(null)).isNull();
        assertThat(EducationLevel.fromCode(" ")).isNull();
        assertThat(EducationLevel.fromCode("unrecognized")).isNull();
    }
}
