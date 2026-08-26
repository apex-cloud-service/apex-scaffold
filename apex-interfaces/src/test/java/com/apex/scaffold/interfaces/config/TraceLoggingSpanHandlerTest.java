package com.apex.scaffold.interfaces.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler.Cause;
import brave.propagation.TraceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TraceLoggingSpanHandler} 单元测试。
 *
 * <p>使用 Brave 原生 API 构造 {@link TraceContext} 和 {@link MutableSpan}，
 * 覆盖链路的四种典型链路追踪场景：</p>
 * <ol>
 *   <li><b>正常 HTTP SERVER Span</b>（如 GET /api/v1/battles 200，45ms）</li>
 *   <li><b>错误 CLIENT Span</b>（如 POST /api/v1/face/detect 500，远程连接拒绝）</li>
 *   <li><b>慢请求 Span</b>（超过 3 秒，应标记 [Span-慢]）</li>
 *   <li><b>非 HTTP Span</b>（如 RocketMQ PRODUCER，无 HTTP 标签）</li>
 * </ol>
 *
 * <p>所有测试仅验证 {@link TraceLoggingSpanHandler#end(TraceContext, MutableSpan, Cause)}
 * 返回 {@code true}（链式传递），不验证日志文本内容。</p>
 *
 * @author Apex Team
 * @see TraceLoggingSpanHandler
 */
class TraceLoggingSpanHandlerTest {

    /** 被测试的 TraceLoggingSpanHandler 实例，慢请求阈值 3000ms。 */
    private final TraceLoggingSpanHandler handler = new TraceLoggingSpanHandler(3000L);

    /**
     * 验证正常 HTTP SERVER Span 正确提取 {@code http.method}、
     * {@code http.path}、{@code http.status_code} 标签。
     *
     * <p>模拟 GET /api/v1/battles 请求返回 200，耗时 45ms 的典型场景。
     * 应输出 INFO 级别日志，标记为正常的 {@code [Span]}。</p>
     */
    @Test
    @DisplayName("正常 HTTP SERVER Span 输出 HTTP method/path/status")
    void shouldLogHttpDetailsForServerSpan() {
        TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(2L).build();
        MutableSpan span = new MutableSpan();
        span.name("GET /api/v1/battles/{battleId}");
        span.kind(brave.Span.Kind.SERVER);
        span.localServiceName("apex-service");
        span.tag("http.method", "GET");
        span.tag("http.path", "/api/v1/battles/abc123");
        span.tag("http.status_code", "200");
        span.startTimestamp(1_000_000L);
        span.finishTimestamp(1_045_000L); // 45ms

        boolean result = handler.end(context, span, Cause.FINISHED);
        assertTrue(result, "应返回 true 传递给下一个 SpanHandler");
    }

    /**
     * 验证错误 CLIENT Span 正确标记为 ERROR 级别并提取 {@code error} 标签。
     *
     * <p>模拟调用 Python 人脸分析服务 POST /api/v1/face/detect 返回 500，
     * 原因是 {@code Connection refused}。远程服务名和 IP:Port 应被正确提取。</p>
     */
    @Test
    @DisplayName("错误 Span 应以 ERROR 级别标记 [Span-错误]")
    void shouldLogErrorSpanAsError() {
        TraceContext context =
                TraceContext.newBuilder().traceId(3L).spanId(4L).parentId(2L).build();
        MutableSpan span = new MutableSpan();
        span.name("POST /api/v1/face/detect");
        span.kind(brave.Span.Kind.CLIENT);
        span.localServiceName("apex-service");
        span.remoteServiceName("python-face-analysis");
        span.remoteIp("127.0.0.1");
        span.remotePort(5001);
        span.tag("http.method", "POST");
        span.tag("http.path", "/api/v1/face/detect");
        span.tag("http.status_code", "500");
        span.tag("error", "Connection refused");
        span.startTimestamp(1_000_000L);
        span.finishTimestamp(1_100_000L); // 100ms

        boolean result = handler.end(context, span, Cause.FINISHED);
        assertTrue(result);
    }

    /**
     * 验证慢请求（超过 3 秒）正确标记为 WARN 级别并输出 {@code [Span-慢]}。
     *
     * <p>模拟列表查询耗时 4100ms 的场景，应触发慢请求阈值（3000ms）。</p>
     */
    @Test
    @DisplayName("慢请求（超过3s）应以 WARN 级别标记 [Span-慢]")
    void shouldLogSlowSpanAsWarn() {
        TraceContext context = TraceContext.newBuilder().traceId(5L).spanId(6L).build();
        MutableSpan span = new MutableSpan();
        span.name("GET /api/v1/photos/list");
        span.kind(brave.Span.Kind.SERVER);
        span.localServiceName("apex-service");
        span.tag("http.method", "GET");
        span.tag("http.path", "/api/v1/photos/list");
        span.tag("http.status_code", "200");
        span.startTimestamp(1_000_000L);
        span.finishTimestamp(5_100_000L); // 4100ms > 3s

        boolean result = handler.end(context, span, Cause.FINISHED);
        assertTrue(result);
    }

    /**
     * 验证非 HTTP Span（如消息队列 PRODUCER）在缺失 HTTP 标签时使用占位符 '-'。
     *
     * <p>模拟 RocketMQ 发送消息的 Span，没有 {@code http.method} 等标签。
     * 日志应输出 {@code http=- -} 而非 NPE 或空字符串。</p>
     */
    @Test
    @DisplayName("无 HTTP 标签时使用占位符 '-'")
    void shouldUsePlaceholderWhenNoHttpTags() {
        TraceContext context = TraceContext.newBuilder().traceId(7L).spanId(8L).build();
        MutableSpan span = new MutableSpan();
        span.name("rocketmq-send");
        span.kind(brave.Span.Kind.PRODUCER);
        span.localServiceName("apex-service");
        span.startTimestamp(1_000_000L);
        span.finishTimestamp(1_010_000L); // 10ms

        boolean result = handler.end(context, span, Cause.FINISHED);
        assertTrue(result);
    }
}
