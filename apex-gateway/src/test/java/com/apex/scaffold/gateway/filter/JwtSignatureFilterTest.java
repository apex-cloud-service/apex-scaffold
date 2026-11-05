package com.apex.scaffold.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.gateway.security.GatewayJwtVerifierService;
import com.apex.scaffold.gateway.security.JwksProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * {@link JwtSignatureFilter} 单元测试.
 *
 * <p>覆盖迁移方案 §5.3 过滤器链第 3 位（order = 10）的安全红线：
 * 内部 API 403、伪造身份头剥离、公开路径放行、缺失/无效 Token 401、验签成功注入 Claims。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("JwtSignatureFilter JWT 验签过滤器测试")
class JwtSignatureFilterTest {

    /** 测试用 HMAC 对称密钥（长度满足 HS256 ≥ 32 字节要求） */
    private static final String TEST_SECRET = "apex-gateway-test-secret-key-0123456789abcdef";

    private JwtSignatureFilter filter;
    private GatewayJwtVerifierService verifierService;

    @BeforeEach
    void setUp() {
        JwksProperties properties = new JwksProperties();
        properties.setHmacSecret(TEST_SECRET);
        verifierService = new GatewayJwtVerifierService(properties);
        filter = new JwtSignatureFilter(verifierService);
    }

    /**
     * 构造可校验链路的直通链，捕获被改写后的交换对象。
     */
    private GatewayFilterChain capturingChain(ServerWebExchange[] holder) {
        return exchange -> {
            holder[0] = exchange;
            return Mono.empty();
        };
    }

    /**
     * 构造链路后立即完成，用于断言仅剥离/校验行为。
     */
    private GatewayFilterChain noopChain() {
        return exchange -> Mono.empty();
    }

    /**
     * 生成一个合法的 HS256 Token（携带头部 kid=hmac-legacy 以命中过渡密钥）.
     *
     * @param subject 主体
     * @return JWT 字符串
     */
    private String validToken(String subject) {
        return Jwts.builder()
                .header()
                .keyId("hmac-legacy")
                .and()
                .subject(subject)
                .claim("userId", subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Nested
    @DisplayName("过滤器顺序")
    class OrderTests {

        @Test
        @DisplayName("getOrder 应返回 10")
        void shouldReturnExpectedOrder() {
            assertThat(filter.getOrder()).isEqualTo(JwtSignatureFilter.ORDER).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("内部 API 强拒")
    class InternalPathTests {

        @Test
        @DisplayName("访问 /internal/v1/** 应返回 403")
        void shouldRejectV1InternalPath() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/internal/v1/orders").build());

            filter.filter(exchange, noopChain()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("访问旧别名 /internal/** 应返回 403")
        void shouldRejectLegacyInternalPath() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/internal/orders").build());

            filter.filter(exchange, noopChain()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("伪造身份头剥离")
    class ForgedHeaderTests {

        @Test
        @DisplayName("公开路径应剥离客户端伪造的 X-User-Id 等身份头")
        void shouldStripForgedHeadersOnPublicPath() {
            ServerWebExchange[] holder = new ServerWebExchange[1];
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login")
                    .header("X-User-Id", "forged-user")
                    .header("X-Tenant-Id", "forged-tenant")
                    .header("X-Client-Type", "forged-client")
                    .header("X-Internal-Call", "true")
                    .build());

            filter.filter(exchange, capturingChain(holder)).block();

            HttpHeaders headers = holder[0].getRequest().getHeaders();
            assertThat(headers.getFirst("X-User-Id")).isNull();
            assertThat(headers.getFirst("X-Tenant-Id")).isNull();
            assertThat(headers.getFirst("X-Client-Type")).isNull();
            assertThat(headers.getFirst("X-Internal-Call")).isNull();
        }
    }

    @Test
    @DisplayName("大小写混合的内部身份和事务头均剥离，合法认证链保持不变")
    void shouldStripTransactionHeadersAndPreserveAuthentication() {
        for (String path : new String[] {"/api/v1/auth/login", "/api/v1/masking/batch", "/admin-api/v1/jobs"}) {
            String authorization = "Bearer " + validToken("user-1001");
            ServerWebExchange[] holder = new ServerWebExchange[1];
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .header("x-internal-call", "forged")
                    .header("x-ApEx-Seata-Xid", "forged")
                    .header("X-Apex-Seata-Key", "forged")
                    .header("TX_XID", "forged")
                    .header("Cookie", "session=retained")
                    .build());
            filter.filter(exchange, capturingChain(holder)).block();
            HttpHeaders headers = holder[0].getRequest().getHeaders();
            assertThat(headers.getFirst("X-Internal-Call")).isNull();
            assertThat(headers.getFirst("X-Apex-Seata-Xid")).isNull();
            assertThat(headers.getFirst("X-Apex-Seata-Key")).isNull();
            assertThat(headers.getFirst("TX_XID")).isNull();
            assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo(authorization);
            assertThat(headers.getFirst("Cookie")).isEqualTo("session=retained");
        }
    }

    @Nested
    @DisplayName("公开路径放行")
    class PublicPathTests {

        @Test
        @DisplayName("公开路径无 Token 也应放行")
        void shouldPassPublicPathWithoutToken() {
            ServerWebExchange[] holder = new ServerWebExchange[1];
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/health").build());

            filter.filter(exchange, capturingChain(holder)).block();

            assertThat(holder[0]).isNotNull();
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("认证中心匿名入口无 Token 也应放行")
        void shouldPassAuthCenterAnonymousPaths() {
            for (String path : new String[] {
                "/auth/v1/login",
                "/auth/v1/refresh",
                "/auth/v1/mfa/verify",
                "/auth/v2/password-recovery/requests",
                "/auth/v2/password-recovery/confirmations",
                "/.well-known/jwks.json"
            }) {
                ServerWebExchange[] holder = new ServerWebExchange[1];
                MockServerWebExchange exchange = MockServerWebExchange.from(
                        MockServerHttpRequest.post(path).build());

                filter.filter(exchange, capturingChain(holder)).block();

                assertThat(holder[0]).as("匿名路径应放行: " + path).isNotNull();
                assertThat(exchange.getResponse().getStatusCode()).isNull();
            }
        }

        @Test
        @DisplayName("认证中心受保护端点无 Token 应返回 401")
        void shouldRejectAuthCenterProtectedPaths() {
            for (String path : new String[] {
                "/auth/v1/logout/1", "/auth/v1/sessions/online", "/api/v1/mfa/setup", "/api/mfa/totp/enable"
            }) {
                MockServerWebExchange exchange = MockServerWebExchange.from(
                        MockServerHttpRequest.post(path).build());

                filter.filter(exchange, noopChain()).block();

                assertThat(exchange.getResponse().getStatusCode())
                        .as("受保护路径应 401: " + path)
                        .isEqualTo(HttpStatus.UNAUTHORIZED);
            }
        }
    }

    @Nested
    @DisplayName("Token 校验")
    class TokenValidationTests {

        @Test
        @DisplayName("受保护路径缺失 Authorization 应返回 401")
        void shouldReturn401WhenTokenMissing() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").build());

            filter.filter(exchange, noopChain()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Token 非法应返回 401（TOKEN_EXPIRED 收敛）")
        void shouldReturn401WhenTokenInvalid() {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt")
                    .build());

            filter.filter(exchange, noopChain()).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("合法 Token 应验签成功并写入 Claims 属性")
        void shouldVerifyAndStoreClaims() {
            ServerWebExchange[] holder = new ServerWebExchange[1];
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken("user-1001"))
                    .build());

            filter.filter(exchange, capturingChain(holder)).block();

            assertThat(exchange.getResponse().getStatusCode()).isNull();
            assertThat(holder[0].getAttributes()).containsKey(JwtSignatureFilter.ATTR_CLAIMS);
        }
    }

    @Nested
    @DisplayName("常量约定")
    class ConstantTests {

        @Test
        @DisplayName("Claims 属性键名与错误码应符合约定")
        void shouldHaveExpectedConstants() {
            assertThat(JwtSignatureFilter.ATTR_CLAIMS).isEqualTo("gateway.verified.claims");
            assertThat(ResultCode.FORBIDDEN.getCode()).isEqualTo(403);
        }
    }
}
