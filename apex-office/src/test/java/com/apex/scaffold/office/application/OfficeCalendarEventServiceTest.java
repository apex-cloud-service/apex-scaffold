package com.apex.scaffold.office.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.office.domain.calendar.CalendarEvent;
import com.apex.scaffold.office.domain.calendar.CalendarEventRepository;
import com.apex.scaffold.office.domain.calendar.CalendarEventType;
import com.apex.scaffold.office.domain.port.CalendarPort;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OfficeCalendarEventServiceTest {
    @Mock
    private CalendarEventRepository repository;

    @Mock
    private Map<String, CalendarPort> calendarClients;

    @InjectMocks
    private OfficeCalendarService service;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void getEventDetail_missingEventErrors() {
        when(repository.findById(1L)).thenReturn(Mono.empty());
        StepVerifier.create(service.getEventDetail(1L))
                .expectError(RuntimeException.class)
                .verify();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {false, true})
    void updateAllDayOnlyWhenProvided(Boolean allDay) throws Exception {
        CalendarEvent event = CalendarEvent.builder()
                .platform("feishu")
                .platformEventId("remote-1")
                .isAllDay(true)
                .status("CONFIRMED")
                .build();
        CalendarPort client = mock(CalendarPort.class);
        when(calendarClients.get("feishuCalendarClient")).thenReturn(client);
        when(repository.findById(1L)).thenReturn(Mono.just(event));
        when(client.updateEvent(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(true));
        when(repository.update(any())).thenReturn(Mono.just(event));
        LocalDateTime start = LocalDateTime.of(2026, 10, 18, 9, 0);
        Mono<Boolean> result = service.updateEvent(
                1L, "title", "description", start, start.plusHours(1), new String[0], "room", allDay);
        StepVerifier.create(result).expectNext(true).verifyComplete();
        assertThat(event.getIsAllDay()).isEqualTo(allDay == null || allDay);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {false, true})
    void createNormalizesAllDayBeforeSaving(Boolean allDay) {
        CalendarPort client = mock(CalendarPort.class);
        when(calendarClients.get("feishuCalendarClient")).thenReturn(client);
        when(client.createEvent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just("remote-1"));
        when(repository.save(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        TenantContext.setTenantId("41");
        LocalDateTime start = LocalDateTime.of(2026, 10, 18, 9, 0);
        StepVerifier.create(service.createEvent(
                        "feishu",
                        "title",
                        "description",
                        CalendarEventType.OTHER,
                        "owner",
                        "Owner",
                        start,
                        start.plusHours(1),
                        allDay,
                        "room",
                        new String[0],
                        new String[0],
                        null,
                        5))
                .assertNext(event -> {
                    assertThat(event.getIsAllDay()).isEqualTo(Boolean.TRUE.equals(allDay));
                    assertThat(event.getIsRecurring()).isFalse();
                })
                .verifyComplete();
    }
}
