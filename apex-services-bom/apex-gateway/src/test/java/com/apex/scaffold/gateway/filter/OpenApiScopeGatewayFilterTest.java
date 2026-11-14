package com.apex.scaffold.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.gateway.capability.TenantCapabilityClient;
import com.apex.scaffold.gateway.capability.TenantCapabilityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * {@link OpenApiScopeGatewayFilter} 单元测试（D2/D4）.
 *
 * <p>覆盖令牌来源断言（个人令牌/非开放授予类型/缺 client_id 拒绝）、scope 断言
 * （路由元数据 any-of、方法族区分、无元数据 fail-closed）、能力门禁
 * （未订阅 403、无租户 claim fail-closed、内部端点失败 503）与可信 scope 头注入
 * （覆盖外部伪造值）。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("OpenApiScopeGatewayFilter 开放接口 scope 与能力门禁测试")
class OpenApiScopeGatewayFilterTest {

    /** 租户能力订阅替身：按 {@code 租户|能力} 键返回结果，可注入错误信号 */
    static class StubCapabilityClient extends TenantCapabilityClient {

        private final Map<String, Boolean> results;
        private final boolean fail;

        StubCapabilityClient(Map<String, Boolean> results, boolean fail) {
            super(
                    new TenantCapabilityProperties(),
                    new com.apex.scaffold.common.internal.InternalAuthProperties(),
                    null);
            this.results = results;
            this.fail = fail;
        }

        @Override
        public Mono<Boolean> subscribed(String tenantCode, String capabilityCode) {
            if (fail) {
                return Mono.error(new IllegalStateException("内部端点不可达"));
            }
            return Mono.just(results.getOrDefault(tenantCode + "|" + capabilityCode, Boolean.FALSE));
        }
    }

    private static final Map<String, Object> FILE_METADATA = Map.of(
            "capability",
            "file-storage",
            "read-scopes",
            List.of("open:file:read"),
            "write-scopes",
            List.of("open:file:write"));

    private StubCapabilityClient client;
    private OpenApiScopeGatewayFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        client = new StubCapabilityClient(Map.of("t-1|file-storage", true), false);
        filter = new OpenApiScopeGatewayFilter(client);
    }

    private GatewayFilterChain capturingChain(ServerWebExchange[] holder) {
        return exchange -> {
            holder[0] = exchange;
            return Mono.empty();
        };
    }

    private GatewayFilterChain noopChain() {
        return exchange -> Mono.empty();
    }

    private Claims claims(Map<String, Object> values) {
        Claims claims = mock(Claims.class);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            when(claims.get(entry.getKey())).thenReturn(entry.getValue());
        }
        return claims;
    }

    private MockServerWebExchange exchangeWith(
            HttpMethod method, String path, Claims claims, Map<String, Object> metadata) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path)
                .header("X-Client-Scopes", "forged-scopes")
                .build());
        Route route = Route.async()
                .id("open-api-file")
                .uri("lb://apex-file-service")
                .predicate(e -> true)
                .metadata(OpenApiScopeGatewayFilter.METADATA_KEY, metadata)
                .build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        if (claims != null) {
            exchange.getAttributes().put(JwtSignatureFilter.ATTR_CLAIMS, claims);
        }
        return exchange;
    }

    private int bodyCode(MockServerWebExchange exchange) throws Exception {
        String body = exchange.getResponse().getBodyAsString().block();
        return objectMapper.readTree(body).path("code").asInt();
    }

    /** 开放应用令牌缺省 claims：client_credentials 应用令牌，含 scope 与租户。 */
    private Map<String, Object> appClaims(String scope) {
        return Map.of(
                "grant_type", "client_credentials",
                "client_id", "partner-app",
                "scope", scope,
                "tenant_id", "t-1");
    }

    @Test
    @DisplayName("getOrder 应为 25（验签 10 / 上下文中继 20 之后、限流 30 之前）")
    void shouldOrderBetweenRelayAndRateLimit() {
        assertThat(filter.getOrder()).isEqualTo(25);
    }

    @Test
    @DisplayName("非 /open-api/ 前缀请求应直接放行，不做任何断言")
    void shouldPassThroughNonOpenApiPath() {
        ServerWebExchange[] holder = new ServerWebExchange[1];
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/files").build());

        filter.filter(exchange, capturingChain(holder)).block();

        assertThat(holder[0]).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Nested
    @DisplayName("令牌来源断言")
    class TokenSourceTests {

        @Test
        @DisplayName("缺失已验签 Claims 应返回 401（防御性出口）")
        void shouldReturn401WhenClaimsMissing() throws Exception {
            MockServerWebExchange exchange = exchangeWith(HttpMethod.GET, "/open-api/files/x", null, FILE_METADATA);

            filter.filter(exchange, noopChain()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("携带 userId 的个人用户令牌应被拒绝（40303）")
        void shouldRejectPersonalToken() throws Exception {
            Map<String, Object> values = new java.util.HashMap<>(appClaims("open:file:read"));
            values.put("userId", "1001");
            MockServerWebExchange exchange =
                    exchangeWith(HttpMethod.GET, "/open-api/files/x", claims(values), FILE_METADATA);

            filter.filter(exchange, noopChain()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(bodyCode(exchange)).isEqualTo(40303);
        }

        @Test
        @DisplayName("grant_type 为 password 等非开放授予类型应被拒绝（40303）")
        void shouldRejectDisallowedGrantType() throws Exception {
            Map<String, Object> values = new java.util.HashMap<>(appClaims("open:file:read"));
            values.put("grant_type", "password");
            MockServerWebExchange exchange =
                    exchangeWith(HttpMethod.GET, "/open-api/files/x", claims(values), FILE_METADATA);

            filter.filter(exchange, noopChain()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(bodyCode(exchange)).isEqualTo(40303);
        }

        @Test
        @DisplayName("无 client_id 的应用令牌应被拒绝（40303）")
        void shouldRejectTokenWithoutClientId() throws Exception {
            Map<String, Object> values = Map.of("grant_type", "client_credentials", "scope", "open:file:read");
            MockServerWebExchange exchange =
                    exchangeWith(HttpMethod.GET, "/open-api/files/x", claims(values), FILE_METADATA);

            filter.filter(exchange, noopChain()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(bodyCode(exchange)).isEqualTo(40303);
        }

        @Test
        @DisplayName("无 scope claim 应返回 scope 不足（40301）")
        void shouldRejectTokenWithoutScope() throws Exception {
            Map<String, Object> values = Map.of("grant_type", "client_credentials", "client_id", "partner-app");
            MockServerWebExchange exchange =
                    exchangeWith(HttpMethod.GET, "/open-api/files/x", claims(values), FILE_METADATA);

            filter.filter(exchange, noopChain()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(bodyCode(exchange)).isEqualTo(40301);
        }

        @Test
        @DisplayName("遗留令牌（无 grant_type 但有 client_id + scope + tenant）应继续走 scope 断言")
        void shouldAllowLegacyTokenWithClientIdAndScope() {
            Map<String, Object> values =
                    Map.of("client_id", "partner-app", "scope", "open:file:read", "tenant_id", "t-1");
            MockServerWebExchange exchange =
                    exchangeWith(HttpMethod.GET, "/open-api/files/x", claims(values), FILE_METADATA);

            filter.filter(exchange, capturingChain(new ServerWebExchange[1])).block();

            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }

    @Nested
    @DisplayName("scope 断言")
    class ScopeAssertionTests {

        @Test
        @DisplayName("路由缺 open-api 元数据应 fail-closed 返回 40301")
        void shouldFailClosedWhenMetadataMissing() throws Exception {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/open-api/files/x").build());
            exchange.getAttributes()
                    .put(
                            ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                            Route.async()
                                    .id("plain")
                                    .uri("lb://x")
                                    .predicate(e -> true)
                                    .build());
            exchange.getAttributes().put(JwtSignatureFilter.ATTR_CLAIMS, claims(appClaims("open:file:read")));

            filter.filter(exchange, noopChain()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(bodyCode(exchange)).isEqualTo(40301);
        }

        @Test
        @DisplayName("GET 使用 read-scopes 断言：令牌 scope 命中即放行")
        void shouldUseReadScopesForGet() {
            MockServerWebExchange exchange = exchangeWith(
                    HttpMethod.GET, "/open-api/files/x", claims(appClaims("open:file:read")), FILE_METADATA);

            filter.filter(exchange, noopChain()).block();

            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("YAML 列表经 Binder 退化为 {0=v} Map 形态时 scope 断言仍应生效")
        void shouldParseBinderDegradedMapMetadata() {
            Map<String, Object> degraded = Map.of(
                    "capability",
                    "file-storage",
                    "read-scopes",
                    Map.of("0", "open:file:read"),
                    "write-scopes",
                    Map.of("0", "open:file:write"));
            MockServerWebExchange exchange =
                    exchangeWith(HttpMethod.GET, "/open-api/files/x", claims(appClaims("open:file:read")), degraded);

            filter.filter(exchange, capturingChain(new ServerWebExchange[1])).block();

            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("POST 使用 write-scopes 断言：仅持有 read scope 应拒绝")
        void shouldUseWriteScopesForPost() throws Exception {
            MockServerWebExchange exchange = exchangeWith(
                    HttpMethod.POST, "/open-api/files/x", claims(appClaims("open:file:read")), FILE_METADATA);

            filter.filter(exchange, noopChain()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(bodyCode(exchange)).isEqualTo(40301);
        }

        @Test
        @DisplayName("令牌 scope 与要求的 write scope 完全无关应拒绝（反例矩阵）")
        void shouldRejectUnrelatedScope() throws Exception {
            MockServerWebExchange exchange = exchangeWith(
                    HttpMethod.POST,
                    "/open-api/files/x",
                    claims(appClaims("open:user:read open:message:send")),
                    FILE_METADATA);

            filter.filter(exchange, noopChain()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(bodyCode(exchange)).isEqualTo(40301);
        }
    }

    @Nested
    @DisplayName("能力门禁与放行")
    class CapabilityGateTests {

        @Test
        @DisplayName("订阅命中应放行并注入可信 X-Client-Scopes（覆盖伪造值）")
        void shouldInjectTrustedScopeHeaderWhenSubscribed() {
            ServerWebExchange[] holder = new ServerWebExchange[1];
            MockServerWebExchange exchange = exchangeWith(
                    HttpMethod.GET,
                    "/open-api/files/x",
                    claims(appClaims("open:file:read open:workflow:read")),
                    FILE_METADATA);

            filter.filter(exchange, capturingChain(holder)).block();

            assertThat(exchange.getResponse().getStatusCode()).isNull();
            assertThat(holder[0].getRequest().getHeaders().getFirst("X-Client-Scopes"))
                    .isEqualTo("open:file:read open:workflow:read");
        }

        @Test
        @DisplayName("放行时应注入可信 X-Client-Id（D2 分档限流/审计维度）")
        void shouldInjectTrustedClientIdHeaderWhenSubscribed() {
            ServerWebExchange[] holder = new ServerWebExchange[1];
            MockServerWebExchange exchange = exchangeWith(
                    HttpMethod.GET, "/open-api/files/x", claims(appClaims("open:file:read")), FILE_METADATA);

            filter.filter(exchange, capturingChain(holder)).block();

            assertThat(holder[0].getRequest().getHeaders().getFirst("X-Client-Id"))
                    .isEqualTo("partner-app");
        }

        @Test
        @DisplayName("放行时应注入能力标记头（服务侧二次校验触发依据，D4）")
        void shouldInjectCapabilityMarkerHeaderWhenSubscribed() {
            ServerWebExchange[] holder = new ServerWebExchange[1];
            MockServerWebExchange exchange = exchangeWith(
                    HttpMethod.GET, "/open-api/files/x", claims(appClaims("open:file:read")), FILE_METADATA);

            filter.filter(exchange, capturingChain(holder)).block();

            assertThat(holder[0].getRequest().getHeaders().getFirst("X-Open-Api-Capability"))
                    .isEqualTo("file-storage");
        }

        @Test
        @DisplayName("能力未订阅应返回 403 + 能力未开通（40302）")
        void shouldRejectWhenCapabilityNotSubscribed() throws Exception {
            client = new StubCapabilityClient(Map.of(), false);
            filter = new OpenApiScopeGatewayFilter(client);
            MockServerWebExchange exchange = exchangeWith(
                    HttpMethod.GET, "/open-api/files/x", claims(appClaims("open:file:read")), FILE_METADATA);

            filter.filter(exchange, noopChain()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(bodyCode(exchange)).isEqualTo(40302);
        }

        @Test
        @DisplayName("令牌无 tenant_id claim 应 fail-closed 拒绝（40302）")
        void shouldFailClosedWhenTenantClaimMissing() throws Exception {
            Map<String, Object> values = new java.util.HashMap<>(appClaims("open:file:read"));
            values.remove("tenant_id");
            MockServerWebExchange exchange =
                    exchangeWith(HttpMethod.GET, "/open-api/files/x", claims(values), FILE_METADATA);

            filter.filter(exchange, noopChain()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(bodyCode(exchange)).isEqualTo(40302);
        }

        @Test
        @DisplayName("能力订阅查询失败应降级为 503（fail-closed）")
        void shouldDegradeTo503WhenCapabilityLookupFails() throws Exception {
            client = new StubCapabilityClient(Map.of(), true);
            filter = new OpenApiScopeGatewayFilter(client);
            MockServerWebExchange exchange = exchangeWith(
                    HttpMethod.GET, "/open-api/files/x", claims(appClaims("open:file:read")), FILE_METADATA);

            filter.filter(exchange, noopChain()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        @Test
        @DisplayName("能力门禁关闭（enabled=false）时仅做 scope 断言即可放行")
        void shouldSkipCapabilityGateWhenDisabled() {
            TenantCapabilityProperties properties = new TenantCapabilityProperties();
            properties.setEnabled(false);
            TenantCapabilityClient disabled = new TenantCapabilityClient(
                    properties, new com.apex.scaffold.common.internal.InternalAuthProperties(), null) {};
            filter = new OpenApiScopeGatewayFilter(disabled);
            MockServerWebExchange exchange = exchangeWith(
                    HttpMethod.GET, "/open-api/files/x", claims(appClaims("open:file:read")), FILE_METADATA);

            filter.filter(exchange, capturingChain(new ServerWebExchange[1])).block();

            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }
}
