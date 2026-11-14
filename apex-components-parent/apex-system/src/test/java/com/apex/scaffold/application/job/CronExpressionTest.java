package com.apex.scaffold.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cron 表达式解析器单元测试。
 *
 * <p>覆盖 6/7 段表达式的解析与下次触发时间计算，验证列表、范围、
 * 步长、名称简写与日/周字段互斥语义等核心能力。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class CronExpressionTest {

    /**
     * 测试每秒执行的表达式。
     */
    @Test
    @DisplayName("每秒执行 - 下次触发时间为下一秒")
    void everySecond() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 27, 10, 0, 0);
        CronExpression cron = new CronExpression("* * * * * ?");

        Optional<LocalDateTime> next = cron.next(base);

        assertThat(next).isPresent();
        assertThat(next.get()).isEqualTo(base.plusSeconds(1));
    }

    /**
     * 测试每 5 秒执行一次。
     */
    @Test
    @DisplayName("步长 - 每5秒执行")
    void stepEvery5Seconds() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 27, 10, 0, 0);
        CronExpression cron = new CronExpression("*/5 * * * * ?");

        Optional<LocalDateTime> next = cron.next(base);

        assertThat(next).isPresent();
        assertThat(next.get().getSecond()).isEqualTo(5);
    }

    /**
     * 测试每天凌晨 2 点执行。
     */
    @Test
    @DisplayName("每天凌晨2点 - 正确计算下次触发")
    void dailyAt2am() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 27, 10, 0, 0);
        CronExpression cron = new CronExpression("0 0 2 * * ?");

        Optional<LocalDateTime> next = cron.next(base);

        assertThat(next).isPresent();
        assertThat(next.get()).isEqualTo(LocalDateTime.of(2026, 8, 28, 2, 0, 0));
    }

    /**
     * 测试指定时间之前执行的情况。
     */
    @Test
    @DisplayName("每天凌晨2点 - 参考时间在触发时间之前")
    void dailyAt2amBeforeFire() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 27, 1, 0, 0);
        CronExpression cron = new CronExpression("0 0 2 * * ?");

        Optional<LocalDateTime> next = cron.next(base);

        assertThat(next).isPresent();
        assertThat(next.get()).isEqualTo(LocalDateTime.of(2026, 8, 27, 2, 0, 0));
    }

    /**
     * 测试列表字段。
     */
    @Test
    @DisplayName("列表字段 - 每小时的10分30分执行")
    void listFields() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 27, 10, 0, 0);
        CronExpression cron = new CronExpression("0 10,30 * * * ?");

        Optional<LocalDateTime> next = cron.next(base);

        assertThat(next).isPresent();
        assertThat(next.get()).isEqualTo(LocalDateTime.of(2026, 8, 27, 10, 10, 0));
    }

    /**
     * 测试范围字段。
     */
    @Test
    @DisplayName("范围字段 - 每分钟的5到10秒执行")
    void rangeFields() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 27, 10, 0, 0);
        CronExpression cron = new CronExpression("5-10 * * * * ?");

        Optional<LocalDateTime> next = cron.next(base);

        assertThat(next).isPresent();
        assertThat(next.get().getSecond()).isEqualTo(5);
    }

    /**
     * 测试月份英文简写。
     */
    @Test
    @DisplayName("月份简写 - JAN-DEC 解析")
    void monthNames() {
        LocalDateTime base = LocalDateTime.of(2026, 6, 1, 0, 0, 0);
        CronExpression cron = new CronExpression("0 0 0 1 JAN-MAR ?");

        Optional<LocalDateTime> next = cron.next(base);

        // 6月不在 1-3 月范围，应跳到次年 1 月 1 日
        assertThat(next).isPresent();
        assertThat(next.get()).isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0, 0));
    }

    /**
     * 测试周字段（1=周日）。
     */
    @Test
    @DisplayName("周字段 - 每周一上午9点执行")
    void dayOfWeek() {
        // 2026-08-27 是周四
        LocalDateTime base = LocalDateTime.of(2026, 8, 27, 12, 0, 0);
        CronExpression cron = new CronExpression("0 0 9 ? * MON");

        Optional<LocalDateTime> next = cron.next(base);

        // 下一个周一为 2026-08-31
        assertThat(next).isPresent();
        assertThat(next.get()).isEqualTo(LocalDateTime.of(2026, 8, 31, 9, 0, 0));
    }

    @Test
    @DisplayName("日与周字段 - 同时指定具体值时抛出异常")
    void dayAndWeekCannotBothBeSpecified() {
        assertThatThrownBy(() -> new CronExpression("0 0 0 15 * MON"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("日字段和周字段");
    }

    @Test
    @DisplayName("日与周字段 - 同时使用问号时抛出异常")
    void dayAndWeekCannotBothUseQuestionMark() {
        assertThatThrownBy(() -> new CronExpression("0 0 0 ? * ?"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("日字段和周字段");
    }

    /**
     * 测试 7 段表达式（含年份）。
     */
    @Test
    @DisplayName("7段表达式 - 指定年份执行")
    void sevenFieldsWithYear() {
        LocalDateTime base = LocalDateTime.of(2026, 12, 31, 23, 59, 0);
        CronExpression cron = new CronExpression("0 0 0 1 1 ? 2027");

        Optional<LocalDateTime> next = cron.next(base);

        assertThat(next).isPresent();
        assertThat(next.get()).isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0, 0));
    }

    /**
     * 测试 2027 年后无匹配时返回空。
     */
    @Test
    @DisplayName("年份限定 - 超出范围后返回空")
    void yearOutOfRange() {
        LocalDateTime base = LocalDateTime.of(2028, 1, 1, 0, 0, 0);
        CronExpression cron = new CronExpression("0 0 0 1 1 ? 2027");

        Optional<LocalDateTime> next = cron.next(base);

        assertThat(next).isEmpty();
    }

    /**
     * 测试非法表达式抛出异常。
     */
    @Test
    @DisplayName("非法表达式 - 段数不足抛出异常")
    void invalidExpressionTooFewFields() {
        assertThatThrownBy(() -> new CronExpression("0 0 2 * *"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("6段");
    }

    /**
     * 测试字段越界抛出异常。
     */
    @Test
    @DisplayName("非法表达式 - 秒字段越界抛出异常")
    void invalidExpressionOutOfRange() {
        assertThatThrownBy(() -> new CronExpression("60 * * * * ?"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("范围");
    }

    @Test
    @DisplayName("非法表达式 - 问号只能用于日或周字段")
    void questionMarkOnlyAllowedInDayOrWeek() {
        String[] expressions = {"? * * * * ?", "0 ? * * * ?", "0 0 ? * * ?", "0 0 0 * ? ?", "0 0 0 * * ? ?"};

        for (String expression : expressions) {
            assertThatThrownBy(() -> new CronExpression(expression))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("问号");
        }
    }

    /**
     * 测试空表达式抛出异常。
     */
    @Test
    @DisplayName("非法表达式 - 空表达式抛出异常")
    void invalidExpressionBlank() {
        assertThatThrownBy(() -> new CronExpression("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }
}
