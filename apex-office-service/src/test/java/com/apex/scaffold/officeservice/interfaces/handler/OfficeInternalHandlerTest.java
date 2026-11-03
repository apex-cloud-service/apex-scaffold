package com.apex.scaffold.officeservice.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.officeservice.infrastructure.stats.OfficeInternalStatsRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link OfficeInternalHandler} 单元测试（D4 计量：消息量维度）。
 *
 * <p>覆盖：显式 {@code tenantId} 参数优先（system 计量门面无租户上下文调用）、
 * 缺失参数回退可信租户上下文、统计信封契约（{@code Result<{messageCount}>}）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("OfficeInternalHandler 消息量统计测试")
class OfficeInternalHandlerTest {

    private OfficeInternalStatsRepository statsRepository;

    private OfficeInternalHandler handler;

    @BeforeEach
    void setUp() {
        statsRepository = mock(OfficeInternalStatsRepository.class);
        handler = new OfficeInternalHandler(statsRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("显式 tenantId 参数优先并返回 messageCount 信封")
    void messageStats_withExplicitTenantParam() {
        when(statsRepository.countMessages("tenant-a")).thenReturn(Mono.just(42L));

        ServerRequest request = officeStatsRequest("?tenantId=tenant-a");

        StepVerifier.create(handler.messageStats(request))
                .assertNext(response -> {
                    assertThat(response.statusCode().value()).isEqualTo(200);
                    Map<?, ?> body = readBody(response);
                    assertThat(((Number) body.get("messageCount")).longValue()).isEqualTo(42L);
                })
                .verifyComplete();

        verify(statsRepository).countMessages("tenant-a");
    }

    @Test
    @DisplayName("缺失参数时回退可信租户上下文")
    void messageStats_fallsBackToTenantContext() {
        when(statsRepository.countMessages("tenant-ctx")).thenReturn(Mono.just(7L));

        StepVerifier.create(handler.messageStats(officeStatsRequest(""))
                        .contextWrite(TenantContext.withTenant("tenant-ctx")))
                .assertNext(response ->
                        assertThat(readBody(response).get("messageCount")).isEqualTo(7L))
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
