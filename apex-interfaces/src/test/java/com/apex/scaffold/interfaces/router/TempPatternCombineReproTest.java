package com.apex.scaffold.interfaces.router;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 临时复现测试：验证 PatternParseException 的触发条件（不提交）。
 *
 * @author Apex Team
 */
@DisplayName("临时复现 PatternParseException")
class TempPatternCombineReproTest {
    @Test
    @DisplayName("组合 apiRoutes+exploreRoutes 后 GET reports/impression/me 不应 400")
    void reproduce() {
        RouterFunction<ServerResponse> api = RouterFunctions.<ServerResponse>route()
                .path(
                        "/api/v1",
                        a -> a.path("/reports", r -> r.GET("/impression/{reportId}", req -> ServerResponse.ok()
                                        .build())
                                .GET("/aesthetic-dna/me", req -> ServerResponse.ok()
                                        .build())
                                .GET("/animal/me", req -> ServerResponse.ok().build())
                                .GET("/{reportType}/me", req -> ServerResponse.ok()
                                        .build())))
                .build();

        RouterFunction<ServerResponse> explore = RouterFunctions.<ServerResponse>route()
                .POST(
                        "/api/v1/explore/battles/{battleId}/submit",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        req -> ServerResponse.ok().build())
                .POST("/api/v1/explore/battles/{battleId}/skip", req -> ServerResponse.ok()
                        .build())
                .build();

        WebTestClient.bindToRouterFunction(api.andOther(explore))
                .build()
                .get()
                .uri("/api/v1/reports/impression/me")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    @DisplayName("组合后 POST submit 不应 400")
    void reproduceSubmit() {
        RouterFunction<ServerResponse> api = RouterFunctions.<ServerResponse>route()
                .path(
                        "/api/v1",
                        a -> a.GET("/health", req -> ServerResponse.ok().build()))
                .build();

        RouterFunction<ServerResponse> explore = RouterFunctions.<ServerResponse>route()
                .POST(
                        "/api/v1/explore/battles/{battleId}/submit",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        req -> ServerResponse.ok().build())
                .POST("/api/v1/explore/battles/{battleId}/skip", req -> ServerResponse.ok()
                        .build())
                .build();

        WebTestClient.bindToRouterFunction(api.andOther(explore))
                .build()
                .post()
                .uri("/api/v1/explore/battles/99999/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"selectedPhotoIds\":[\"1\"],\"clickOrder\":[\"1\"]}")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    @DisplayName("组合 MethodNotAllowedRouter 后 GET reports/impression/me 不应 400")
    void reproduceWithMethodNotAllowed() {
        RouterFunction<ServerResponse> api = RouterFunctions.<ServerResponse>route()
                .path(
                        "/api/v1",
                        a -> a.path("/reports", r -> r.GET("/impression/{reportId}", req -> ServerResponse.ok()
                                        .build())
                                .GET("/aesthetic-dna/me", req -> ServerResponse.ok()
                                        .build())
                                .GET("/animal/me", req -> ServerResponse.ok().build())
                                .GET("/{reportType}/me", req -> ServerResponse.ok()
                                        .build())))
                .build();

        RouterFunction<ServerResponse> explore = RouterFunctions.<ServerResponse>route()
                .POST(
                        "/api/v1/explore/battles/{battleId}/submit",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        req -> ServerResponse.ok().build())
                .POST("/api/v1/explore/battles/{battleId}/skip", req -> ServerResponse.ok()
                        .build())
                .build();

        List<RouterFunction<ServerResponse>> delegates = List.of(api, explore);
        MethodNotAllowedRouter mnr = new MethodNotAllowedRouter(delegates);

        WebTestClient.bindToRouterFunction(api.andOther(explore).andOther(mnr))
                .build()
                .get()
                .uri("/api/v1/reports/impression/me")
                .exchange()
                .expectStatus()
                .isOk();
    }
}
