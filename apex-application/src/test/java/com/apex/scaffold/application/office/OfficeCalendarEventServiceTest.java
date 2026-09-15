package com.apex.scaffold.application.office;

import com.apex.scaffold.domain.office.calendar.CalendarEvent;
import com.apex.scaffold.domain.office.calendar.CalendarRepository;
import com.apex.scaffold.domain.office.calendar.CalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 日历事件服务测试
 *
 * @author Apex Team
 * @since 1.3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("日历事件服务测试")
class OfficeCalendarEventServiceTest {

    @Mock
    private CalendarService feishuService;

    @Mock
    private CalendarService dingtalkService;

    @Mock
    private CalendarService wecomService;

    @Mock
    private CalendarRepository calendarRepository;

    @InjectMocks
    private OfficeCalendarEventService calendarService;

    private CalendarEvent mockEvent;

    @BeforeEach
    void setUp() {
        mockEvent = CalendarEvent.builder()
                .id(1L)
                .tenantId(1L)
                .platform("FEISHU")
                .platformEventId("event_001")
                .calendarId("calendar_001")
                .title("项目评审会议")
                .description("讨论Q3项目规划")
                .location("会议室A")
                .startTime(LocalDateTime.of(2026, 9, 15, 14, 0))
                .endTime(LocalDateTime.of(2026, 9, 15, 16, 0))
                .isAllDay(false)
                .organizerUserId("user_001")
                .attendees(Arrays.asList("user_002", "user_003"))
                .status("CONFIRMED")
                .build();
    }

    @Test
    @DisplayName("创建日历事件 - 飞书 - 成功")
    void testCreateCalendarEvent_Feishu_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        when(feishuService.createEvent(eq(tenantId), eq(appId), any(CalendarEvent.class)))
                .thenReturn(Mono.just("event_001"));
        when(calendarRepository.save(any(CalendarEvent.class)))
                .thenReturn(Mono.just(mockEvent));

        // When & Then
        StepVerifier.create(calendarService.createCalendarEvent(tenantId, appId, platform, mockEvent))
                .expectNext("event_001")
                .verifyComplete();

        verify(feishuService, times(1)).createEvent(eq(tenantId), eq(appId), any(CalendarEvent.class));
        verify(calendarRepository, times(1)).save(any(CalendarEvent.class));
    }

    @Test
    @DisplayName("创建日历事件 - 钉钉 - 成功")
    void testCreateCalendarEvent_Dingtalk_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "DINGTALK";
        CalendarEvent dingtalkEvent = mockEvent.toBuilder()
                .platform("DINGTALK")
                .build();

        when(dingtalkService.createEvent(eq(tenantId), eq(appId), any(CalendarEvent.class)))
                .thenReturn(Mono.just("event_002"));
        when(calendarRepository.save(any(CalendarEvent.class)))
                .thenReturn(Mono.just(dingtalkEvent));

        // When & Then
        StepVerifier.create(calendarService.createCalendarEvent(tenantId, appId, platform, dingtalkEvent))
                .expectNext("event_002")
                .verifyComplete();

        verify(dingtalkService, times(1)).createEvent(eq(tenantId), eq(appId), any(CalendarEvent.class));
    }

    @Test
    @DisplayName("创建日历事件 - 企业微信 - 成功")
    void testCreateCalendarEvent_Wecom_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "WECOM";
        CalendarEvent wecomEvent = mockEvent.toBuilder()
                .platform("WECOM")
                .build();

        when(wecomService.createEvent(eq(tenantId), eq(appId), any(CalendarEvent.class)))
                .thenReturn(Mono.just("event_003"));
        when(calendarRepository.save(any(CalendarEvent.class)))
                .thenReturn(Mono.just(wecomEvent));

        // When & Then
        StepVerifier.create(calendarService.createCalendarEvent(tenantId, appId, platform, wecomEvent))
                .expectNext("event_003")
                .verifyComplete();

        verify(wecomService, times(1)).createEvent(eq(tenantId), eq(appId), any(CalendarEvent.class));
    }

    @Test
    @DisplayName("创建全天事件 - 成功")
    void testCreateAllDayEvent_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        CalendarEvent allDayEvent = mockEvent.toBuilder()
                .title("年度体检")
                .isAllDay(true)
                .endTime(null)
                .build();

        when(feishuService.createEvent(eq(tenantId), eq(appId), any(CalendarEvent.class)))
                .thenReturn(Mono.just("event_allday_001"));
        when(calendarRepository.save(any(CalendarEvent.class)))
                .thenReturn(Mono.just(allDayEvent));

        // When & Then
        StepVerifier.create(calendarService.createCalendarEvent(tenantId, appId, platform, allDayEvent))
                .expectNext("event_allday_001")
                .verifyComplete();
    }

    @Test
    @DisplayName("查询日历事件 - 成功")
    void testGetCalendarEvent_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        String eventId = "event_001";

        when(feishuService.getEvent(eq(tenantId), eq(appId), eq(eventId)))
                .thenReturn(Mono.just(mockEvent));

        // When & Then
        StepVerifier.create(calendarService.getCalendarEvent(tenantId, appId, platform, eventId))
                .expectNext(mockEvent)
                .verifyComplete();

        verify(feishuService, times(1)).getEvent(eq(tenantId), eq(appId), eq(eventId));
    }

    @Test
    @DisplayName("查询用户日历事件列表 - 成功")
    void testListUserCalendarEvents_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        String userId = "user_001";
        LocalDateTime startTime = LocalDateTime.of(2026, 9, 15, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 9, 15, 23, 59);

        CalendarEvent event2 = mockEvent.toBuilder()
                .id(2L)
                .platformEventId("event_002")
                .title("部门周会")
                .startTime(LocalDateTime.of(2026, 9, 15, 10, 0))
                .endTime(LocalDateTime.of(2026, 9, 15, 11, 0))
                .build();

        when(feishuService.listEvents(eq(tenantId), eq(appId), eq(userId), eq(startTime), eq(endTime)))
                .thenReturn(Flux.just(mockEvent, event2));

        // When & Then
        StepVerifier.create(calendarService.listCalendarEvents(tenantId, appId, platform, userId, startTime, endTime))
                .expectNext(mockEvent)
                .expectNext(event2)
                .verifyComplete();

        verify(feishuService, times(1)).listEvents(eq(tenantId), eq(appId), eq(userId), eq(startTime), eq(endTime));
    }

    @Test
    @DisplayName("更新日历事件 - 成功")
    void testUpdateCalendarEvent_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        String eventId = "event_001";
        CalendarEvent updatedEvent = mockEvent.toBuilder()
                .title("项目评审会议（延期）")
                .startTime(LocalDateTime.of(2026, 9, 16, 14, 0))
                .endTime(LocalDateTime.of(2026, 9, 16, 16, 0))
                .build();

        when(feishuService.updateEvent(eq(tenantId), eq(appId), eq(eventId), any(CalendarEvent.class)))
                .thenReturn(Mono.just(true));
        when(calendarRepository.save(any(CalendarEvent.class)))
                .thenReturn(Mono.just(updatedEvent));

        // When & Then
        StepVerifier.create(calendarService.updateCalendarEvent(tenantId, appId, platform, eventId, updatedEvent))
                .expectNext(true)
                .verifyComplete();

        verify(feishuService, times(1)).updateEvent(eq(tenantId), eq(appId), eq(eventId), any(CalendarEvent.class));
    }

    @Test
    @DisplayName("删除日历事件 - 成功")
    void testDeleteCalendarEvent_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        String eventId = "event_001";

        when(feishuService.deleteEvent(eq(tenantId), eq(appId), eq(eventId)))
                .thenReturn(Mono.just(true));
        when(calendarRepository.deleteByPlatformEventId(eq(eventId)))
                .thenReturn(Mono.just(true));

        // When & Then
        StepVerifier.create(calendarService.deleteCalendarEvent(tenantId, appId, platform, eventId))
                .expectNext(true)
                .verifyComplete();

        verify(feishuService, times(1)).deleteEvent(eq(tenantId), eq(appId), eq(eventId));
        verify(calendarRepository, times(1)).deleteByPlatformEventId(eq(eventId));
    }

    @Test
    @DisplayName("创建日历事件 - 平台服务异常")
    void testCreateCalendarEvent_PlatformServiceError() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";

        when(feishuService.createEvent(eq(tenantId), eq(appId), any(CalendarEvent.class)))
                .thenReturn(Mono.error(new RuntimeException("平台服务异常")));

        // When & Then
        StepVerifier.create(calendarService.createCalendarEvent(tenantId, appId, platform, mockEvent))
                .expectError(RuntimeException.class)
                .verify();

        verify(calendarRepository, never()).save(any(CalendarEvent.class));
    }

    @Test
    @DisplayName("查询日历事件 - 事件不存在")
    void testGetCalendarEvent_NotFound() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        String eventId = "event_not_exist";

        when(feishuService.getEvent(eq(tenantId), eq(appId), eq(eventId)))
                .thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(calendarService.getCalendarEvent(tenantId, appId, platform, eventId))
                .expectComplete()
                .verify();
    }

    @Test
    @DisplayName("创建多参与人会议 - 成功")
    void testCreateMultiAttendeeEvent_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        CalendarEvent multiAttendeeEvent = mockEvent.toBuilder()
                .attendees(Arrays.asList("user_002", "user_003", "user_004", "user_005"))
                .build();

        when(feishuService.createEvent(eq(tenantId), eq(appId), any(CalendarEvent.class)))
                .thenReturn(Mono.just("event_multi_001"));
        when(calendarRepository.save(any(CalendarEvent.class)))
                .thenReturn(Mono.just(multiAttendeeEvent));

        // When & Then
        StepVerifier.create(calendarService.createCalendarEvent(tenantId, appId, platform, multiAttendeeEvent))
                .expectNext("event_multi_001")
                .verifyComplete();
    }

    @Test
    @DisplayName("查询空日程 - 返回空列表")
    void testListEmptyCalendarEvents() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        String userId = "user_001";
        LocalDateTime startTime = LocalDateTime.of(2026, 9, 20, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 9, 20, 23, 59);

        when(feishuService.listEvents(eq(tenantId), eq(appId), eq(userId), eq(startTime), eq(endTime)))
                .thenReturn(Flux.empty());

        // When & Then
        StepVerifier.create(calendarService.listCalendarEvents(tenantId, appId, platform, userId, startTime, endTime))
                .expectComplete()
                .verify();
    }

    @Test
    @DisplayName("创建带提醒的事件 - 成功")
    void testCreateEventWithReminders_Success() {
        // Given
        Long tenantId = 1L;
        Long appId = 100L;
        String platform = "FEISHU";
        CalendarEvent eventWithReminders = mockEvent.toBuilder()
                .reminders("15,60")
                .build();

        when(feishuService.createEvent(eq(tenantId), eq(appId), any(CalendarEvent.class)))
                .thenReturn(Mono.just("event_reminder_001"));
        when(calendarRepository.save(any(CalendarEvent.class)))
                .thenReturn(Mono.just(eventWithReminders));

        // When & Then
        StepVerifier.create(calendarService.createCalendarEvent(tenantId, appId, platform, eventWithReminders))
                .expectNext("event_reminder_001")
                .verifyComplete();
    }
}
