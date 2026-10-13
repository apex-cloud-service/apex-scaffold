package com.apex.scaffold.office.application;

import static org.mockito.Mockito.*;

import com.apex.scaffold.office.domain.calendar.CalendarEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private java.util.Map<String, com.apex.scaffold.office.domain.port.CalendarPort> calendarClients;

    @InjectMocks
    private OfficeCalendarService service;

    @Test
    void getEventDetail_missingEventErrors() {
        when(repository.findById(1L)).thenReturn(Mono.empty());
        StepVerifier.create(service.getEventDetail(1L))
                .expectError(RuntimeException.class)
                .verify();
    }
}
