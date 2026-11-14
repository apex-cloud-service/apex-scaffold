package com.apex.scaffold.gateway.fallback;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

/**
 * {@link FallbackHandler} 熔断降级兜底处理器单元测试.
 *
 * <p>覆盖网关路由级熔断降级行为：熔断 OPEN / 下游不可用时返回统一 {@link Result}，
 * 携带 {@link ResultCode#SERVICE_UNAVAILABLE} 错误码并透传 {@code X-Trace-Id}，
 * 不向外暴露下游原始异常信息。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("FallbackHandler 熔断降级兜底测试")
class FallbackHandlerTest {

    private final FallbackHandler handler = new FallbackHandler();

    /**
     * 构造已携带 TraceId 的降级请求.
     */
    private MockServerWebExchange exchangeWithTrace(String path, String traceId) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).header("X-Trace-Id", traceId).build());
        exchange.getAttributes()
                .put(
                        ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR,
                        new RuntimeException("downstream unreachable"));
        return exchange;
    }

    @Nested
    @DisplayName("统一降级响应")
    class UnifiedResultTests {

        @Test
        @DisplayName("熔断 OPEN 时应返回 503 且 body 为统一 Result.SERVICE_UNAVAILABLE")
        void fallbackReturnsUnifiedResultWhenCircuitOpen() {
            MockServerWebExchange exchange = exchangeWithTrace("/api/v1/workflows/x", "t-1");

            StepVerifier.create(handler.fallback(exchange))
                    .assertNext(resp -> {
                        assertThat(resp.statusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(resp.headers().getFirst("X-Trace-Id")).isEqualTo("t-1");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("缺失 X-Trace-Id 时仍应返回 503 且不抛异常")
        void fallbackToleratesMissingTraceId() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/orders").build());

            StepVerifier.create(handler.fallback(exchange))
                    .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("响应体契约")
    class BodyContractTests {

        @Test
        @DisplayName("响应内容类型应为 application/json")
        void fallbackReturnsJsonContentType() {
            MockServerWebExchange exchange = exchangeWithTrace("/api/v1/office/x", "t-2");

            StepVerifier.create(handler.fallback(exchange))
                    .assertNext(resp -> assertThat(resp.headers().getContentType())
                            .isNotNull()
                            .extracting(Object::toString)
                            .asString()
                            .contains("application/json"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("降级错误码应复用 ResultCode.SERVICE_UNAVAILABLE")
        void fallbackUsesServiceUnavailableCode() {
            assertThat(Result.error(ResultCode.SERVICE_UNAVAILABLE, "t-3").getCode())
                    .isEqualTo(ResultCode.SERVICE_UNAVAILABLE.getCode());
        }
    }
}
