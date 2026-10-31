package com.apex.scaffold.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link RemoteTenantStatusAdapter} 登录前置租户校验测试（统一认证收口 A6）。
 *
 * <p>覆盖：ACTIVE 放行、未知/冻结/过期拒绝、租户中心不可达 fail-closed（平台 default
 * 白名单除外）、判定缓存（第二次校验不触发拉取）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("RemoteTenantStatusAdapter 租户校验适配器测试")
class RemoteTenantStatusAdapterTest {

    private RemoteTenantStatusAdapter adapterWith(RemoteTenantStatusAdapter.Snapshot snapshot) {
        RemoteTenantStatusAdapter adapter = new RemoteTenantStatusAdapter("http://system.internal", 60, 3000) {
            @Override
            Mono<Snapshot> fetch(String tenantCode) {
                return Mono.just(snapshot);
            }
        };
        return adapter;
    }

    private RemoteTenantStatusAdapter failingAdapter() {
        return new RemoteTenantStatusAdapter("http://system.internal", 60, 3000) {
            @Override
            Mono<Snapshot> fetch(String tenantCode) {
                return Mono.error(new IllegalStateException("system unreachable"));
            }
        };
    }

    @Test
    @DisplayName("ACTIVE 租户放行")
    void allowsActiveTenant() {
        StepVerifier.create(adapterWith(new RemoteTenantStatusAdapter.Snapshot(true, "ACTIVE", false))
                        .validateLoginTenant("tenant-a"))
                .verifyComplete();
    }

    @Test
    @DisplayName("未知、冻结、过期租户一律拒绝")
    void rejectsInactiveTenants() {
        StepVerifier.create(adapterWith(new RemoteTenantStatusAdapter.Snapshot(false, null, false))
                        .validateLoginTenant("ghost"))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(adapterWith(new RemoteTenantStatusAdapter.Snapshot(true, "FROZEN", false))
                        .validateLoginTenant("frozen"))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(adapterWith(new RemoteTenantStatusAdapter.Snapshot(true, "ACTIVE", true))
                        .validateLoginTenant("expired"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("租户中心不可达 fail-closed，仅平台 default 白名单放行")
    void failsClosedOnUnreachableExceptPlatformTenant() {
        StepVerifier.create(failingAdapter().validateLoginTenant("tenant-a"))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(failingAdapter().validateLoginTenant("default")).verifyComplete();
    }

    @Test
    @DisplayName("判定缓存生效：第二次校验不再触发拉取")
    void cachesDecisions() {
        var fetchCount = new int[] {0};
        RemoteTenantStatusAdapter adapter = new RemoteTenantStatusAdapter("http://system.internal", 60, 3000) {
            @Override
            Mono<Snapshot> fetch(String tenantCode) {
                fetchCount[0]++;
                return Mono.just(new RemoteTenantStatusAdapter.Snapshot(true, "ACTIVE", false));
            }
        };
        StepVerifier.create(adapter.validateLoginTenant("tenant-a")).verifyComplete();
        StepVerifier.create(adapter.validateLoginTenant("tenant-a")).verifyComplete();
        assertThat(fetchCount[0]).isEqualTo(1);
    }
}
