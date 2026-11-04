package com.apex.scaffold.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.authservice.infrastructure.mq.TenantChangedEventListener;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link TenantChangedEventListener} 单元测试（D5 主数据分发消费方）。
 *
 * <p>覆盖：事件消费使判定缓存失效（禁用租户最迟一个投递周期内拒绝登录）、
 * 重复消费天然幂等、非法负载不阻断消费（TTL 回源兜底）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("租户主数据变更事件监听器测试")
class TenantChangedEventListenerTest {

    private RemoteTenantStatusAdapter adapter;

    private TenantChangedEventListener listener;

    /** 桩快照：第一次查询返回活跃，之后返回禁用（模拟租户被禁用）。 */
    private final AtomicInteger fetchCount = new AtomicInteger();

    @BeforeEach
    void setUp() {
        adapter =
                new RemoteTenantStatusAdapter(
                        "http://tenant-center:8082",
                        60,
                        3000,
                        new com.apex.scaffold.common.internal.InternalAuthProperties(),
                        null) {
                    @Override
                    Mono<Snapshot> fetch(String tenantCode) {
                        boolean disabled = fetchCount.incrementAndGet() > 1;
                        return Mono.just(new Snapshot(true, disabled ? "DISABLED" : "ACTIVE", false));
                    }
                };
        listener = new TenantChangedEventListener(adapter);
    }

    @Test
    @DisplayName("事件消费失效判定缓存：禁用租户下一次登录即被拒绝")
    void shouldInvalidateCacheOnTenantChanged() {
        // 第一次校验：活跃，结果进入 60s 判定缓存
        StepVerifier.create(adapter.validateLoginTenant("tenant-a")).verifyComplete();

        // 不消费事件（缓存未失效）：60s TTL 内仍放行（模拟缓存命中）
        StepVerifier.create(adapter.validateLoginTenant("tenant-a")).verifyComplete();
        assertThat(fetchCount.get()).isEqualTo(1);

        // 消费 TENANT_CHANGED 事件 → 缓存失效 → 下一次校验强制回源并拒绝
        listener.onMessage("{\"eventType\":\"TENANT_CHANGED\",\"tenantCode\":\"tenant-a\",\"action\":\"UPDATED\"}");

        StepVerifier.create(adapter.validateLoginTenant("tenant-a"))
                .expectErrorSatisfies(error -> assertThat(error).hasMessageContaining("租户不可用"))
                .verify();
        assertThat(fetchCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("重复消费同一事件天然幂等（重复失效同键）")
    void shouldBeIdempotentOnReplay() {
        listener.onMessage("{\"eventType\":\"TENANT_CHANGED\",\"tenantCode\":\"tenant-a\",\"action\":\"UPDATED\"}");
        listener.onMessage("{\"eventType\":\"TENANT_CHANGED\",\"tenantCode\":\"tenant-a\",\"action\":\"UPDATED\"}");

        assertThat(fetchCount.get()).isZero();
    }

    @Test
    @DisplayName("非法负载解析失败不外溢（TTL 回源兜底）")
    void shouldSwallowUnparsablePayload() {
        listener.onMessage("not-json");
        listener.onMessage(null);

        assertThat(fetchCount.get()).isZero();
    }
}
