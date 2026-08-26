package com.apex.scaffold.interfaces.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.PATCH;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.nest;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.test.StepVerifier;

/**
 * 验证 MethodNotAllowedRouter 行为与边界条件的单元测试。
 *
 * @author weiqiang.ling
 */
@DisplayName("HTTP 405 兜底路由测试")
class MethodNotAllowedRouterTest {

    /**
     * 验证 {@code shouldReturn405WhenPathExistsForAnotherMethod} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("已注册路径使用错误方法时应返回 405")
    void shouldReturn405WhenPathExistsForAnotherMethod() {
        RouterFunction<ServerResponse> postRoute =
                route(POST("/api/v1/explore/battles/{battleId}/submit"), request -> ServerResponse.ok()
                        .build());
        MethodNotAllowedRouter fallback = new MethodNotAllowedRouter(List.of(postRoute));

        WebTestClient.bindToRouterFunction(postRoute.andOther(fallback))
                .build()
                .get()
                .uri("/api/v1/explore/battles/2084564840815366146/submit")
                .exchange()
                .expectStatus()
                .isEqualTo(405)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectHeader()
                .valueEquals("Allow", "POST")
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(405);
    }

    /**
     * 验证 {@code shouldNotRecapturePathVariableOnOriginalRequest} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("探测路由不得重复捕获或污染原请求的 battleId")
    void shouldNotRecapturePathVariableOnOriginalRequest() {
        RouterFunction<ServerResponse> nestedRoute = nest(
                org.springframework.web.reactive.function.server.RequestPredicates.path("/api/v1/explore"),
                route(POST("/battles/{battleId}/submit"), request -> ServerResponse.ok()
                        .build()));
        MethodNotAllowedRouter fallback = new MethodNotAllowedRouter(List.of(nestedRoute));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/explore/battles/2084564840815366146/submit")
                        .build());
        Map<String, String> existingVariables = Map.of("battleId", "2084564840815366146");
        exchange.getAttributes().put(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, existingVariables);
        ServerRequest request =
                ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders());

        StepVerifier.create(fallback.route(request)).verifyComplete();

        assertThat((Object) exchange.getAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
                .isSameAs(existingVariables);
    }

    /**
     * 验证 {@code shouldProbeGetAndPatchRoutes} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("GET 与 PATCH 注册路径也应被 405 探测覆盖")
    void shouldProbeGetAndPatchRoutes() {
        RouterFunction<ServerResponse> getRoute =
                route(GET("/api/v1/items/{id}"), request -> ServerResponse.ok().build());
        RouterFunction<ServerResponse> patchRoute = route(
                PATCH("/api/v1/items/{id}"), request -> ServerResponse.ok().build());
        MethodNotAllowedRouter fallback = new MethodNotAllowedRouter(List.of(getRoute, patchRoute));

        WebTestClient client = WebTestClient.bindToRouterFunction(
                        getRoute.andOther(patchRoute).andOther(fallback))
                .build();
        client.delete().uri("/api/v1/items/1").exchange().expectStatus().isEqualTo(405);
        client.post().uri("/api/v1/items/1").exchange().expectStatus().isEqualTo(405);
    }

    /**
     * 验证 {@code shouldKeep404ForUnknownPath} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("未注册路径仍应返回 404")
    void shouldKeep404ForUnknownPath() {
        RouterFunction<ServerResponse> getRoute =
                route(GET("/api/v1/items/{id}"), request -> ServerResponse.ok().build());
        MethodNotAllowedRouter fallback = new MethodNotAllowedRouter(List.of(getRoute));

        WebTestClient.bindToRouterFunction(getRoute.andOther(fallback))
                .build()
                .post()
                .uri("/api/v1/unknown")
                .exchange()
                .expectStatus()
                .isNotFound();
    }
}
