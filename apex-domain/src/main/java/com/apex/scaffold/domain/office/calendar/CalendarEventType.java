package com.apex.scaffold.domain.office.calendar;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 日历事件类型枚举
 *
 * <p>定义日历事件的类型，用于区分不同类型的日程安排。
 * 不同类型的事件可能有不同的展示样式和处理逻辑。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Getter
@RequiredArgsConstructor
public enum CalendarEventType {

    /** 会议 */
    MEETING("会议"),

    /** 任务 */
    TASK("任务"),

    /** 提醒 */
    REMINDER("提醒"),

    /** 假期 */
    VACATION("假期"),

    /** 生日 */
    BIRTHDAY("生日"),

    /** 其他 */
    OTHER("其他");

    /** 事件类型说明 */
    private final String description;
}
