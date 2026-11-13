package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.application.config.OverviewProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 跨域只读聚合门面压力测试（{@code -Pstress-tests} 档执行）。
 *
 * <p>通过覆写 HTTP 边界（{@code fetchBody}）注入可控延迟的桩响应，验证高并发下的三类
 * 关键语义：
 * <ul>
 *   <li>并发合并：同（租户、凭证）的并发请求合并为一次下游刷新，下游不被打满；</li>
 *   <li>舱壁隔离与许可回收：饱和源立即降级不排队，订阅终止后许可释放，后续请求恢复 UP；</li>
 *   <li>降级不缓存：降级结果不写入租户缓存，下游恢复后下一次请求立即拿到全源 UP。</li>
 * </ul>
 *
 * <p>纯 JUnit 无外部依赖；延迟为毫秒级，执行时间受控。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("跨域只读聚合门面压力测试")
class OverviewFacadeServiceStressTest {

    private static final String OK_FILE_BODY = "{\"code\":0,\"msg\":\"success\",\"data\":{\"storageUsageBytes\":1024}}";
    private static final String OK_WORKFLOW_BODY =
            "{\"code\":0,\"msg\":\"success\",\"data\":{\"inProgressInstances\":3}}";
    private static final String OK_IOT_BODY = "{\"code\":0,\"msg\":\"success\",\"data\":{\"deviceCount\":5}}";

    private static final int CONCURRENCY = 64;

    private static final long UPSTREAM_DELAY_MILLIS = 60;

    /** 测试桩：仅覆写 HTTP 边界，其余聚合/合并/舱壁/缓存逻辑走真实实现。 */
    private final class StubFacadeService extends OverviewFacadeService {

        private final Map<String, Mono<String>> stubBodies = new HashMap<>();

        private final AtomicInteger fetchCount = new AtomicInteger();

        private final AtomicInteger maxConcurrentFile = new AtomicInteger();

        private final AtomicInteger currentConcurrentFile = new AtomicInteger();

        StubFacadeService(OverviewProperties properties) {
            super(
                    properties,
                    new ObjectMapper(),
                    WebClient.builder(),
                    new com.apex.scaffold.common.internal.InternalAuthProperties(),
                    null);
        }

        @Override
        protected Mono<String> fetchBody(String url, String authorization) {
            fetchCount.incrementAndGet();
            String key =
                    url.contains("/file/stats") ? "file" : url.contains("/workflow/stats") ? "workflow" : "integration";
            Mono<String> body = stubBodies.get(key);
            if (body == null) {
                return Mono.error(new IllegalStateException("unreachable"));
            }
            if ("file".equals(key)) {
                body = body.doOnSubscribe(ignore -> recordFileEnter())
                        .doFinally(signal -> currentConcurrentFile.decrementAndGet());
            }
            return body;
        }

        private void recordFileEnter() {
            int current = currentConcurrentFile.incrementAndGet();
            maxConcurrentFile.accumulateAndGet(current, Math::max);
        }
    }

    private OverviewProperties propertiesWithBulkheadOne() {
        OverviewProperties properties = new OverviewProperties();
        properties.setCacheTtlSeconds(0);
        properties.setTimeoutMillis(2000);
        properties.setBulkhead(Map.of("file", 1, "workflow", 1, "integration", 1));
        return properties;
    }

    @Test
    @DisplayName("同租户高并发请求合并为一次下游刷新（每源恰好 1 次拉取）")
    void concurrentRequestsMergeIntoSingleDownstreamRefresh() throws Exception {
        OverviewProperties properties = new OverviewProperties();
        properties.setCacheTtlSeconds(0);
        StubFacadeService service = new StubFacadeService(properties);
        service.stubBodies.put(
                "file", Mono.delay(Duration.ofMillis(UPSTREAM_DELAY_MILLIS)).thenReturn(OK_FILE_BODY));
        service.stubBodies.put(
                "workflow", Mono.delay(Duration.ofMillis(UPSTREAM_DELAY_MILLIS)).thenReturn(OK_WORKFLOW_BODY));
        service.stubBodies.put(
                "integration",
                Mono.delay(Duration.ofMillis(UPSTREAM_DELAY_MILLIS)).thenReturn(OK_IOT_BODY));

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        try {
            List<Future<java.util.Optional<String>>> futures = IntStream.range(0, CONCURRENCY)
                    .mapToObj(i -> pool.submit(() -> {
                        service.getOverview("T-MERGE", "Bearer same-token")
                                .map(response -> response.tenantId())
                                .block(Duration.ofSeconds(10));
                        return java.util.Optional.<String>empty();
                    }))
                    .toList();
            for (Future<java.util.Optional<String>> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // 三源各拉取一次：合并后的响应以 .cache() 共享给全部并发调用方
        assertThat(service.fetchCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("舱壁饱和立即降级不排队，许可随订阅终止释放后恢复 UP")
    void bulkheadSaturationDegradesAndReleasesPermits() throws Exception {
        StubFacadeService service = new StubFacadeService(propertiesWithBulkheadOne());
        service.stubBodies.put(
                "file", Mono.delay(Duration.ofMillis(UPSTREAM_DELAY_MILLIS)).thenReturn(OK_FILE_BODY));
        service.stubBodies.put(
                "workflow", Mono.delay(Duration.ofMillis(UPSTREAM_DELAY_MILLIS)).thenReturn(OK_WORKFLOW_BODY));
        service.stubBodies.put(
                "integration",
                Mono.delay(Duration.ofMillis(UPSTREAM_DELAY_MILLIS)).thenReturn(OK_IOT_BODY));

        // 顺序订阅即形成并发竞争：首个订阅占住许可异步执行，后续订阅同步走到
        // tryAcquire 失败立即降级（订阅不阻塞，舱壁饱和语义即为目标行为）
        List<reactor.core.Disposable> disposables = IntStream.range(0, 16)
                .mapToObj(i -> service.getOverview("T-BULK", null).subscribe(response -> {}, error -> {}))
                .toList();

        long deadline = System.currentTimeMillis() + 15_000;
        while (service.currentConcurrentFile.get() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        disposables.forEach(reactor.core.Disposable::dispose);

        // 饱和期 file 源并发不超过舱壁许可 1
        assertThat(service.maxConcurrentFile.get()).isEqualTo(1);
        // 部分请求因 file 舱壁饱和降级（file 仅 1 个许可，16 个并发必有降级）
        assertThat(service.fetchCount.get()).isLessThan(16 * 3);

        // 许可释放后单次请求恢复全源 UP
        var response = service.getOverview("T-BULK", null).block(Duration.ofSeconds(10));
        assertThat(response).isNotNull();
        assertThat(response.degraded()).isFalse();
    }

    @Test
    @DisplayName("降级结果不缓存：下游恢复后下一次请求立即拿到全源 UP")
    void degradedResultsAreNotCached() {
        StubFacadeService service = new StubFacadeService(propertiesWithBulkheadOne());
        service.stubBodies.put("workflow", Mono.just(OK_WORKFLOW_BODY));
        service.stubBodies.put("integration", Mono.just(OK_IOT_BODY));
        // file 源始终不可达 → 每次请求 file 都失败
        service.stubBodies.put("file", Mono.error(new IllegalStateException("file down")));

        IntStream.range(0, 5).forEach(i -> {
            var degraded = service.getOverview("T-DEGRADED", null).block(Duration.ofSeconds(10));
            assertThat(degraded).isNotNull();
            assertThat(degraded.degraded()).isTrue();
        });
        // 降级结果不进缓存：5 次请求 file 源各拉取一次（不合并到缓存结果）
        int fileFetchesBeforeRecovery = service.fetchCount.get();
        assertThat(fileFetchesBeforeRecovery).isGreaterThanOrEqualTo(5);

        service.stubBodies.put("file", Mono.just(OK_FILE_BODY));
        var recovered = service.getOverview("T-DEGRADED", null).block(Duration.ofSeconds(10));
        assertThat(recovered).isNotNull();
        assertThat(recovered.degraded()).isFalse();
    }
}
