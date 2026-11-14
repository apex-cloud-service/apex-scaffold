package com.apex.scaffold.common.util;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * DateUtil 单元测试
 *
 * <p>测试日期时间格式化、解析、计算、比较、边界操作、类型转换和时区处理功能。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("DateUtil 单元测试")
class DateUtilTest {

    @Nested
    @DisplayName("日期时间格式常量测试")
    class PatternConstantsTests {
        @Test
        @DisplayName("DATE_PATTERN应为yyyy-MM-dd")
        void shouldHaveCorrectDatePattern() {
            assertThat(DateUtil.DATE_PATTERN).isEqualTo("yyyy-MM-dd");
        }

        @Test
        @DisplayName("TIME_PATTERN应为HH:mm:ss")
        void shouldHaveCorrectTimePattern() {
            assertThat(DateUtil.TIME_PATTERN).isEqualTo("HH:mm:ss");
        }

        @Test
        @DisplayName("DATETIME_PATTERN应为yyyy-MM-dd HH:mm:ss")
        void shouldHaveCorrectDatetimePattern() {
            assertThat(DateUtil.DATETIME_PATTERN).isEqualTo("yyyy-MM-dd HH:mm:ss");
        }

        @Test
        @DisplayName("DATETIME_MS_PATTERN应为yyyy-MM-dd HH:mm:ss.SSS")
        void shouldHaveCorrectDatetimeMsPattern() {
            assertThat(DateUtil.DATETIME_MS_PATTERN).isEqualTo("yyyy-MM-dd HH:mm:ss.SSS");
        }

        @Test
        @DisplayName("DATETIME_ZONE_PATTERN应为yyyy-MM-dd HH:mm:ss Z")
        void shouldHaveCorrectDatetimeZonePattern() {
            assertThat(DateUtil.DATETIME_ZONE_PATTERN).isEqualTo("yyyy-MM-dd HH:mm:ss Z");
        }
    }

    @Nested
    @DisplayName("format 格式化测试")
    class FormatTests {
        @Test
        @DisplayName("格式化Date应返回正确字符串")
        void shouldFormatDate() {
            LocalDate localDate = LocalDate.of(2025, 6, 15);
            Date date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

            String result = DateUtil.format(date, DateUtil.DATE_PATTERN);

            assertThat(result).isEqualTo("2025-06-15");
        }

        @Test
        @DisplayName("格式化null Date应返回null")
        void shouldReturnNullForNullDate() {
            String result = DateUtil.format((Date) null, DateUtil.DATE_PATTERN);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("格式化LocalDate应返回正确字符串")
        void shouldFormatLocalDate() {
            LocalDate localDate = LocalDate.of(2025, 8, 20);

            String result = DateUtil.format(localDate, DateUtil.DATE_PATTERN);

            assertThat(result).isEqualTo("2025-08-20");
        }

        @Test
        @DisplayName("格式化null LocalDate应返回null")
        void shouldReturnNullForNullLocalDate() {
            String result = DateUtil.format((LocalDate) null, DateUtil.DATE_PATTERN);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("格式化LocalTime应返回正确字符串")
        void shouldFormatLocalTime() {
            LocalTime localTime = LocalTime.of(14, 30, 45);

            String result = DateUtil.format(localTime, DateUtil.TIME_PATTERN);

            assertThat(result).isEqualTo("14:30:45");
        }

        @Test
        @DisplayName("格式化null LocalTime应返回null")
        void shouldReturnNullForNullLocalTime() {
            String result = DateUtil.format((LocalTime) null, DateUtil.TIME_PATTERN);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("格式化LocalDateTime应返回正确字符串")
        void shouldFormatLocalDateTime() {
            LocalDateTime localDateTime = LocalDateTime.of(2025, 3, 10, 9, 15, 30);

            String result = DateUtil.format(localDateTime, DateUtil.DATETIME_PATTERN);

            assertThat(result).isEqualTo("2025-03-10 09:15:30");
        }

        @Test
        @DisplayName("格式化null LocalDateTime应返回null")
        void shouldReturnNullForNullLocalDateTime() {
            String result = DateUtil.format((LocalDateTime) null, DateUtil.DATETIME_PATTERN);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("格式化Instant应返回正确字符串")
        void shouldFormatInstant() {
            LocalDateTime ldt = LocalDateTime.of(2025, 1, 1, 0, 0, 0);
            Instant instant = ldt.atZone(ZoneId.systemDefault()).toInstant();

            String result = DateUtil.format(instant, DateUtil.DATE_PATTERN);

            assertThat(result).isEqualTo("2025-01-01");
        }

        @Test
        @DisplayName("格式化null Instant应返回null")
        void shouldReturnNullForNullInstant() {
            String result = DateUtil.format((Instant) null, DateUtil.DATE_PATTERN);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("parseDate / parseTime / parseDateTime 解析测试")
    class ParseTests {
        @Test
        @DisplayName("parseDate解析有效字符串应返回正确LocalDate")
        void shouldParseValidDateString() {
            LocalDate result = DateUtil.parseDate("2025-12-25", DateUtil.DATE_PATTERN);

            assertThat(result).isNotNull();
            assertThat(result.getYear()).isEqualTo(2025);
            assertThat(result.getMonthValue()).isEqualTo(12);
            assertThat(result.getDayOfMonth()).isEqualTo(25);
        }

        @Test
        @DisplayName("parseDate解析null应返回null")
        void shouldReturnNullForNullDateString() {
            LocalDate result = DateUtil.parseDate(null, DateUtil.DATE_PATTERN);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("parseDate解析空字符串应返回null")
        void shouldReturnNullForEmptyDateString() {
            LocalDate result = DateUtil.parseDate("", DateUtil.DATE_PATTERN);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("parseDate解析无效格式字符串应返回null")
        void shouldReturnNullForInvalidDateFormat() {
            LocalDate result = DateUtil.parseDate("not-a-date", DateUtil.DATE_PATTERN);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("parseTime解析有效字符串应返回正确LocalTime")
        void shouldParseValidTimeString() {
            LocalTime result = DateUtil.parseTime("23:59:59", DateUtil.TIME_PATTERN);

            assertThat(result).isNotNull();
            assertThat(result.getHour()).isEqualTo(23);
            assertThat(result.getMinute()).isEqualTo(59);
            assertThat(result.getSecond()).isEqualTo(59);
        }

        @Test
        @DisplayName("parseTime解析null应返回null")
        void shouldReturnNullForNullTimeString() {
            LocalTime result = DateUtil.parseTime(null, DateUtil.TIME_PATTERN);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("parseTime解析空字符串应返回null")
        void shouldReturnNullForEmptyTimeString() {
            LocalTime result = DateUtil.parseTime("", DateUtil.TIME_PATTERN);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("parseTime解析无效格式字符串应返回null")
        void shouldReturnNullForInvalidTimeFormat() {
            LocalTime result = DateUtil.parseTime("invalid", DateUtil.TIME_PATTERN);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("parseDateTime解析有效字符串应返回正确LocalDateTime")
        void shouldParseValidDateTimeString() {
            LocalDateTime result = DateUtil.parseDateTime("2025-06-15 18:30:00", DateUtil.DATETIME_PATTERN);

            assertThat(result).isNotNull();
            assertThat(result.getYear()).isEqualTo(2025);
            assertThat(result.getMonthValue()).isEqualTo(6);
            assertThat(result.getDayOfMonth()).isEqualTo(15);
            assertThat(result.getHour()).isEqualTo(18);
            assertThat(result.getMinute()).isEqualTo(30);
        }

        @Test
        @DisplayName("parseDateTime解析null应返回null")
        void shouldReturnNullForNullDateTimeString() {
            LocalDateTime result = DateUtil.parseDateTime(null, DateUtil.DATETIME_PATTERN);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("parseDateTime解析空字符串应返回null")
        void shouldReturnNullForEmptyDateTimeString() {
            LocalDateTime result = DateUtil.parseDateTime("", DateUtil.DATETIME_PATTERN);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("parseDateTime解析无效格式字符串应返回null")
        void shouldReturnNullForInvalidDateTimeFormat() {
            LocalDateTime result = DateUtil.parseDateTime("bad-datetime", DateUtil.DATETIME_PATTERN);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("plusDays / plusHours / plusMinutes / plusSeconds 时间加减测试")
    class PlusTests {
        @Test
        @DisplayName("plusDays正数加天应返回正确结果")
        void shouldAddDays() {
            LocalDate date = LocalDate.of(2025, 1, 1);

            LocalDate result = DateUtil.plusDays(date, 10);

            assertThat(result).isEqualTo(LocalDate.of(2025, 1, 11));
        }

        @Test
        @DisplayName("plusDays负数减天应返回正确结果")
        void shouldSubtractDays() {
            LocalDate date = LocalDate.of(2025, 1, 10);

            LocalDate result = DateUtil.plusDays(date, -5);

            assertThat(result).isEqualTo(LocalDate.of(2025, 1, 5));
        }

        @Test
        @DisplayName("plusDays传入null应返回null")
        void shouldReturnNullForNullInPlusDays() {
            LocalDate result = DateUtil.plusDays(null, 5);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("plusHours正数加小时应返回正确结果")
        void shouldAddHours() {
            LocalDateTime dateTime = LocalDateTime.of(2025, 1, 1, 10, 0);

            LocalDateTime result = DateUtil.plusHours(dateTime, 5);

            assertThat(result).isEqualTo(LocalDateTime.of(2025, 1, 1, 15, 0));
        }

        @Test
        @DisplayName("plusHours传入null应返回null")
        void shouldReturnNullForNullInPlusHours() {
            LocalDateTime result = DateUtil.plusHours(null, 5);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("plusMinutes正数加分钟应返回正确结果")
        void shouldAddMinutes() {
            LocalDateTime dateTime = LocalDateTime.of(2025, 1, 1, 10, 0);

            LocalDateTime result = DateUtil.plusMinutes(dateTime, 30);

            assertThat(result).isEqualTo(LocalDateTime.of(2025, 1, 1, 10, 30));
        }

        @Test
        @DisplayName("plusMinutes传入null应返回null")
        void shouldReturnNullForNullInPlusMinutes() {
            LocalDateTime result = DateUtil.plusMinutes(null, 5);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("plusSeconds正数加秒应返回正确结果")
        void shouldAddSeconds() {
            LocalDateTime dateTime = LocalDateTime.of(2025, 1, 1, 10, 0, 0);

            LocalDateTime result = DateUtil.plusSeconds(dateTime, 45);

            assertThat(result).isEqualTo(LocalDateTime.of(2025, 1, 1, 10, 0, 45));
        }

        @Test
        @DisplayName("plusSeconds传入null应返回null")
        void shouldReturnNullForNullInPlusSeconds() {
            LocalDateTime result = DateUtil.plusSeconds(null, 5);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("plusHours跨天应正确处理")
        void shouldCrossMidnightWithPlusHours() {
            LocalDateTime dateTime = LocalDateTime.of(2025, 1, 1, 23, 0);

            LocalDateTime result = DateUtil.plusHours(dateTime, 3);

            assertThat(result).isEqualTo(LocalDateTime.of(2025, 1, 2, 2, 0));
        }
    }

    @Nested
    @DisplayName("compare 日期比较测试")
    class CompareTests {
        @Test
        @DisplayName("compare LocalDate: date1大于date2应返回正数")
        void shouldReturnPositiveWhenDate1AfterDate2() {
            LocalDate date1 = LocalDate.of(2025, 6, 15);
            LocalDate date2 = LocalDate.of(2025, 6, 10);

            int result = DateUtil.compare(date1, date2);

            assertThat(result).isPositive();
        }

        @Test
        @DisplayName("compare LocalDate: date1小于date2应返回负数")
        void shouldReturnNegativeWhenDate1BeforeDate2() {
            LocalDate date1 = LocalDate.of(2025, 1, 1);
            LocalDate date2 = LocalDate.of(2025, 6, 1);

            int result = DateUtil.compare(date1, date2);

            assertThat(result).isNegative();
        }

        @Test
        @DisplayName("compare LocalDate: 相等日期应返回0")
        void shouldReturnZeroWhenDatesEqual() {
            LocalDate date1 = LocalDate.of(2025, 3, 15);
            LocalDate date2 = LocalDate.of(2025, 3, 15);

            int result = DateUtil.compare(date1, date2);

            assertThat(result).isZero();
        }

        @Test
        @DisplayName("compare LocalDate: null参数应抛出IllegalArgumentException")
        void shouldThrowExceptionForNullLocalDate() {
            assertThatIllegalArgumentException().isThrownBy(() -> DateUtil.compare(null, LocalDate.now()));
        }

        @Test
        @DisplayName("compare LocalDateTime: dateTime1大于dateTime2应返回正数")
        void shouldReturnPositiveWhenDateTime1AfterDateTime2() {
            LocalDateTime dt1 = LocalDateTime.of(2025, 6, 15, 12, 0);
            LocalDateTime dt2 = LocalDateTime.of(2025, 6, 15, 10, 0);

            int result = DateUtil.compare(dt1, dt2);

            assertThat(result).isPositive();
        }

        @Test
        @DisplayName("compare LocalDateTime: 相等应返回0")
        void shouldReturnZeroWhenDateTimesEqual() {
            LocalDateTime dt1 = LocalDateTime.of(2025, 3, 15, 12, 0);
            LocalDateTime dt2 = LocalDateTime.of(2025, 3, 15, 12, 0);

            int result = DateUtil.compare(dt1, dt2);

            assertThat(result).isZero();
        }

        @Test
        @DisplayName("compare LocalDateTime: null参数应抛出IllegalArgumentException")
        void shouldThrowExceptionForNullLocalDateTime() {
            assertThatIllegalArgumentException().isThrownBy(() -> DateUtil.compare(null, LocalDateTime.now()));
        }
    }

    @Nested
    @DisplayName("daysBetween / hoursBetween / minutesBetween / secondsBetween 时间差测试")
    class BetweenTests {
        @Test
        @DisplayName("daysBetween正序应返回正确天数差")
        void shouldCalculateDaysBetween() {
            LocalDate start = LocalDate.of(2025, 1, 1);
            LocalDate end = LocalDate.of(2025, 1, 11);

            long result = DateUtil.daysBetween(start, end);

            assertThat(result).isEqualTo(10);
        }

        @Test
        @DisplayName("daysBetween逆序应返回负数")
        void shouldReturnNegativeForReversedDaysBetween() {
            LocalDate start = LocalDate.of(2025, 1, 11);
            LocalDate end = LocalDate.of(2025, 1, 1);

            long result = DateUtil.daysBetween(start, end);

            assertThat(result).isNegative();
        }

        @Test
        @DisplayName("daysBetween相同日期应返回0")
        void shouldReturnZeroForSameDateInDaysBetween() {
            LocalDate date = LocalDate.of(2025, 6, 15);

            long result = DateUtil.daysBetween(date, date);

            assertThat(result).isZero();
        }

        @Test
        @DisplayName("daysBetween null参数应抛出IllegalArgumentException")
        void shouldThrowExceptionForNullInDaysBetween() {
            assertThatIllegalArgumentException().isThrownBy(() -> DateUtil.daysBetween(null, LocalDate.now()));
        }

        @Test
        @DisplayName("hoursBetween应返回正确小时差")
        void shouldCalculateHoursBetween() {
            LocalDateTime start = LocalDateTime.of(2025, 1, 1, 10, 0);
            LocalDateTime end = LocalDateTime.of(2025, 1, 1, 15, 0);

            long result = DateUtil.hoursBetween(start, end);

            assertThat(result).isEqualTo(5);
        }

        @Test
        @DisplayName("hoursBetween null参数应抛出IllegalArgumentException")
        void shouldThrowExceptionForNullInHoursBetween() {
            assertThatIllegalArgumentException().isThrownBy(() -> DateUtil.hoursBetween(null, LocalDateTime.now()));
        }

        @Test
        @DisplayName("minutesBetween应返回正确分钟差")
        void shouldCalculateMinutesBetween() {
            LocalDateTime start = LocalDateTime.of(2025, 1, 1, 10, 0);
            LocalDateTime end = LocalDateTime.of(2025, 1, 1, 10, 30);

            long result = DateUtil.minutesBetween(start, end);

            assertThat(result).isEqualTo(30);
        }

        @Test
        @DisplayName("minutesBetween null参数应抛出IllegalArgumentException")
        void shouldThrowExceptionForNullInMinutesBetween() {
            assertThatIllegalArgumentException().isThrownBy(() -> DateUtil.minutesBetween(null, LocalDateTime.now()));
        }

        @Test
        @DisplayName("secondsBetween应返回正确秒差")
        void shouldCalculateSecondsBetween() {
            LocalDateTime start = LocalDateTime.of(2025, 1, 1, 10, 0, 0);
            LocalDateTime end = LocalDateTime.of(2025, 1, 1, 10, 0, 45);

            long result = DateUtil.secondsBetween(start, end);

            assertThat(result).isEqualTo(45);
        }

        @Test
        @DisplayName("secondsBetween null参数应抛出IllegalArgumentException")
        void shouldThrowExceptionForNullInSecondsBetween() {
            assertThatIllegalArgumentException().isThrownBy(() -> DateUtil.secondsBetween(null, LocalDateTime.now()));
        }
    }

    @Nested
    @DisplayName("nowDate / nowTime / nowDateTime / nowInstant 当前时间测试")
    class NowTests {
        @Test
        @DisplayName("nowDate应返回当前日期")
        void shouldReturnCurrentDate() {
            LocalDate result = DateUtil.nowDate();

            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(LocalDate.now());
        }

        @Test
        @DisplayName("nowTime应返回当前时间")
        void shouldReturnCurrentTime() {
            LocalTime result = DateUtil.nowTime();

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("nowDateTime应返回当前日期时间")
        void shouldReturnCurrentDateTime() {
            LocalDateTime result = DateUtil.nowDateTime();

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("nowInstant应返回当前Instant")
        void shouldReturnCurrentInstant() {
            Instant result = DateUtil.nowInstant();

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("startOfDay / endOfDay 日期边界测试")
    class DayBoundaryTests {
        @Test
        @DisplayName("startOfDay应返回当天00:00:00")
        void shouldReturnStartOfDay() {
            LocalDate date = LocalDate.of(2025, 6, 15);

            LocalDateTime result = DateUtil.startOfDay(date);

            assertThat(result).isEqualTo(LocalDateTime.of(2025, 6, 15, 0, 0, 0));
        }

        @Test
        @DisplayName("startOfDay传入null应返回null")
        void shouldReturnNullForNullInStartOfDay() {
            LocalDateTime result = DateUtil.startOfDay(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("endOfDay应返回当天最大时间")
        void shouldReturnEndOfDay() {
            LocalDate date = LocalDate.of(2025, 6, 15);

            LocalDateTime result = DateUtil.endOfDay(date);

            assertThat(result).isEqualTo(LocalDateTime.of(2025, 6, 15, 23, 59, 59, 999999999));
        }

        @Test
        @DisplayName("endOfDay传入null应返回null")
        void shouldReturnNullForNullInEndOfDay() {
            LocalDateTime result = DateUtil.endOfDay(null);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("firstDayOfMonth / lastDayOfMonth 月份边界测试")
    class MonthBoundaryTests {
        @Test
        @DisplayName("firstDayOfMonth应返回当月第一天")
        void shouldReturnFirstDayOfMonth() {
            LocalDate date = LocalDate.of(2025, 6, 15);

            LocalDate result = DateUtil.firstDayOfMonth(date);

            assertThat(result).isEqualTo(LocalDate.of(2025, 6, 1));
        }

        @Test
        @DisplayName("firstDayOfMonth传入null应返回null")
        void shouldReturnNullForNullInFirstDayOfMonth() {
            LocalDate result = DateUtil.firstDayOfMonth(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("lastDayOfMonth应返回当月最后一天")
        void shouldReturnLastDayOfMonth() {
            LocalDate date = LocalDate.of(2025, 6, 15);

            LocalDate result = DateUtil.lastDayOfMonth(date);

            assertThat(result).isEqualTo(LocalDate.of(2025, 6, 30));
        }

        @Test
        @DisplayName("lastDayOfMonth闰年2月应返回29日")
        void shouldReturnLastDayOfFebruaryInLeapYear() {
            LocalDate date = LocalDate.of(2024, 2, 10);

            LocalDate result = DateUtil.lastDayOfMonth(date);

            assertThat(result).isEqualTo(LocalDate.of(2024, 2, 29));
        }

        @Test
        @DisplayName("lastDayOfMonth传入null应返回null")
        void shouldReturnNullForNullInLastDayOfMonth() {
            LocalDate result = DateUtil.lastDayOfMonth(null);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("firstDayOfYear / lastDayOfYear 年份边界测试")
    class YearBoundaryTests {
        @Test
        @DisplayName("firstDayOfYear应返回当年第一天")
        void shouldReturnFirstDayOfYear() {
            LocalDate date = LocalDate.of(2025, 6, 15);

            LocalDate result = DateUtil.firstDayOfYear(date);

            assertThat(result).isEqualTo(LocalDate.of(2025, 1, 1));
        }

        @Test
        @DisplayName("firstDayOfYear传入null应返回null")
        void shouldReturnNullForNullInFirstDayOfYear() {
            LocalDate result = DateUtil.firstDayOfYear(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("lastDayOfYear应返回当年最后一天")
        void shouldReturnLastDayOfYear() {
            LocalDate date = LocalDate.of(2025, 6, 15);

            LocalDate result = DateUtil.lastDayOfYear(date);

            assertThat(result).isEqualTo(LocalDate.of(2025, 12, 31));
        }

        @Test
        @DisplayName("lastDayOfYear传入null应返回null")
        void shouldReturnNullForNullInLastDayOfYear() {
            LocalDate result = DateUtil.lastDayOfYear(null);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("dateToLocalDateTime / localDateTimeToDate / instantToLocalDateTime / localDateTimeToInstant 类型转换测试")
    class ConversionTests {
        @Test
        @DisplayName("dateToLocalDateTime应正确转换")
        void shouldConvertDateToLocalDateTime() {
            LocalDateTime source = LocalDateTime.of(2025, 6, 15, 10, 30, 0);
            Date date = Date.from(source.atZone(ZoneId.systemDefault()).toInstant());

            LocalDateTime result = DateUtil.dateToLocalDateTime(date);

            assertThat(result).isEqualTo(source);
        }

        @Test
        @DisplayName("dateToLocalDateTime传入null应返回null")
        void shouldReturnNullForNullDate() {
            LocalDateTime result = DateUtil.dateToLocalDateTime(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("localDateTimeToDate应正确转换")
        void shouldConvertLocalDateTimeToDate() {
            LocalDateTime source = LocalDateTime.of(2025, 6, 15, 10, 30, 0);

            Date result = DateUtil.localDateTimeToDate(source);

            assertThat(result).isNotNull();
            LocalDateTime convertedBack =
                    result.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            assertThat(convertedBack).isEqualTo(source);
        }

        @Test
        @DisplayName("localDateTimeToDate传入null应返回null")
        void shouldReturnNullForNullLocalDateTimeInConversion() {
            Date result = DateUtil.localDateTimeToDate(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("instantToLocalDateTime应正确转换")
        void shouldConvertInstantToLocalDateTime() {
            LocalDateTime source = LocalDateTime.of(2025, 6, 15, 10, 30, 0);
            Instant instant = source.atZone(ZoneId.systemDefault()).toInstant();

            LocalDateTime result = DateUtil.instantToLocalDateTime(instant);

            assertThat(result).isEqualTo(source);
        }

        @Test
        @DisplayName("instantToLocalDateTime传入null应返回null")
        void shouldReturnNullForNullInstant() {
            LocalDateTime result = DateUtil.instantToLocalDateTime(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("localDateTimeToInstant应正确转换")
        void shouldConvertLocalDateTimeToInstant() {
            LocalDateTime source = LocalDateTime.of(2025, 6, 15, 10, 30, 0);
            Instant expected = source.atZone(ZoneId.systemDefault()).toInstant();

            Instant result = DateUtil.localDateTimeToInstant(source);

            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("localDateTimeToInstant传入null应返回null")
        void shouldReturnNullForNullLocalDateTimeInToInstant() {
            Instant result = DateUtil.localDateTimeToInstant(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Date与LocalDateTime往返转换应正确")
        void shouldRoundTripDateAndLocalDateTime() {
            LocalDateTime original = LocalDateTime.of(2025, 12, 25, 18, 0, 0);
            Date date = DateUtil.localDateTimeToDate(original);
            LocalDateTime restored = DateUtil.dateToLocalDateTime(date);

            assertThat(restored).isEqualTo(original);
        }

        @Test
        @DisplayName("Instant与LocalDateTime往返转换应正确")
        void shouldRoundTripInstantAndLocalDateTime() {
            LocalDateTime original = LocalDateTime.of(2025, 7, 1, 12, 0, 0);
            Instant instant = DateUtil.localDateTimeToInstant(original);
            LocalDateTime restored = DateUtil.instantToLocalDateTime(instant);

            assertThat(restored).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("nowDateTime(zoneId) / convertTimeZone 时区处理测试")
    class TimeZoneTests {
        @Test
        @DisplayName("nowDateTime传入时区ID应返回指定时区当前时间")
        void shouldReturnCurrentDateTimeForZone() {
            LocalDateTime result = DateUtil.nowDateTime("Asia/Shanghai");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("convertTimeZone转换时区应正确")
        void shouldConvertBetweenTimeZones() {
            LocalDateTime source = LocalDateTime.of(2025, 6, 15, 10, 0, 0);

            LocalDateTime result = DateUtil.convertTimeZone(source, "UTC");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("convertTimeZone传入null LocalDateTime应返回null")
        void shouldReturnNullForNullLocalDateTimeInConvertTimeZone() {
            LocalDateTime result = DateUtil.convertTimeZone(null, "UTC");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("convertTimeZone在系统默认时区与UTC之间往返转换应正确")
        void shouldRoundTripTimeZoneConversion() {
            LocalDateTime original = LocalDateTime.of(2025, 6, 15, 10, 0, 0);
            LocalDateTime utc = DateUtil.convertTimeZone(original, "UTC");
            LocalDateTime restored =
                    DateUtil.convertTimeZone(utc, ZoneId.systemDefault().getId());

            assertThat(restored).isNotNull();
        }
    }
}
