package com.apex.scaffold.officeservice.interfaces.handler.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.office.application.OfficeCalendarService;
import com.apex.scaffold.office.domain.calendar.CalendarEvent;
import com.apex.scaffold.office.domain.calendar.CalendarEventType;
import com.apex.scaffold.officeservice.interfaces.assembler.office.CalendarEventAssembler;
import com.apex.scaffold.officeservice.interfaces.dto.office.calendar.CalendarEventResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.calendar.CreateCalendarEventRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.calendar.UpdateCalendarEventRequest;
import com.apex.scaffold.officeservice.interfaces.security.AuthenticatedUser;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("OfficeCalendarHandler 单元测试")
class OfficeCalendarHandlerTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();
    private final RequestHandlerUtil requestHandlerUtil = new RequestHandlerUtil(validator);

    private OfficeCalendarService calendarService;
    private CalendarEventAssembler assembler;
    private OfficeCalendarHandler handler;

    @BeforeEach
    void setUp() {
        calendarService = mock(OfficeCalendarService.class);
        assembler = mock(CalendarEventAssembler.class);
        handler = new OfficeCalendarHandler(calendarService, assembler, requestHandlerUtil);
    }

    private UsernamePasswordAuthenticationToken auth() {
        AuthenticatedUser user = new AuthenticatedUser(100L, "openid", "1");
        return new UsernamePasswordAuthenticationToken(user, null, List.of());
    }

    private CalendarEvent event() {
        return CalendarEvent.builder()
                .id(10L)
                .tenantId(1L)
                .platform("feishu")
                .platformEventId("evt_1")
                .title("会议")
                .eventType(CalendarEventType.MEETING)
                .organizerId("100")
                .organizerName("张三")
                .startTime(LocalDateTime.of(2026, 9, 15, 14, 0))
                .endTime(LocalDateTime.of(2026, 9, 15, 16, 0))
                .isAllDay(false)
                .status("CONFIRMED")
                .build();
    }

    private CalendarEventResponse response() {
        return new CalendarEventResponse();
    }

    @Nested
    @DisplayName("createCalendarEvent")
    class CreateCalendarEventTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            CreateCalendarEventRequest body = CreateCalendarEventRequest.builder()
                    .platform("feishu")
                    .title("会议")
                    .description("讨论")
                    .startTime(LocalDateTime.of(2026, 9, 15, 14, 0))
                    .endTime(LocalDateTime.of(2026, 9, 15, 16, 0))
                    .isAllDay(false)
                    .eventType("MEETING")
                    .location("A301")
                    .organizerId("100")
                    .organizerName("张三")
                    .attendeeIds(List.of("u2"))
                    .attendeeNames(List.of("李四"))
                    .reminderMinutes(15)
                    .build();
            when(calendarService.createEvent(
                            eq("feishu"),
                            eq("会议"),
                            eq("讨论"),
                            eq(CalendarEventType.MEETING),
                            eq("100"),
                            eq("张三"),
                            any(LocalDateTime.class),
                            any(LocalDateTime.class),
                            eq(false),
                            eq("A301"),
                            any(String[].class),
                            any(String[].class),
                            isNull(),
                            eq(15)))
                    .thenReturn(Mono.just(event()));
            when(assembler.toResponse(any(CalendarEvent.class))).thenReturn(response());

            StepVerifier.create(handler.createCalendarEvent(
                                    MockServerRequest.builder().body(Mono.just(body)))
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            CreateCalendarEventRequest body = CreateCalendarEventRequest.builder()
                    .platform("feishu")
                    .title("会议")
                    .startTime(LocalDateTime.of(2026, 9, 15, 14, 0))
                    .endTime(LocalDateTime.of(2026, 9, 15, 16, 0))
                    .eventType("MEETING")
                    .organizerId("100")
                    .organizerName("张三")
                    .build();
            when(calendarService.createEvent(
                            anyString(),
                            anyString(),
                            any(),
                            any(),
                            anyString(),
                            anyString(),
                            any(LocalDateTime.class),
                            any(LocalDateTime.class),
                            any(),
                            any(),
                            any(String[].class),
                            any(String[].class),
                            any(),
                            any()))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.createCalendarEvent(
                                    MockServerRequest.builder().body(Mono.just(body)))
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("非法事件类型返回 HTTP 400")
        void invalidEventType() {
            CreateCalendarEventRequest body = CreateCalendarEventRequest.builder()
                    .platform("feishu")
                    .title("会议")
                    .startTime(LocalDateTime.of(2026, 9, 15, 14, 0))
                    .endTime(LocalDateTime.of(2026, 9, 15, 16, 0))
                    .eventType("UNKNOWN")
                    .organizerId("100")
                    .organizerName("张三")
                    .build();

            StepVerifier.create(handler.createCalendarEvent(
                                    MockServerRequest.builder().body(Mono.just(body)))
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("缺少认证上下文返回 HTTP 400")
        void noAuth() {
            CreateCalendarEventRequest body = CreateCalendarEventRequest.builder()
                    .platform("feishu")
                    .title("会议")
                    .startTime(LocalDateTime.of(2026, 9, 15, 14, 0))
                    .endTime(LocalDateTime.of(2026, 9, 15, 16, 0))
                    .eventType("MEETING")
                    .organizerId("100")
                    .organizerName("张三")
                    .build();

            StepVerifier.create(handler.createCalendarEvent(
                            MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("参数校验失败返回 HTTP 400")
        void validationError() {
            CreateCalendarEventRequest body =
                    CreateCalendarEventRequest.builder().build();

            StepVerifier.create(handler.createCalendarEvent(
                                    MockServerRequest.builder().body(Mono.just(body)))
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getCalendarEventDetail")
    class GetCalendarEventDetailTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(calendarService.getEventDetail(10L)).thenReturn(Mono.just(event()));
            when(assembler.toResponse(any(CalendarEvent.class))).thenReturn(response());

            StepVerifier.create(handler.getCalendarEventDetail(MockServerRequest.builder()
                            .pathVariable("eventId", "10")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(calendarService.getEventDetail(10L)).thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.getCalendarEventDetail(MockServerRequest.builder()
                            .pathVariable("eventId", "10")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("updateCalendarEvent")
    class UpdateCalendarEventTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            UpdateCalendarEventRequest body = UpdateCalendarEventRequest.builder()
                    .title("新标题")
                    .description("新描述")
                    .startTime(LocalDateTime.of(2026, 9, 15, 14, 0))
                    .endTime(LocalDateTime.of(2026, 9, 15, 16, 0))
                    .location("线上")
                    .attendeeIds(List.of("u2"))
                    .build();
            when(calendarService.updateEvent(
                            eq(10L),
                            eq("新标题"),
                            eq("新描述"),
                            any(LocalDateTime.class),
                            any(LocalDateTime.class),
                            any(String[].class),
                            eq("线上")))
                    .thenReturn(Mono.just(true));

            StepVerifier.create(handler.updateCalendarEvent(MockServerRequest.builder()
                            .pathVariable("eventId", "10")
                            .body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            UpdateCalendarEventRequest body =
                    UpdateCalendarEventRequest.builder().title("x").build();
            when(calendarService.updateEvent(eq(10L), any(), any(), any(), any(), any(String[].class), any()))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.updateCalendarEvent(MockServerRequest.builder()
                            .pathVariable("eventId", "10")
                            .body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("deleteCalendarEvent")
    class DeleteCalendarEventTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(calendarService.deleteEvent(10L, "100")).thenReturn(Mono.just(true));

            StepVerifier.create(handler.deleteCalendarEvent(MockServerRequest.builder()
                                    .pathVariable("eventId", "10")
                                    .build())
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(calendarService.deleteEvent(10L, "100")).thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.deleteCalendarEvent(MockServerRequest.builder()
                                    .pathVariable("eventId", "10")
                                    .build())
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("缺少认证上下文返回 HTTP 400")
        void noAuth() {
            StepVerifier.create(handler.deleteCalendarEvent(MockServerRequest.builder()
                            .pathVariable("eventId", "10")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getCalendarEventsByTimeRange")
    class GetCalendarEventsByTimeRangeTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(calendarService.listEvents(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(Mono.just(List.of(event())));
            when(assembler.toResponse(any(CalendarEvent.class))).thenReturn(response());

            StepVerifier.create(handler.getCalendarEventsByTimeRange(MockServerRequest.builder()
                            .queryParam("startTime", "2026-09-15T00:00:00")
                            .queryParam("endTime", "2026-09-16T00:00:00")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(calendarService.listEvents(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.getCalendarEventsByTimeRange(MockServerRequest.builder()
                            .queryParam("startTime", "2026-09-15T00:00:00")
                            .queryParam("endTime", "2026-09-16T00:00:00")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("时间参数非法时抛出 DateTimeParseException")
        void invalidTime() {
            assertThrows(
                    java.time.format.DateTimeParseException.class,
                    () -> handler.getCalendarEventsByTimeRange(MockServerRequest.builder()
                            .queryParam("startTime", "bad")
                            .queryParam("endTime", "bad")
                            .build()));
        }
    }

    @Nested
    @DisplayName("getMyCalendarEvents")
    class GetMyCalendarEventsTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(calendarService.getMyOrganizedEvents("100")).thenReturn(Mono.just(List.of(event())));
            when(assembler.toResponse(any(CalendarEvent.class))).thenReturn(response());

            StepVerifier.create(handler.getMyCalendarEvents(MockServerRequest.builder()
                            .queryParam("userId", "100")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(calendarService.getMyOrganizedEvents("100")).thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.getMyCalendarEvents(MockServerRequest.builder()
                            .queryParam("userId", "100")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getUpcomingEvents")
    class GetUpcomingEventsTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            CalendarEvent owned = event();
            CalendarEvent participant = CalendarEvent.builder()
                    .id(11L)
                    .organizerId("other")
                    .participantIds("[\"100\"]")
                    .eventType(CalendarEventType.MEETING)
                    .build();
            when(calendarService.listEvents(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(Mono.just(List.of(owned, participant)));
            when(assembler.toResponse(any(CalendarEvent.class))).thenReturn(response());

            StepVerifier.create(handler.getUpcomingEvents(MockServerRequest.builder()
                            .queryParam("userId", "100")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(calendarService.listEvents(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.getUpcomingEvents(MockServerRequest.builder()
                            .queryParam("userId", "100")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }
}
