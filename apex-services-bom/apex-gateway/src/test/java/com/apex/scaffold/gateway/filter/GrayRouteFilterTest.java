package com.apex.scaffold.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * {@link GrayRouteFilter} 单元测试.
 *
 * <p>覆盖迁移方案 §15 灰度切流的三级判定逻辑：
 * 总开关、Header 强制、租户白名单、用户尾号百分比，以及标记头透传。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("GrayRouteFilter 灰度路由过滤器测试")
class GrayRouteFilterTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private GrayRouteFilter currentFilter;

    /**
     * 构造被测过滤器（注入独立指标注册表）.
     *
     * @param enabled         灰度总开关
     * @param percentage      用户尾号灰度百分比
     * @param tenantWhitelist 租户白名单
     * @return 灰度路由过滤器
     */
    private GrayRouteFilter newFilter(boolean enabled, int percentage, List<String> tenantWhitelist) {
        return new GrayRouteFilter(enabled, percentage, tenantWhitelist, meterRegistry);
    }

    /**
     * 构造直通链路，捕获被改写的交换对象。
     */
    private GatewayFilterChain capturingChain(ServerWebExchange[] holder) {
        return exchange -> {
            holder[0] = exchange;
            return Mono.empty();
        };
    }

    /**
     * 执行过滤并返回最终 X-Gray-Tag 值.
     *
     * @param exchange 交换对象
     * @return 灰度标记值
     */
    private String grayTagOf(ServerWebExchange exchange) {
        ServerWebExchange[] holder = new ServerWebExchange[1];
        return ((GrayRouteFilter) currentFilter)
                .filter(exchange, capturingChain(holder))
                .then(Mono.fromSupplier(
                        () -> holder[0].getRequest().getHeaders().getFirst(GrayRouteFilter.HEADER_GRAY_TAG)))
                .block();
    }

    /**
     * 读取指定来源的灰度命中计数.
     *
     * @param source 命中来源 tag
     * @return 命中次数；无该来源指标时为 0
     */
    private double grayHitCount(String source) {
        var counter = meterRegistry
                .find(GrayRouteFilter.METRIC_GRAY_HIT)
                .tag("source", source)
                .counter();
        return counter == null ? 0.0d : counter.count();
    }

    @Nested
    @DisplayName("过滤器顺序")
    class OrderTests {

        @Test
        @DisplayName("getOrder 应返回 40")
        void shouldReturnExpectedOrder() {
            assertThat(newFilter(false, 0, List.of()).getOrder())
                    .isEqualTo(GrayRouteFilter.ORDER)
                    .isEqualTo(40);
        }
    }

    @Nested
    @DisplayName("总开关")
    class SwitchTests {

        @Test
        @DisplayName("灰度关闭时应标记 stable")
        void shouldMarkStableWhenDisabled() {
            currentFilter = newFilter(false, 100, List.of());
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                    .header(GrayRouteFilter.HEADER_GRAY_TAG, "gray")
                    .build());

            assertThat(grayTagOf(exchange)).isEqualTo(GrayRouteFilter.STABLE_VALUE);
            assertThat(grayHitCount("header")).isZero();
        }
    }

    @Nested
    @DisplayName("Header 强制灰度")
    class HeaderForceTests {

        @Test
        @DisplayName("携带 X-Gray-Tag: gray 应强制命中灰度")
        void shouldForceGrayByHeader() {
            currentFilter = newFilter(true, 0, List.of());
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                    .header(GrayRouteFilter.HEADER_GRAY_TAG, "gray")
                    .build());

            assertThat(grayTagOf(exchange)).isEqualTo(GrayRouteFilter.GRAY_VALUE);
            assertThat(grayHitCount("header")).isEqualTo(1.0d);
        }
    }

    @Nested
    @DisplayName("租户白名单灰度")
    class TenantWhitelistTests {

        @Test
        @DisplayName("租户命中白名单应灰度")
        void shouldGrayWhenTenantWhitelisted() {
            currentFilter = newFilter(true, 0, List.of("tenant-9"));
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                    .header("X-Tenant-Id", "tenant-9")
                    .build());

            assertThat(grayTagOf(exchange)).isEqualTo(GrayRouteFilter.GRAY_VALUE);
            assertThat(grayHitCount("tenant")).isEqualTo(1.0d);
        }

        @Test
        @DisplayName("租户未命中白名单应稳定")
        void shouldStableWhenTenantNotWhitelisted() {
            currentFilter = newFilter(true, 0, List.of("tenant-9"));
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                    .header("X-Tenant-Id", "tenant-1")
                    .build());

            assertThat(grayTagOf(exchange)).isEqualTo(GrayRouteFilter.STABLE_VALUE);
            assertThat(grayHitCount("tenant")).isZero();
        }
    }

    @Nested
    @DisplayName("用户尾号百分比灰度")
    class PercentageTests {

        @Test
        @DisplayName("百分比 100 时任意用户均灰度")
        void shouldGrayAllWhenPercentageFull() {
            currentFilter = newFilter(true, 100, List.of());
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                    .header("X-User-Id", "user-1001")
                    .build());

            assertThat(grayTagOf(exchange)).isEqualTo(GrayRouteFilter.GRAY_VALUE);
            assertThat(grayHitCount("percentage")).isEqualTo(1.0d);
        }

        @Test
        @DisplayName("百分比 0 时任意用户均稳定")
        void shouldStableAllWhenPercentageZero() {
            currentFilter = newFilter(true, 0, List.of());
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                    .header("X-User-Id", "user-1001")
                    .build());

            assertThat(grayTagOf(exchange)).isEqualTo(GrayRouteFilter.STABLE_VALUE);
            assertThat(grayHitCount("percentage")).isZero();
        }

        @Test
        @DisplayName("同一用户的灰度状态应稳定（多次调用一致）")
        void shouldBeStableForSameUser() {
            currentFilter = newFilter(true, 50, List.of());
            String first = grayTagOf(MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                    .header("X-User-Id", "stable-user")
                    .build()));
            String second = grayTagOf(MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                    .header("X-User-Id", "stable-user")
                    .build()));

            assertThat(first).isEqualTo(second);
        }
    }
}
