package com.apex.scaffold.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ActivityStatus} 的单元测试类
 *
 * <p>测试活跃度标签枚举的常量定义、name()、ordinal()、曝光系数获取和静态工厂方法。
 *
 * @author Apex Team
 */
class ActivityStatusTest {

    @Nested
    @DisplayName("枚举常量定义")
    class EnumConstantsTest {
        @Test
        @DisplayName("应包含 2 个枚举常量")
        void shouldHaveTwoConstants() {
            assertThat(ActivityStatus.values()).hasSize(2);
        }

        @Test
        @DisplayName("应包含 ACTIVE 常量")
        void shouldContainActive() {
            assertThat(ActivityStatus.valueOf("ACTIVE")).isEqualTo(ActivityStatus.ACTIVE);
        }

        @Test
        @DisplayName("应包含 INACTIVE 常量")
        void shouldContainInactive() {
            assertThat(ActivityStatus.valueOf("INACTIVE")).isEqualTo(ActivityStatus.INACTIVE);
        }
    }

    @Nested
    @DisplayName("name() 方法")
    class NameMethodTest {
        @Test
        @DisplayName("ACTIVE 的 name() 应返回 ACTIVE")
        void activeNameShouldBeACTIVE() {
            assertThat(ActivityStatus.ACTIVE.name()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("INACTIVE 的 name() 应返回 INACTIVE")
        void inactiveNameShouldBeINACTIVE() {
            assertThat(ActivityStatus.INACTIVE.name()).isEqualTo("INACTIVE");
        }
    }

    @Nested
    @DisplayName("ordinal() 方法")
    class OrdinalMethodTest {
        @Test
        @DisplayName("ACTIVE 的 ordinal 应为 0")
        void activeOrdinalShouldBeZero() {
            assertThat(ActivityStatus.ACTIVE.ordinal()).isZero();
        }

        @Test
        @DisplayName("INACTIVE 的 ordinal 应为 1")
        void inactiveOrdinalShouldBeOne() {
            assertThat(ActivityStatus.INACTIVE.ordinal()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getCoefficient() 方法")
    class GetCoefficientMethodTest {
        @Test
        @DisplayName("ACTIVE 的曝光系数应为 1.0")
        void activeCoefficientShouldBe1() {
            assertThat(ActivityStatus.ACTIVE.getCoefficient()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("INACTIVE 的曝光系数应为 0.0")
        void inactiveCoefficientShouldBe0() {
            assertThat(ActivityStatus.INACTIVE.getCoefficient()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("values() 方法")
    class ValuesMethodTest {
        @Test
        @DisplayName("values() 应返回按声明顺序排列的常量")
        void valuesShouldReturnConstantsInDeclarationOrder() {
            ActivityStatus[] values = ActivityStatus.values();
            assertThat(values[0]).isEqualTo(ActivityStatus.ACTIVE);
            assertThat(values[1]).isEqualTo(ActivityStatus.INACTIVE);
        }
    }

    @Nested
    @DisplayName("fromLastLoginTime(LocalDateTime) 静态工厂方法")
    class FromLastLoginTimeMethodTest {
        @Test
        @DisplayName("lastLoginTime 为 null 应返回 INACTIVE")
        void nullLastLoginTimeShouldReturnInactive() {
            assertThat(ActivityStatus.fromLastLoginTime(null)).isEqualTo(ActivityStatus.INACTIVE);
        }

        @Test
        @DisplayName("最近登录在 3 天内应返回 ACTIVE")
        void recentLoginShouldReturnActive() {
            LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);
            assertThat(ActivityStatus.fromLastLoginTime(twoDaysAgo)).isEqualTo(ActivityStatus.ACTIVE);
        }

        @Test
        @DisplayName("最近登录刚好 3 天应返回 ACTIVE")
        void exactlyThreeDaysShouldReturnActive() {
            LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
            assertThat(ActivityStatus.fromLastLoginTime(threeDaysAgo)).isEqualTo(ActivityStatus.ACTIVE);
        }

        @Test
        @DisplayName("最近登录超过 3 天应返回 INACTIVE")
        void longAgoLoginShouldReturnInactive() {
            LocalDateTime fiveDaysAgo = LocalDateTime.now().minusDays(5);
            assertThat(ActivityStatus.fromLastLoginTime(fiveDaysAgo)).isEqualTo(ActivityStatus.INACTIVE);
        }

        @Test
        @DisplayName("最近登录在 30 天前应返回 INACTIVE")
        void monthAgoLoginShouldReturnInactive() {
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            assertThat(ActivityStatus.fromLastLoginTime(thirtyDaysAgo)).isEqualTo(ActivityStatus.INACTIVE);
        }
    }
}
