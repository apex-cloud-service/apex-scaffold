package com.apex.scaffold.office.infrastructure.converter;

import com.apex.scaffold.office.domain.calendar.CalendarEvent;
import com.apex.scaffold.office.domain.calendar.CalendarEventType;
import com.apex.scaffold.office.infrastructure.po.CalendarEventPO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * 日历事件转换器
 *
 * <p>使用MapStruct实现CalendarEvent领域实体与CalendarEventPO持久化对象之间的转换。
 * 处理枚举类型、JSON字段等特殊类型的转换逻辑。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
@SuppressWarnings("PMD.LowerCamelCaseVariableNamingRule")
public interface CalendarEventConverter {

    /**
     * 领域实体转换为持久化对象
     *
     * @param event 日历事件领域实体
     * @return 日历事件持久化对象
     */
    @Mapping(target = "eventType", source = "eventType", qualifiedByName = "eventTypeToString")
    CalendarEventPO toPO(CalendarEvent event);

    /**
     * 持久化对象转换为领域实体
     *
     * @param po 日历事件持久化对象
     * @return 日历事件领域实体
     */
    @Mapping(target = "eventType", source = "eventType", qualifiedByName = "stringToEventType")
    CalendarEvent toEntity(CalendarEventPO po);

    /**
     * 事件类型枚举转换为字符串
     *
     * @param eventType 事件类型枚举
     * @return 事件类型字符串
     */
    @Named("eventTypeToString")
    default String eventTypeToString(CalendarEventType eventType) {
        return eventType != null ? eventType.name() : null;
    }

    /**
     * 字符串转换为事件类型枚举
     *
     * @param eventType 事件类型字符串
     * @return 事件类型枚举
     */
    @Named("stringToEventType")
    default CalendarEventType stringToEventType(String eventType) {
        if (eventType == null) {
            return null;
        }
        try {
            return CalendarEventType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            return CalendarEventType.OTHER;
        }
    }
}
