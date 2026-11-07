package com.apex.scaffold.officeservice.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.office.application.port.OfficeMessageStatsQueryPort;
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
 * <p>租户信任模型：以可信租户上下文为准。覆盖：上下文存在且参数一致时正常统计、
 * 参数与上下文不一致返回 403、上下文缺失（含仅有参数）返回 401、
 * 统计信封契约（{@code Result<{messageCount}>}）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("OfficeInternalHandler 消息量统计测试")
class OfficeInternalHandlerTest {

    private OfficeMessageStatsQueryPort statsQueryPort;

    private OfficeInternalHandler handler;

    @BeforeEach
    void setUp() {
        statsQueryPort = mock(OfficeMessageStatsQueryPort.class);
        handler = new OfficeInternalHandler(statsQueryPort);
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
