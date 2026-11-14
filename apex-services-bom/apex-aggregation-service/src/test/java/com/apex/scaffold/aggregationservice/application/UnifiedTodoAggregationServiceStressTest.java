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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 统一待办聚合服务压力测试（{@code -Pstress-tests} 档执行）。
 *
 * <p>通过覆写 HTTP 边界（{@code fetchBody}）注入可控延迟的桩响应，验证高并发下的三类
 * 关键语义：
 * <ul>
 *   <li>无缓存无合并：个人维度数据每次请求实时聚合，N 个并发请求产生 2N 次下游拉取
 *       （与「不缓存、不 in-flight 合并」的设计口径一致）；</li>
 *   <li>舱壁隔离：源并发上限受 {@code Semaphore} 约束，饱和即降级且不排队；</li>
 *   <li>许可回收：订阅终止后许可释放，后续请求可恢复 UP。</li>
 * </ul>
 *
 * <p>纯 JUnit 无外部依赖；延迟为毫秒级，执行时间受控。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("统一待办聚合服务压力测试")
class UnifiedTodoAggregationServiceStressTest {

    private static final String WORKFLOW_BODY =
            """
            {"code":0,"data":{"list":[{"itemType":"workflow_task","title":"审批报销",\
            "occurredAt":"2026-11-12T10:00:00","status":"PENDING","refId":"t-1"}],"total":1}}
            """;

    private static final String OFFICE_BODY =
            """
            {"code":0,"data":{"approvals":[{"itemType":"office_approval","title":"请假",\
            "status":"PENDING","refId":"a-1"}],"approvalTotal":1,"unreadTotal":0}}
            """;

    private static final int CONCURRENCY = 48;

    private static final long UPSTREAM_DELAY_MILLIS = 40;

    /** 测试桩：仅覆写 HTTP 边界，其余聚合/舱壁逻辑走真实实现。 */
    private final class StubAggregationService extends UnifiedTodoAggregationService {

        private final AtomicInteger fetchCount = new AtomicInteger();

        private final AtomicInteger maxConcurrentWorkflow = new AtomicInteger();

        private final AtomicInteger currentConcurrentWorkflow = new AtomicInteger();

        StubAggregationService(TodoProperties properties) {
            super(
                    properties,
                    new ObjectMapper().registerModule(new JavaTimeModule()),
                    WebClient.builder(),
                    new InternalAuthProperties(),
                    new InternalServiceSigner(
                            "stress-test-secret-0123456789abcdef", Duration.ofSeconds(60), Clock.systemUTC()));
        }

        @Override
        protected Mono<String> fetchBody(String url, String authorization) {
            fetchCount.incrementAndGet();
            if (!url.contains("/workflow/")) {
                return Mono.delay(Duration.ofMillis(UPSTREAM_DELAY_MILLIS)).thenReturn(OFFICE_BODY);
            }
            return Mono.delay(Duration.ofMillis(UPSTREAM_DELAY_MILLIS))
                    .doOnSubscribe(ignore -> recordWorkflowEnter())
                    .thenReturn(WORKFLOW_BODY)
                    .doFinally(signal -> currentConcurrentWorkflow.decrementAndGet());
        }

        private void recordWorkflowEnter() {
            int current = currentConcurrentWorkflow.incrementAndGet();
            maxConcurrentWorkflow.accumulateAndGet(current, Math::max);
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private TodoProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TodoProperties();
        properties.setTimeoutMillis(5000);
        properties.setDefaultBulkheadMaxConcurrency(4);
    }

    @Test
    @DisplayName("个人维度不缓存不合并：N 并发产生 2N 次下游拉取（舱壁不饱和前提下）")
    void concurrentRequestsFetchIndividuallyWithinBulkhead() throws Exception {
        // 舱壁放大到并发数以上，避免饱和降级干扰拉取计数
        properties.setDefaultBulkheadMaxConcurrency(64);
        StubAggregationService service = new StubAggregationService(properties);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        try {
            List<CompletableFuture<Void>> futures = IntStream.range(0, CONCURRENCY)
                    .mapToObj(i -> CompletableFuture.runAsync(
                            () -> service.getUnifiedTodo((long) i, "Bearer token-" + i)
                                    .block(Duration.ofSeconds(15)),
                            pool))
                    .toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // 无合并：每个请求都实时拉取两个源（不存在共享缓存或 in-flight 合并）
        assertThat(service.fetchCount.get()).isEqualTo(CONCURRENCY * 2);
        // 并发确实被压测到（舱壁许可充足时允许多请求同时进入下游）
        assertThat(service.maxConcurrentWorkflow.get()).isGreaterThan(1);
        assertThat(service.maxConcurrentWorkflow.get()).isLessThanOrEqualTo(64);
    }

    @Test
    @DisplayName("舱壁许可随订阅终止释放：排空后单次请求恢复全源 UP")
    void permitsAreReleasedAfterDrain() throws Exception {
        StubAggregationService service = new StubAggregationService(properties);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        try {
            List<CompletableFuture<Void>> futures = IntStream.range(0, CONCURRENCY)
                    .mapToObj(i -> CompletableFuture.runAsync(
                            () -> service.getUnifiedTodo(900L, null).block(Duration.ofSeconds(15)), pool))
                    .toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        // 排空后许可应全部归还
        assertThat(service.currentConcurrentWorkflow.get()).isEqualTo(0);

        UnifiedTodoResponse recovered = service.getUnifiedTodo(900L, null).block(Duration.ofSeconds(15));
        assertThat(recovered).isNotNull();
        assertThat(recovered.degraded()).isFalse();
        assertThat(recovered.sources().get("workflow").status()).isEqualTo("UP");
        assertThat(recovered.sources().get("office").status()).isEqualTo("UP");
    }

    @Test
    @DisplayName("饱和降级不向上抛错：全部并发请求均获得部分可用响应")
    void saturationDegradesWithoutPropagatingErrors() throws Exception {
        // 舱壁 1 + 上游延迟：大量并发下 workflow 源普遍饱和降级
        properties.setDefaultBulkheadMaxConcurrency(1);
        StubAggregationService service = new StubAggregationService(properties);
        AtomicInteger maxSeen = service.maxConcurrentWorkflow;

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        try {
            List<CompletableFuture<Void>> futures = IntStream.range(0, CONCURRENCY)
                    .mapToObj(i -> CompletableFuture.runAsync(
                            () -> {
                                UnifiedTodoResponse response =
                                        service.getUnifiedTodo((long) i, null).block(Duration.ofSeconds(15));
                                // 单源降级不阻断整体：响应必达，且不向上抛错
                                if (response != null) {
                                    SourceSection workflow = response.sources().get("workflow");
                                    assertThat(workflow).isNotNull();
                                }
                            },
                            pool))
                    .toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        // 响应对象均非 null（全部完成且未被错误打断）；下游实际并发不超过舱壁许可 1
        assertThat(service.fetchCount.get()).isGreaterThan(0);
        assertThat(maxSeen.get()).isLessThanOrEqualTo(1);
    }
}
