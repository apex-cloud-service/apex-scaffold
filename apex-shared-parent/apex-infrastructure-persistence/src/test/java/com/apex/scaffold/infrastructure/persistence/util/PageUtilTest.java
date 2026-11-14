package com.apex.scaffold.infrastructure.persistence.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PageUtil 单元测试")
class PageUtilTest {

    @Test
    @DisplayName("normalizeOrderBy - 允许单字段和排序方向")
    void normalizeOrderBy_shouldAllowSingleColumnAndDirection() {
        assertThat(PageUtil.normalizeOrderBy("create_time desc")).isEqualTo("create_time desc");
        assertThat(PageUtil.normalizeOrderBy("updated_at ASC")).isEqualTo("updated_at asc");
    }

    @Test
    @DisplayName("normalizeOrderBy - 空值不启用排序")
    void normalizeOrderBy_shouldReturnNullForBlankValue() {
        assertThat(PageUtil.normalizeOrderBy(null)).isNull();
        assertThat(PageUtil.normalizeOrderBy("  ")).isNull();
    }

    @Test
    @DisplayName("normalizeOrderBy - 拒绝 SQL 片段和多字段排序")
    void normalizeOrderBy_shouldRejectUnsafeExpression() {
        assertThatIllegalArgumentException().isThrownBy(() -> PageUtil.normalizeOrderBy("create_time desc, id"));
        assertThatIllegalArgumentException().isThrownBy(() -> PageUtil.normalizeOrderBy("create_time desc --"));
    }
}
