package com.apex.scaffold.interfaces.assembler.office;

import com.apex.scaffold.domain.office.calendar.CalendarEvent;
import com.apex.scaffold.interfaces.dto.office.calendar.CalendarEventResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * 日历事件DTO转换器
 *
 * <p>使用MapStruct实现CalendarEvent领域实体与CalendarEventResponse响应DTO之间的转换。
 * 负责将领域模型转换为客户端可理解的数据传输对象。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CalendarEventAssembler {

    /**
     * 领域实体转换为响应DTO
     *
     * @param calendarEvent 日历事件领域实体
     * @return 日历事件响应DTO
     */
    @Mapping(target = "eventType", source = "eventType", qualifiedByName = "eventTypeToString")
    CalendarEventResponse toResponse(CalendarEvent calendarEvent);

    /**
     * 领域实体列表转换为响应DTO列表
     *
     * @param calendarEvents 日历事件领域实体列表
     * @return 日历事件响应DTO列表
     */
    List<CalendarEventResponse> toResponseList(List<CalendarEvent> calendarEvents);

    /**
     * 事件类型枚举转换为字符串
     *
     * @param eventType 事件类型枚举
     * @return 事件类型字符串
     */
    @Named("eventTypeToString")
    default String eventTypeToString(com.apex.scaffold.domain.office.calendar.CalendarEventType eventType) {
        return eventType != null ? eventType.name() : null;
    }
}
