package com.apex.scaffold.infrastructure.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.domain.model.moderation.ModerationDecision;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 百度文本审核适配器限流重试单元测试：用本地 HTTP 服务器模拟百度 QPS 限流（error_code=18）。
 *
 * @author weiqiang.ling
 * @version 1.0.0
 * @since 1.0.0
 */
class BaiduTextModerationRetryTest {

    private HttpServer server;
    private String tokenUrl;
    private String moderationUrl;
    private final AtomicInteger moderationCalls = new AtomicInteger();
    private volatile int failFirstCalls;

    /**
     * 验证 {@code setUp} 场景。
     *
     * @throws IOException 执行失败时
     */
    @BeforeEach
    void setUp() throws IOException {
        moderationCalls.set(0);
        failFirstCalls = 0;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            byte[] body = "{\"access_token\":\"tok\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/moderate", exchange -> {
            int call = moderationCalls.incrementAndGet();
            String responseBody = call <= failFirstCalls
                    ? "{\"error_code\":18,\"error_msg\":\"Open api qps request limit reached\"}"
                    : "{\"log_id\":1,\"conclusion\":\"合规\",\"conclusionType\":1}";
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        tokenUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/token";
        moderationUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/moderate";
    }

    /**
     * 验证 {@code tearDown} 场景。
     */
    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /**
     * 验证 {@code retriesOnQpsLimitAndSucceeds} 场景。
     */
    @Test
    void retriesOnQpsLimitAndSucceeds() {
        failFirstCalls = 2;
        var adapter = adapterWithRetry(3, 5);

        var result = adapter.moderate("合规文本").block();

        assertThat(result.decision()).isEqualTo(ModerationDecision.PASS);
        assertThat(moderationCalls.get()).isEqualTo(3);
    }

    /**
     * 验证 {@code failsAfterRetriesExhausted} 场景。
     */
    @Test
    void failsAfterRetriesExhausted() {
        failFirstCalls = 10;
        var adapter = adapterWithRetry(3, 5);

        assertThatThrownBy(() -> adapter.moderate("任何文本").block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("限流");
        assertThat(moderationCalls.get()).isEqualTo(4);
    }

    /**
     * 验证 {@code nonRateLimitErrorFailsImmediately} 场景。
     */
    @Test
    void nonRateLimitErrorFailsImmediately() {
        // error_code=18 之外的业务错误不重试。
        server.removeContext("/moderate");
        server.createContext("/moderate", exchange -> {
            moderationCalls.incrementAndGet();
            byte[] body =
                    "{\"error_code\":282000,\"error_msg\":\"missing required param\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        var adapter = adapterWithRetry(3, 5);

        assertThatThrownBy(() -> adapter.moderate("任何文本").block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("282000");
        assertThat(moderationCalls.get()).isEqualTo(1);
    }

    /**
     * 验证 {@code helpersClassifyRateLimitCorrectly} 场景。
     */
    @Test
    void helpersClassifyRateLimitCorrectly() {
        assertThat(BaiduTextModerationAdapter.isRateLimitedErrorCode(18)).isTrue();
        assertThat(BaiduTextModerationAdapter.isRateLimitedErrorCode(17)).isFalse();
        assertThat(BaiduTextModerationAdapter.isRetryableHttpStatus(429)).isTrue();
        assertThat(BaiduTextModerationAdapter.isRetryableHttpStatus(503)).isTrue();
        assertThat(BaiduTextModerationAdapter.isRetryableHttpStatus(500)).isFalse();
        assertThat(BaiduTextModerationAdapter.retryDelayMillis(1, 250)).isEqualTo(250L);
        assertThat(BaiduTextModerationAdapter.retryDelayMillis(3, 250)).isEqualTo(750L);
    }

    /**
     * 执行 {@code adapterWithRetry} 逻辑。
     *
     * @param maxRetryAttempts maxRetryAttempts 参数
     *
     * @param retryBaseDelayMillis retryBaseDelayMillis 参数
     *
     * @return 执行结果
     */
    private BaiduTextModerationAdapter adapterWithRetry(int maxRetryAttempts, int retryBaseDelayMillis) {
        return new BaiduTextModerationAdapter(
                "api-key", "secret-key", tokenUrl, moderationUrl, 10, maxRetryAttempts, retryBaseDelayMillis);
    }
}
