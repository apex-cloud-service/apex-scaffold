package com.apex.scaffold.office.application;

import static org.mockito.Mockito.*;

import com.apex.scaffold.office.domain.document.DocumentInfoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OfficeDocumentOperationServiceTest {
    @Mock
    private DocumentInfoRepository repository;

    @InjectMocks
    private OfficeDocumentService service;

    @Test
    void getDocumentDetail_missingDocumentErrors() {
        when(repository.findById(1L)).thenReturn(Mono.empty());
        StepVerifier.create(service.getDocumentDetail(1L))
                .expectError(RuntimeException.class)
                .verify();
    }
}
