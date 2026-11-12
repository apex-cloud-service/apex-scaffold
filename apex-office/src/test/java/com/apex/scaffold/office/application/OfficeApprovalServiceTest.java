package com.apex.scaffold.office.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.approval.ApprovalAction;
import com.apex.scaffold.office.domain.approval.ApprovalCancelOperation;
import com.apex.scaffold.office.domain.approval.ApprovalCancelOperationRepository;
import com.apex.scaffold.office.domain.approval.ApprovalCancelStatus;
import com.apex.scaffold.office.domain.approval.ApprovalProcess;
import com.apex.scaffold.office.domain.approval.ApprovalProcessRepository;
import com.apex.scaffold.office.domain.approval.ApprovalRecord;
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
    private ApprovalCancelOperationRepository cancelOperationRepository;

    @Mock
    private ApprovalPort client;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OfficeApprovalService service;

    @BeforeEach
    void setUp() {
        service = new OfficeApprovalService(
                repository,
                cancelOperationRepository,
                new HashMap<>(Map.of(
                        "feishuApprovalClient", client,
                        "dingtalkApprovalClient", client,
                        "wecomApprovalClient", client)),
                objectMapper);
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

    @Test
    void getApprovalDetail_parsesFeishuTaskList() {
        ApprovalProcess process = ApprovalProcess.builder()
                .processInstanceId("local-1")
                .platform("FEISHU")
                .platformInstanceId("remote-1")
                .build();
        when(repository.findByProcessInstanceId("local-1")).thenReturn(Mono.just(process));
        when(client.getApprovalDetail("remote-1"))
                .thenReturn(Mono.just(Map.of(
                        "task_list",
                        java.util.List.of(
                                Map.of(
                                        "user_id", "u1",
                                        "action", "APPROVED",
                                        "comment", "ok",
                                        "create_time", "1700000000000"),
                                Map.of(
                                        "user_id", "u2",
                                        "action", "REJECT",
                                        "comment", "no",
                                        "create_time", 1700000001000L)))));

        StepVerifier.create(service.getApprovalDetail("local-1"))
                .assertNext(detail -> {
                    assertThat(detail.getRecords()).hasSize(2);
                    ApprovalRecord first = detail.getRecords().get(0);
                    assertThat(first.getApproverId()).isEqualTo("u1");
                    assertThat(first.getAction()).isEqualTo(ApprovalAction.APPROVE);
                    assertThat(first.getComment()).isEqualTo("ok");
                    assertThat(first.getApprovalTime()).isNotNull();
                    assertThat(first.getNodeOrder()).isEqualTo(1);
                    ApprovalRecord second = detail.getRecords().get(1);
                    assertThat(second.getApproverId()).isEqualTo("u2");
                    assertThat(second.getAction()).isEqualTo(ApprovalAction.REJECT);
                    assertThat(second.getNodeOrder()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    void getApprovalDetail_parsesWecomApproverAttr() {
        ApprovalProcess process = ApprovalProcess.builder()
                .processInstanceId("local-2")
                .platform("WECOM")
                .platformInstanceId("remote-2")
                .build();
        when(repository.findByProcessInstanceId("local-2")).thenReturn(Mono.just(process));
        when(client.getApprovalDetail("remote-2"))
                .thenReturn(Mono.just(Map.of(
                        "info",
                        Map.of(
                                "record",
                                java.util.List.of(Map.of(
                                        "sp_status",
                                        "2",
                                        "approverattr",
                                        java.util.List.of(Map.of(
                                                "userid", "w1",
                                                "approve_name", "张三",
                                                "speech", "同意",
                                                "sp_time", "1700000000"))))))));

        StepVerifier.create(service.getApprovalDetail("local-2"))
                .assertNext(detail -> {
                    assertThat(detail.getRecords()).hasSize(1);
                    ApprovalRecord record = detail.getRecords().get(0);
                    assertThat(record.getApproverId()).isEqualTo("w1");
                    assertThat(record.getApproverName()).isEqualTo("张三");
                    assertThat(record.getAction()).isEqualTo(ApprovalAction.APPROVE);
                    assertThat(record.getComment()).isEqualTo("同意");
                    assertThat(record.getApprovalTime()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void getApprovalDetail_parsesDingtalkTasks() {
        ApprovalProcess process = ApprovalProcess.builder()
                .processInstanceId("local-3")
                .platform("DINGTALK")
                .platformInstanceId("remote-3")
                .build();
        when(repository.findByProcessInstanceId("local-3")).thenReturn(Mono.just(process));
        when(client.getApprovalDetail("remote-3"))
                .thenReturn(Mono.just(Map.of(
                        "tasks",
                        java.util.List.of(Map.of(
                                "userid", "d1", "action", "AGREE", "remark", "fine", "createTime", 1700000000000L)))));

        StepVerifier.create(service.getApprovalDetail("local-3"))
                .assertNext(detail -> {
                    assertThat(detail.getRecords()).hasSize(1);
                    ApprovalRecord record = detail.getRecords().get(0);
                    assertThat(record.getApproverId()).isEqualTo("d1");
                    assertThat(record.getAction()).isEqualTo(ApprovalAction.APPROVE);
                    assertThat(record.getComment()).isEqualTo("fine");
                    assertThat(record.getApprovalTime()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void getApprovalDetail_unknownStructureDegradesToEmptyRecords() {
        ApprovalProcess process = ApprovalProcess.builder()
                .processInstanceId("local-4")
                .platform("FEISHU")
                .platformInstanceId("remote-4")
                .build();
        when(repository.findByProcessInstanceId("local-4")).thenReturn(Mono.just(process));
        when(client.getApprovalDetail("remote-4")).thenReturn(Mono.just(Map.of("foo", "bar")));

        StepVerifier.create(service.getApprovalDetail("local-4"))
                .assertNext(detail -> assertThat(detail.getRecords()).isEmpty())
                .verifyComplete();
    }

    @Test
    void cancelApproval_completedOperationReplaysSuccess() {
        String previousTenantId = TenantContext.setTenantId("1");
        try {
            ApprovalCancelOperation operation = ApprovalCancelOperation.builder()
                    .id(9L)
                    .tenantId(1L)
                    .processInstanceId("inst-1")
                    .idempotencyKey("key-1")
                    .status(ApprovalCancelStatus.COMPLETED)
                    .build();
            when(cancelOperationRepository.findByTenantAndKey(1L, "key-1")).thenReturn(Mono.just(operation));

            StepVerifier.create(service.cancelApproval("inst-1", "key-1"))
                    .expectNext(true)
                    .verifyComplete();

            verify(client, never()).cancelApproval(anyString(), anyString());
            verify(cancelOperationRepository, never()).update(any());
        } finally {
            TenantContext.restoreTenantId(previousTenantId);
        }
    }

    @Test
    void cancelApproval_remoteCancelledReplaysLocalCompensation() {
        String previousTenantId = TenantContext.setTenantId("1");
        try {
            ApprovalCancelOperation operation = ApprovalCancelOperation.builder()
                    .id(7L)
                    .tenantId(1L)
                    .processInstanceId("inst-2")
                    .idempotencyKey("key-2")
                    .status(ApprovalCancelStatus.REMOTE_CANCELLED)
                    .build();
            when(cancelOperationRepository.findByTenantAndKey(1L, "key-2")).thenReturn(Mono.just(operation));
            when(cancelOperationRepository.completeCancel(1L, "inst-2", 7L)).thenReturn(Mono.just(true));

            StepVerifier.create(service.cancelApproval("inst-2", "key-2"))
                    .expectNext(true)
                    .verifyComplete();

            verify(client, never()).cancelApproval(anyString(), anyString());
            verify(cancelOperationRepository).completeCancel(1L, "inst-2", 7L);
        } finally {
            TenantContext.restoreTenantId(previousTenantId);
        }
    }

    @Test
    void cancelApproval_pendingOperationRejectsReplay() {
        String previousTenantId = TenantContext.setTenantId("1");
        try {
            ApprovalCancelOperation operation = ApprovalCancelOperation.builder()
                    .id(3L)
                    .tenantId(1L)
                    .processInstanceId("inst-3")
                    .idempotencyKey("key-3")
                    .status(ApprovalCancelStatus.PENDING)
                    .build();
            when(cancelOperationRepository.findByTenantAndKey(1L, "key-3")).thenReturn(Mono.just(operation));

            StepVerifier.create(service.cancelApproval("inst-3", "key-3"))
                    .expectErrorMessage("撤销操作正在处理中，请稍后重试")
                    .verify();

            verify(client, never()).cancelApproval(anyString(), anyString());
        } finally {
            TenantContext.restoreTenantId(previousTenantId);
        }
    }

    @Test
    void cancelApproval_failedOperationRetriesFromScratch() {
        String previousTenantId = TenantContext.setTenantId("1");
        try {
            ApprovalCancelOperation operation = ApprovalCancelOperation.builder()
                    .id(5L)
                    .tenantId(1L)
                    .processInstanceId("inst-4")
                    .idempotencyKey("key-4")
                    .status(ApprovalCancelStatus.FAILED)
                    .errorMessage("前一次失败")
                    .build();
            ApprovalProcess process = ApprovalProcess.builder()
                    .processInstanceId("inst-4")
                    .platform("FEISHU")
                    .platformInstanceId("remote-4")
                    .initiatorId("u1")
                    .status(ApprovalStatus.PENDING)
                    .build();
            when(cancelOperationRepository.findByTenantAndKey(1L, "key-4")).thenReturn(Mono.just(operation));
            when(cancelOperationRepository.update(any()))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(repository.findByProcessInstanceId("inst-4")).thenReturn(Mono.just(process));
            when(client.cancelApproval("remote-4", "u1")).thenReturn(Mono.just(true));
            when(cancelOperationRepository.completeCancel(1L, "inst-4", 5L)).thenReturn(Mono.just(true));

            StepVerifier.create(service.cancelApproval("inst-4", "key-4"))
                    .expectNext(true)
                    .verifyComplete();

            verify(cancelOperationRepository, times(2)).update(any());
            assertThat(operation.getStatus()).isEqualTo(ApprovalCancelStatus.REMOTE_CANCELLED);
            assertThat(operation.getErrorMessage()).isNull();
        } finally {
            TenantContext.restoreTenantId(previousTenantId);
        }
    }

    @Test
    void cancelApproval_remoteRejectionMarksFailed() {
        String previousTenantId = TenantContext.setTenantId("1");
        try {
            ApprovalProcess process = ApprovalProcess.builder()
                    .processInstanceId("inst-5")
                    .platform("FEISHU")
                    .platformInstanceId("remote-5")
                    .initiatorId("u5")
                    .status(ApprovalStatus.PENDING)
                    .build();
            when(cancelOperationRepository.findByTenantAndKey(1L, "key-5")).thenReturn(Mono.empty());
            when(repository.findByProcessInstanceId("inst-5")).thenReturn(Mono.just(process));
            when(cancelOperationRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(cancelOperationRepository.update(any()))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(client.cancelApproval("remote-5", "u5")).thenReturn(Mono.just(false));

            StepVerifier.create(service.cancelApproval("inst-5", "key-5"))
                    .expectNext(false)
                    .verifyComplete();

            ArgumentCaptor<ApprovalCancelOperation> captor = ArgumentCaptor.forClass(ApprovalCancelOperation.class);
            verify(cancelOperationRepository).update(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ApprovalCancelStatus.FAILED);
            assertThat(captor.getValue().getErrorMessage()).isEqualTo("远端平台拒绝撤销");
            verify(cancelOperationRepository, never()).completeCancel(anyLong(), anyString(), anyLong());
        } finally {
            TenantContext.restoreTenantId(previousTenantId);
        }
    }

    @Test
    void cancelApproval_remoteErrorMarksFailed() {
        String previousTenantId = TenantContext.setTenantId("1");
        try {
            ApprovalProcess process = ApprovalProcess.builder()
                    .processInstanceId("inst-6")
                    .platform("FEISHU")
                    .platformInstanceId("remote-6")
                    .initiatorId("u6")
                    .status(ApprovalStatus.PENDING)
                    .build();
            when(cancelOperationRepository.findByTenantAndKey(1L, "key-6")).thenReturn(Mono.empty());
            when(repository.findByProcessInstanceId("inst-6")).thenReturn(Mono.just(process));
            when(cancelOperationRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(cancelOperationRepository.update(any()))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(client.cancelApproval("remote-6", "u6")).thenReturn(Mono.error(new IllegalStateException("远端超时")));

            StepVerifier.create(service.cancelApproval("inst-6", "key-6"))
                    .expectNext(false)
                    .verifyComplete();

            ArgumentCaptor<ApprovalCancelOperation> captor = ArgumentCaptor.forClass(ApprovalCancelOperation.class);
            verify(cancelOperationRepository).update(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ApprovalCancelStatus.FAILED);
            assertThat(captor.getValue().getErrorMessage()).isEqualTo("远端超时");
            verify(cancelOperationRepository, never()).completeCancel(anyLong(), anyString(), anyLong());
        } finally {
            TenantContext.restoreTenantId(previousTenantId);
        }
    }

    @Test
    void cancelApproval_blankIdempotencyKeyDerivesAutoKey() {
        String previousTenantId = TenantContext.setTenantId("1");
        try {
            ApprovalCancelOperation operation = ApprovalCancelOperation.builder()
                    .id(3L)
                    .tenantId(1L)
                    .processInstanceId("inst-7")
                    .idempotencyKey("auto:inst-7")
                    .status(ApprovalCancelStatus.COMPLETED)
                    .build();
            when(cancelOperationRepository.findByTenantAndKey(1L, "auto:inst-7"))
                    .thenReturn(Mono.just(operation));

            StepVerifier.create(service.cancelApproval("inst-7", "   "))
                    .expectNext(true)
                    .verifyComplete();
            StepVerifier.create(service.cancelApproval("inst-7"))
                    .expectNext(true)
                    .verifyComplete();

            verify(cancelOperationRepository, times(2)).findByTenantAndKey(1L, "auto:inst-7");
        } finally {
            TenantContext.restoreTenantId(previousTenantId);
        }
    }

    @Test
    void cancelApproval_localCompensationFailureKeepsRemoteCancelledStage() {
        String previousTenantId = TenantContext.setTenantId("1");
        try {
            ApprovalProcess process = ApprovalProcess.builder()
                    .processInstanceId("inst-8")
                    .platform("FEISHU")
                    .platformInstanceId("remote-8")
                    .initiatorId("u8")
                    .status(ApprovalStatus.PENDING)
                    .build();
            when(cancelOperationRepository.findByTenantAndKey(1L, "key-8")).thenReturn(Mono.empty());
            when(repository.findByProcessInstanceId("inst-8")).thenReturn(Mono.just(process));
            when(cancelOperationRepository.save(any())).thenAnswer(invocation -> {
                ApprovalCancelOperation pending = invocation.getArgument(0);
                pending.setId(11L);
                return Mono.just(pending);
            });
            when(cancelOperationRepository.update(any()))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(client.cancelApproval("remote-8", "u8")).thenReturn(Mono.just(true));
            when(cancelOperationRepository.completeCancel(1L, "inst-8", 11L)).thenReturn(Mono.just(false));

            StepVerifier.create(service.cancelApproval("inst-8", "key-8"))
                    .expectNext(true)
                    .verifyComplete();

            ArgumentCaptor<ApprovalCancelOperation> captor = ArgumentCaptor.forClass(ApprovalCancelOperation.class);
            verify(cancelOperationRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ApprovalCancelStatus.REMOTE_CANCELLED);
            verify(cancelOperationRepository).completeCancel(1L, "inst-8", 11L);
        } finally {
            TenantContext.restoreTenantId(previousTenantId);
        }
    }
}
