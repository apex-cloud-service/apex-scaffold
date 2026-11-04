package com.apex.scaffold.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * {@link UserContextRelayFilter} 单元测试.
 *
 * <p>覆盖迁移方案 §5.3 过滤器链第 4 位（order = 20）的核心安全语义：
 * 验签后主体声明注入、缺少主体标识时严格 401（历史缺陷修复）、公开路径无 Claims 时放行。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("UserContextRelayFilter 用户上下文透传过滤器测试")
class UserContextRelayFilterTest {

    private final UserContextRelayFilter filter = new UserContextRelayFilter();

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
     * 构造指定声明的 Claims（不签名，仅承载 payload）.
     *
     * @param userId     用户 ID（可空）
     * @param tenantId   租户 ID（可空）
     * @param clientType 客户端类型（可空）
     * @return Claims 对象
     */
    private Claims buildClaims(String userId, String tenantId, String clientType) {
        var builder = Jwts.claims();
        if (userId != null) {
            builder.add("userId", userId);
        }
        if (tenantId != null) {
            builder.add("tenantId", tenantId);
        }
        if (clientType != null) {
            builder.add("clientType", clientType);
        }
        return builder.build();
    }

    @Nested
    @DisplayName("过滤器顺序")
    class OrderTests {

        @Test
        @DisplayName("getOrder 应返回 20")
        void shouldReturnExpectedOrder() {
            assertThat(filter.getOrder())
                    .isEqualTo(UserContextRelayFilter.ORDER)
                    .isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("身份头注入")
    class InjectionTests {

        @Test
        @DisplayName("验签成功后应注入 X-User-Id / X-Tenant-Id / X-Client-Type")
        void shouldInjectIdentityHeaders() {
            ServerWebExchange[] holder = new ServerWebExchange[1];
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").build());
            exchange.getAttributes().put(JwtSignatureFilter.ATTR_CLAIMS, buildClaims("user-1001", "tenant-9", "app"));

            filter.filter(exchange, capturingChain(holder)).block();

            var headers = holder[0].getRequest().getHeaders();
            assertThat(headers.getFirst(UserContextRelayFilter.HEADER_USER_ID)).isEqualTo("user-1001");
            assertThat(headers.getFirst(UserContextRelayFilter.HEADER_TENANT_ID))
                    .isEqualTo("tenant-9");
            assertThat(headers.getFirst(UserContextRelayFilter.HEADER_CLIENT_TYPE))
                    .isEqualTo("app");
        }

        @Test
        @DisplayName("tenantId 缺失时应注入空字符串而非 null")
        void shouldInjectBlankForMissingTenant() {
            ServerWebExchange[] holder = new ServerWebExchange[1];
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").build());
            exchange.getAttributes().put(JwtSignatureFilter.ATTR_CLAIMS, buildClaims("user-1001", null, null));

            filter.filter(exchange, capturingChain(holder)).block();

            var headers = holder[0].getRequest().getHeaders();
            assertThat(headers.getFirst(UserContextRelayFilter.HEADER_TENANT_ID))
                    .isEmpty();
            assertThat(headers.getFirst(UserContextRelayFilter.HEADER_CLIENT_TYPE))
                    .isEmpty();
        }

        @Test
        @DisplayName("应回退到 accountId / sub 解析用户标识")
        void shouldFallbackToSubClaim() {
            ServerWebExchange[] holder = new ServerWebExchange[1];
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").build());
            Claims claims = Jwts.claims().subject("subject-777").build();
            exchange.getAttributes().put(JwtSignatureFilter.ATTR_CLAIMS, claims);

            filter.filter(exchange, capturingChain(holder)).block();

            assertThat(holder[0].getRequest().getHeaders().getFirst(UserContextRelayFilter.HEADER_USER_ID))
                    .isEqualTo("subject-777");
        }
    }

    @Nested
    @DisplayName("无主体标识强拒")
    class RejectTests {

        @Test
        @DisplayName("Token 缺少 userId/accountId/sub 时应返回 401")
        void shouldReturn401WhenNoSubject() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").build());
            exchange.getAttributes().put(JwtSignatureFilter.ATTR_CLAIMS, buildClaims(null, "tenant-9", "app"));

            filter.filter(exchange, e -> Mono.empty()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("开放应用身份中继")
    class OpenApplicationTests {

        @Test
        void shouldRelayApplicationTenantWithoutInventingUserIdentity() {
            ServerWebExchange[] holder = new ServerWebExchange[1];
            MockServerWebExchange exchange = applicationExchange("/open-api/files/", "tenant-9");

            reactor.test.StepVerifier.create(filter.filter(exchange, capturingChain(holder)))
                    .verifyComplete();

            var headers = holder[0].getRequest().getHeaders();
            assertThat(headers.getFirst(UserContextRelayFilter.HEADER_TENANT_ID))
                    .isEqualTo("tenant-9");
            assertThat(headers.getFirst(UserContextRelayFilter.HEADER_USER_ID)).isNull();
            assertThat(headers.getFirst(OpenApiScopeGatewayFilter.CLIENT_ID_HEADER))
                    .isEqualTo("client-1");
        }

        @Test
        void shouldRejectApplicationOnRegularBusinessRoute() {
            ServerWebExchange[] holder = new ServerWebExchange[1];
            MockServerWebExchange exchange = applicationExchange("/api/v1/users", "tenant-9");

            reactor.test.StepVerifier.create(filter.filter(exchange, capturingChain(holder)))
                    .verifyComplete();

            assertThat(holder[0]).isNull();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void shouldRejectConflictingTenantClaims() {
            ServerWebExchange[] holder = new ServerWebExchange[1];
            MockServerWebExchange exchange = applicationExchange("/open-api/files/", "tenant-9");
            exchange.getAttributes()
                    .put(
                            JwtSignatureFilter.ATTR_CLAIMS,
                            Jwts.claims()
                                    .subject("client-1")
                                    .add("client_id", "client-1")
                                    .add("tenant_id", "tenant-9")
                                    .add("tenantId", "tenant-other")
                                    .add("token_use", "access")
                                    .add("grant_type", "client_credentials")
                                    .build());

            reactor.test.StepVerifier.create(filter.filter(exchange, capturingChain(holder)))
                    .verifyComplete();

            assertThat(holder[0]).isNull();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void shouldRejectApplicationWithoutTenant() {
            ServerWebExchange[] holder = new ServerWebExchange[1];
            MockServerWebExchange exchange = applicationExchange("/open-api/files/", "");

            reactor.test.StepVerifier.create(filter.filter(exchange, capturingChain(holder)))
                    .verifyComplete();

            assertThat(holder[0]).isNull();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        private MockServerWebExchange applicationExchange(String path, String tenant) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path)
                    .header(UserContextRelayFilter.HEADER_USER_ID, "forged-user")
                    .header(UserContextRelayFilter.HEADER_TENANT_ID, "forged-tenant")
                    .build());
            exchange.getAttributes()
                    .put(
                            JwtSignatureFilter.ATTR_CLAIMS,
                            Jwts.claims()
                                    .subject("client-1")
                                    .add("client_id", "client-1")
                                    .add("tenant_id", tenant)
                                    .add("token_use", "access")
                                    .add("grant_type", "client_credentials")
                                    .build());
            return exchange;
        }
    }

    @Nested
    @DisplayName("无 Claims 放行")
    class NoClaimsTests {

        @Test
        @DisplayName("公开路径无 Claims 应原样放行")
        void shouldPassThroughWithoutClaims() {
            ServerWebExchange[] holder = new ServerWebExchange[1];
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/health").build());

            filter.filter(exchange, capturingChain(holder)).block();

            assertThat(holder[0]).isNotNull();
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }
}
