package com.apex.scaffold.workflowservice.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.contract.dto.internal.InternalTodoItem;
import com.apex.scaffold.workflow.application.dto.WorkflowTaskDTO;
import com.apex.scaffold.workflow.application.query.WorkflowInternalStatsService;
import com.apex.scaffold.workflow.application.query.WorkflowQueryService;
import com.apex.scaffold.workflowservice.interfaces.security.AuthenticatedUser;
import java.util.List;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link WorkflowInternalHandler} 统一待办端点单元测试（S4-a）。
 *
 * <p>覆盖：认证用户待办任务映射为统一待办条目分页（信封契约
 * {@code Result<PageResponse<InternalTodoItem>>}）、pageSize 上限收敛、
 * 未认证 fail-closed 返回 401。不发起真实查询。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("WorkflowInternalHandler 统一待办端点测试")
class WorkflowInternalHandlerTest {

    private WorkflowQueryService workflowQueryService;

    private WorkflowInternalHandler handler;

    @BeforeEach
    void setUp() {
        workflowQueryService = mock(WorkflowQueryService.class);
        handler = new WorkflowInternalHandler(mock(WorkflowInternalStatsService.class), workflowQueryService);
    }

    @Test
    @DisplayName("认证用户待办任务映射为统一待办条目分页")
    void todoTasks_mapsTasksToTodoItems() {
        WorkflowTaskDTO task = new WorkflowTaskDTO();
        task.setId(5001L);
        task.setTaskName("待办任务A");
        task.setStatus("PENDING");
        when(workflowQueryService.listMyTasks(100L, 1, 10))
                .thenReturn(Mono.just(PageResponse.of(List.of(task), 1L, 1, 10)));

        StepVerifier.create(handler.todoTasks(todoRequest(""))
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                .assertNext(response -> {
                    assertThat(response.statusCode().value()).isEqualTo(200);
                    PageResponse<InternalTodoItem> page = readBody(response);
                    assertThat(page.getTotal()).isEqualTo(1L);
                    assertThat(page.getRecords()).hasSize(1);
                    InternalTodoItem item = page.getRecords().get(0);
                    assertThat(item.getItemType()).isEqualTo("workflow_task");
                    assertThat(item.getTitle()).isEqualTo("待办任务A");
                    assertThat(item.getStatus()).isEqualTo("PENDING");
                    assertThat(item.getRefId()).isEqualTo("5001");
                })
                .verifyComplete();

        verify(workflowQueryService).listMyTasks(100L, 1, 10);
    }

    @Test
    @DisplayName("pageSize 超过上限时收敛到 50")
    void todoTasks_capsPageSize() {
        when(workflowQueryService.listMyTasks(100L, 2, 50)).thenReturn(Mono.just(emptyPage()));

        StepVerifier.create(handler.todoTasks(todoRequest("?pageNum=2&pageSize=999"))
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth())))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(200))
                .verifyComplete();

        verify(workflowQueryService).listMyTasks(100L, 2, 50);
    }

    @Test
    @DisplayName("未认证时 fail-closed 返回 401")
    void todoTasks_failsClosedWithoutAuthentication() {
        StepVerifier.create(handler.todoTasks(todoRequest("")))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(401))
                .verifyComplete();
    }

    private PageResponse<WorkflowTaskDTO> emptyPage() {
        return PageResponse.of(List.of(), 0L, 1, 50);
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(100L, "openid", "1", null), null, List.of());
    }

    private ServerRequest todoRequest(String query) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/v1/workflow/todo/tasks" + query)
                .build();
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

    @SuppressWarnings("unchecked")
    private PageResponse<InternalTodoItem> readBody(ServerResponse response) {
        Object entity = ((EntityResponse<?>) response).entity();
        return (PageResponse<InternalTodoItem>) ((Result<?>) entity).getData();
    }
}
