package com.apex.scaffold.interfaces.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.interfaces.config.DegradeSnapshotProperties;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 降级兜底服务单元测试。
 *
 * <p>验证 {@link DegradeFallbackService} 的缓存快照保存/读取、
 * 读接口降级返回缓存数据和写接口降级返回排队中状态。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DegradeFallbackService 降级兜底服务测试")
class DegradeFallbackServiceTest {

    /** Mock Redis 模板 */
    @Mock
    @SuppressWarnings("rawtypes")
    private ReactiveRedisTemplate redisTemplate;

    /** Mock Redis Value 操作 */
    @Mock
    @SuppressWarnings("rawtypes")
    private ReactiveValueOperations valueOperations;

    /** 被测服务 */
    private DegradeFallbackService service;

    /** 测试资源名称 */
    private static final String TEST_RESOURCE = "GET:/api/v1/explore";

    /** 测试 traceId */
    private static final String TEST_TRACE_ID = "test-trace-001";

    /**
     * 初始化 Mock 环境，经构造器注入可选 Redis 模板与默认配置。
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new DegradeFallbackService(fixedProvider(redisTemplate), new DegradeSnapshotProperties());
    }

    /**
     * 构造固定返回给定 Bean 的 {@link ObjectProvider}，用于单测构造器注入。
     *
     * @param bean 可选 Bean，可为 {@code null}
     * @return ObjectProvider 实现
     */
    @SuppressWarnings("unchecked")
    private static ObjectProvider<ReactiveRedisTemplate<String, Object>> fixedProvider(
            ReactiveRedisTemplate redisTemplate) {
        return new ObjectProvider<>() {
            /**
             * {@inheritDoc}
             *
             * @return ReactiveRedisTemplate<String, Object> 类型的实例
             */
            @Override
            public ReactiveRedisTemplate<String, Object> getObject() {
                return redisTemplate;
            }

            /**
             * {@inheritDoc}
             *
             * @param args 方法输入 {@code args}，类型为 {@code Object[]}
             * @return ReactiveRedisTemplate<String, Object> 类型的实例
             */
            @Override
            public ReactiveRedisTemplate<String, Object> getObject(Object... args) {
                return redisTemplate;
            }

            /**
             * {@inheritDoc}
             *
             * @return ReactiveRedisTemplate<String, Object> 类型的实例
             */
            @Override
            public ReactiveRedisTemplate<String, Object> getIfAvailable() {
                return redisTemplate;
            }

            /**
             * {@inheritDoc}
             *
             * @return ReactiveRedisTemplate<String, Object> 类型的实例
             */
            @Override
            public ReactiveRedisTemplate<String, Object> getIfUnique() {
                return redisTemplate;
            }
        };
    }

    /**
     * 测试保存快照应同时写入 Redis 和本地缓存。
     */
    @Test
    @DisplayName("保存快照应写入Redis和本地缓存")
    @SuppressWarnings("unchecked")
    void shouldSaveSnapshotToRedisAndLocal() {
        when(valueOperations.set(anyString(), any(), any())).thenReturn(Mono.just(true));
        Object testData = Collections.singletonMap("key", "value");
        service.saveSnapshot(TEST_RESOURCE, testData);
        // 验证本地缓存已写入
        StepVerifier.create(service.getSnapshot(TEST_RESOURCE))
                .assertNext(obj -> assertThat(obj).isNotNull())
                .verifyComplete();
    }

    /**
     * 测试读接口降级时快照命中应返回缓存数据。
     */
    @Test
    @DisplayName("读接口降级快照命中应返回缓存数据")
    @SuppressWarnings("unchecked")
    void shouldReturnCachedDataOnReadDegraded() {
        Object testData = Collections.singletonMap("rank", 1);
        service.saveSnapshot(TEST_RESOURCE, testData);
        // getSnapshot 会先查 Redis，Mock 返回空后回退到本地
        when(valueOperations.get(anyString())).thenReturn(Mono.just(testData));
        StepVerifier.create(service.buildDegradedReadResponse(TEST_RESOURCE, TEST_TRACE_ID))
                .assertNext(result -> {
                    assertThat(result.getCode()).isEqualTo(0);
                    assertThat(result.getData()).isNotNull();
                    assertThat(result.getTraceId()).isEqualTo(TEST_TRACE_ID);
                })
                .verifyComplete();
    }

    /**
     * 测试读接口降级时快照未命中应返回降级错误码。
     */
    @Test
    @DisplayName("读接口降级快照未命中应返回降级错误码")
    @SuppressWarnings("unchecked")
    void shouldReturnDegradedErrorWhenSnapshotMiss() {
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());
        StepVerifier.create(service.buildDegradedReadResponse(TEST_RESOURCE, TEST_TRACE_ID))
                .assertNext(result -> {
                    assertThat(result.getCode()).isEqualTo(2003);
                    assertThat(result.getTraceId()).isEqualTo(TEST_TRACE_ID);
                })
                .verifyComplete();
    }

    /**
     * 测试写接口降级应返回排队中状态。
     */
    @Test
    @DisplayName("写接口降级应返回排队中状态")
    void shouldReturnQueuedOnWriteDegraded() {
        StepVerifier.create(service.buildDegradedWriteResponse(TEST_RESOURCE, TEST_TRACE_ID))
                .assertNext(result -> {
                    assertThat(result.getCode()).isEqualTo(2003);
                    assertThat(result.getMsg()).contains("排队");
                    assertThat(result.getTraceId()).isEqualTo(TEST_TRACE_ID);
                })
                .verifyComplete();
    }

    /**
     * 测试传入 null 资源名时应安全返回空 Mono。
     */
    @Test
    @DisplayName("null资源名应返回空Mono")
    void shouldReturnEmptyWhenResourceNull() {
        StepVerifier.create(service.getSnapshot(null)).verifyComplete();
    }

    /**
     * 测试保存 null 数据时应跳过不报错。
     */
    @Test
    @DisplayName("保存null数据应跳过不报错")
    void shouldSkipWhenDataNull() {
        service.saveSnapshot(TEST_RESOURCE, null);
        // 不应抛出异常
        assertThat(true).isTrue();
    }

    /**
     * 没有通用 Redis 模板时，服务仍应使用本地缓存完成降级兜底。
     */
    @Test
    @DisplayName("没有通用Redis模板时应回退到本地缓存")
    void shouldUseLocalCacheWhenRedisTemplateUnavailable() {
        DegradeFallbackService localOnlyService =
                new DegradeFallbackService(fixedProvider(null), new DegradeSnapshotProperties());
        localOnlyService.saveSnapshot(TEST_RESOURCE, Collections.singletonMap("local", true));

        StepVerifier.create(localOnlyService.getSnapshot(TEST_RESOURCE))
                .assertNext(snapshot -> assertThat(snapshot).isNotNull())
                .verifyComplete();
    }

    /**
     * 测试同一资源在节流窗口内重复保存时，仅向 Redis 写入一次。
     *
     * <p>节流窗口内再次保存只更新本地缓存，避免 Redis 写放大。</p>
     */
    @Test
    @DisplayName("节流窗口内同一资源只写一次Redis")
    @SuppressWarnings("unchecked")
    void shouldThrottleRedisWriteWithinWindow() {
        when(valueOperations.set(anyString(), any(), any())).thenReturn(Mono.just(true));
        DegradeSnapshotProperties props = new DegradeSnapshotProperties();
        props.setRedisThrottleSeconds(60);
        DegradeFallbackService throttled = new DegradeFallbackService(fixedProvider(redisTemplate), props);

        Map<String, Object> data = Map.of("k", "v");
        throttled.saveSnapshot(TEST_RESOURCE, data);
        throttled.saveSnapshot(TEST_RESOURCE, data);
        throttled.saveSnapshot(TEST_RESOURCE, data);

        // 窗口内三次保存，Redis 只应写入一次
        verify(valueOperations, times(1)).set(anyString(), any(), any());
    }

    @Test
    @DisplayName("并发保存同一资源只发起一次Redis写入")
    @SuppressWarnings("unchecked")
    void shouldOnlyWriteRedisOnceWhenSnapshotsAreSavedConcurrently() throws Exception {
        when(valueOperations.set(anyString(), any(), any())).thenReturn(Mono.just(true));
        DegradeSnapshotProperties props = new DegradeSnapshotProperties();
        props.setRedisThrottleSeconds(60);
        DegradeFallbackService throttled = new DegradeFallbackService(fixedProvider(redisTemplate), props);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < 8; i++) {
            executor.submit(() -> {
                ready.countDown();
                start.await();
                throttled.saveSnapshot(TEST_RESOURCE, Map.of("k", "v"));
                return null;
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        verify(valueOperations, times(1)).set(anyString(), any(), any());
    }

    @Test
    @DisplayName("Redis写入失败后下一次保存可以重试")
    @SuppressWarnings("unchecked")
    void shouldRetryRedisWriteAfterPreviousWriteFails() {
        when(valueOperations.set(anyString(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("redis unavailable")))
                .thenReturn(Mono.just(true));
        DegradeSnapshotProperties props = new DegradeSnapshotProperties();
        props.setRedisThrottleSeconds(60);
        DegradeFallbackService throttled = new DegradeFallbackService(fixedProvider(redisTemplate), props);

        throttled.saveSnapshot(TEST_RESOURCE, Map.of("k", "v"));
        throttled.saveSnapshot(TEST_RESOURCE, Map.of("k", "v2"));

        verify(valueOperations, times(2)).set(anyString(), any(), any());
    }

    /**
     * 测试本地缓存超过容量上限时，应淘汰最旧的条目。
     *
     * <p>容量设为 1，先保存的条目在写入第二个不同资源后应被淘汰。</p>
     */
    @Test
    @DisplayName("本地缓存超容量应淘汰最旧条目")
    void shouldEvictOldestWhenOverCapacity() throws InterruptedException {
        DegradeSnapshotProperties props = new DegradeSnapshotProperties();
        props.setLocalMaxEntries(1);
        DegradeFallbackService capped = new DegradeFallbackService(fixedProvider(null), props);

        capped.saveSnapshot(TEST_RESOURCE, Collections.singletonMap("first", 1));
        // 等待 2ms，确保第一条写入时间严格早于第二条，淘汰选择确定
        Thread.sleep(2);
        String secondResource = "GET:/api/v1/other";
        capped.saveSnapshot(secondResource, Collections.singletonMap("second", 2));

        // 最旧的 TEST_RESOURCE 应被淘汰，新保存的 secondResource 应保留
        StepVerifier.create(capped.getSnapshot(TEST_RESOURCE)).verifyComplete();
        StepVerifier.create(capped.getSnapshot(secondResource))
                .assertNext(snapshot -> assertThat(snapshot).isNotNull())
                .verifyComplete();
    }

    /**
     * 测试总开关关闭时，保存快照应被整体跳过。
     *
     * <p>关闭后不写本地缓存、不写 Redis，读取也应无命中。</p>
     */
    @Test
    @DisplayName("总开关关闭时应跳过快照保存")
    void shouldSkipSnapshotWhenDisabled() {
        DegradeSnapshotProperties props = new DegradeSnapshotProperties();
        props.setEnabled(false);
        DegradeFallbackService disabled = new DegradeFallbackService(fixedProvider(null), props);

        disabled.saveSnapshot(TEST_RESOURCE, Collections.singletonMap("k", "v"));
        StepVerifier.create(disabled.getSnapshot(TEST_RESOURCE)).verifyComplete();
    }
}
