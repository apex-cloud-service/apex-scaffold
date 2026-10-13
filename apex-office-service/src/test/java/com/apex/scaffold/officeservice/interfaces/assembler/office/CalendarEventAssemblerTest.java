package com.apex.scaffold.officeservice.interfaces.assembler.office;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.office.domain.calendar.CalendarEvent;
import com.apex.scaffold.office.domain.calendar.CalendarEventType;
import com.apex.scaffold.officeservice.interfaces.dto.office.calendar.CalendarEventResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@DisplayName("CalendarEventAssembler 单元测试")
class CalendarEventAssemblerTest {

    private CalendarEventAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = Mappers.getMapper(CalendarEventAssembler.class);
    }

    @Test
    @DisplayName("toResponse 将事件类型枚举转为名称字符串")
    void toResponse() {
        CalendarEvent entity = CalendarEvent.builder()
                .id(1L)
                .title("评审会议")
                .eventType(CalendarEventType.MEETING)
                .build();

        CalendarEventResponse response = assembler.toResponse(entity);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("评审会议");
        assertThat(response.getEventType()).isEqualTo("MEETING");
    }

    @Test
    @DisplayName("toResponse 列表映射")
    void toResponseList() {
        CalendarEvent a = CalendarEvent.builder()
                .id(1L)
                .eventType(CalendarEventType.MEETING)
                .build();
        CalendarEvent b =
                CalendarEvent.builder().id(2L).eventType(CalendarEventType.TASK).build();

        List<CalendarEventResponse> list = assembler.toResponseList(List.of(a, b));

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getEventType()).isEqualTo("MEETING");
        assertThat(list.get(1).getEventType()).isEqualTo("TASK");
    }

    @Test
    @DisplayName("eventTypeToString 对 null 返回 null")
    void eventTypeNull() {
        assertThat(assembler.eventTypeToString(null)).isNull();
    }

    @Test
    @DisplayName("eventTypeToString 返回枚举名称")
    void eventTypeValue() {
        assertThat(assembler.eventTypeToString(CalendarEventType.VACATION)).isEqualTo("VACATION");
    }

    @Test
    @DisplayName("toResponse null 输入返回 null")
    void toResponseNull() {
        assertThat(assembler.toResponse(null)).isNull();
    }
}
