package com.apex.scaffold.common.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.util.JsonUtil;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * {@link OpenApiCapabilityGuardFilter} 单元测试（D4 服务侧二次校验）。
 *
 * <p>覆盖：无标记头透传（普通流量零影响）、标记头但缺租户头 403、
 * 订阅命中放行、未订阅 403 CAPABILITY_NOT_SUBSCRIBED、
 * 订阅查询失败 503 fail-closed。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("服务侧开放能力二次校验过滤器测试")
class OpenApiCapabilityGuardFilterTest {

    private OpenApiGuardProperties properties;

    private TenantCapabilityVerifier verifier;

    private OpenApiCapabilityGuardFilter filter;

    /** 桩订阅结果：null 表示查询失败（错误信号）。 */
    private volatile Boolean stubSubscribed;

    private final AtomicInteger fetchCount = new AtomicInteger();

    @BeforeEach
    void setUp() {
        properties = new OpenApiGuardProperties();
        verifier = new TenantCapabilityVerifier(properties) {
            @Override
            Mono<String> fetch(String tenantCode) {
                fetchCount.incrementAndGet();
                if (stubSubscribed == null) {
                    return Mono.error(new IllegalStateException("connection refused"));
                }
                return Mono.just(JsonUtil.toJson(Map.of(
                        "code",
                        0,
                        "data",
                        Map.of(
                                "exists",
                                true,
                                "unbounded",
                                false,
                                "features",
                                stubSubscribed
                                        ? java.util.List.of("file-storage")
                                        // 空 features 表示未订阅任何能力（全量须 unbounded=true）
                                        : java.util.List.of()))));
            }
        };
        filter = new OpenApiCapabilityGuardFilter(properties, verifier);
        stubSubscribed = Boolean.TRUE;
    }

    private WebFilterChain passthrough() {
        AtomicInteger invoked = new AtomicInteger();
        return exchange -> {
            invoked.incrementAndGet();
            return Mono.empty();
        };
    }

    private MockServerWebExchange exchangeWith(String capability, String tenant) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/api/v1/file/content");
        if (capability != null) {
            builder = builder.header("X-Open-Api-Capability", capability);
        }
        if (tenant != null) {
            builder = builder.header("X-Tenant-Id", tenant);
        }
        return MockServerWebExchange.from(builder.build());
    }

    @Test
    @DisplayName("无能力标记头的普通流量完全不受影响")
    void shouldPassThroughWithoutMarker() {
        MockServerWebExchange exchange = exchangeWith(null, "tenant-a");
        filter.filter(exchange, passthrough()).block();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(fetchCount.get()).isZero();
    }

    @Test
    @DisplayName("开放路径缺少能力标记头也必须按开放流量校验，不能删头绕过")
    void shouldNotBypassByDroppingMarkerOnOpenPath() {
        stubSubscribed = Boolean.FALSE;
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/open-api/file/content")
                .header("X-Tenant-Id", "tenant-a")
                .build());
        filter.filter(exchange, passthrough()).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
        assertThat(fetchCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("开放路径声明能力与路由能力不一致时应拒绝")
    void shouldRejectCapabilityMismatchOnOpenPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/open-api/file/content")
                .header("X-Open-Api-Capability", "workflow")
                .header("X-Tenant-Id", "tenant-a")
                .build());
        filter.filter(exchange, passthrough()).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
        assertThat(fetchCount.get()).isZero();
    }

    @Test
    @DisplayName("开放路径缺少可信租户头应 403")
    void shouldRejectWithoutTenantHeader() {
        MockServerWebExchange exchange = exchangeWith("file-storage", null);
        filter.filter(exchange, passthrough()).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
        assertThat(fetchCount.get()).isZero();
    }

    @Test
    @DisplayName("订阅命中应放行")
    void shouldAllowWhenSubscribed() {
        MockServerWebExchange exchange = exchangeWith("file-storage", "tenant-a");
        filter.filter(exchange, passthrough()).block();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("租户未订阅能力应 403 + 能力未开通业务码")
    void shouldRejectWhenNotSubscribed() {
        stubSubscribed = Boolean.FALSE;
        MockServerWebExchange exchange = exchangeWith("file-storage", "tenant-a");
        filter.filter(exchange, passthrough()).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString())
                .contains("application/json");
    }

    @Test
    @DisplayName("订阅查询失败应 fail-closed 503")
    void shouldFailClosedWhenLookupFails() {
        stubSubscribed = null;
        MockServerWebExchange exchange = exchangeWith("file-storage", "tenant-a");
        filter.filter(exchange, passthrough()).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(503);
    }

    @Test
    @DisplayName("TTL 内重复请求复用订阅快照")
    void shouldReuseSnapshotWithinTtl() {
        filter.filter(exchangeWith("file-storage", "tenant-a"), passthrough()).block();
        filter.filter(exchangeWith("file-storage", "tenant-a"), passthrough()).block();
        assertThat(fetchCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("守卫开关默认值为开启，默认配置下未订阅仍应 403")
    void shouldGuardByDefault() {
        assertThat(properties.isEnabled()).isTrue();
        stubSubscribed = Boolean.FALSE;
        MockServerWebExchange exchange = exchangeWith("file-storage", "tenant-a");
        filter.filter(exchange, passthrough()).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("非开放路径且无标记头不触发能力查询")
    void shouldNotQueryCapabilityOutsideOpenPath() {
        MockServerWebExchange exchange = exchangeWith(null, "tenant-a");
        filter.filter(exchange, passthrough()).block();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(fetchCount.get()).isZero();
    }
}
