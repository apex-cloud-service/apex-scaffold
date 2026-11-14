package com.apex.scaffold.officeservice.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.contract.dto.internal.InternalTodoItem;
import com.apex.scaffold.contract.dto.internal.OfficeTodoInternalResponse;
import com.apex.scaffold.office.application.OfficeApprovalService;
import com.apex.scaffold.office.application.port.OfficeMessageStatsQueryPort;
import com.apex.scaffold.office.domain.approval.ApprovalProcess;
import com.apex.scaffold.office.off.application.dto.MessageDTO;
import com.apex.scaffold.office.off.application.service.OffMessageService;
import com.apex.scaffold.officeservice.interfaces.security.AuthenticatedUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.server.EntityResponse;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link OfficeInternalHandler} 单元测试（D4 计量：消息量维度；S4-a 统一待办）。
 *
 * <p>租户信任模型：以可信租户上下文为准。覆盖：上下文存在且参数一致时正常统计、
 * 参数与上下文不一致返回 403、上下文缺失（含仅有参数）返回 401、
 * 统计信封契约（{@code Result<{messageCount}>}）；统一待办端点覆盖认证上下文
 * 解析、条目映射与截断、未认证 fail-closed。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("OfficeInternalHandler 消息量统计测试")
class OfficeInternalHandlerTest {

    private OfficeMessageStatsQueryPort statsQueryPort;

    private OfficeApprovalService approvalService;

    private OffMessageService offMessageService;

    private OfficeInternalHandler handler;

    @BeforeEach
    void setUp() {
        statsQueryPort = mock(OfficeMessageStatsQueryPort.class);
        approvalService = mock(OfficeApprovalService.class);
        offMessageService = mock(OffMessageService.class);
        handler = new OfficeInternalHandler(statsQueryPort, approvalService, offMessageService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("上下文存在且参数一致时返回 messageCount 信封")
    void messageStats_withConsistentTenantParam() {
        when(statsQueryPort.countMessages("tenant-a")).thenReturn(Mono.just(42L));

        ServerRequest request = officeStatsRequest("?tenantId=tenant-a");

        StepVerifier.create(handler.messageStats(request).contextWrite(TenantContext.withTenant("tenant-a")))
                .assertNext(response -> {
                    assertThat(response.statusCode().value()).isEqualTo(200);
                    Map<?, ?> body = readBody(response);
                    assertThat(((Number) body.get("messageCount")).longValue()).isEqualTo(42L);
                })
                .verifyComplete();

        verify(statsQueryPort).countMessages("tenant-a");
    }

    @Test
    @DisplayName("缺失参数时使用可信租户上下文")
    void messageStats_usesTenantContextWhenParamAbsent() {
        when(statsQueryPort.countMessages("tenant-ctx")).thenReturn(Mono.just(7L));

        StepVerifier.create(handler.messageStats(officeStatsRequest(""))
                        .contextWrite(TenantContext.withTenant("tenant-ctx")))
                .assertNext(response ->
                        assertThat(readBody(response).get("messageCount")).isEqualTo(7L))
                .verifyComplete();
    }

    @Test
    @DisplayName("参数与上下文不一致时返回 403")
    void messageStats_rejectsMismatchedTenantParam() {
        StepVerifier.create(handler.messageStats(officeStatsRequest("?tenantId=tenant-b"))
                        .contextWrite(TenantContext.withTenant("tenant-a")))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(403))
                .verifyComplete();
    }

    @Test
    @DisplayName("双缺失租户标识时 fail-closed 返回 401")
    void messageStats_failsClosedWithoutTenant() {
        StepVerifier.create(handler.messageStats(officeStatsRequest("")))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(401))
                .verifyComplete();
    }

    @Test
    @DisplayName("仅提供参数但无上下文时仍返回 401，不允许参数覆盖上下文")
    void messageStats_rejectsParamWithoutContext() {
        StepVerifier.create(handler.messageStats(officeStatsRequest("?tenantId=tenant-a")))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(401))
                .verifyComplete();
    }

    @Test
    @DisplayName("todo：认证用户返回待审批与未读消息两组条目及真实总数")
    void todo_returnsApprovalsAndUnreadMessages() {
        when(approvalService.getPendingApprovals("100"))
                .thenReturn(Flux.fromIterable(List.of(
                        ApprovalProcess.builder()
                                .id(9001L)
                                .title("审批A")
                                .initiateTime(LocalDateTime.of(2026, 11, 11, 9, 0))
                                .build(),
                        ApprovalProcess.builder().id(9002L).title("审批B").build())));
        when(offMessageService.getInbox(100L, 1, 10))
                .thenReturn(Flux.fromIterable(List.of(
                        MessageDTO.builder()
                                .id(7L)
                                .subject("通知信")
                                .readStatus(false)
                                .sendTime(LocalDateTime.of(2026, 11, 11, 8, 0))
                                .build(),
                        MessageDTO.builder()
                                .id(8L)
                                .subject("已读信")
                                .readStatus(true)
                                .build())));
        when(offMessageService.countUnread(100L)).thenReturn(Mono.just(1L));

        StepVerifier.create(handler.todo(todoRequest(""))
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                .assertNext(response -> {
                    assertThat(response.statusCode().value()).isEqualTo(200);
                    OfficeTodoInternalResponse body = readTodoBody(response);
                    assertThat(body.getApprovals()).hasSize(2);
                    assertThat(body.getApprovalTotal()).isEqualTo(2L);
                    assertThat(body.getMessages()).hasSize(1);
                    assertThat(body.getUnreadTotal()).isEqualTo(1L);
                    InternalTodoItem approval = body.getApprovals().get(0);
                    assertThat(approval.getItemType()).isEqualTo("office_approval");
                    assertThat(approval.getTitle()).isEqualTo("审批A");
                    assertThat(approval.getRefId()).isEqualTo("9001");
                    assertThat(approval.getStatus()).isEqualTo("PENDING");
                    InternalTodoItem message = body.getMessages().get(0);
                    assertThat(message.getItemType()).isEqualTo("office_message");
                    assertThat(message.getTitle()).isEqualTo("通知信");
                    assertThat(message.getStatus()).isEqualTo("UNREAD");
                })
                .verifyComplete();

        verify(approvalService).getPendingApprovals("100");
    }

    @Test
    @DisplayName("todo：limit 参数截断条目但总数保留真实数量")
    void todo_truncatesItemsWithRealTotals() {
        when(approvalService.getPendingApprovals("100"))
                .thenReturn(Flux.fromIterable(List.of(
                        ApprovalProcess.builder().id(1L).title("A").build(),
                        ApprovalProcess.builder().id(2L).title("B").build(),
                        ApprovalProcess.builder().id(3L).title("C").build())));
        when(offMessageService.getInbox(100L, 1, 2)).thenReturn(Flux.empty());
        when(offMessageService.countUnread(100L)).thenReturn(Mono.just(0L));

        StepVerifier.create(handler.todo(todoRequest("?approvalLimit=2&messageLimit=2"))
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                .assertNext(response -> {
                    OfficeTodoInternalResponse body = readTodoBody(response);
                    assertThat(body.getApprovals()).hasSize(2);
                    assertThat(body.getApprovalTotal()).isEqualTo(3L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("todo：未认证时 fail-closed 返回 401")
    void todo_failsClosedWithoutAuthentication() {
        StepVerifier.create(handler.todo(todoRequest("")))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(401))
                .verifyComplete();
    }

    private ServerRequest todoRequest(String query) {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/internal/v1/office/todo" + query).build();
        MockServerRequest.Builder builder =
                MockServerRequest.builder().uri(request.getURI()).exchange(MockServerWebExchange.from(request));
        if (query != null && query.startsWith("?")) {
            for (String pair : query.substring(1).split("&")) {
                int eq = pair.indexOf('=');
                builder.queryParam(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return builder.build();
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(100L, "openid", "1"), null, List.of());
    }

    @SuppressWarnings("unchecked")
    private OfficeTodoInternalResponse readTodoBody(ServerResponse response) {
        Object entity = ((EntityResponse<?>) response).entity();
        return (OfficeTodoInternalResponse) ((com.apex.scaffold.common.result.Result<?>) entity).getData();
    }

    private ServerRequest officeStatsRequest(String query) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/v1/office/messages/stats" + query)
                .build();
        org.springframework.mock.web.reactive.function.server.MockServerRequest.Builder builder =
                MockServerRequest.builder().uri(request.getURI()).exchange(MockServerWebExchange.from(request));
        if (query != null && !query.isBlank()) {
            // 去掉开头的 ?，按 k=v 解析（本测试仅单参数场景）
            String pair = query.substring(1);
            int eq = pair.indexOf('=');
            builder.queryParam(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> readBody(org.springframework.web.reactive.function.server.ServerResponse response) {
        Object entity = ((org.springframework.web.reactive.function.server.EntityResponse<?>) response).entity();
        return (Map<?, ?>) ((com.apex.scaffold.common.result.Result<?>) entity).getData();
    }
}
