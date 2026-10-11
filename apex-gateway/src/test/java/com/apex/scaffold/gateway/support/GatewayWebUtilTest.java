package com.apex.scaffold.gateway.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.result.ResultCode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

/**
 * {@link GatewayWebUtil} 单元测试.
 *
 * <p>覆盖迁移方案 §17 验收项的"统一错误出口"契约：所有网关过滤器终止请求时，
 * 必须经 {@link GatewayWebUtil#writeError} 写出与下游单体逐字段一致的
 * {@link com.apex.scaffold.common.result.Result} JSON；同时覆盖
 * {@link GatewayWebUtil#clientIp} 在限流/日志中的客户端标识提取分支。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("GatewayWebUtil 网关统一响应工具测试")
class GatewayWebUtilTest {

    /** 从 MockServerWebExchange 响应体中读取已写出的 JSON 字符串 */
    private String readBody(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }

    @Nested
    @DisplayName("工具类实例化防护")
    class InstantiationTests {

        @Test
        @DisplayName("工具类应禁止实例化（私有构造函数抛异常）")
        void shouldPreventInstantiation() throws Exception {
            java.lang.reflect.Constructor<GatewayWebUtil> ctor = GatewayWebUtil.class.getDeclaredConstructor();
            ctor.setAccessible(true);

            assertThatThrownBy(ctor::newInstance).hasCauseInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("writeError 统一错误出口")
    class WriteErrorTests {

        @Test
        @DisplayName("由 ResultCode 写出 401：状态码 / JSON 内容类型 / 业务码一致")
        void shouldWriteUnauthorizedByResultCode() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").build());

            StepVerifier.create(GatewayWebUtil.writeError(exchange, HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

            String body = readBody(exchange);
            assertThat(body).contains("\"code\":401");
            assertThat(body).contains("\"msg\":\"未授权\"");
        }

        @Test
        @DisplayName("由 ResultCode + traceId 写出 403：traceId 必须透传")
        void shouldWriteForbiddenWithTraceId() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/admin").build());

            StepVerifier.create(GatewayWebUtil.writeError(
                            exchange, HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN, "trace-abc-123"))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            String body = readBody(exchange);
            assertThat(body).contains("\"code\":403");
            assertThat(body).contains("\"traceId\":\"trace-abc-123\"");
        }

        @Test
        @DisplayName("由 Result 对象写出 429 限流：使用自定义 Result 原样序列化")
        void shouldWriteRateLimitWithCustomResult() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").build());

            StepVerifier.create(GatewayWebUtil.writeError(
                            exchange,
                            HttpStatus.TOO_MANY_REQUESTS,
                            com.apex.scaffold.common.result.Result.error(ResultCode.RATE_LIMIT, "trace-rate-1")))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            String body = readBody(exchange);
            assertThat(body).contains("\"code\":2001");
            assertThat(body).contains("\"traceId\":\"trace-rate-1\"");
        }

        @Test
        @DisplayName("响应体应为 UTF-8 编码的中文 JSON（不出现乱码）")
        void shouldEncodeBodyAsUtf8() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").build());

            StepVerifier.create(GatewayWebUtil.writeError(exchange, HttpStatus.UNAUTHORIZED, ResultCode.TOKEN_EXPIRED))
                    .verifyComplete();

            byte[] raw = exchange.getResponse().getBodyAsString().block().getBytes(StandardCharsets.UTF_8);
            assertThat(new String(raw, StandardCharsets.UTF_8)).contains("登录已过期");
        }
    }

    @Nested
    @DisplayName("clientIp 客户端 IP 提取")
    class ClientIpTests {

        @Test
        @DisplayName("X-Forwarded-For 存在时取首段（多级代理只取最外层）")
        void shouldReturnFirstForwardedIp() {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                    .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1, 10.0.0.2")
                    .build());

            assertThat(GatewayWebUtil.clientIp(exchange)).isEqualTo("203.0.113.7");
        }

        @Test
        @DisplayName("X-Forwarded-For 为空白时回退到 X-Real-IP")
        void shouldFallbackToRealIpWhenForwardedBlank() {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                    .header("X-Forwarded-For", "   ")
                    .header("X-Real-IP", "198.51.100.9")
                    .build());

            assertThat(GatewayWebUtil.clientIp(exchange)).isEqualTo("198.51.100.9");
        }

        @Test
        @DisplayName("无代理头时回退到 socket 远端地址")
        void shouldFallbackToRemoteAddress() {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                    .remoteAddress(new InetSocketAddress("192.0.2.55", 54321))
                    .build());

            assertThat(GatewayWebUtil.clientIp(exchange)).isEqualTo("192.0.2.55");
        }

        @Test
        @DisplayName("无任何来源信息时返回 unknown 兜底")
        void shouldReturnUnknownWhenNoSource() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").build());

            assertThat(GatewayWebUtil.clientIp(exchange)).isEqualTo("unknown");
        }
    }
}
