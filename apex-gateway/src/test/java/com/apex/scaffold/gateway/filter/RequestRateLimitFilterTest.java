package com.apex.scaffold.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.gateway.ratelimit.ClientTierClient;
import com.apex.scaffold.gateway.ratelimit.GatewayRateLimitProperties;
import com.apex.scaffold.gateway.ratelimit.TokenBucketRateLimitFilter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link RequestRateLimitFilter} 单元测试.
 *
 * <p>Task 2 重构后，本过滤器只承担「维度解析 → Key 拼接 → 阈值解析 → 委托内核」的编排职责，
 * 具体的令牌桶判定由 {@link TokenBucketRateLimitFilter}（另见 {@code TokenBucketRateLimitFilterTest}）
 * 负责。因此本测试聚焦：过滤器顺序、维度优先级（租户 → 用户 → IP）、Key 组成、
 * 规则阈值解析、开关透传与内核委托。限流算法行为（429 / 降级）在核测试中覆盖。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("RequestRateLimitFilter 限流过滤器测试")
class RequestRateLimitFilterTest {

    private KeyResolver tenantKeyResolver;
    private KeyResolver userKeyResolver;
    private KeyResolver ipKeyResolver;
    private TokenBucketRateLimitFilter rateLimitCore;
    private ClientTierClient clientTierClient;
    private GatewayRateLimitProperties properties;
    private RequestRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        tenantKeyResolver = mock(KeyResolver.class);
        userKeyResolver = mock(KeyResolver.class);
        ipKeyResolver = mock(KeyResolver.class);
        rateLimitCore = mock(TokenBucketRateLimitFilter.class);
        clientTierClient = mock(ClientTierClient.class);
        properties = new GatewayRateLimitProperties();
        filter = new RequestRateLimitFilter(
                tenantKeyResolver, userKeyResolver, ipKeyResolver, rateLimitCore, clientTierClient, properties);
        lenient().when(clientTierClient.tierOf(anyString())).thenReturn(Mono.just("default"));

        // 默认：无租户、无用户身份，回退 IP 维度
        lenient().when(tenantKeyResolver.resolve(any())).thenReturn(Mono.empty());
        lenient().when(userKeyResolver.resolve(any())).thenReturn(Mono.empty());
        lenient().when(ipKeyResolver.resolve(any())).thenReturn(Mono.just("ip:127.0.0.1"));
        lenient()
                .when(rateLimitCore.apply(any(), any(), anyString(), anyLong(), anyDouble()))
                .thenReturn(Mono.empty());
    }

    private GatewayFilterChain passthroughChain() {
        return exchange -> Mono.empty();
    }

    private MockServerWebExchange exchangeWith(String header, String value) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/api/v1/orders");
        if (header != null) {
            builder = builder.header(header, value);
        }
        return MockServerWebExchange.from(builder.build());
    }

    @Nested
    @DisplayName("过滤器顺序")
    class OrderTests {

        @Test
        @DisplayName("getOrder 应返回 30")
        void shouldReturnExpectedOrder() {
            assertThat(filter.getOrder())
                    .isEqualTo(RequestRateLimitFilter.ORDER)
                    .isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("开关")
    class EnabledTests {

        @Test
        @DisplayName("关闭限流时应直接透传，不委托内核")
        void shouldBypassWhenDisabled() {
            properties.setEnabled(false);

            MockServerWebExchange exchange = exchangeWith(null, null);

            StepVerifier.create(filter.filter(exchange, passthroughChain())).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isNull();
            verify(rateLimitCore, never()).apply(any(), any(), anyString(), anyLong(), anyDouble());
        }
    }

    @Nested
    @DisplayName("维度优先级")
    class DimensionTests {

        @Test
        @DisplayName("租户身份存在时应优先使用租户维度")
        void shouldPreferTenantWhenPresent() {
            when(tenantKeyResolver.resolve(any())).thenReturn(Mono.just("tenant:acme"));

            filter.filter(exchangeWith("X-Tenant-Id", "acme"), passthroughChain())
                    .block();

            verify(rateLimitCore)
                    .apply(any(), any(), eq("apex:gateway:rl:tenant:acme:/api/v1/orders"), anyLong(), anyDouble());
        }

        @Test
        @DisplayName("无租户但有用户身份时应使用用户维度")
        void shouldUseUserWhenNoTenant() {
            when(userKeyResolver.resolve(any())).thenReturn(Mono.just("user:user-1001"));

            filter.filter(exchangeWith("X-User-Id", "user-1001"), passthroughChain())
                    .block();

            verify(rateLimitCore)
                    .apply(any(), any(), eq("apex:gateway:rl:user:user-1001:/api/v1/orders"), anyLong(), anyDouble());
        }

        @Test
        @DisplayName("匿名请求应回退 IP 维度")
        void shouldFallbackToIpWhenAnonymous() {
            filter.filter(exchangeWith(null, null), passthroughChain()).block();

            verify(rateLimitCore)
                    .apply(any(), any(), eq("apex:gateway:rl:ip:127.0.0.1:/api/v1/orders"), anyLong(), anyDouble());
            verify(userKeyResolver).resolve(any(ServerWebExchange.class));
        }
    }

    @Nested
    @DisplayName("阈值规则解析")
    class ThresholdRuleTests {

        @Test
        @DisplayName("命中资源规则时应使用规则容量与补充速率")
        void shouldUseMatchedRuleThreshold() {
            GatewayRateLimitProperties.Rule rule = new GatewayRateLimitProperties.Rule();
            rule.setResource("/api/v1/**");
            rule.setCapacity(2000L);
            rule.setRefillRate(200.0d);
            properties.setRules(List.of(rule));

            filter.filter(exchangeWith(null, null), passthroughChain()).block();

            verify(rateLimitCore).apply(any(), any(), anyString(), eq(2000L), eq(200.0d));
        }

        @Test
        @DisplayName("未命中规则时应回退默认容量与速率")
        void shouldFallbackToDefaultThreshold() {
            GatewayRateLimitProperties.Rule rule = new GatewayRateLimitProperties.Rule();
            rule.setResource("/api/v1/workflows/**");
            rule.setCapacity(2000L);
            properties.setRules(List.of(rule));

            filter.filter(exchangeWith(null, null), passthroughChain()).block();

            verify(rateLimitCore)
                    .apply(
                            any(),
                            any(),
                            anyString(),
                            eq(properties.getDefaultCapacity()),
                            eq(properties.getDefaultRefillRate()));
        }
    }

    @Nested
    @DisplayName("内核委托")
    class DelegationTests {

        @Test
        @DisplayName("内核返回的错误（如下游异常）必须向上传播")
        void shouldPropagateCoreError() {
            when(rateLimitCore.apply(any(), any(), anyString(), anyLong(), anyDouble()))
                    .thenReturn(Mono.error(new RuntimeException("downstream connection refused")));

            StepVerifier.create(filter.filter(exchangeWith(null, null), passthroughChain()))
                    .expectErrorMatches(err -> err.getMessage().contains("connection refused"))
                    .verify();
        }
    }

    /** 开放接口按应用分档限流（D2 {@code rateLimitTier}）。 */
    @Nested
    @DisplayName("开放流量按应用维度（D2）")
    class OpenApiClientDimension {

        private MockServerWebExchange openApiExchange(String clientId) {
            Claims claims = new DefaultClaims(Map.of("client_id", clientId, "scope", "open:file:read"));
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/open-api/file/content").build());
            exchange.getAttributes().put(JwtSignatureFilter.ATTR_CLAIMS, claims);
            return exchange;
        }

        @Test
        @DisplayName("开放路径固定按 client:{clientId} 维度判定")
        void shouldUseClientIdentityForOpenApi() {
            filter.filter(openApiExchange("app-001"), passthroughChain()).block();

            verify(rateLimitCore)
                    .apply(any(), any(), contains("client:app-001:/open-api/file/content"), anyLong(), anyDouble());
        }

        @Test
        @DisplayName("档位阈值按 open-api-tiers 映射生效")
        void shouldApplyTierThreshold() {
            GatewayRateLimitProperties.Tier premium = new GatewayRateLimitProperties.Tier(10_000L, 1_000.0d);
            properties.setOpenApiTiers(
                    Map.of("default", new GatewayRateLimitProperties.Tier(100L, 10.0d), "premium", premium));
            when(clientTierClient.tierOf("app-premium")).thenReturn(Mono.just("premium"));

            filter.filter(openApiExchange("app-premium"), passthroughChain()).block();

            verify(rateLimitCore).apply(any(), any(), anyString(), eq(10_000L), eq(1_000.0d));
        }

        @Test
        @DisplayName("未知档回退 default 档阈值")
        void shouldFallbackToDefaultTier() {
            properties.setOpenApiTiers(Map.of("default", new GatewayRateLimitProperties.Tier(100L, 10.0d)));
            when(clientTierClient.tierOf("app-x")).thenReturn(Mono.just("unheard-tier"));

            filter.filter(openApiExchange("app-x"), passthroughChain()).block();

            verify(rateLimitCore).apply(any(), any(), anyString(), eq(100L), eq(10.0d));
        }

        @Test
        @DisplayName("无法识别 client_id 时回退既有租户/用户/IP 维度")
        void shouldFallbackToLegacyDimensionWithoutClientId() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/open-api/file/content").build());

            filter.filter(exchange, passthroughChain()).block();

            verify(rateLimitCore)
                    .apply(any(), any(), contains("ip:127.0.0.1:/open-api/file/content"), anyLong(), anyDouble());
        }
    }
}
