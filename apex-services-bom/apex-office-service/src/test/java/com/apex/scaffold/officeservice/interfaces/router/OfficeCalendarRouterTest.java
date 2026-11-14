package com.apex.scaffold.officeservice.interfaces.router;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.office.application.OfficeCalendarService;
import com.apex.scaffold.office.domain.calendar.CalendarEvent;
import com.apex.scaffold.officeservice.interfaces.assembler.office.CalendarEventAssembler;
import com.apex.scaffold.officeservice.interfaces.dto.office.calendar.CalendarEventResponse;
import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeCalendarHandler;
import com.apex.scaffold.officeservice.interfaces.router.office.OfficeCalendarRouter;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * 日历事件实际路由匹配回归测试。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("日历事件路由匹配回归测试")
class OfficeCalendarRouterTest {

    private static final String BASE_PATH = "/api/office/calendar/events";
    private OfficeCalendarService calendarService;
    private CalendarEventAssembler assembler;
    private WebTestClient client;

    /** 绑定真实路由与处理器，隔离应用服务依赖。 */
    @BeforeEach
    void setUp() {
        calendarService = mock(OfficeCalendarService.class);
        assembler = mock(CalendarEventAssembler.class);
        OfficeCalendarHandler handler =
                new OfficeCalendarHandler(calendarService, assembler, new RequestHandlerUtil(mock(Validator.class)));
        client = WebTestClient.bindToRouterFunction(new OfficeCalendarRouter(handler).officeCalendarRoutes())
                .build();
    }

    /** 时间范围静态路径必须调用列表查询，而非事件详情。 */
    @Test
    void shouldRouteRangeToTimeRangeQuery() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 21, 9, 0);
        LocalDateTime end = start.plusDays(1);
        when(calendarService.listEvents(start, end)).thenReturn(Mono.just(List.of()));

        client.get()
                .uri(builder -> builder.path(BASE_PATH + "/range")
                        .queryParam("startTime", start)
                        .queryParam("endTime", end)
                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isArray();

        verify(calendarService).listEvents(start, end);
        verifyNoMoreInteractions(calendarService);
    }

    /** 我的日历静态路径必须调用组织者查询，而非事件详情。 */
    @Test
    void shouldRouteMyToOrganizedEventsQuery() {
        when(calendarService.getMyOrganizedEvents("100")).thenReturn(Mono.just(List.of()));

        client.get()
                .uri(BASE_PATH + "/my?userId=100")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isArray();

        verify(calendarService).getMyOrganizedEvents("100");
        verifyNoMoreInteractions(calendarService);
    }

    /** 即将到来静态路径必须调用时间范围查询，而非事件详情。 */
    @Test
    void shouldRouteUpcomingToUpcomingEventsQuery() {
        when(calendarService.listEvents(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Mono.just(List.of()));

        client.get()
                .uri(BASE_PATH + "/upcoming?userId=100")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isArray();

        verify(calendarService).listEvents(any(LocalDateTime.class), any(LocalDateTime.class));
        verifyNoMoreInteractions(calendarService);
    }

    /** 数字事件 ID 仍应路由至详情查询。 */
    @Test
    void shouldRouteNumericEventIdToDetailQuery() {
        CalendarEvent event = CalendarEvent.builder().id(10L).build();
        CalendarEventResponse response = CalendarEventResponse.builder().id(10L).build();
        when(calendarService.getEventDetail(10L)).thenReturn(Mono.just(event));
        when(assembler.toResponse(event)).thenReturn(response);

        client.get()
                .uri(BASE_PATH + "/10")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.id")
                .isEqualTo(10);

        verify(calendarService).getEventDetail(10L);
        verifyNoMoreInteractions(calendarService);
    }
}
