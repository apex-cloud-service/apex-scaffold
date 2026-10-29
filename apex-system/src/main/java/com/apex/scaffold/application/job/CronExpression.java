package com.apex.scaffold.application.job;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.BitSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * 轻量级 Cron 表达式解析与调度计算器。
 *
 * <p>支持 Quartz 风格 6 段与 7 段 Cron 表达式，不依赖任何第三方库，
 * 用于定时任务管理模块的动态调度。表达式格式：</p>
 *
 * <pre>{@code
 *   秒   分   时   日   月   周   [年]
 *   0-59 0-59 0-23 1-31 1-12 1-7  1970-2099
 * }</pre>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code *} — 任意值</li>
 *   <li>{@code ?} — 不指定，仅允许单独用于日字段或周字段</li>
 *   <li>{@code 1,5,10} — 列表</li>
 *   <li>{@code 1-5} — 范围</li>
 *   <li>{@code &#42;/5} — 步长</li>
 *   <li>{@code JAN-DEC} / {@code SUN-SAT} — 月份与星期英文简写</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>周字段取值 1-7，其中 1 表示周日（与 Quartz 一致），2-7 表示周一至周六。
 * 日字段与周字段必须且只能有一个使用 {@code ?}。</p>
 *
 * <h2>线程安全性</h2>
 * <p>实例解析完成后不可变，可安全并发复用。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class CronExpression {

    private static final int CRON_MIN_FIELDS = 6;
    private static final int CRON_MAX_FIELDS = 7;
    private static final int DAY_FIELD_INDEX = 3;
    private static final int WEEK_FIELD_INDEX = 5;
    private static final String WILDCARD = "*";
    private static final String QUESTION_MARK = "?";
    private static final String COMMA = ",";
    private static final int JWT_PARTS_COUNT = 2;

    /** 月名称简写到数值的映射。 */
    private static final Map<String, Integer> MONTH_NAMES = Map.ofEntries(
            Map.entry("JAN", 1),
            Map.entry("FEB", 2),
            Map.entry("MAR", 3),
            Map.entry("APR", 4),
            Map.entry("MAY", 5),
            Map.entry("JUN", 6),
            Map.entry("JUL", 7),
            Map.entry("AUG", 8),
            Map.entry("SEP", 9),
            Map.entry("OCT", 10),
            Map.entry("NOV", 11),
            Map.entry("DEC", 12));

    /** 星期名称简写到数值的映射（1=周日，与 Quartz 一致）。 */
    private static final Map<String, Integer> DAY_OF_WEEK_NAMES = Map.ofEntries(
            Map.entry("SUN", 1),
            Map.entry("MON", 2),
            Map.entry("TUE", 3),
            Map.entry("WED", 4),
            Map.entry("THU", 5),
            Map.entry("FRI", 6),
            Map.entry("SAT", 7));

    /** 扫描保护上限：最多允许向前跳跃 3660 次（约 10 年），防止异常表达式死循环。 */
    private static final int MAX_SCAN_ITERATIONS = 3660;

    /** 原始表达式。 */
    @lombok.Getter
    private final String expression;

    /** 秒字段取值集合（0-59）。 */
    private final BitSet seconds = new BitSet(60);

    /** 分字段取值集合（0-59）。 */
    private final BitSet minutes = new BitSet(60);

    /** 时字段取值集合（0-23）。 */
    private final BitSet hours = new BitSet(24);

    /** 日字段取值集合（1-31，index=day-1）。 */
    private final BitSet daysOfMonth = new BitSet(31);

    /** 月字段取值集合（1-12，index=month-1）。 */
    private final BitSet months = new BitSet(12);

    /** 周字段取值集合（1-7，index=day-1）。 */
    private final BitSet daysOfWeek = new BitSet(7);

    /** 是否显式指定了日字段。 */
    private final boolean dayFieldSpecified;

    /** 是否显式指定了周字段。 */
    private final boolean weekFieldSpecified;

    /** 年字段取值集合（1970-2099，index=year-1970），为空表示不限制年份。 */
    private final BitSet years = new BitSet(130);

    /**
     * 解析 Cron 表达式。
     *
     * @param expression Cron 表达式，6 段（秒-周）或 7 段（秒-年）
     * @throws IllegalArgumentException 当表达式为空或格式非法时抛出
     */
    public CronExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Cron表达式不能为空");
        }
        this.expression = expression.trim();

        String[] fields = this.expression.split("\\s+");
        if (fields.length < CRON_MIN_FIELDS || fields.length > CRON_MAX_FIELDS) {
            throw new IllegalArgumentException("Cron表达式必须包含6段（秒-周）或7段（秒-年），当前为 " + fields.length + " 段");
        }

        validateQuestionMarks(fields);

        parseField(fields[0], 0, 59, null, seconds);
        parseField(fields[1], 0, 59, null, minutes);
        parseField(fields[2], 0, 23, null, hours);

        String dayField = fields[DAY_FIELD_INDEX];
        this.dayFieldSpecified = !WILDCARD.equals(dayField) && !QUESTION_MARK.equals(dayField);
        parseField(dayField, 1, 31, null, daysOfMonth);

        parseField(fields[4], 1, 12, MONTH_NAMES, months);

        String weekField = fields[WEEK_FIELD_INDEX];
        this.weekFieldSpecified = !WILDCARD.equals(weekField) && !QUESTION_MARK.equals(weekField);
        parseField(weekField, 1, 7, DAY_OF_WEEK_NAMES, daysOfWeek);

        if (fields.length == CRON_MAX_FIELDS) {
            parseField(fields[6], 1970, 2099, null, years);
        }
    }

    private void validateQuestionMarks(String[] fields) {
        for (int index = 0; index < fields.length; index++) {
            if (index != DAY_FIELD_INDEX && index != WEEK_FIELD_INDEX && fields[index].contains(QUESTION_MARK)) {
                throw new IllegalArgumentException("问号只能用于日字段或周字段");
            }
        }

        String dayField = fields[DAY_FIELD_INDEX];
        String weekField = fields[WEEK_FIELD_INDEX];
        boolean dayHasQuestionMark = dayField.contains(QUESTION_MARK) && !QUESTION_MARK.equals(dayField);
        boolean weekHasQuestionMark = weekField.contains(QUESTION_MARK) && !QUESTION_MARK.equals(weekField);
        if (dayHasQuestionMark || weekHasQuestionMark) {
            throw new IllegalArgumentException("问号必须单独用于日字段或周字段");
        }
        if (QUESTION_MARK.equals(dayField) == QUESTION_MARK.equals(weekField)) {
            throw new IllegalArgumentException("日字段和周字段必须且只能有一个使用问号");
        }
    }

    /**
     * 解析单个字段到取值集合。
     *
     * @param field 字段文本
     * @param min 最小值
     * @param max 最大值
     * @param names 名称映射（月份/星期），可为 {@code null}
     * @param target 目标取值集合
     */
    private void parseField(String field, int min, int max, Map<String, Integer> names, BitSet target) {
        if (WILDCARD.equals(field) || QUESTION_MARK.equals(field)) {
            target.set(0, max - min + 1);
            return;
        }

        TreeSet<Integer> values = new TreeSet<>();
        for (String part : field.split(COMMA)) {
            String stepStr = null;
            String rangeStr = part;
            int slashIndex = part.indexOf('/');
            if (slashIndex >= 0) {
                rangeStr = part.substring(0, slashIndex);
                stepStr = part.substring(slashIndex + 1);
            }

            int step = 1;
            if (stepStr != null && !stepStr.isEmpty()) {
                step = Integer.parseInt(stepStr);
                if (step <= 0) {
                    throw new IllegalArgumentException("步长必须为正整数: " + field);
                }
            }

            int rangeStart;
            int rangeEnd;
            if ("*".equals(rangeStr) || "?".equals(rangeStr)) {
                rangeStart = min;
                rangeEnd = max;
            } else if (rangeStr.contains("-")) {
                String[] bounds = rangeStr.split("-", 2);
                rangeStart = resolveValue(bounds[0], names, min, max);
                rangeEnd = resolveValue(bounds[1], names, min, max);
                if (rangeStart > rangeEnd) {
                    throw new IllegalArgumentException("范围起始不能大于结束: " + field);
                }
            } else {
                rangeStart = resolveValue(rangeStr, names, min, max);
                rangeEnd = stepStr == null ? rangeStart : max;
            }

            for (int v = rangeStart; v <= rangeEnd; v += step) {
                values.add(v);
            }
        }

        values.forEach(v -> target.set(v - min));
    }

    /**
     * 将字段值解析为数值。
     *
     * @param raw 原始文本
     * @param names 名称映射，可为 {@code null}
     * @param min 最小值
     * @param max 最大值
     * @return 解析后的数值
     */
    private int resolveValue(String raw, Map<String, Integer> names, int min, int max) {
        String trimmed = raw.trim();
        Integer named = names == null ? null : names.get(trimmed.toUpperCase(Locale.ROOT));
        int value;
        if (named != null) {
            value = named;
        } else {
            try {
                value = Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("无法解析的字段值: " + raw);
            }
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException("字段值超出范围 [" + min + "," + max + "]: " + raw);
        }
        return value;
    }

    /**
     * 计算指定时间之后的最近一次触发时间。
     *
     * @param after 参考时间（不含），通常为当前时间
     * @return 最近触发时间；无法在可扫描范围内找到时返回 {@link Optional#empty()}
     */
    public Optional<LocalDateTime> next(LocalDateTime after) {
        if (after == null) {
            throw new IllegalArgumentException("参考时间不能为空");
        }
        LocalDateTime t = after.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);

        for (int guard = 0; guard < MAX_SCAN_ITERATIONS; guard++) {
            if (!years.isEmpty() && !years.get(t.getYear() - 1970)) {
                t = LocalDateTime.of(t.getYear() + 1, 1, 1, 0, 0, 0);
                continue;
            }
            if (!months.get(t.getMonthValue() - 1)) {
                t = t.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
                continue;
            }
            if (!dayMatches(t)) {
                t = t.plusDays(1).withHour(0).withMinute(0).withSecond(0);
                continue;
            }
            if (!hours.get(t.getHour())) {
                t = t.plusHours(1).withMinute(0).withSecond(0);
                continue;
            }
            if (!minutes.get(t.getMinute())) {
                t = t.plusMinutes(1).withSecond(0);
                continue;
            }
            int second = t.getSecond();
            if (seconds.get(second)) {
                return Optional.of(t);
            }
            Integer nextSecond = nextInSet(seconds, second);
            if (nextSecond == null) {
                t = t.plusMinutes(1).withSecond(0);
                continue;
            }
            return Optional.of(t.withSecond(nextSecond));
        }
        return Optional.empty();
    }

    /**
     * 判断日期是否满足日/周字段约束。
     *
     * @param t 候选时间
     * @return true-日期匹配
     */
    private boolean dayMatches(LocalDateTime t) {
        return dayFieldSpecified
                ? daysOfMonth.get(t.getDayOfMonth() - 1)
                : !weekFieldSpecified || daysOfWeek.get(toCronDayOfWeek(t) - 1);
    }

    /**
     * 将 {@link java.time.DayOfWeek} 转换为 Cron 周值（1=周日）。
     *
     * @param t 候选时间
     * @return 1-7，1 表示周日
     */
    private int toCronDayOfWeek(LocalDateTime t) {
        // 1=周一 .. 7=周日
        int iso = t.getDayOfWeek().getValue();
        return iso == 7 ? 1 : iso + 1;
    }

    /**
     * 从取值集合中查找严格大于 {@code from} 的下一个值。
     *
     * @param set 取值集合
     * @param from 当前值（不含）
     * @return 下一个值；无更大值时返回 {@code null}
     */
    private Integer nextInSet(BitSet set, int from) {
        int next = set.nextSetBit(from + 1);
        return next < 0 ? null : next;
    }

    @Override
    public String toString() {
        return "CronExpression{" + expression + '}';
    }
}
