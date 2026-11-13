package com.apex.scaffold.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.internal.OverviewResponse;
import com.apex.scaffold.application.service.OverviewFacadeService;
import com.apex.scaffold.common.context.TenantContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 业务端概览聚合 Handler 单元测试。
 *
 * <p>验证 {@code GET /api/v2/overview} 的租户口径：租户只取自可信租户上下文
 * （不接受查询参数覆盖，防止跨租户读取），缺失可信上下文时按 500 返回；
 * Authorization 头原样透传给门面。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("业务端概览聚合 Handler 测试")
class PlatformOverviewHandlerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final OverviewFacadeService facade = mock(OverviewFacadeService.class);

    private final PlatformOverviewHandler handler = new PlatformOverviewHandler(facade);

    private final ServerRequest request = mock(ServerRequest.class);

    private OverviewResponse sample() {
        return new OverviewResponse("T001", LocalDateTime.now(), false, Map.of());
    }

    @Test
    @DisplayName("可信租户上下文生效且不接受查询参数覆盖租户")
    void tenantTakenFromContextOnly() {
        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(headers.firstHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        when(request.headers()).thenReturn(headers);
        // 尝试通过查询参数覆盖租户：Handler 不得消费该参数
        when(request.queryParam("tenantId")).thenReturn(java.util.Optional.of("EVIL"));
        when(facade.getOverview(eq("T001"), eq("Bearer token"))).thenReturn(Mono.just(sample()));

        StepVerifier.create(handler.overview(request).contextWrite(TenantContext.withTenant("T001")))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(200))
                .expectComplete()
                .verify(TIMEOUT);
        // 门面收到的是上下文租户，而非请求参数携带的 EVIL
        verify(facade).getOverview("T001", "Bearer token");
        verify(facade, never()).getOverview(eq("EVIL"), anyString());
    }

    @Test
    @DisplayName("缺失可信租户上下文返回 500 且不触达门面")
    void missingTenantContextReturns500() {
        when(request.headers()).thenReturn(mock(ServerRequest.Headers.class));

        StepVerifier.create(handler.overview(request))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(500))
                .expectComplete()
                .verify(TIMEOUT);
        verifyNoInteractions(facade);
    }

    @Test
    @DisplayName("门面异常映射为 500，不向上传播")
    void facadeErrorMapsTo500() {
        when(request.headers()).thenReturn(mock(ServerRequest.Headers.class));
        when(facade.getOverview(anyString(), any())).thenReturn(Mono.error(new IllegalStateException("boom")));

        StepVerifier.create(handler.overview(request).contextWrite(TenantContext.withTenant("T001")))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(500))
                .expectComplete()
                .verify(TIMEOUT);
    }
}
