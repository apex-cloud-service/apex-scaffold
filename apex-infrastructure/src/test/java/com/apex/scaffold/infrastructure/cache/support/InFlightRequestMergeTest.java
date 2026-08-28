package com.apex.scaffold.infrastructure.cache.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 回源请求合并器（Single-Flight）单元测试
 *
 * <p>验证 InFlightRequestMerge 的击穿防护语义：
 * 同一 key 并发回源只执行一次、不同 key 互不影响、错误后可重试。
 *
 * @author Apex Team
 */
@DisplayName("InFlightRequestMerge 缓存击穿防护测试")
class InFlightRequestMergeTest {

    private final InFlightRequestMerge merge = new InFlightRequestMerge();

    @Test
    @DisplayName("同一 key 并发回源只执行一次加载")
    void shouldMergeConcurrentLoadsForSameKey() {
        AtomicInteger loadCount = new AtomicInteger();

        Mono<String> first = merge.join("user:1", () -> delayedLoad("v1", loadCount));
        Mono<String> second = merge.join("user:1", () -> delayedLoad("v2", loadCount));

        // 两个订阅几乎同时发起，但只有首个注册的 source 真正执行
        Mono<String> combined = Mono.zip(first, second, (a, b) -> a + "|" + b);

        StepVerifier.create(combined).assertNext(result -> {
            // 两个订阅者共享同一次加载结果（同为 v1 或同为 v2）
            assertThat(result).matches("v1\\|v1|v2\\|v2");
            assertThat(loadCount.get()).isEqualTo(1);
        }).verifyComplete();
    }

    @Test
    @DisplayName("不同 key 的回源互不合并")
    void shouldNotMergeLoadsForDifferentKeys() {
        AtomicInteger loadCount = new AtomicInteger();

        Mono<String> a = merge.join("user:1", () -> delayedLoad("a", loadCount));
        Mono<String> b = merge.join("user:2", () -> delayedLoad("b", loadCount));

        StepVerifier.create(Mono.zip(a, b, (x, y) -> x + y))
                .assertNext(result -> {
                    assertThat(result).isEqualTo("ab");
                    assertThat(loadCount.get()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("加载失败后条目移除，后续请求可重试")
    void shouldRemoveEntryOnErrorAndAllowRetry() {
        AtomicInteger attempts = new AtomicInteger();

        Mono<String> failed = merge.join("key", () -> Mono.defer(() -> {
            attempts.incrementAndGet();
            return Mono.error(new IllegalStateException("db down"));
        }));
        StepVerifier.create(failed).expectError(IllegalStateException.class).verify();

        assertThat(merge.inflightCount()).isZero();

        Mono<String> retried = merge.join("key", () -> Mono.fromSupplier(() -> {
            attempts.incrementAndGet();
            return "recovered";
        }));
        StepVerifier.create(retried).assertNext(v -> {
            assertThat(v).isEqualTo("recovered");
            assertThat(attempts.get()).isEqualTo(2);
        }).verifyComplete();
    }

    @Test
    @DisplayName("加载完成后条目移除，后续请求重新走缓存路径")
    void shouldRemoveEntryAfterCompletion() {
        AtomicInteger loadCount = new AtomicInteger();

        Mono<String> inFlightMono = merge.join("user:1", () -> Mono.fromSupplier(() -> {
                    loadCount.incrementAndGet();
                    return "value";
                }).delayElement(Duration.ofMillis(10)));

        // 完成后条目应被移除
        StepVerifier.create(inFlightMono).assertNext(v -> assertThat(v).isEqualTo("value")).verifyComplete();
        assertThat(merge.inflightCount()).isZero();

        // 第二次 join 是全新加载（缓存层应命中，不会到这里；此处验证合并器语义）
        Mono<String> second = merge.join("user:1", () -> Mono.fromSupplier(() -> {
            loadCount.incrementAndGet();
            return "value2";
        }));
        StepVerifier.create(second).assertNext(v -> assertThat(v).isEqualTo("value2")).verifyComplete();
        assertThat(loadCount.get()).isEqualTo(2);
    }

    /**
     * 构造延迟加载源：记录加载次数并延迟完成，确保并发订阅期间条目仍在表中
     */
    private Mono<String> delayedLoad(String value, AtomicInteger counter) {
        return Mono.fromSupplier(() -> {
                    counter.incrementAndGet();
                    return value;
                })
                .delayElement(Duration.ofMillis(50));
    }
}
