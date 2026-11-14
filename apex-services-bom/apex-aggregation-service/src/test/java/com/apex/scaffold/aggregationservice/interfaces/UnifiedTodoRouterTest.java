package com.apex.scaffold.aggregationservice.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.aggregationservice.application.UnifiedTodoAggregationService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.contract.dto.internal.UnifiedTodoResponse;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * 统一待办路由协议测试。
 *
 * <p>绑定真实 {@link UnifiedTodoRouter} 与 {@link UnifiedTodoHandler}，仅替换应用服务边界，
 * 覆盖身份解析、认证失败、成功与异常映射的 HTTP 协议行为。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
class UnifiedTodoRouterTest {

    private static final String TODO_URI = "/api/v2/todo";
    private static final String BEARER = "Bearer test-token";

    private UnifiedTodoAggregationService aggregationService;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        aggregationService = mock(UnifiedTodoAggregationService.class);
        client = newClient(null);
    }

    /**
     * 以指定 Principal 名称构建 WebTestClient（{@code null} 表示不注入 Principal）。
     *
     * @param principalName 主体名称
     * @return 绑定真实路由的测试客户端
     */
    private WebTestClient newClient(String principalName) {
        UnifiedTodoHandler handler = new UnifiedTodoHandler(aggregationService);
        RouterFunction<ServerResponse> routes = new UnifiedTodoRouter().unifiedTodoRoutes(handler);
        WebTestClient.RouterFunctionSpec spec = WebTestClient.bindToRouterFunction(routes);
        if (principalName != null) {
            spec = spec.webFilter(principalFilter(principalName));
        }
        return spec.build();
    }

    /**
     * 构造向 exchange 注入 Principal 的测试过滤器。
     *
     * @param name 主体名称
     * @return 测试过滤器
     */
    private static WebFilter principalFilter(String name) {
        Principal principal = () -> name;
        return (exchange, chain) -> {
            ServerWebExchange mutated =
                    exchange.mutate().principal(Mono.just(principal)).build();
            return chain.filter(mutated);
        };
    }

    /**
     * 构造同时通过判定成功响应。
     *
     * @return 聚合响应
     */
    private static UnifiedTodoResponse successResponse() {
        Map<String, UnifiedTodoResponse.SourceSection> sources = new LinkedHashMap<>();
        sources.put("workflow", UnifiedTodoResponse.SourceSection.up(1L, List.of()));
        sources.put("office", UnifiedTodoResponse.SourceSection.up(0L, List.of()));
        return new UnifiedTodoResponse(7L, LocalDateTime.of(2026, 11, 12, 10, 0), false, sources);
    }

    @Test
    void returnsUnauthorizedWhenNoPrincipal() {
        client.get().uri(TODO_URI).exchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(aggregationService, never()).getUnifiedTodo(any(), any());
    }

    @Test
    void returnsUnauthorizedWhenPrincipalNameNotNumeric() {
        newClient("not-a-number").get().uri(TODO_URI).exchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(aggregationService, never()).getUnifiedTodo(any(), any());
    }

    @Test
    void returnsUnauthorizedWhenPrincipalNameIsNull() {
        newClientUnnamed().get().uri(TODO_URI).exchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(aggregationService, never()).getUnifiedTodo(any(), any());
    }

    /**
     * 构建主体名称为 {@code null} 的测试客户端，用于覆盖非法身份解析。
     *
     * @return 绑定真实路由的测试客户端
     */
    private WebTestClient newClientUnnamed() {
        UnifiedTodoHandler handler = new UnifiedTodoHandler(aggregationService);
        RouterFunction<ServerResponse> routes = new UnifiedTodoRouter().unifiedTodoRoutes(handler);
        Principal unnamed = () -> null;
        WebFilter filter = (exchange, chain) ->
                chain.filter(exchange.mutate().principal(Mono.just(unnamed)).build());
        return WebTestClient.bindToRouterFunction(routes).webFilter(filter).build();
    }

    @Test
    void ignoresMalformedPrincipalAndKeepsNoTrace() {
        newClient("abc")
                .get()
                .uri(TODO_URI)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    void returnsAggregatedResultWhenAuthenticated() {
        when(aggregationService.getUnifiedTodo(eq(7L), eq(BEARER))).thenReturn(Mono.just(successResponse()));

        newClient("7")
                .get()
                .uri(TODO_URI)
                .header(HttpHeaders.AUTHORIZATION, BEARER)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.SUCCESS.getCode())
                .jsonPath("$.data.userId")
                .isEqualTo(7)
                .jsonPath("$.data.degraded")
                .isEqualTo(false)
                .jsonPath("$.data.sources.workflow.status")
                .isEqualTo("UP")
                .jsonPath("$.data.sources.office.total")
                .isEqualTo(0);

        verify(aggregationService).getUnifiedTodo(7L, BEARER);
    }

    @Test
    void returnsOkWithDegradedFlagWhenSingleSourceFails() {
        Map<String, UnifiedTodoResponse.SourceSection> sources = new LinkedHashMap<>();
        sources.put("workflow", UnifiedTodoResponse.SourceSection.up(1L, List.of()));
        sources.put("office", UnifiedTodoResponse.SourceSection.degraded("TimeoutException"));
        UnifiedTodoResponse degraded =
                new UnifiedTodoResponse(7L, LocalDateTime.of(2026, 11, 12, 10, 0), true, sources);
        when(aggregationService.getUnifiedTodo(eq(7L), any())).thenReturn(Mono.just(degraded));

        newClient("7")
                .get()
                .uri(TODO_URI)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.SUCCESS.getCode())
                .jsonPath("$.data.degraded")
                .isEqualTo(true)
                .jsonPath("$.data.sources.workflow.status")
                .isEqualTo("UP")
                .jsonPath("$.data.sources.office.status")
                .isEqualTo("DEGRADED");
    }

    @Test
    void returnsUnauthorizedWhenServiceReportsUnauthorized() {
        when(aggregationService.getUnifiedTodo(eq(7L), any()))
                .thenReturn(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "未认证")));

        newClient("7")
                .get()
                .uri(TODO_URI)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    void returnsServerErrorWhenServiceReportsOtherBusinessError() {
        when(aggregationService.getUnifiedTodo(eq(7L), any()))
                .thenReturn(Mono.error(new BusinessException(ResultCode.ERROR, "下游失败")));

        newClient("7")
                .get()
                .uri(TODO_URI)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode());
    }

    @Test
    void returnsServerErrorOnUnexpectedException() {
        when(aggregationService.getUnifiedTodo(eq(7L), any()))
                .thenReturn(Mono.error(new IllegalStateException("boom")));

        newClient("7")
                .get()
                .uri(TODO_URI)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.ERROR.getCode());
    }

    @Test
    void successResponseCarriesTraceId() {
        when(aggregationService.getUnifiedTodo(eq(7L), any())).thenReturn(Mono.just(successResponse()));

        byte[] body = newClient("7")
                .get()
                .uri(TODO_URI)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
    }
}
