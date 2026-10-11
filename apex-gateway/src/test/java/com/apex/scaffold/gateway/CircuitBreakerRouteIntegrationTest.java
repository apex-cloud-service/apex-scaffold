package com.apex.scaffold.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.result.ResultCode;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 网关路由级熔断降级集成测试.
 *
 * <p>对应迁移方案 Task 1 Step 8 验收：当某条路由的下游<b>持续不可用</b>时，
 * 网关 {@code CircuitBreaker} 过滤器应捕获下游异常并转发到
 * {@code forward:/__gateway/fallback}，最终对外返回 HTTP 503 且响应体为统一
 * {@link com.apex.scaffold.common.result.Result}（错误码 {@link ResultCode#SERVICE_UNAVAILABLE}，
 * 透传 {@code X-Trace-Id}），绝不暴露下游原始异常。
 *
 * <p><b>测试策略</b>：通过 {@link TestConfiguration} 追加一条仅测试可见的路由
 * （{@code /api/example/cb-it/**} → 指向一个本地未监听端口），该路径命中
 * {@code SystemConstants.PUBLIC_PATHS} 中的 {@code /api/example/**}，
 * 从而跳过 JWT 验签、专注于熔断降级链路本身，避免测试依赖真实 Token / 下游服务。
 *
 * <p>使用 {@code dev} 环境（关闭 Nacos 与注册中心），保证无外部服务时上下文仍可加载；
 * 复用随机端口 + {@link WebTestClient} 发起真实 HTTP 调用，端到端验证过滤器链行为。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@DisplayName("网关路由级熔断降级集成测试")
class CircuitBreakerRouteIntegrationTest {

    /** 测试专用路由的匹配路径（属于 PUBLIC_PATHS 白名单，跳过验签） */
    private static final String PROBE_PATH = "/api/example/cb-it/probe";

    /** 测试熔断实例名（对应 CircuitBreaker 过滤器 args.name） */
    private static final String CB_INSTANCE_NAME = "itCbService";

    /** 指向本地未监听端口：连接被拒绝，模拟下游持续不可用 */
    private static final String UNREACHABLE_DOWNSTREAM_URI = "http://127.0.0.1:65001";

    /** 链路追踪 ID，用于断言 fallback 透传 */
    private static final String TRACE_ID = "it-cb-trace";

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @BeforeEach
    void bindClient() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .responseTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * 追加一条下游不可达的测试路由，并挂载路由级 CircuitBreaker + 统一降级兜底.
     */
    @TestConfiguration
    static class UnreachableDownstreamRouteConfig {

        /**
         * 注册仅测试可见的失败路由.
         *
         * @param builder 网关路由构建器
         * @return 含路由级熔断器的路由定位器
         */
        @Bean
        RouteLocator unreachableDownstreamRouteLocator(RouteLocatorBuilder builder) {
            return builder.routes()
                    .route("cb-it-unreachable", route -> route.order(0)
                            .path("/api/example/cb-it/**")
                            .filters(filter -> filter.circuitBreaker(config ->
                                    config.setName(CB_INSTANCE_NAME).setFallbackUri("forward:/__gateway/fallback")))
                            .uri(UNREACHABLE_DOWNSTREAM_URI))
                    .build();
        }
    }

    @Test
    @DisplayName("下游持续失败时应返回 503 且 body 为统一 Result.SERVICE_UNAVAILABLE")
    void shouldReturnUnifiedResultWhenDownstreamKeepsFailing() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            webTestClient
                    .get()
                    .uri(PROBE_PATH)
                    .header("X-Trace-Id", TRACE_ID)
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectHeader()
                    .valueEquals("X-Trace-Id", TRACE_ID)
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(ResultCode.SERVICE_UNAVAILABLE.getCode())
                    .jsonPath("$.msg")
                    .isEqualTo(ResultCode.SERVICE_UNAVAILABLE.getMsg())
                    .jsonPath("$.traceId")
                    .isEqualTo(TRACE_ID)
                    .jsonPath("$.data")
                    .doesNotExist();
        }
    }

    @Test
    @DisplayName("降级响应内容类型应为 application/json 且不暴露下游异常细节")
    void fallbackResponseShouldBeJsonWithoutDownstreamDetail() {
        webTestClient
                .get()
                .uri(PROBE_PATH)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader()
                .contentTypeCompatibleWith("application/json")
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains(String.valueOf(ResultCode.SERVICE_UNAVAILABLE.getCode()))
                        .doesNotContain("ConnectException")
                        .doesNotContain("65001"));
    }
}
