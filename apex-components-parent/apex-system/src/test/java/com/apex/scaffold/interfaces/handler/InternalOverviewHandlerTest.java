package com.apex.scaffold.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.internal.OverviewResponse;
import com.apex.scaffold.application.service.OverviewFacadeService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 跨域只读聚合门面内部端点 Handler 单元测试。
 *
 * <p>验证 {@code GET /internal/v1/overview} 的入参校验（tenantId 必填、长度上限 64）、
 * Authorization 头透传与失败路径映射（门面异常按 500 返回，不向上抛错）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("内部概览聚合 Handler 测试")
class InternalOverviewHandlerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final OverviewFacadeService facade = mock(OverviewFacadeService.class);

    private final InternalOverviewHandler handler = new InternalOverviewHandler(facade);

    private final ServerRequest request = mock(ServerRequest.class);

    private OverviewResponse sample() {
        return new OverviewResponse("T001", LocalDateTime.now(), false, Map.of());
    }

    private void stubQueryParam(String tenantId) {
        when(request.queryParam("tenantId")).thenReturn(Optional.ofNullable(tenantId));
        when(request.headers()).thenReturn(mock(ServerRequest.Headers.class));
    }

    @Test
    @DisplayName("合法 tenantId 返回 200 并透传 Authorization")
    void validTenantIdReturnsAggregatedResult() {
        stubQueryParam("T001");
        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(headers.firstHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(request.headers()).thenReturn(headers);
        when(facade.getOverview(eq("T001"), eq("Bearer token"))).thenReturn(Mono.just(sample()));

        StepVerifier.create(handler.getOverview(request))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(200))
                .expectComplete()
                .verify(TIMEOUT);
    }

    @Test
    @DisplayName("tenantId 缺失返回 400 且不触达门面")
    void missingTenantIdReturns400() {
        stubQueryParam(null);

        StepVerifier.create(handler.getOverview(request))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(400))
                .expectComplete()
                .verify(TIMEOUT);
        verifyNoInteractions(facade);
    }

    @Test
    @DisplayName("tenantId 空白或超长返回 400 且不触达门面")
    void blankOrOverlongTenantIdReturns400() {
        stubQueryParam("   ");
        StepVerifier.create(handler.getOverview(request))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(400))
                .expectComplete()
                .verify(TIMEOUT);

        stubQueryParam("T".repeat(65));
        StepVerifier.create(handler.getOverview(request))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(400))
                .expectComplete()
                .verify(TIMEOUT);
        verifyNoInteractions(facade);
    }

    @Test
    @DisplayName("门面异常映射为 500，不向上传播")
    void facadeErrorMapsTo500() {
        stubQueryParam("T001");
        when(facade.getOverview(anyString(), any())).thenReturn(Mono.error(new IllegalStateException("boom")));

        StepVerifier.create(handler.getOverview(request))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(500))
                .expectComplete()
                .verify(TIMEOUT);
    }
}
