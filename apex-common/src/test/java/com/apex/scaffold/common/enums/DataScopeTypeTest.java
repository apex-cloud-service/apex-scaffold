package com.apex.scaffold.common.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 数据权限类型枚举测试。
 *
 * <p>验证 {@link DataScopeType} 的五种类型定义、编码查找方法与边界输入。
 *
 * @author Apex Team
 */
@DisplayName("DataScopeType 枚举测试")
class DataScopeTypeTest {

    @Nested
    @DisplayName("枚举常量定义")
    class EnumConstantsTest {

        @Test
        void shouldHaveExpectedCount() {
            assertThat(DataScopeType.values())
                    .as("数据权限类型应包含 5 种: ALL/DEPT/DEPT_AND_CHILD/SELF/CUSTOM")
                    .hasSize(5);
        }

        @Test
        void shouldHaveUniqueCodes() {
            assertThat(DataScopeType.values())
                    .extracting(DataScopeType::getCode)
                    .doesNotHaveDuplicates();
        }

        @Test
        void shouldHaveNonBlankDesc() {
            for (DataScopeType type : DataScopeType.values()) {
                assertThat(type.getDesc())
                        .as("DataScopeType." + type + " 的 desc 不能为空白")
                        .isNotBlank();
            }
        }

        @Test
        void shouldHaveExpectedCodes() {
            assertThat(DataScopeType.ALL.getCode()).isEqualTo(1);
            assertThat(DataScopeType.DEPT.getCode()).isEqualTo(2);
            assertThat(DataScopeType.DEPT_AND_CHILD.getCode()).isEqualTo(3);
            assertThat(DataScopeType.SELF.getCode()).isEqualTo(4);
            assertThat(DataScopeType.CUSTOM.getCode()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("fromCode() 查找方法")
    class FromCodeTest {

        @Test
        void shouldReturnCorrectEnumForValidCodes() {
            assertThat(DataScopeType.fromCode(1)).isEqualTo(DataScopeType.ALL);
            assertThat(DataScopeType.fromCode(2)).isEqualTo(DataScopeType.DEPT);
            assertThat(DataScopeType.fromCode(3)).isEqualTo(DataScopeType.DEPT_AND_CHILD);
            assertThat(DataScopeType.fromCode(4)).isEqualTo(DataScopeType.SELF);
            assertThat(DataScopeType.fromCode(5)).isEqualTo(DataScopeType.CUSTOM);
        }

        @Test
        void shouldReturnNullForNullCode() {
            assertThat(DataScopeType.fromCode(null)).isNull();
        }

        @Test
        void shouldReturnNullForInvalidCode() {
            assertThat(DataScopeType.fromCode(0)).isNull();
            assertThat(DataScopeType.fromCode(99)).isNull();
            assertThat(DataScopeType.fromCode(-1)).isNull();
        }

        @Test
        void shouldFallbackToDefaultForInvalidCode() {
            assertThat(DataScopeType.fromCodeOrDefault(null, DataScopeType.DEPT)).isEqualTo(DataScopeType.DEPT);
            assertThat(DataScopeType.fromCodeOrDefault(99, DataScopeType.DEPT)).isEqualTo(DataScopeType.DEPT);
            assertThat(DataScopeType.fromCodeOrDefault(3, DataScopeType.DEPT)).isEqualTo(DataScopeType.DEPT_AND_CHILD);
        }
    }

    @Nested
    @DisplayName("fromName() 查找方法")
    class FromNameTest {

        @Test
        void shouldIgnoreCaseAndTrim() {
            assertThat(DataScopeType.fromName("dept")).isEqualTo(DataScopeType.DEPT);
            assertThat(DataScopeType.fromName(" DEPT_AND_CHILD ")).isEqualTo(DataScopeType.DEPT_AND_CHILD);
            assertThat(DataScopeType.fromName("all")).isEqualTo(DataScopeType.ALL);
        }

        @Test
        void shouldReturnNullForInvalidOrNullName() {
            assertThat(DataScopeType.fromName(null)).isNull();
            assertThat(DataScopeType.fromName("unknown")).isNull();
            assertThat(DataScopeType.fromName("")).isNull();
        }
    }
}
