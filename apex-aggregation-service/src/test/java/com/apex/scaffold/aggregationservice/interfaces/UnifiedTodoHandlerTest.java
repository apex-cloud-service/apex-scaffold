package com.apex.scaffold.aggregationservice.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.aggregationservice.application.UnifiedTodoAggregationService;
import com.apex.scaffold.contract.dto.internal.UnifiedTodoResponse;
import com.apex.scaffold.contract.dto.internal.UnifiedTodoResponse.SourceSection;
import java.security.Principal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 统一待办查询 Handler 单元测试。
 *
 * <p>验证 {@code GET /api/v2/todo} 的身份解析（Principal 取用户标识、非认证返回 401）、
 * 聚合服务委派与错误映射（聚合异常按 500 返回业务包装，不向上抛错）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("统一待办查询 Handler 测试")
class UnifiedTodoHandlerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final UnifiedTodoAggregationService aggregationService = mock(UnifiedTodoAggregationService.class);

    private final UnifiedTodoHandler handler = new UnifiedTodoHandler(aggregationService);

    private final ServerRequest request = mock(ServerRequest.class);

    private UnifiedTodoResponse sample(Long userId) {
        Map<String, SourceSection> sources = new LinkedHashMap<>();
        sources.put("workflow", SourceSection.up(1L, java.util.List.of()));
        sources.put("office", SourceSection.degraded("source not configured"));
        return new UnifiedTodoResponse(userId, LocalDateTime.now(), true, sources);
    }

    @Test
    @DisplayName("未认证（无 Principal）返回 401 且不触达聚合服务")
    void missingPrincipalReturns401() {
        when(request.headers())
                .thenReturn(mock(org.springframework.web.reactive.function.server.ServerRequest.Headers.class));
        org.mockito.Mockito.doReturn(Mono.empty()).when(request).principal();

        StepVerifier.create(handler.todo(request))
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
                .expectComplete()
                .verify(TIMEOUT);
        verify(aggregationService, never()).getUnifiedTodo(any(), anyString());
    }

    @Test
    @DisplayName("Principal 名称非数字标识返回 401")
    void nonNumericPrincipalReturns401() {
        when(request.headers())
                .thenReturn(mock(org.springframework.web.reactive.function.server.ServerRequest.Headers.class));
        org.mockito.Mockito.doReturn(Mono.just((Principal) () -> "not-a-number"))
                .when(request)
                .principal();

        StepVerifier.create(handler.todo(request))
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
                .expectComplete()
                .verify(TIMEOUT);
        verify(aggregationService, never()).getUnifiedTodo(any(), anyString());
    }

    @Test
    @DisplayName("合法身份返回 200 并透传 Authorization 给聚合服务")
    void authenticatedRequestDelegatesToAggregation() {
        org.mockito.Mockito.doReturn(Mono.just((Principal) () -> "1001"))
                .when(request)
                .principal();
        org.springframework.web.reactive.function.server.ServerRequest.Headers headers =
                mock(org.springframework.web.reactive.function.server.ServerRequest.Headers.class);
        when(headers.firstHeader(org.springframework.http.HttpHeaders.AUTHORIZATION))
                .thenReturn("Bearer token");
        when(request.headers()).thenReturn(headers);
        when(aggregationService.getUnifiedTodo(1001L, "Bearer token")).thenReturn(Mono.just(sample(1001L)));

        StepVerifier.create(handler.todo(request))
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.OK))
                .expectComplete()
                .verify(TIMEOUT);
        verify(aggregationService).getUnifiedTodo(1001L, "Bearer token");
    }

    @Test
    @DisplayName("聚合服务异常映射为 500 业务包装，不向上传播")
    void aggregationErrorMapsTo500() {
        org.mockito.Mockito.doReturn(Mono.just((Principal) () -> "1001"))
                .when(request)
                .principal();
        when(request.headers())
                .thenReturn(mock(org.springframework.web.reactive.function.server.ServerRequest.Headers.class));
        when(aggregationService.getUnifiedTodo(any(), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("downstream boom")));

        StepVerifier.create(handler.todo(request))
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR))
                .expectComplete()
                .verify(TIMEOUT);
    }
}
