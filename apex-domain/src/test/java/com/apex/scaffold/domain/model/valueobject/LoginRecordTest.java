package com.apex.scaffold.domain.model.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link LoginRecord} 值对象的单元测试类
 *
 * <p>测试登录记录的创建、IP校验、内网IP判断、同一天/同IP比较、近期判断等核心逻辑。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("LoginRecord 值对象单元测试")
class LoginRecordTest {

    @Nested
    @DisplayName("of 工厂方法")
    class OfTests {

        /**
         * 执行 {@code shouldCreateWithValidParams} 操作。
         */
        @Test
        @DisplayName("有效参数应创建成功")
        void shouldCreateWithValidParams() {
            LocalDateTime now = LocalDateTime.now();
            LoginRecord record = LoginRecord.of(now, "192.168.1.1");
            assertThat(record.getLoginTime()).isEqualTo(now);
            assertThat(record.getLoginIp()).isEqualTo("192.168.1.1");
        }

        /**
         * 执行 {@code shouldCreateWithNullIp} 操作。
         */
        @Test
        @DisplayName("IP为null时应创建成功")
        void shouldCreateWithNullIp() {
            LocalDateTime now = LocalDateTime.now();
            LoginRecord record = LoginRecord.of(now, null);
            assertThat(record.getLoginIp()).isNull();
        }

        /**
         * 执行 {@code shouldCreateWithBlankIp} 操作。
         */
        @Test
        @DisplayName("空白IP应规范化为null")
        void shouldCreateWithBlankIp() {
            LoginRecord record = LoginRecord.of(LocalDateTime.now(), "");
            assertThat(record.getLoginIp()).isNull();
        }

        /**
         * 执行 {@code shouldThrowExceptionWhenTimeIsNull} 操作。
         */
        @Test
        @DisplayName("登录时间为null时应抛出异常")
        void shouldThrowExceptionWhenTimeIsNull() {
            assertThatThrownBy(() -> LoginRecord.of(null, "192.168.1.1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("登录时间不能为空");
        }

        /**
         * 执行 {@code shouldThrowExceptionWhenInvalidIp} 操作。
         */
        @Test
        @DisplayName("无效IP格式应抛出异常")
        void shouldThrowExceptionWhenInvalidIp() {
            assertThatThrownBy(() -> LoginRecord.of(LocalDateTime.now(), "not-an-ip"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("无效的IP地址格式");
        }
    }

    @Nested
    @DisplayName("now 工厂方法")
    class NowTests {

        /**
         * 执行 {@code shouldCreateRecordWithCurrentTime} 操作。
         */
        @Test
        @DisplayName("应创建当前时间的登录记录")
        void shouldCreateRecordWithCurrentTime() {
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);
            LoginRecord record = LoginRecord.now("192.168.1.1");
            assertThat(record.getLoginTime()).isAfter(before);
            assertThat(record.getLoginIp()).isEqualTo("192.168.1.1");
        }
    }

    @Nested
    @DisplayName("isInternalIp 方法")
    class IsInternalIpTests {

        /**
         * 执行 {@code shouldDetectClassAInternalIp} 操作。
         */
        @Test
        @DisplayName("10.x.x.x 应判断为内网IP")
        void shouldDetectClassAInternalIp() {
            LoginRecord record = LoginRecord.of(LocalDateTime.now(), "10.0.0.1");
            assertThat(record.isInternalIp()).isTrue();
        }

        /**
         * 执行 {@code shouldDetectClassBInternalIpLow} 操作。
         */
        @Test
        @DisplayName("172.16.x.x 应判断为内网IP")
        void shouldDetectClassBInternalIpLow() {
            LoginRecord record = LoginRecord.of(LocalDateTime.now(), "172.16.0.1");
            assertThat(record.isInternalIp()).isTrue();
        }

        /**
         * 执行 {@code shouldDetectClassBInternalIpHigh} 操作。
         */
        @Test
        @DisplayName("172.31.x.x 应判断为内网IP")
        void shouldDetectClassBInternalIpHigh() {
            LoginRecord record = LoginRecord.of(LocalDateTime.now(), "172.31.0.1");
            assertThat(record.isInternalIp()).isTrue();
        }

        /**
         * 执行 {@code shouldDetectClassCInternalIp} 操作。
         */
        @Test
        @DisplayName("192.168.x.x 应判断为内网IP")
        void shouldDetectClassCInternalIp() {
            LoginRecord record = LoginRecord.of(LocalDateTime.now(), "192.168.1.1");
            assertThat(record.isInternalIp()).isTrue();
        }

        /**
         * 执行 {@code shouldDetectLoopbackAsInternal} 操作。
         */
        @Test
        @DisplayName("127.0.0.1 应判断为内网IP")
        void shouldDetectLoopbackAsInternal() {
            LoginRecord record = LoginRecord.of(LocalDateTime.now(), "127.0.0.1");
            assertThat(record.isInternalIp()).isTrue();
        }

        /**
         * 执行 {@code shouldDetectPublicIp} 操作。
         */
        @Test
        @DisplayName("公网IP应判断为非内网IP")
        void shouldDetectPublicIp() {
            LoginRecord record = LoginRecord.of(LocalDateTime.now(), "8.8.8.8");
            assertThat(record.isInternalIp()).isFalse();
        }

        /**
         * 执行 {@code shouldReturnFalseForNullIp} 操作。
         */
        @Test
        @DisplayName("IP为null时应返回false")
        void shouldReturnFalseForNullIp() {
            LoginRecord record = LoginRecord.of(LocalDateTime.now(), (String) null);
            assertThat(record.isInternalIp()).isFalse();
        }
    }

    @Nested
    @DisplayName("isSameDay 方法")
    class IsSameDayTests {

        /**
         * 执行 {@code shouldReturnTrueForSameDay} 操作。
         */
        @Test
        @DisplayName("同一天的记录应返回 true")
        void shouldReturnTrueForSameDay() {
            LocalDateTime time = LocalDateTime.of(2025, 6, 15, 10, 0);
            LoginRecord record1 = LoginRecord.of(time, "192.168.1.1");
            LoginRecord record2 = LoginRecord.of(time.plusHours(5), "10.0.0.1");
            assertThat(record1.isSameDay(record2)).isTrue();
        }

        /**
         * 执行 {@code shouldReturnFalseForDifferentDay} 操作。
         */
        @Test
        @DisplayName("不同天的记录应返回 false")
        void shouldReturnFalseForDifferentDay() {
            LoginRecord record1 = LoginRecord.of(LocalDateTime.of(2025, 6, 15, 10, 0), "192.168.1.1");
            LoginRecord record2 = LoginRecord.of(LocalDateTime.of(2025, 6, 16, 10, 0), "192.168.1.1");
            assertThat(record1.isSameDay(record2)).isFalse();
        }

        /**
         * 执行 {@code shouldReturnFalseForNull} 操作。
         */
        @Test
        @DisplayName("比较null时应返回 false")
        void shouldReturnFalseForNull() {
            LoginRecord record = LoginRecord.of(LocalDateTime.now(), "192.168.1.1");
            assertThat(record.isSameDay(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("isSameIp 方法")
    class IsSameIpTests {

        /**
         * 执行 {@code shouldReturnTrueForSameIp} 操作。
         */
        @Test
        @DisplayName("相同IP应返回 true")
        void shouldReturnTrueForSameIp() {
            LoginRecord record1 = LoginRecord.of(LocalDateTime.now(), "192.168.1.1");
            LoginRecord record2 = LoginRecord.of(LocalDateTime.now(), "192.168.1.1");
            assertThat(record1.isSameIp(record2)).isTrue();
        }

        /**
         * 执行 {@code shouldReturnFalseForDifferentIp} 操作。
         */
        @Test
        @DisplayName("不同IP应返回 false")
        void shouldReturnFalseForDifferentIp() {
            LoginRecord record1 = LoginRecord.of(LocalDateTime.now(), "192.168.1.1");
            LoginRecord record2 = LoginRecord.of(LocalDateTime.now(), "10.0.0.1");
            assertThat(record1.isSameIp(record2)).isFalse();
        }

        /**
         * 执行 {@code shouldReturnTrueWhenBothNull} 操作。
         */
        @Test
        @DisplayName("双方IP均为null应返回 true")
        void shouldReturnTrueWhenBothNull() {
            LoginRecord record1 = LoginRecord.of(LocalDateTime.now(), (String) null);
            LoginRecord record2 = LoginRecord.of(LocalDateTime.now(), (String) null);
            assertThat(record1.isSameIp(record2)).isTrue();
        }
    }

    @Nested
    @DisplayName("isRecent 方法")
    class IsRecentTests {

        /**
         * 执行 {@code shouldBeRecentJustCreated} 操作。
         */
        @Test
        @DisplayName("刚创建的记录应在近期内")
        void shouldBeRecentJustCreated() {
            LoginRecord record = LoginRecord.now("192.168.1.1");
            assertThat(record.isRecent(10)).isTrue();
        }
    }

    @Nested
    @DisplayName("getFormattedTime 方法")
    class GetFormattedTimeTests {

        /**
         * 执行 {@code shouldReturnFormattedTime} 操作。
         */
        @Test
        @DisplayName("应返回 yyyy-MM-dd HH:mm:ss 格式")
        void shouldReturnFormattedTime() {
            LocalDateTime time = LocalDateTime.of(2025, 6, 15, 14, 30, 45);
            LoginRecord record = LoginRecord.of(time, "192.168.1.1");
            assertThat(record.getFormattedTime()).isEqualTo("2025-06-15 14:30:45");
        }
    }
}
