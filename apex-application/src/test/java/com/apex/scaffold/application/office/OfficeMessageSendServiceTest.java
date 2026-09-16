package com.apex.scaffold.application.office;

import static org.mockito.Mockito.*;

import com.apex.scaffold.domain.office.message.OfficeMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OfficeMessageSendServiceTest {
    @Mock
    private OfficeMessageRepository repository;

    @InjectMocks
    private OfficeMessageService service;

    @Test
    void getMessageStatus_missingMessageCompletesEmpty() {
        when(repository.findById(1L)).thenReturn(Mono.empty());
        StepVerifier.create(service.getMessageStatus(1L)).verifyComplete();
    }
}
