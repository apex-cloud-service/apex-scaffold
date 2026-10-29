package com.apex.scaffold.common.util;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 日期时间工具类
 *
 * <p>提供日期时间格式化、解析、计算、比较等功能。
 * 部分方法提供 {@link Clock} 参数的重载版本以支持单元测试中的时间注入。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class DateUtil {

    /** 私有构造函数，防止实例化 */
    private DateUtil() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    // ==================== 日期时间格式 ====================

    /** 标准日期格式：yyyy-MM-dd */
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    /** 标准时间格式：HH:mm:ss */
    public static final String TIME_PATTERN = "HH:mm:ss";

    /** 标准日期时间格式：yyyy-MM-dd HH:mm:ss */
    public static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 标准日期时间格式（带毫秒）：yyyy-MM-dd HH:mm:ss.SSS */
    public static final String DATETIME_MS_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";

    /** 标准日期时间格式（带时区）：yyyy-MM-dd HH:mm:ss Z */
    public static final String DATETIME_ZONE_PATTERN = "yyyy-MM-dd HH:mm:ss Z";

    // ==================== 预编译的DateTimeFormatter（线程安全，避免重复创建）====================

    /** 日期格式化器：yyyy-MM-dd */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);

    /** 时间格式化器：HH:mm:ss */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(TIME_PATTERN);

    /** 日期时间格式化器：yyyy-MM-dd HH:mm:ss */
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern(DATETIME_PATTERN);

    /** 日期时间格式化器（带毫秒）：yyyy-MM-dd HH:mm:ss.SSS */
    private static final DateTimeFormatter DATETIME_MS_FORMATTER = DateTimeFormatter.ofPattern(DATETIME_MS_PATTERN);

    /** 日期时间格式化器（带时区）：yyyy-MM-dd HH:mm:ss Z */
    private static final DateTimeFormatter DATETIME_ZONE_FORMATTER = DateTimeFormatter.ofPattern(DATETIME_ZONE_PATTERN);

    // ==================== 日期时间格式化 ====================

    /**
     * 格式化日期为字符串
     *
     * @param date 日期
     * @param pattern 格式
     * @return 格式化后的字符串
     */
    public static String format(Date date, String pattern) {
        if (date == null) {
            return null;
        }
        if (pattern == null) {
            throw new IllegalArgumentException("pattern 不能为 null");
        }
        Instant instant = date.toInstant();
        LocalDateTime localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
        return format(localDateTime, pattern);
    }

    /**
     * 格式化LocalDate为字符串
     *
     * @param localDate LocalDate
     * @param pattern 格式
     * @return 格式化后的字符串
     */
    public static String format(LocalDate localDate, String pattern) {
        if (localDate == null) {
            return null;
        }
        if (pattern == null) {
            throw new IllegalArgumentException("pattern 不能为 null");
        }
        // 优先使用预编译的formatter
        DateTimeFormatter formatter = getPrecompiledFormatter(pattern);
        return localDate.format(formatter);
    }

    /**
     * 格式化LocalTime为字符串
     *
     * @param localTime LocalTime
     * @param pattern 格式
     * @return 格式化后的字符串
     */
    public static String format(LocalTime localTime, String pattern) {
        if (localTime == null) {
            return null;
        }
        if (pattern == null) {
            throw new IllegalArgumentException("pattern 不能为 null");
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return localTime.format(formatter);
    }

    /**
     * 格式化LocalDateTime为字符串
     *
     * @param localDateTime LocalDateTime
     * @param pattern 格式
     * @return 格式化后的字符串
     */
    public static String format(LocalDateTime localDateTime, String pattern) {
        if (localDateTime == null) {
            return null;
        }
        if (pattern == null) {
            throw new IllegalArgumentException("pattern 不能为 null");
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return localDateTime.format(formatter);
    }

    /**
     * 格式化Instant为字符串
     *
     * @param instant Instant
     * @param pattern 格式
     * @return 格式化后的字符串
     */
    public static String format(Instant instant, String pattern) {
        if (instant == null) {
            return null;
        }
        if (pattern == null) {
            throw new IllegalArgumentException("pattern 不能为 null");
        }
        LocalDateTime localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
        return format(localDateTime, pattern);
    }

    // ==================== 日期时间解析 ====================

    /**
     * 解析字符串为LocalDate
     *
     * @param dateStr 日期字符串
     * @param pattern 格式
     * @return LocalDate，解析失败时返回 null
     */
    public static LocalDate parseDate(String dateStr, String pattern) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        if (pattern == null) {
            throw new IllegalArgumentException("pattern 不能为 null");
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalDate.parse(dateStr, formatter);
        } catch (DateTimeParseException e) {
            log.warn("解析日期失败: {}", dateStr, e);
            return null;
        }
    }

    /**
     * 解析字符串为LocalTime
     *
     * @param timeStr 时间字符串
     * @param pattern 格式
     * @return LocalTime，解析失败时返回 null
     */
    public static LocalTime parseTime(String timeStr, String pattern) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }
        if (pattern == null) {
            throw new IllegalArgumentException("pattern 不能为 null");
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalTime.parse(timeStr, formatter);
        } catch (DateTimeParseException e) {
            log.warn("解析时间失败: {}", timeStr, e);
            return null;
        }
    }

    /**
     * 解析字符串为LocalDateTime
     *
     * @param dateTimeStr 日期时间字符串
     * @param pattern 格式
     * @return LocalDateTime，解析失败时返回 null
     */
    public static LocalDateTime parseDateTime(String dateTimeStr, String pattern) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return null;
        }
        if (pattern == null) {
            throw new IllegalArgumentException("pattern 不能为 null");
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalDateTime.parse(dateTimeStr, formatter);
        } catch (DateTimeParseException e) {
            log.warn("解析日期时间失败: {}", dateTimeStr, e);
            return null;
        }
    }

    // ==================== 日期时间计算 ====================

    /**
     * 日期加减天数
     *
     * @param localDate LocalDate
     * @param days 天数（正数为加，负数为减）
     * @return 计算后的LocalDate
     */
    public static LocalDate plusDays(LocalDate localDate, long days) {
        if (localDate == null) {
            return null;
        }
        return localDate.plusDays(days);
    }

    /**
     * 日期时间加减小时
     *
     * @param localDateTime LocalDateTime
     * @param hours 小时数（正数为加，负数为减）
     * @return 计算后的LocalDateTime
     */
    public static LocalDateTime plusHours(LocalDateTime localDateTime, long hours) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.plusHours(hours);
    }

    /**
     * 日期时间加减分钟
     *
     * @param localDateTime LocalDateTime
     * @param minutes 分钟数（正数为加，负数为减）
     * @return 计算后的LocalDateTime
     */
    public static LocalDateTime plusMinutes(LocalDateTime localDateTime, long minutes) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.plusMinutes(minutes);
    }

    /**
     * 日期时间加减秒
     *
     * @param localDateTime LocalDateTime
     * @param seconds 秒数（正数为加，负数为减）
     * @return 计算后的LocalDateTime
     */
    public static LocalDateTime plusSeconds(LocalDateTime localDateTime, long seconds) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.plusSeconds(seconds);
    }

    // ==================== 日期时间比较 ====================

    /**
     * 比较两个 LocalDate 的大小
     *
     * @param date1 第一个日期
     * @param date2 第二个日期
     * @return 比较结果：1 表示 date1 &gt; date2，0 表示 date1 == date2，-1 表示 date1 &lt; date2
     */
    public static int compare(LocalDate date1, LocalDate date2) {
        if (date1 == null || date2 == null) {
            throw new IllegalArgumentException("日期不能为null");
        }
        return date1.compareTo(date2);
    }

    /**
     * 比较两个 LocalDateTime 的大小
     *
     * @param dateTime1 第一个日期时间
     * @param dateTime2 第二个日期时间
     * @return 比较结果：1 表示 dateTime1 &gt; dateTime2，0 表示 dateTime1 == dateTime2，-1 表示 dateTime1 &lt;
     *         dateTime2
     */
    public static int compare(LocalDateTime dateTime1, LocalDateTime dateTime2) {
        if (dateTime1 == null || dateTime2 == null) {
            throw new IllegalArgumentException("日期时间不能为null");
        }
        return dateTime1.compareTo(dateTime2);
    }

    /**
     * 计算两个LocalDate之间的天数差
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 天数差
     */
    public static long daysBetween(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("日期不能为null");
        }
        return ChronoUnit.DAYS.between(startDate, endDate);
    }

    /**
     * 计算两个LocalDateTime之间的小时差
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 小时差
     */
    public static long hoursBetween(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("日期时间不能为null");
        }
        return ChronoUnit.HOURS.between(startTime, endTime);
    }

    /**
     * 计算两个LocalDateTime之间的分钟差
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 分钟差
     */
    public static long minutesBetween(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("日期时间不能为null");
        }
        return ChronoUnit.MINUTES.between(startTime, endTime);
    }

    /**
     * 计算两个LocalDateTime之间的秒差
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 秒差
     */
    public static long secondsBetween(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("日期时间不能为null");
        }
        return ChronoUnit.SECONDS.between(startTime, endTime);
    }

    // ==================== 常用日期时间操作 ====================

    /**
     * 获取当前日期
     *
     * @return 当前LocalDate
     */
    public static LocalDate nowDate() {
        return LocalDate.now();
    }

    /**
     * 获取当前日期（可注入 Clock 以便测试）
     *
     * @param clock 时钟，用于单元测试中的时间注入
     * @return 当前LocalDate
     */
    public static LocalDate nowDate(Clock clock) {
        return LocalDate.now(clock);
    }

    /**
     * 获取当前时间
     *
     * @return 当前LocalTime
     */
    public static LocalTime nowTime() {
        return LocalTime.now();
    }

    /**
     * 获取当前时间（可注入 Clock 以便测试）
     *
     * @param clock 时钟，用于单元测试中的时间注入
     * @return 当前LocalTime
     */
    public static LocalTime nowTime(Clock clock) {
        return LocalTime.now(clock);
    }

    /**
     * 获取当前日期时间
     *
     * @return 当前LocalDateTime
     */
    public static LocalDateTime nowDateTime() {
        return LocalDateTime.now();
    }

    /**
     * 获取当前日期时间（可注入 Clock 以便测试）
     *
     * @param clock 时钟，用于单元测试中的时间注入
     * @return 当前LocalDateTime
     */
    public static LocalDateTime nowDateTime(Clock clock) {
        return LocalDateTime.now(clock);
    }

    /**
     * 获取当前Instant
     *
     * @return 当前Instant
     */
    public static Instant nowInstant() {
        return Instant.now();
    }

    /**
     * 获取指定日期的开始时间（00:00:00）
     *
     * @param localDate LocalDate
     * @return 当天开始时间
     */
    public static LocalDateTime startOfDay(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return localDate.atStartOfDay();
    }

    /**
     * 获取指定日期的结束时间（23:59:59.999999999）
     *
     * @param localDate LocalDate
     * @return 当天结束时间
     */
    public static LocalDateTime endOfDay(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return localDate.atTime(LocalTime.MAX);
    }

    /**
     * 获取指定月份的第一天
     *
     * @param localDate LocalDate
     * @return 当月第一天
     */
    public static LocalDate firstDayOfMonth(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return localDate.with(TemporalAdjusters.firstDayOfMonth());
    }

    /**
     * 获取指定月份的最后一天
     *
     * @param localDate LocalDate
     * @return 当月最后一天
     */
    public static LocalDate lastDayOfMonth(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return localDate.with(TemporalAdjusters.lastDayOfMonth());
    }

    /**
     * 获取指定年份的第一天
     *
     * @param localDate LocalDate
     * @return 当年第一天
     */
    public static LocalDate firstDayOfYear(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return localDate.with(TemporalAdjusters.firstDayOfYear());
    }

    /**
     * 获取指定年份的最后一天
     *
     * @param localDate LocalDate
     * @return 当年最后一天
     */
    public static LocalDate lastDayOfYear(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return localDate.with(TemporalAdjusters.lastDayOfYear());
    }

    // ==================== 类型转换 ====================

    /**
     * Date转LocalDateTime
     *
     * @param date Date
     * @return LocalDateTime
     */
    public static LocalDateTime dateToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        Instant instant = date.toInstant();
        return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * LocalDateTime转Date
     *
     * @param localDateTime LocalDateTime
     * @return Date
     */
    public static Date localDateTimeToDate(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
        Instant instant = zonedDateTime.toInstant();
        return Date.from(instant);
    }

    /**
     * Instant转LocalDateTime
     *
     * @param instant Instant
     * @return LocalDateTime
     */
    public static LocalDateTime instantToLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * LocalDateTime转Instant
     *
     * @param localDateTime LocalDateTime
     * @return Instant
     */
    public static Instant localDateTimeToInstant(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
        return zonedDateTime.toInstant();
    }

    // ==================== 时区处理 ====================

    /**
     * 获取指定时区的当前时间
     *
     * @param zoneId 时区ID
     * @return 指定时区的LocalDateTime
     */
    public static LocalDateTime nowDateTime(String zoneId) {
        if (zoneId == null) {
            throw new IllegalArgumentException("zoneId 不能为 null");
        }
        return LocalDateTime.now(ZoneId.of(zoneId));
    }

    /**
     * 将LocalDateTime转换到指定时区
     *
     * @param localDateTime LocalDateTime
     * @param zoneId 目标时区ID
     * @return 转换后的LocalDateTime
     */
    public static LocalDateTime convertTimeZone(LocalDateTime localDateTime, String zoneId) {
        if (localDateTime == null) {
            return null;
        }
        ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
        ZonedDateTime targetZonedDateTime = zonedDateTime.withZoneSameInstant(ZoneId.of(zoneId));
        return targetZonedDateTime.toLocalDateTime();
    }

    // ==================== 内部工具方法 ====================

    /**
     * 预编译格式化器注册表：pattern → 线程安全的静态 {@link DateTimeFormatter}。
     *
     * <p>仅在首次访问时惰性初始化；未注册的 pattern 由
     * {@link #getPrecompiledFormatter(String)} 动态创建。</p>
     */
    private static final Map<String, DateTimeFormatter> PRECOMPILED_FORMATTERS = Map.of(
            DATE_PATTERN, DATE_FORMATTER,
            TIME_PATTERN, TIME_FORMATTER,
            DATETIME_PATTERN, DATETIME_FORMATTER,
            DATETIME_MS_PATTERN, DATETIME_MS_FORMATTER,
            DATETIME_ZONE_PATTERN, DATETIME_ZONE_FORMATTER);

    /**
     * 根据pattern获取预编译的DateTimeFormatter。
     *
     * <p>优先从 {@link #PRECOMPILED_FORMATTERS} 注册表返回静态 formatter；
     * 未注册的 pattern 动态创建（每次创建开销较小，调用方应避免高频动态 pattern）。</p>
     *
     * @param pattern 日期格式模式
     * @return DateTimeFormatter实例
     */
    private static DateTimeFormatter getPrecompiledFormatter(String pattern) {
        return PRECOMPILED_FORMATTERS.getOrDefault(pattern, DateTimeFormatter.ofPattern(pattern));
    }
}
