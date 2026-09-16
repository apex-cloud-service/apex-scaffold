package com.apex.scaffold.application.office;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.domain.office.approval.ApprovalProcess;
import com.apex.scaffold.domain.office.approval.ApprovalProcessRepository;
import com.apex.scaffold.domain.office.port.ApprovalPort;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private OfficeApprovalService service;

    @BeforeEach
    void setUp() throws Exception {
        Field field = OfficeApprovalService.class.getDeclaredField("approvalClients");
        field.setAccessible(true);
        field.set(service, new HashMap<>(Map.of("feishuApprovalClient", client)));
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
            verify(repository).save(any(ApprovalProcess.class));
        } finally {
            TenantContext.restoreTenantId(previousTenantId);
        }
    }

    @Test
    void getApprovalDetail_missingProcessErrors() {
        when(repository.findByProcessInstanceId("missing")).thenReturn(Mono.empty());
        StepVerifier.create(service.getApprovalDetail("missing"))
                .expectError(RuntimeException.class)
                .verify();
    }
}
