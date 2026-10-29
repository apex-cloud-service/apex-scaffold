package com.apex.scaffold.office.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.office.domain.document.DocumentInfoRepository;
import com.apex.scaffold.office.domain.document.DocumentType;
import com.apex.scaffold.office.domain.port.DocumentPort;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
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

    @Mock
    private Map<String, DocumentPort> documentClients;

    @InjectMocks
    private OfficeDocumentService service;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void getDocumentDetail_missingDocumentErrors() {
        when(repository.findById(1L)).thenReturn(Mono.empty());
        StepVerifier.create(service.getDocumentDetail(1L))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void createFolderPersistsFolderFlag() {
        DocumentPort client = mock(DocumentPort.class);
        when(documentClients.get("feishuDocumentClient")).thenReturn(client);
        when(client.createDocument(any(), any(), any())).thenReturn(Mono.just("folder-1"));
        when(client.getDocumentDetail("folder-1")).thenReturn(Mono.just(Map.of("title", "folder")));
        when(repository.save(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        TenantContext.setTenantId("41");
        StepVerifier.create(service.createDocument("feishu", "root", "folder", DocumentType.FOLDER, "owner", "Owner"))
                .assertNext(document -> {
                    assertThat(document.getIsFolder()).isTrue();
                    assertThat(document.getIsArchived()).isFalse();
                })
                .verifyComplete();
    }
}
