package com.apex.scaffold.gateway.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.internal.InternalAuthProperties;
import com.apex.scaffold.common.openapi.OpenApiGuardProperties;
import com.apex.scaffold.common.openapi.TenantCapabilityVerifier;
import com.apex.scaffold.common.util.JsonUtil;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 网关与服务侧租户能力判定一致性测试（方案 G6）。
 *
 * <p>两侧各自实现了一份能力快照拉取与判定逻辑，本测试用同一个本地 HTTP 快照端点
 * 为两侧客户端提供相同响应，逐场景断言两侧对同一快照得出相同判定，防止口径漂移。
 *
 * <p>覆盖场景：全量订阅、套餐存在但特性集合为空（收紧为未订阅任何能力）、
 * 部分订阅命中/未命中、租户不存在、租户编码缺失、字段类型非法、解析失败不缓存、
 * TTL 内命中与过期刷新、内部端点错误传播。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("租户能力判定网关/服务侧一致性测试")
class TenantCapabilityConsistencyTest {

    private static final String CONTEXT_PATH = "/internal/v1/tenants/";

    private HttpServer server;

    private String baseUrl;

    private TenantCapabilityClient gatewayClient;

    private TenantCapabilityVerifier serviceVerifier;

    /** 租户编码到快照响应体的映射，按场景切换。 */
    private final Map<String, String> bodies = new ConcurrentHashMap<>();

    /** 租户编码到内部端点请求次数，用于验证缓存与解析失败语义。 */
    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

    /** 返回 HTTP 500 的租户集合，用于验证错误传播。 */
    private final Set<String> failingTenants = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(CONTEXT_PATH, exchange -> {
            String path = exchange.getRequestURI().getPath();
            String rest = path.substring(CONTEXT_PATH.length());
            String tenantCode = rest.replace("/capabilities", "");
            requestCounts
                    .computeIfAbsent(tenantCode, key -> new AtomicInteger())
                    .incrementAndGet();
            if (failingTenants.contains(tenantCode)) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }
            String body = bodies.get(tenantCode);
            byte[] payload = (body == null ? "{}" : body).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        gatewayClient = newGatewayClient(30000L);

        OpenApiGuardProperties guardProperties = new OpenApiGuardProperties();
        guardProperties.setBaseUrl(baseUrl);
        serviceVerifier = new TenantCapabilityVerifier(guardProperties, new InternalAuthProperties(), null);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private TenantCapabilityClient newGatewayClient(long cacheTtlMillis) {
        TenantCapabilityProperties gatewayProperties = new TenantCapabilityProperties();
        gatewayProperties.setBaseUrl(baseUrl);
        gatewayProperties.setCacheTtlMillis(cacheTtlMillis);
        return new TenantCapabilityClient(gatewayProperties, new InternalAuthProperties(), null);
    }

    private void stub(String tenantCode, String body) {
        bodies.put(tenantCode, body);
    }

    private int requestCount(String tenantCode) {
        AtomicInteger counter = requestCounts.get(tenantCode);
        return counter == null ? 0 : counter.get();
    }

    private static String snapshot(boolean exists, boolean unbounded, String... features) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exists", exists);
        data.put("unbounded", unbounded);
        data.put("features", List.of(features));
        return JsonUtil.toJson(Map.of("code", 0, "data", data));
    }

    private void assertConsistent(String tenantCode, String capabilityCode) {
        Boolean gateway = gatewayClient.subscribed(tenantCode, capabilityCode).block();
        Boolean service = serviceVerifier.subscribed(tenantCode, capabilityCode).block();
        assertThat(gateway).isEqualTo(service);
    }

    @Test
    @DisplayName("全量订阅：unbounded=true 时两侧一致放行")
    void shouldAgreeOnUnboundedSubscription() {
        stub("t-unbounded", snapshot(true, true));
        assertConsistent("t-unbounded", "file-storage");
        assertThat(gatewayClient.subscribed("t-unbounded", "file-storage").block())
                .isTrue();
    }

    @Test
    @DisplayName("套餐存在但特性为空：两侧一致拒绝全部能力")
    void shouldAgreeOnEmptyFeaturesRejection() {
        stub("t-empty", snapshot(true, false));
        assertConsistent("t-empty", "file-storage");
        assertThat(gatewayClient.subscribed("t-empty", "file-storage").block()).isFalse();
    }

    @Test
    @DisplayName("部分订阅：命中放行、未命中拒绝且两侧一致")
    void shouldAgreeOnPartialSubscription() {
        stub("t-partial", snapshot(true, false, "file-storage"));
        assertConsistent("t-partial", "file-storage");
        assertConsistent("t-partial", "workflow");
        assertThat(gatewayClient.subscribed("t-partial", "file-storage").block())
                .isTrue();
        assertThat(gatewayClient.subscribed("t-partial", "workflow").block()).isFalse();
    }

    @Test
    @DisplayName("租户不存在：两侧一致拒绝")
    void shouldAgreeOnAbsentTenant() {
        stub("t-absent", snapshot(false, false));
        assertConsistent("t-absent", "file-storage");
        assertThat(gatewayClient.subscribed("t-absent", "file-storage").block()).isFalse();
    }

    @Test
    @DisplayName("能力快照必需字段类型错误：两侧均传播解析错误")
    void shouldRejectMalformedSnapshotFields() {
        stub("t-malformed", "{\"code\":0,\"data\":{\"exists\":\"true\",\"unbounded\":false,\"features\":[]}}");

        assertThatThrownBy(() ->
                        gatewayClient.subscribed("t-malformed", "file-storage").block())
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> serviceVerifier
                        .subscribed("t-malformed", "file-storage")
                        .block())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("能力快照变体非法：unbounded 非布尔、features 非数组、元素非字符串两侧均报错")
    void shouldRejectIllegalFieldTypes() {
        stub("t-bad-unbounded", "{\"code\":0,\"data\":{\"exists\":true,\"unbounded\":\"true\",\"features\":[]}}");
        stub(
                "t-bad-features",
                "{\"code\":0,\"data\":{\"exists\":true,\"unbounded\":false,\"features\":\"file-storage\"}}");
        stub("t-bad-item", "{\"code\":0,\"data\":{\"exists\":true,\"unbounded\":false,\"features\":[1]}}");

        for (String tenant : List.of("t-bad-unbounded", "t-bad-features", "t-bad-item")) {
            assertThatThrownBy(() ->
                            gatewayClient.subscribed(tenant, "file-storage").block())
                    .as("网关租户 %s 应传播解析错误", tenant)
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() ->
                            serviceVerifier.subscribed(tenant, "file-storage").block())
                    .as("服务侧租户 %s 应传播解析错误", tenant)
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    @DisplayName("解析失败不写缓存：同一租户重复判定仍会重新拉取")
    void shouldNotCacheMalformedSnapshot() {
        stub(
                "t-bad-cache",
                "{\"code\":0,\"data\":{\"exists\":true,\"unbounded\":false,\"features\":\"file-storage\"}}");

        assertThatThrownBy(() ->
                        gatewayClient.subscribed("t-bad-cache", "file-storage").block())
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() ->
                        gatewayClient.subscribed("t-bad-cache", "file-storage").block())
                .isInstanceOf(RuntimeException.class);
        assertThat(requestCount("t-bad-cache")).isEqualTo(2);

        assertThatThrownBy(() -> serviceVerifier
                        .subscribed("t-bad-cache", "file-storage")
                        .block())
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> serviceVerifier
                        .subscribed("t-bad-cache", "file-storage")
                        .block())
                .isInstanceOf(RuntimeException.class);
        assertThat(requestCount("t-bad-cache")).isEqualTo(4);
    }

    @Test
    @DisplayName("TTL 内命中缓存：重复判定不重复请求内部端点")
    void shouldReuseSnapshotWithinTtl() {
        stub("t-ttl-hit", snapshot(true, false, "file-storage"));

        assertThat(gatewayClient.subscribed("t-ttl-hit", "file-storage").block())
                .isTrue();
        assertThat(gatewayClient.subscribed("t-ttl-hit", "file-storage").block())
                .isTrue();
        assertThat(requestCount("t-ttl-hit")).isEqualTo(1);

        assertThat(serviceVerifier.subscribed("t-ttl-hit", "file-storage").block())
                .isTrue();
        assertThat(serviceVerifier.subscribed("t-ttl-hit", "file-storage").block())
                .isTrue();
        assertThat(requestCount("t-ttl-hit")).isEqualTo(2);
    }

    @Test
    @DisplayName("TTL 过期后重新拉取快照")
    void shouldRefreshAfterTtlExpiry() throws InterruptedException {
        stub("t-ttl-expire", snapshot(true, false, "file-storage"));
        TenantCapabilityClient shortTtlClient = newGatewayClient(1000L);

        assertThat(shortTtlClient.subscribed("t-ttl-expire", "file-storage").block())
                .isTrue();
        assertThat(requestCount("t-ttl-expire")).isEqualTo(1);

        Thread.sleep(1200L);

        assertThat(shortTtlClient.subscribed("t-ttl-expire", "file-storage").block())
                .isTrue();
        assertThat(requestCount("t-ttl-expire")).isEqualTo(2);
    }

    @Test
    @DisplayName("内部端点错误：两侧均传播错误（网关据此 503、服务侧失败关闭）")
    void shouldPropagateServerError() {
        failingTenants.add("t-error");

        assertThatThrownBy(() ->
                        gatewayClient.subscribed("t-error", "file-storage").block())
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() ->
                        serviceVerifier.subscribed("t-error", "file-storage").block())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("租户编码缺失：两侧一致拒绝且不发请求")
    void shouldAgreeOnBlankTenant() {
        assertConsistent(" ", "file-storage");
        assertThat(gatewayClient.subscribed(" ", "file-storage").block()).isFalse();
        assertThat(requestCount(" ")).isZero();
    }
}
