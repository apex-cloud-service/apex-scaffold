package com.apex.scaffold.application.office;

import static org.mockito.Mockito.*;

import com.apex.scaffold.domain.office.OfficeOrganizationService;
import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.domain.office.OfficeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OfficeOrganizationSyncServiceTest {
    @Mock
    private OfficeRepository repository;

    @Mock
    private OfficeOrganizationService organizationService;

    @Mock
    private TransactionalOperator transactionalOperator;

    @InjectMocks
    private OfficeOrganizationSyncService service;

    @Test
    void getDepartmentMapping_delegatesToRepository() {
        when(repository.findDepartmentMapping("tenant", OfficePlatform.FEISHU, "dept"))
                .thenReturn(Mono.empty());
        StepVerifier.create(service.getDepartmentMapping("tenant", OfficePlatform.FEISHU, "dept"))
                .verifyComplete();
        verify(repository).findDepartmentMapping("tenant", OfficePlatform.FEISHU, "dept");
    }
}
