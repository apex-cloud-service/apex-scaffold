package com.apex.scaffold.officeservice.interfaces.handler.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.office.application.OfficeApprovalService;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.approval.ApprovalAction;
import com.apex.scaffold.office.domain.approval.ApprovalProcess;
import com.apex.scaffold.office.domain.approval.ApprovalStatus;
import com.apex.scaffold.officeservice.interfaces.assembler.office.ApprovalProcessAssembler;
import com.apex.scaffold.officeservice.interfaces.dto.office.approval.ApprovalProcessResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.approval.ApproveRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.approval.InitiateApprovalRequest;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("OfficeApprovalHandler 单元测试")
class OfficeApprovalHandlerTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();
    private final RequestHandlerUtil requestHandlerUtil = new RequestHandlerUtil(validator);

    private OfficeApprovalService approvalService;
    private ApprovalProcessAssembler assembler;
    private OfficeApprovalHandler handler;

    @BeforeEach
    void setUp() {
        approvalService = mock(OfficeApprovalService.class);
        assembler = mock(ApprovalProcessAssembler.class);
        handler = new OfficeApprovalHandler(approvalService, assembler, requestHandlerUtil);
    }

    private ApprovalProcess process() {
        return ApprovalProcess.builder()
                .id(1L)
                .tenantId(1L)
                .platform("FEISHU")
                .platformInstanceId("pinst_1")
                .processInstanceId("inst_1")
                .templateId("tpl_1")
                .title("请假申请")
                .initiatorId("u1")
                .initiatorName("张三")
                .status(ApprovalStatus.PENDING)
                .build();
    }

    private ApprovalProcessResponse response() {
        return new ApprovalProcessResponse();
    }

    @Nested
    @DisplayName("initiateApproval")
    class InitiateApprovalTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            InitiateApprovalRequest body = InitiateApprovalRequest.builder()
                    .platform(OfficePlatform.FEISHU)
                    .appId(1L)
                    .processDefinitionId("tpl_1")
                    .initiatorId("u1")
                    .formData("{\"days\":3}")
                    .build();
            when(approvalService.initiateApproval(OfficePlatform.FEISHU, 1L, "tpl_1", "u1", "{\"days\":3}"))
                    .thenReturn(Mono.just(process()));
            when(assembler.toResponse(any(ApprovalProcess.class))).thenReturn(response());

            StepVerifier.create(
                            handler.initiateApproval(MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            InitiateApprovalRequest body = InitiateApprovalRequest.builder()
                    .platform(OfficePlatform.FEISHU)
                    .appId(1L)
                    .processDefinitionId("tpl_1")
                    .initiatorId("u1")
                    .formData("{}")
                    .build();
            when(approvalService.initiateApproval(OfficePlatform.FEISHU, 1L, "tpl_1", "u1", "{}"))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(
                            handler.initiateApproval(MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("参数校验失败返回 HTTP 400")
        void validationError() {
            InitiateApprovalRequest body = InitiateApprovalRequest.builder().build();

            StepVerifier.create(
                            handler.initiateApproval(MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getApprovalDetail")
    class GetApprovalDetailTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(approvalService.getApprovalDetail("inst_1")).thenReturn(Mono.just(process()));
            when(assembler.toResponse(any(ApprovalProcess.class))).thenReturn(response());

            StepVerifier.create(handler.getApprovalDetail(MockServerRequest.builder()
                            .pathVariable("processInstanceId", "inst_1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(approvalService.getApprovalDetail("inst_1")).thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.getApprovalDetail(MockServerRequest.builder()
                            .pathVariable("processInstanceId", "inst_1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("approveProcess")
    class ApproveProcessTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            ApproveRequest body = ApproveRequest.builder()
                    .approverId("u2")
                    .action(ApprovalAction.APPROVE)
                    .comment("同意")
                    .build();
            when(approvalService.approveProcess("inst_1", "u2", ApprovalAction.APPROVE, "同意"))
                    .thenReturn(Mono.just(process()));
            when(assembler.toResponse(any(ApprovalProcess.class))).thenReturn(response());

            StepVerifier.create(handler.approveProcess(MockServerRequest.builder()
                            .pathVariable("processInstanceId", "inst_1")
                            .body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            ApproveRequest body = ApproveRequest.builder()
                    .approverId("u2")
                    .action(ApprovalAction.REJECT)
                    .build();
            when(approvalService.approveProcess("inst_1", "u2", ApprovalAction.REJECT, null))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.approveProcess(MockServerRequest.builder()
                            .pathVariable("processInstanceId", "inst_1")
                            .body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("参数校验失败返回 HTTP 400")
        void validationError() {
            ApproveRequest body = ApproveRequest.builder().build();

            StepVerifier.create(handler.approveProcess(MockServerRequest.builder()
                            .pathVariable("processInstanceId", "inst_1")
                            .body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("cancelApproval")
    class CancelApprovalTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(approvalService.cancelApproval("inst_1")).thenReturn(Mono.just(true));

            StepVerifier.create(handler.cancelApproval(MockServerRequest.builder()
                            .pathVariable("processInstanceId", "inst_1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(approvalService.cancelApproval("inst_1")).thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.cancelApproval(MockServerRequest.builder()
                            .pathVariable("processInstanceId", "inst_1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getPendingApprovals")
    class GetPendingApprovalsTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(approvalService.getPendingApprovals("u2")).thenReturn(Flux.just(process()));
            when(assembler.toResponse(any(ApprovalProcess.class))).thenReturn(response());

            StepVerifier.create(handler.getPendingApprovals(MockServerRequest.builder()
                            .queryParam("approverId", "u2")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(approvalService.getPendingApprovals("u2")).thenReturn(Flux.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.getPendingApprovals(MockServerRequest.builder()
                            .queryParam("approverId", "u2")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("缺少 approverId 参数时抛出 IllegalArgumentException")
        void missingApproverId() {
            MockServerRequest request = MockServerRequest.builder().build();

            assertThatThrownBy(() -> handler.getPendingApprovals(request)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getMyInitiatedApprovals")
    class GetMyInitiatedApprovalsTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(approvalService.getMyInitiatedApprovals("u1")).thenReturn(Flux.just(process()));
            when(assembler.toResponse(any(ApprovalProcess.class))).thenReturn(response());

            StepVerifier.create(handler.getMyInitiatedApprovals(MockServerRequest.builder()
                            .queryParam("initiatorId", "u1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(approvalService.getMyInitiatedApprovals("u1"))
                    .thenReturn(Flux.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.getMyInitiatedApprovals(MockServerRequest.builder()
                            .queryParam("initiatorId", "u1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("缺少 initiatorId 参数时抛出 IllegalArgumentException")
        void missingInitiatorId() {
            MockServerRequest request = MockServerRequest.builder().build();

            assertThatThrownBy(() -> handler.getMyInitiatedApprovals(request))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
