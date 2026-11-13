package com.apex.scaffold.interfaces.router;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.internal.OverviewResponse;
import com.apex.scaffold.application.service.OverviewFacadeService;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.interfaces.handler.PlatformOverviewHandler;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 业务端概览聚合路由单元测试。
 *
 * <p>验证 {@code GET /api/v2/overview} 注册到 {@code PlatformOverviewHandler}：
 * 路由命中后租户取自可信租户上下文（由测试 WebFilter 模拟网关验签后注入的上下文）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("业务端概览聚合路由测试")
class PlatformOverviewRouterTest {

    private final OverviewFacadeService facade = mock(OverviewFacadeService.class);

    private final RouterFunction<ServerResponse> routes =
            new PlatformOverviewRouter().platformOverviewRoutes(new PlatformOverviewHandler(facade));

    private final WebTestClient client = WebTestClient.bindToRouterFunction(routes)
            .webFilter((exchange, chain) -> chain.filter(exchange).contextWrite(TenantContext.withTenant("T001")))
            .build();

    @Test
    @DisplayName("GET /api/v2/overview 命中路由并按上下文租户聚合")
    void overviewRouteDispatchesToHandler() {
        when(facade.getOverview(eq("T001"), eq(null)))
                .thenReturn(Mono.just(new OverviewResponse("T001", LocalDateTime.now(), false, Map.of())));

        client.get()
                .uri("/api/v2/overview")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.tenantId")
                .isEqualTo("T001");
    }
}
