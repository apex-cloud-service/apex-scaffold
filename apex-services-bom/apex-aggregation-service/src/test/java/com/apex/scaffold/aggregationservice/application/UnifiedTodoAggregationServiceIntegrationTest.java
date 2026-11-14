package com.apex.scaffold.aggregationservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.aggregationservice.config.TodoProperties;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.internal.InternalAuthProperties;
import com.apex.scaffold.common.internal.InternalServiceSigner;
import com.apex.scaffold.contract.dto.internal.UnifiedTodoResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

/**
 * 统一待办聚合服务集成测试（真实 HTTP 出站链路，Failsafe 门禁执行）。
 *
 * <p>以 {@code MockWebServer} 扮演 workflow / office 内部端点，经真实 WebClient +
 * 内部签名过滤器（{@code InternalAuthExchangeFilter}）验证端到端出站契约：
 * <ul>
 *   <li>两源聚合与信封解析（含分页/待审批/未读站内信负载）；</li>
 *   <li>内部签名头（{@code X-Internal-Service}）与可信租户头（{@code X-Internal-Tenant}，
 *       取自 Reactor 租户上下文）按契约下发；调用方 Authorization 原样透传；</li>
 *   <li>单源超时与业务失败（信封非 0）按源降级为 DEGRADED，不影响其他源。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("统一待办聚合服务集成测试")
class UnifiedTodoAggregationServiceIntegrationTest {

    private static final String WORKFLOW_BODY =
            """
            {"code":0,"data":{"list":[{"itemType":"workflow_task","title":"审批报销",\
            "occurredAt":"2026-11-12T10:00:00","status":"PENDING","refId":"t-1"}],"total":1}}
            """;

    private static final String OFFICE_BODY =
            """
            {"code":0,"data":{"approvals":[{"itemType":"office_approval","title":"请假",\
            "status":"PENDING","refId":"a-1"}],"messages":[{"itemType":"office_message",\
            "title":"通知","status":"UNREAD","refId":"m-1"}],"approvalTotal":1,"unreadTotal":1}}
            """;

    private MockWebServer server;

    private TodoProperties properties;

    private UnifiedTodoAggregationService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        properties = new TodoProperties();
        // 超时预算放宽到 5s：本机 -T4 全量构建（PMD/JaCoCo/Failsafe 并行）时 MockWebServer
        // 回环响应可能被 CPU 抢占延迟，2000ms 会偶发误判为降级（非产品超时语义）
        properties.setTimeoutMillis(5000);
        properties.setSources(java.util.Map.of(
                "workflow", server.url("/").toString(),
                "office", server.url("/").toString()));
        service = new UnifiedTodoAggregationService(
                properties,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                WebClient.builder(),
                new InternalAuthProperties(),
                new InternalServiceSigner("it-secret-0123456789abcdef0123", Duration.ofSeconds(60), Clock.systemUTC()));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private void dispatchNormal() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                if (path.startsWith("/internal/v1/workflow/todo/tasks")) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody(WORKFLOW_BODY);
                }
                if (path.startsWith("/internal/v1/office/todo")) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody(OFFICE_BODY);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
    }

    @Test
    @DisplayName("两源聚合成功且内部签名/租户/凭证头按契约下发")
    void aggregatesBothSourcesWithSignedHeaders() throws Exception {
        dispatchNormal();

        UnifiedTodoResponse response = service.getUnifiedTodo(1001L, "Bearer token")
                .contextWrite(TenantContext.withTenant("T001"))
                .block(Duration.ofSeconds(10));

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(1001L);
        assertThat(response.degraded()).isFalse();
        assertThat(response.sources().get("workflow").status()).isEqualTo("UP");
        assertThat(response.sources().get("office").status()).isEqualTo("UP");
        assertThat(response.sources().get("office").total()).isEqualTo(2L);

        // 两源并发出站，顺序不保证：按路径分别取 workflow 与 office 请求校验契约
        RecordedRequest workflowRequest = null;
        RecordedRequest officeRequest = null;
        for (int i = 0; i < 2; i++) {
            RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(recorded).isNotNull();
            String path = recorded.getPath() == null ? "" : recorded.getPath();
            if (path.startsWith("/internal/v1/workflow/todo/tasks")) {
                workflowRequest = recorded;
            } else if (path.startsWith("/internal/v1/office/todo")) {
                officeRequest = recorded;
            }
        }
        assertThat(workflowRequest).as("workflow 源出站请求").isNotNull();
        assertThat(officeRequest).as("office 源出站请求").isNotNull();
        assertThat(workflowRequest.getHeader("X-Internal-Service")).isEqualTo("apex-service");
        assertThat(workflowRequest.getHeader("X-Internal-Tenant")).isEqualTo("T001");
        assertThat(workflowRequest.getHeader("Authorization")).isEqualTo("Bearer token");
        assertThat(workflowRequest.getHeader("X-Internal-Signature")).isNotBlank();
        assertThat(workflowRequest.getPath()).startsWith("/internal/v1/workflow/todo/tasks?pageNum=1&pageSize=10");
        assertThat(officeRequest.getPath()).startsWith("/internal/v1/office/todo?approvalLimit=10&messageLimit=10");
    }

    @Test
    @DisplayName("上游超时的源按 DEGRADED 降级且不影响其他源")
    void upstreamTimeoutDegradesSingleSource() {
        dispatchNormal();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                MockResponse response = new MockResponse().setHeader("Content-Type", "application/json");
                if (path.startsWith("/internal/v1/workflow/todo/tasks")) {
                    // 超过门面超时阈值才返回，workflow 源按超时降级
                    return response.setHeadersDelay(7, TimeUnit.SECONDS).setBody(WORKFLOW_BODY);
                }
                if (path.startsWith("/internal/v1/office/todo")) {
                    return response.setBody(OFFICE_BODY);
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        StepVerifier.create(service.getUnifiedTodo(1002L, null))
                .assertNext(result -> {
                    assertThat(result.degraded()).isTrue();
                    assertThat(result.sources().get("workflow").status()).isEqualTo("DEGRADED");
                    assertThat(result.sources().get("workflow").error()).contains("TimeoutException");
                    assertThat(result.sources().get("office").status()).isEqualTo("UP");
                })
                .expectComplete()
                .verify(Duration.ofSeconds(25));
    }

    @Test
    @DisplayName("信封业务失败（code 非 0）的源按 DEGRADED 降级")
    void businessErrorEnvelopeDegradesSingleSource() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                MockResponse response = new MockResponse().setHeader("Content-Type", "application/json");
                if (path.startsWith("/internal/v1/workflow/todo/tasks")) {
                    return response.setBody("{\"code\":500,\"msg\":\"boom\"}");
                }
                if (path.startsWith("/internal/v1/office/todo")) {
                    return response.setBody(OFFICE_BODY);
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        StepVerifier.create(service.getUnifiedTodo(1003L, null))
                .assertNext(result -> {
                    assertThat(result.degraded()).isTrue();
                    assertThat(result.sources().get("workflow").status()).isEqualTo("DEGRADED");
                    assertThat(result.sources().get("office").status()).isEqualTo("UP");
                })
                .expectComplete()
                .verify(Duration.ofSeconds(25));
    }
}
