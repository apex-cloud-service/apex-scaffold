package com.apex.scaffold.contract.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.apex.scaffold.common.result.Result;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 同步 Feign 调用响应式桥接工具 {@link FeignReactiveBridge} 测试.
 *
 * <p>校验桥接范式能把同步阻塞调用安全调度到 {@code apex.remote} 隔离线程池执行，
 * 并以 {@link Mono} 语义正确发出元素 / 错误：
 * <ul>
 *   <li>同步返回正常值 → Mono 发出该值</li>
 *   <li>同步抛异常 → Mono 转为 onError</li>
 *   <li>执行为非订阅惰性（未订阅不产生副作用）</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("FeignReactiveBridge 桥接范式测试")
class FeignReactiveBridgeTest {

    @Test
    @DisplayName("同步 Feign 返回值正确桥接为 Mono")
    void bridgeSyncValue() {
        Mono<Result<String>> mono = FeignReactiveBridge.bridgeResult(() -> Result.success("permissions"));
        StepVerifier.create(mono)
                .expectNextMatches(result -> "permissions".equals(result.getData()))
                .verifyComplete();
    }

    @Test
    @DisplayName("同步调用抛出异常桥接为 Mono onError")
    void bridgeSyncException() {
        Mono<Result<String>> mono = FeignReactiveBridge.bridgeResult(() -> {
            throw new IllegalStateException("feign call failed");
        });
        StepVerifier.create(mono)
                .expectErrorMatches(error ->
                        error instanceof IllegalStateException && "feign call failed".equals(error.getMessage()))
                .verify();
    }

    @Test
    @DisplayName("桥接为惰性执行：未订阅不触发同步调用")
    void bridgeIsLazy() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        FeignReactiveBridge.bridge(() -> {
            invoked.set(true);
            return "done";
        });
        assertThat(invoked).as("未订阅时不应触发同步调用").isFalse();
    }

    @Test
    @DisplayName("supplier 为 null 时抛出 NullPointerException")
    void bridgeRejectsNullSupplier() {
        assertThatNullPointerException().isThrownBy(() -> FeignReactiveBridge.bridge(null));
    }
}
