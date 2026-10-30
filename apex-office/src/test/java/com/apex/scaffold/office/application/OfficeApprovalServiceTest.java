package com.apex.scaffold.office.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.approval.ApprovalProcess;
import com.apex.scaffold.office.domain.approval.ApprovalProcessRepository;
import com.apex.scaffold.office.domain.approval.ApprovalStatus;
import com.apex.scaffold.office.domain.port.ApprovalPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OfficeApprovalServiceTest {
    @Mock
    private ApprovalProcessRepository repository;

    @Mock
    private ApprovalPort client;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OfficeApprovalService service;

    @BeforeEach
    void setUp() {
        service = new OfficeApprovalService(
                repository, new HashMap<>(Map.of("feishuApprovalClient", client)), objectMapper);
    }

    @Test
    void initiateApproval_savesProcess() {
        String previousTenantId = TenantContext.setTenantId("1");
        try {
            when(client.createApproval(anyString(), anyString(), any(), anyMap(), anyString()))
                    .thenReturn(Mono.just("remote-1"));
            ApprovalProcess saved = ApprovalProcess.builder()
                    .processInstanceId("local-1")
                    .platform("FEISHU")
                    .platformInstanceId("remote-1")
                    .build();
            when(repository.save(any())).thenReturn(Mono.just(saved));
            StepVerifier.create(service.initiateApproval(OfficePlatform.FEISHU, 1L, "tpl", "user", "{}"))
                    .expectNext(saved)
                    .verifyComplete();

            ArgumentCaptor<ApprovalProcess> processCaptor = ArgumentCaptor.forClass(ApprovalProcess.class);
            verify(repository).save(processCaptor.capture());
            assertThat(processCaptor.getValue().getFormData()).isEqualTo("{}");
            assertThat(processCaptor.getValue().getCurrentApproverIds()).isEqualTo("[]");
        } finally {
            TenantContext.restoreTenantId(previousTenantId);
        }
    }

    @Test
    void getCompletedApprovals_delegatesToRepository() {
        ApprovalProcess process = ApprovalProcess.builder()
                .processInstanceId("completed-1")
                .status(ApprovalStatus.APPROVED)
                .build();
        when(repository.findCompletedByApprover("42")).thenReturn(Mono.just(java.util.List.of(process)));

        StepVerifier.create(service.getCompletedApprovals("42"))
                .expectNext(process)
                .verifyComplete();

        verify(repository).findCompletedByApprover("42");
    }

    @Test
    void getApprovalDetail_missingProcessErrors() {
        when(repository.findByProcessInstanceId("missing")).thenReturn(Mono.empty());
        StepVerifier.create(service.getApprovalDetail("missing"))
                .expectError(RuntimeException.class)
                .verify();
    }
}
