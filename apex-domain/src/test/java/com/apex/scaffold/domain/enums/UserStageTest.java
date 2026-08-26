package com.apex.scaffold.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link UserStage} 的单元测试类
 *
 * <p>测试用户投票阶段枚举的常量定义、name()、ordinal() 和静态工厂方法。
 *
 * @author Apex Team
 */
class UserStageTest {

    @Nested
    @DisplayName("枚举常量定义")
    class EnumConstantsTest {

        /**
         * 执行 {@code shouldHaveTwoConstants} 操作。
         */
        @Test
        @DisplayName("应包含 2 个枚举常量")
        void shouldHaveTwoConstants() {
            assertThat(UserStage.values()).hasSize(2);
        }

        /**
         * 执行 {@code shouldContainNewbie} 操作。
         */
        @Test
        @DisplayName("应包含 NEWBIE 常量")
        void shouldContainNewbie() {
            assertThat(UserStage.valueOf("NEWBIE")).isEqualTo(UserStage.NEWBIE);
        }

        /**
         * 执行 {@code shouldContainMature} 操作。
         */
        @Test
        @DisplayName("应包含 MATURE 常量")
        void shouldContainMature() {
            assertThat(UserStage.valueOf("MATURE")).isEqualTo(UserStage.MATURE);
        }
    }

    @Nested
    @DisplayName("name() 方法")
    class NameMethodTest {

        /**
         * 执行 {@code newbieNameShouldBeNEWBIE} 操作。
         */
        @Test
        @DisplayName("NEWBIE 的 name() 应返回 NEWBIE")
        void newbieNameShouldBeNEWBIE() {
            assertThat(UserStage.NEWBIE.name()).isEqualTo("NEWBIE");
        }

        /**
         * 执行 {@code matureNameShouldBeMATURE} 操作。
         */
        @Test
        @DisplayName("MATURE 的 name() 应返回 MATURE")
        void matureNameShouldBeMATURE() {
            assertThat(UserStage.MATURE.name()).isEqualTo("MATURE");
        }
    }

    @Nested
    @DisplayName("ordinal() 方法")
    class OrdinalMethodTest {

        /**
         * 执行 {@code newbieOrdinalShouldBeZero} 操作。
         */
        @Test
        @DisplayName("NEWBIE 的 ordinal 应为 0")
        void newbieOrdinalShouldBeZero() {
            assertThat(UserStage.NEWBIE.ordinal()).isZero();
        }

        /**
         * 执行 {@code matureOrdinalShouldBeOne} 操作。
         */
        @Test
        @DisplayName("MATURE 的 ordinal 应为 1")
        void matureOrdinalShouldBeOne() {
            assertThat(UserStage.MATURE.ordinal()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("values() 方法")
    class ValuesMethodTest {

        /**
         * 执行 {@code valuesShouldReturnConstantsInDeclarationOrder} 操作。
         */
        @Test
        @DisplayName("values() 应返回按声明顺序排列的常量")
        void valuesShouldReturnConstantsInDeclarationOrder() {
            UserStage[] values = UserStage.values();
            assertThat(values[0]).isEqualTo(UserStage.NEWBIE);
            assertThat(values[1]).isEqualTo(UserStage.MATURE);
        }
    }

    @Nested
    @DisplayName("fromVoteCount(int) 静态工厂方法")
    class FromVoteCountMethodTest {

        /**
         * 执行 {@code countLessOrEqualThirtyShouldReturnNewbie} 操作。
         */
        @Test
        @DisplayName("voteCount <= 30 应返回 NEWBIE")
        void countLessOrEqualThirtyShouldReturnNewbie() {
            assertThat(UserStage.fromVoteCount(0)).isEqualTo(UserStage.NEWBIE);
            assertThat(UserStage.fromVoteCount(30)).isEqualTo(UserStage.NEWBIE);
        }

        /**
         * 执行 {@code countGreaterThanThirtyShouldReturnMature} 操作。
         */
        @Test
        @DisplayName("voteCount > 30 应返回 MATURE")
        void countGreaterThanThirtyShouldReturnMature() {
            assertThat(UserStage.fromVoteCount(31)).isEqualTo(UserStage.MATURE);
            assertThat(UserStage.fromVoteCount(100)).isEqualTo(UserStage.MATURE);
        }
    }
}
