package com.apex.scaffold.aggregationservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.aggregationservice.config.TodoProperties;
import com.apex.scaffold.common.internal.InternalAuthProperties;
import com.apex.scaffold.common.internal.InternalServiceSigner;
import com.apex.scaffold.contract.dto.internal.UnifiedTodoResponse;
import com.apex.scaffold.contract.dto.internal.UnifiedTodoResponse.SourceSection;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 统一待办聚合服务单元测试。
 *
 * <p>覆写 {@link UnifiedTodoAggregationService#fetchBody} 注入桩响应，完全隔离网络；
 * 覆盖双源正常聚合、单源降级、信封业务失败、数据源未配置、舱壁饱和与不缓存语义。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
class UnifiedTodoAggregationServiceTest {

    /** workflow 分页桩响应；注意 PageResponse 序列化键为 {@code list}。 */
    private static final String WORKFLOW_BODY =
            """
            {"code":0,"data":{"list":[{"itemType":"workflow_task","title":"审批报销",\
            "link":"/workflow/1","occurredAt":"2026-11-12T10:00:00","status":"PENDING","refId":"t-1"}],\
            "total":1}}
            """;

    /** office 待办桩响应：待审批 1 条 + 未读站内信 1 条。 */
    private static final String OFFICE_BODY =
            """
            {"code":0,"data":{"approvals":[{"itemType":"office_approval","title":"请假","status":"PENDING",\
            "refId":"a-1"}],"messages":[{"itemType":"office_message","title":"通知","status":"UNREAD",\
            "refId":"m-1"}],"approvalTotal":1,"unreadTotal":1}}
            """;

    private ObjectMapper objectMapper;

    private TodoProperties properties;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        properties = new TodoProperties();
        properties.setTimeoutMillis(1000);
    }

    private UnifiedTodoAggregationService newService(Function<String, Mono<String>> stub) {
        return new UnifiedTodoAggregationService(
                properties,
                objectMapper,
                WebClient.builder(),
                new InternalAuthProperties(),
                new InternalServiceSigner("", Duration.ofSeconds(60), Clock.systemUTC())) {
            @Override
            protected Mono<String> fetchBody(String url, String authorization) {
                return stub.apply(url);
            }
        };
    }

    private static Function<String, Mono<String>> bySource(String workflow, String office) {
        return url -> url.contains("/workflow/") ? Mono.just(workflow) : Mono.just(office);
    }

    @Test
    @DisplayName("双源正常：聚合两源条目且不降级")
    void aggregatesBothSources() {
        UnifiedTodoAggregationService service = newService(bySource(WORKFLOW_BODY, OFFICE_BODY));

        StepVerifier.create(service.getUnifiedTodo(7L, "Bearer token"))
                .assertNext(response -> {
                    assertThat(response.userId()).isEqualTo(7L);
                    assertThat(response.retrievedAt()).isNotNull();
                    assertThat(response.degraded()).isFalse();
                    Map<String, SourceSection> sources = response.sources();
                    assertThat(sources).containsOnlyKeys("workflow", "office");

                    SourceSection workflow = sources.get("workflow");
                    assertThat(workflow.status()).isEqualTo("UP");
                    assertThat(workflow.total()).isEqualTo(1L);
                    assertThat(workflow.items()).hasSize(1);
                    assertThat(workflow.items().get(0).source()).isEqualTo("workflow");
                    assertThat(workflow.items().get(0).title()).isEqualTo("审批报销");

                    SourceSection office = sources.get("office");
                    assertThat(office.status()).isEqualTo("UP");
                    assertThat(office.total()).isEqualTo(2L);
                    assertThat(office.items()).hasSize(2);
                    assertThat(office.items()).allMatch(item -> "office".equals(item.source()));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("单源异常：该源降级为异常类型，另一源仍可用且不向上抛错")
    void degradesSingleSourceOnError() {
        UnifiedTodoAggregationService service = newService(url -> {
            if (url.contains("/workflow/")) {
                return Mono.error(new IllegalStateException("boom"));
            }
            return Mono.just(OFFICE_BODY);
        });

        StepVerifier.create(service.getUnifiedTodo(1L, null))
                .assertNext(response -> {
                    assertThat(response.degraded()).isTrue();
                    SourceSection workflow = response.sources().get("workflow");
                    assertThat(workflow.status()).isEqualTo("DEGRADED");
                    assertThat(workflow.error()).isEqualTo("IllegalStateException");
                    assertThat(workflow.items()).isNull();
                    assertThat(response.sources().get("office").status()).isEqualTo("UP");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("信封业务失败：code!=0 时该源降级为 invalid response body")
    void degradesOnBusinessErrorEnvelope() {
        UnifiedTodoAggregationService service =
                newService(bySource("{\"code\":500,\"msg\":\"fail\",\"data\":null}", OFFICE_BODY));

        StepVerifier.create(service.getUnifiedTodo(1L, null))
                .assertNext(response -> {
                    assertThat(response.degraded()).isTrue();
                    SourceSection workflow = response.sources().get("workflow");
                    assertThat(workflow.status()).isEqualTo("DEGRADED");
                    assertThat(workflow.error()).isEqualTo("invalid response body");
                    assertThat(response.sources().get("office").status()).isEqualTo("UP");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("数据源未配置：该源降级为 source not configured")
    void degradesWhenSourceNotConfigured() {
        properties.getSources().remove("office");
        UnifiedTodoAggregationService service = newService(bySource(WORKFLOW_BODY, OFFICE_BODY));

        StepVerifier.create(service.getUnifiedTodo(1L, null))
                .assertNext(response -> {
                    assertThat(response.degraded()).isTrue();
                    SourceSection office = response.sources().get("office");
                    assertThat(office.status()).isEqualTo("DEGRADED");
                    assertThat(office.error()).isEqualTo("source not configured");
                    assertThat(response.sources().get("workflow").status()).isEqualTo("UP");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("舱壁饱和：同源并发许可耗尽时立即降级不排队")
    void degradesWhenBulkheadSaturated() {
        properties.setDefaultBulkheadMaxConcurrency(1);
        UnifiedTodoAggregationService service = newService(url -> Mono.never());

        Disposable holding = service.fetchSource(
                        "workflow", "http://wf/x", null, body -> SourceSection.up(0L, List.of()))
                .subscribe();
        try {
            StepVerifier.create(service.fetchSource(
                            "workflow", "http://wf/x", null, body -> SourceSection.up(0L, List.of())))
                    .assertNext(section -> {
                        assertThat(section.status()).isEqualTo("DEGRADED");
                        assertThat(section.error()).isEqualTo("bulkhead saturated");
                    })
                    .verifyComplete();
        } finally {
            holding.dispose();
        }
    }

    @Test
    @DisplayName("不缓存：相同参数连续请求均实时拉取全部数据源")
    void doesNotCacheBetweenRequests() {
        AtomicInteger calls = new AtomicInteger();
        UnifiedTodoAggregationService service = newService(url -> {
            calls.incrementAndGet();
            return url.contains("/workflow/") ? Mono.just(WORKFLOW_BODY) : Mono.just(OFFICE_BODY);
        });

        UnifiedTodoResponse first = service.getUnifiedTodo(1L, null).block();
        UnifiedTodoResponse second = service.getUnifiedTodo(1L, null).block();

        assertThat(calls.get()).isEqualTo(4);
        assertThat(first).isNotSameAs(second);
        assertThat(second).isNotNull();
        assertThat(second.degraded()).isFalse();
    }
}
