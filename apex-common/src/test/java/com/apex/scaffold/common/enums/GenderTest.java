package com.apex.scaffold.common.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 性别枚举测试
 *
 * <p>验证 {@link Gender} 枚举的定义完整性、查找方法边界条件和数值稳定性。
 * 涵盖标准路径、null 输入、无效码值等场景。
 *
 * @author Apex Team
 */
@DisplayName("Gender 枚举测试")
class GenderTest {

    @Nested
    @DisplayName("枚举常量定义")
    class EnumConstantsTest {

        /**
         * 验证枚举常量名称和描述非空
         */
        @Test
        void shouldHaveNonNullNameAndDesc() {
            for (Gender gender : Gender.values()) {
                assertNotNull(gender.getDesc(), "Gender." + gender + " 的 desc 不能为 null");
            }
        }

        /**
         * 验证枚举常量数量符合预期
         */
        @Test
        void shouldHaveExpectedCount() {
            assertThat(Gender.values())
                    .as("Gender 枚举应包含 3 个常量: UNKNOWN, MALE, FEMALE")
                    .hasSize(3);
        }
    }

    @Nested
    @DisplayName("人脸分析编码映射")
    class AnalysisCodeTest {

        /**
         * 执行 {@code shouldMapAnalysisCodes} 操作。
         */
        @Test
        void shouldMapAnalysisCodes() {
            assertThat(Gender.MALE.getAnalysisCode()).isEqualTo("male");
            assertThat(Gender.FEMALE.getAnalysisCode()).isEqualTo("female");
            assertThat(Gender.UNKNOWN.getAnalysisCode()).isEmpty();
            assertThat(Gender.getByAnalysisCode(" MALE ")).isEqualTo(Gender.MALE);
            assertThat(Gender.getByAnalysisCode("female")).isEqualTo(Gender.FEMALE);
            assertThat(Gender.getByAnalysisCode("1")).isEqualTo(Gender.MALE);
            assertThat(Gender.getByAnalysisCode("2")).isEqualTo(Gender.FEMALE);
            assertThat(Gender.getByAnalysisCode("unknown")).isEqualTo(Gender.UNKNOWN);
            assertThat(Gender.getByAnalysisCode(null)).isEqualTo(Gender.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("getByCode() 查找方法")
    class GetByCodeTest {

        /**
         * 验证有效码值能正确映射到对应的枚举常量
         */
        @Test
        void shouldReturnCorrectEnumForValidCodes() {
            assertThat(Gender.getByCode(0)).isEqualTo(Gender.UNKNOWN);
            assertThat(Gender.getByCode(1)).isEqualTo(Gender.MALE);
            assertThat(Gender.getByCode(2)).isEqualTo(Gender.FEMALE);
        }

        /**
         * 验证 null 输入返回 UNKNOWN 默认值
         */
        @Test
        void shouldReturnUnknownForNullCode() {
            assertThat(Gender.getByCode(null)).isEqualTo(Gender.UNKNOWN);
        }

        /**
         * 验证无效码值返回 UNKNOWN 默认值（安全降级）
         */
        @Test
        void shouldReturnUnknownForInvalidCode() {
            assertThat(Gender.getByCode(-1)).isEqualTo(Gender.UNKNOWN);
            assertThat(Gender.getByCode(999)).isEqualTo(Gender.UNKNOWN);
            assertThat(Gender.getByCode(100)).isEqualTo(Gender.UNKNOWN);
        }

        /**
         * 验证边界码值的行为
         *
         * <p>Gender 枚举定义码值 0=UNKNOWN, 1=MALE, 2=FEMALE
         */
        @Test
        void shouldHandleBoundaryCodes() {
            assertThat(Gender.getByCode(Integer.MAX_VALUE)).isEqualTo(Gender.UNKNOWN);
            assertThat(Gender.getByCode(Integer.MIN_VALUE)).isEqualTo(Gender.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("枚举属性")
    class EnumPropertiesTest {
        /**
         * 执行 {@code shouldHaveNonNullDeclaringClass} 操作。
         */
        @Test
        void shouldHaveNonNullDeclaringClass() {
            for (Gender gender : Gender.values()) {
                assertThat(gender.getDeclaringClass())
                        .as("Gender." + gender + " 的 declaring class 不能为 null")
                        .isNotNull();
            }
        }
    }
}
