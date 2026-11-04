package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.application.config.OverviewProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 跨域只读聚合门面单元测试（方案 D5）。
 *
 * <p>通过覆写 HTTP 边界（{@code fetchBody}）注入桩响应体，验证真实聚合、
 * 信封解析、降级映射与短 TTL 缓存逻辑；不发起真实网络调用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@DisplayName("跨域只读聚合门面测试")
class OverviewFacadeServiceTest {

    private static final String OK_FILE_BODY = "{\"code\":0,\"msg\":\"success\",\"data\":{\"storageUsageBytes\":1024}}";
    private static final String OK_WORKFLOW_BODY =
            "{\"code\":0,\"msg\":\"success\",\"data\":{\"inProgressInstances\":3}}";
    private static final String OK_IOT_BODY = "{\"code\":0,\"msg\":\"success\",\"data\":{\"deviceCount\":5}}";
    private static final String BUSINESS_ERROR_BODY = "{\"code\":500,\"msg\":\"boom\"}";

    private final ObjectMapperStub objectMapperStub = new ObjectMapperStub();

    /**
     * 测试桩：仅覆写 HTTP 边界，其余聚合/解析/降级/缓存逻辑走真实实现。
     */
    private final class StubFacadeService extends OverviewFacadeService {

        private final Map<String, Mono<String>> stubBodies = new HashMap<>();

        private final AtomicInteger fetchCount = new AtomicInteger();

        private final AtomicReference<String> lastAuthorization = new AtomicReference<>();

        StubFacadeService(OverviewProperties properties) {
            super(
                    properties,
                    objectMapperStub.mapper(),
                    WebClient.builder(),
                    new com.apex.scaffold.common.internal.InternalAuthProperties(),
                    null);
        }

        @Override
        protected Mono<String> fetchBody(String url, String authorization) {
            fetchCount.incrementAndGet();
            lastAuthorization.set(authorization);
            String key =
                    url.contains("/file/stats") ? "file" : url.contains("/workflow/stats") ? "workflow" : "integration";
            Mono<String> body = stubBodies.get(key);
            return body != null ? body : Mono.error(new IllegalStateException("unreachable"));
        }
    }

    /** 共享 ObjectMapper 持有者。 */
    private static final class ObjectMapperStub {
        private final com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();

        com.fasterxml.jackson.databind.ObjectMapper mapper() {
            return mapper;
        }
    }

    @Test
    @DisplayName("三源全部可用：聚合结果 degraded=false，Authorization 透传，TTL 内命中缓存")
    void allSourcesUp() {
        StubFacadeService service = new StubFacadeService(new OverviewProperties());
        service.stubBodies.put("file", Mono.just(OK_FILE_BODY));
        service.stubBodies.put("workflow", Mono.just(OK_WORKFLOW_BODY));
        service.stubBodies.put("integration", Mono.just(OK_IOT_BODY));

        StepVerifier.create(service.getOverview("T001", "Bearer token"))
                .assertNext(response -> {
                    assertThat(response.tenantId()).isEqualTo("T001");
                    assertThat(response.generatedAt()).isNotNull();
                    assertThat(response.degraded()).isFalse();
                    assertThat(response.sources().get("file").status()).isEqualTo("UP");
                    assertThat(response.sources()
                                    .get("file")
                                    .data()
                                    .path("storageUsageBytes")
                                    .asLong())
                            .isEqualTo(1024L);
                    assertThat(response.sources()
                                    .get("workflow")
                                    .data()
                                    .path("inProgressInstances")
                                    .asLong())
                            .isEqualTo(3L);
                    assertThat(response.sources()
                                    .get("integration")
                                    .data()
                                    .path("deviceCount")
                                    .asLong())
                            .isEqualTo(5L);
                })
                .verifyComplete();

        assertThat(service.lastAuthorization.get()).isEqualTo("Bearer token");

        // 短 TTL 缓存命中：第二次调用不再发起下游拉取
        StepVerifier.create(service.getOverview("T001", "Bearer token"))
                .assertNext(response -> assertThat(response.degraded()).isFalse())
                .verifyComplete();
        assertThat(service.fetchCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("单源超时降级：部分可用，整体 degraded=true，该源结果缺失并带错误摘要")
    void workflowDegradedOnTimeout() {
        StubFacadeService service = new StubFacadeService(new OverviewProperties());
        service.stubBodies.put("file", Mono.just(OK_FILE_BODY));
        service.stubBodies.put("workflow", Mono.error(new TimeoutException("no response within timeout")));
        service.stubBodies.put("integration", Mono.just(OK_IOT_BODY));

        StepVerifier.create(service.getOverview("T001", null))
                .assertNext(response -> {
                    assertThat(response.degraded()).isTrue();
                    assertThat(response.sources().get("file").status()).isEqualTo("UP");
                    assertThat(response.sources().get("file").error()).isNull();
                    assertThat(response.sources().get("workflow").status()).isEqualTo("DEGRADED");
                    assertThat(response.sources().get("workflow").data()).isNull();
                    assertThat(response.sources().get("workflow").error()).isEqualTo("TimeoutException");
                    assertThat(response.sources().get("integration").status()).isEqualTo("UP");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("下游业务错误与非法响应体均按源降级")
    void businessErrorAndInvalidBodyDegradePerSource() {
        StubFacadeService service = new StubFacadeService(new OverviewProperties());
        service.stubBodies.put("file", Mono.just(OK_FILE_BODY));
        service.stubBodies.put("workflow", Mono.just(BUSINESS_ERROR_BODY));
        service.stubBodies.put("integration", Mono.just("not-json"));

        StepVerifier.create(service.getOverview("T001", "Bearer token"))
                .assertNext(response -> {
                    assertThat(response.degraded()).isTrue();
                    assertThat(response.sources().get("workflow").status()).isEqualTo("DEGRADED");
                    assertThat(response.sources().get("workflow").error()).isEqualTo("boom");
                    assertThat(response.sources().get("integration").status()).isEqualTo("DEGRADED");
                    assertThat(response.sources().get("integration").error()).isEqualTo("invalid response body");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("未配置地址的数据源按 DEGRADED 降级，不影响其他源")
    void unconfiguredSourceDegrades() {
        OverviewProperties properties = new OverviewProperties();
        properties.getSources().remove("integration");
        StubFacadeService service = new StubFacadeService(properties);
        service.stubBodies.put("file", Mono.just(OK_FILE_BODY));
        service.stubBodies.put("workflow", Mono.just(OK_WORKFLOW_BODY));

        StepVerifier.create(service.getOverview("T001", "Bearer token"))
                .assertNext(response -> {
                    assertThat(response.degraded()).isTrue();
                    assertThat(response.sources().get("integration").status()).isEqualTo("DEGRADED");
                    assertThat(response.sources().get("integration").error()).isEqualTo("source not configured");
                    assertThat(response.sources().get("file").status()).isEqualTo("UP");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("TTL 为 0 时不缓存：每次调用重新拉取下游")
    void zeroTtlAlwaysRefetches() {
        OverviewProperties properties = new OverviewProperties();
        properties.setCacheTtlSeconds(0);
        StubFacadeService service = new StubFacadeService(properties);
        service.stubBodies.put("file", Mono.just(OK_FILE_BODY));
        service.stubBodies.put("workflow", Mono.just(OK_WORKFLOW_BODY));
        service.stubBodies.put("integration", Mono.just(OK_IOT_BODY));

        StepVerifier.create(service.getOverview("T001", "Bearer token"))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(service.getOverview("T001", "Bearer token"))
                .expectNextCount(1)
                .verifyComplete();
        assertThat(service.fetchCount.get()).isEqualTo(6);
    }

    @Test
    @DisplayName("调用方未携带凭证时不透传 Authorization 头")
    void blankAuthorizationNotForwarded() {
        StubFacadeService service = new StubFacadeService(new OverviewProperties());
        service.stubBodies.put("file", Mono.just(OK_FILE_BODY));
        service.stubBodies.put("workflow", Mono.just(OK_WORKFLOW_BODY));
        service.stubBodies.put("integration", Mono.just(OK_IOT_BODY));

        StepVerifier.create(service.getOverview("T001", " ")).expectNextCount(1).verifyComplete();
        assertThat(service.lastAuthorization.get()).isEqualTo(" ");
    }
}
