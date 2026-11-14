package com.apex.scaffold.interfaces.router;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.internal.OverviewResponse;
import com.apex.scaffold.application.service.OverviewFacadeService;
import com.apex.scaffold.interfaces.handler.InternalOverviewHandler;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 内部概览聚合路由单元测试。
 *
 * <p>验证 {@code GET /internal/v1/overview} 注册到 {@code InternalOverviewHandler}，
 * 且未匹配路径不命中该路由。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("内部概览聚合路由测试")
class InternalOverviewRouterTest {

    private final OverviewFacadeService facade = mock(OverviewFacadeService.class);

    private final RouterFunction<ServerResponse> routes =
            new InternalOverviewRouter().internalOverviewRoutes(new InternalOverviewHandler(facade));

    private final WebTestClient client =
            WebTestClient.bindToRouterFunction(routes).build();

    private OverviewResponse sample() {
        return new OverviewResponse("T001", LocalDateTime.now(), false, Map.of());
    }

    @Test
    @DisplayName("GET /internal/v1/overview 命中路由并返回聚合结果")
    void overviewRouteDispatchesToHandler() {
        when(facade.getOverview(eq("T001"), eq(null))).thenReturn(Mono.just(sample()));

        client.get()
                .uri("/internal/v1/overview?tenantId=T001")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.tenantId")
                .isEqualTo("T001");
    }

    @Test
    @DisplayName("其余方法与路径不命中该路由")
    void otherPathsAreNotMatched() {
        client.post()
                .uri("/internal/v1/overview?tenantId=T001")
                .exchange()
                .expectStatus()
                .isNotFound();
        client.get()
                .uri("/internal/v1/overview-unrelated?tenantId=T001")
                .exchange()
                .expectStatus()
                .isNotFound();
    }
}
