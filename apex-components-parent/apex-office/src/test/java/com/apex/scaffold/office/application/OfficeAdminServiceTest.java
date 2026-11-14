package com.apex.scaffold.office.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.office.application.port.OfficeUserQueryPort;
import com.apex.scaffold.office.application.port.OfficeUserSnapshot;
import com.apex.scaffold.office.domain.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OfficeAdminServiceTest {
    private final OfficeRepository repository = mock(OfficeRepository.class);
    private final OfficeUserQueryPort users = mock(OfficeUserQueryPort.class);
    private final OfficeAdminService service =
            new OfficeAdminService(repository, users, Mappers.getMapper(OfficeAdminAssembler.class));

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void missingTenantRejectsBeforeRepository() {
        StepVerifier.create(service.listApps())
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(repository, users);
    }

    @Test
    void createUsesTrustedTenantAndInitialVersion() {
        TenantContext.setTenantId("tenant-a");
        when(repository.findApp("tenant-a", OfficePlatform.FEISHU)).thenReturn(Mono.empty());
        when(repository.saveApp(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        OfficeAppCommand command = command();
        StepVerifier.create(service.createApp(OfficePlatform.FEISHU, command))
                .assertNext(app -> {
                    assertThat(app.getTenantId()).isEqualTo("tenant-a");
                    assertThat(app.getVersion()).isZero();
                    assertThat(app.getDeleted()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void duplicateAppDoesNotWrite() {
        TenantContext.setTenantId("tenant-a");
        when(repository.findApp("tenant-a", OfficePlatform.FEISHU)).thenReturn(Mono.just(app()));
        StepVerifier.create(service.createApp(OfficePlatform.FEISHU, command()))
                .expectError(BusinessException.class)
                .verify();
        verify(repository, never()).saveApp(any());
    }

    @Test
    void updateRejectsStaleVersion() {
        TenantContext.setTenantId("tenant-a");
        when(repository.findApp("tenant-a", OfficePlatform.FEISHU)).thenReturn(Mono.just(app()));
        OfficeAppCommand command = command();
        command.setVersion(3);
        StepVerifier.create(service.updateApp(OfficePlatform.FEISHU, command))
                .expectError(BusinessException.class)
                .verify();
        verify(repository, never()).saveApp(any());
    }

    @Test
    void deleteUsesTenantAndExpectedVersion() {
        TenantContext.setTenantId("tenant-a");
        when(repository.findApp("tenant-a", OfficePlatform.FEISHU)).thenReturn(Mono.just(app()));
        when(repository.deleteApp("tenant-a", OfficePlatform.FEISHU, 0)).thenReturn(Mono.just(true));
        StepVerifier.create(service.deleteApp(OfficePlatform.FEISHU, 0))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void approvalRejectsCrossTenantUser() {
        TenantContext.setTenantId("tenant-a");
        when(repository.findBindingById("tenant-a", 9L)).thenReturn(Mono.just(binding("PENDING")));
        OfficeUserSnapshot user = OfficeUserSnapshot.builder()
                .id(7L)
                .tenantId("tenant-b")
                .status(1)
                .deleted(0)
                .build();
        when(users.getById(7L)).thenReturn(Mono.just(user));
        StepVerifier.create(service.reviewBinding(9L, 0, true))
                .expectError(BusinessException.class)
                .verify();
        verify(repository, never()).changeBindingStatus(anyString(), anyLong(), anyInt(), anyString(), anyString());
    }

    @Test
    void approvalOnlyTransitionsPendingBinding() {
        TenantContext.setTenantId("tenant-a");
        when(repository.findBindingById("tenant-a", 9L)).thenReturn(Mono.just(binding("APPROVED")));
        StepVerifier.create(service.reviewBinding(9L, 0, true))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(users);
    }

    @Test
    void rejectDoesNotNeedActiveUser() {
        TenantContext.setTenantId("tenant-a");
        when(repository.findBindingById("tenant-a", 9L)).thenReturn(Mono.just(binding("PENDING")));
        when(repository.changeBindingStatus("tenant-a", 9L, 0, "PENDING", "REJECTED"))
                .thenReturn(Mono.just(true));
        StepVerifier.create(service.reviewBinding(9L, 0, false))
                .expectNext(true)
                .verifyComplete();
        verifyNoInteractions(users);
    }

    @Test
    void unbindRevokesApprovedBindingWithVersionGuard() {
        TenantContext.setTenantId("tenant-a");
        when(repository.findBindingById("tenant-a", 9L)).thenReturn(Mono.just(binding("APPROVED")));
        when(repository.changeBindingStatus("tenant-a", 9L, 0, "APPROVED", "REVOKED"))
                .thenReturn(Mono.just(true));
        StepVerifier.create(service.unbind(9L, 0)).expectNext(true).verifyComplete();
    }

    @Test
    void listBindingsPageReturnsPageResponseWithTotal() {
        TenantContext.setTenantId("tenant-a");
        OfficeIdentityBinding binding = binding("PENDING");
        PageResponse<OfficeIdentityBinding> page = PageResponse.of(java.util.List.of(binding), 1L, 1, 20);
        when(repository.listBindingsPage("tenant-a", OfficePlatform.FEISHU, "PENDING", 1, 20))
                .thenReturn(Mono.just(page));

        StepVerifier.create(service.listBindingsPage(OfficePlatform.FEISHU, "PENDING", 1, 20))
                .assertNext(result -> {
                    assertThat(result.getTotal()).isEqualTo(1L);
                    assertThat(result.getPageNum()).isEqualTo(1);
                    assertThat(result.getPageSize()).isEqualTo(20);
                    assertThat(result.getList()).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    void listBindingsPageRejectsInvalidPageSize() {
        TenantContext.setTenantId("tenant-a");
        StepVerifier.create(service.listBindingsPage(OfficePlatform.FEISHU, null, 1, 101))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(repository);
    }

    private OfficeAppCommand command() {
        OfficeAppCommand command = new OfficeAppCommand();
        command.setAppId("app");
        command.setSecretReference("OFFICE_SECRET");
        command.setRedirectUri("https://example.com/callback");
        command.setEnabled(true);
        command.setVersion(0);
        return command;
    }

    private OfficePlatformApp app() {
        return OfficePlatformApp.builder()
                .id(1L)
                .tenantId("tenant-a")
                .platform(OfficePlatform.FEISHU)
                .version(0)
                .deleted(0)
                .build();
    }

    private OfficeIdentityBinding binding(String status) {
        return OfficeIdentityBinding.builder()
                .id(9L)
                .tenantId("tenant-a")
                .platform(OfficePlatform.FEISHU)
                .userId(7L)
                .externalUserId("external")
                .status(status)
                .enabled(true)
                .version(0)
                .deleted(0)
                .build();
    }
}
