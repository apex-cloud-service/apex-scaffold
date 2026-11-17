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
import org.junit.jupiter.api.DisplayName;
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

    @Mock
    private com.apex.scaffold.office.domain.OfficeRepository officeRepository;

    @InjectMocks
    private OfficeCalendarService service;

    /**
     * 让「系统用户 → 平台用户」换算命中：返回固定平台标识。
     *
     * <p>BE-038 起服务层在调用客户端前必须换算组织者，测试统一桩掉。
     */
    private void stubOrganizerMapping() {
        com.apex.scaffold.office.domain.OfficeOrganizationMapping mapping =
                com.apex.scaffold.office.domain.OfficeOrganizationMapping.builder()
                        .tenantId("1")
                        .externalUserId("ext-user")
                        .apexUserId("1001")
                        .build();
        lenient()
                .when(officeRepository.findMemberMappingByApexUser(any(), any(), any()))
                .thenReturn(Mono.just(mapping));
    }

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
        stubOrganizerMapping();
        CalendarEvent event = CalendarEvent.builder()
                .tenantId(1L)
                .platform("feishu")
                .platformEventId("remote-1")
                .organizerId("1001")
                .isAllDay(true)
                .status("CONFIRMED")
                .build();
        CalendarPort client = mock(CalendarPort.class);
        when(calendarClients.get("feishuCalendarClient")).thenReturn(client);
        when(repository.findById(1L)).thenReturn(Mono.just(event));
        // BE-038：updateEvent 新增 organizerId 参数（8 个实参）
        when(client.updateEvent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(true));
        when(repository.update(any())).thenReturn(Mono.just(event));
        LocalDateTime start = LocalDateTime.of(2026, 10, 18, 9, 0);
        Mono<Boolean> result = service.updateEvent(
                1L, "title", "description", start, start.plusHours(1), new String[0], "room", allDay);
        StepVerifier.create(result).expectNext(true).verifyComplete();
        assertThat(event.getIsAllDay()).isEqualTo(allDay == null || allDay);
    }

    @Test
    @DisplayName("未绑定平台账号时创建直接拒绝，不发平台请求也不落库")
    void createRejectsUnboundOrganizer() {
        CalendarPort client = mock(CalendarPort.class);
        when(calendarClients.get("feishuCalendarClient")).thenReturn(client);
        when(officeRepository.findMemberMappingByApexUser(any(), any(), any())).thenReturn(Mono.empty());
        TenantContext.setTenantId("41");

        StepVerifier.create(service.createEvent(
                        "feishu",
                        "title",
                        null,
                        CalendarEventType.MEETING,
                        "1001",
                        "系统管理员",
                        LocalDateTime.of(2026, 10, 18, 9, 0),
                        LocalDateTime.of(2026, 10, 18, 10, 0),
                        false,
                        null,
                        new String[0],
                        new String[0],
                        null,
                        15))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(com.apex.scaffold.common.exception.BusinessException.class)
                        .hasMessageContaining("未绑定"))
                .verify();

        verifyNoInteractions(client);
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {false, true})
    void createNormalizesAllDayBeforeSaving(Boolean allDay) {
        stubOrganizerMapping();
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
