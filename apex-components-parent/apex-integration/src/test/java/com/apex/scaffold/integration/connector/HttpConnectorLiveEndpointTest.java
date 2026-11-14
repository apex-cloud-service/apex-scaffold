package com.apex.scaffold.integration.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.integration.connector.domain.connector.HttpConnector;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * HTTP 连接器真实端点联调测试（方案 M5 / 批次 E2 未验证项收口）。
 *
 * <p>以 JDK 内置 {@link HttpServer} 启动<b>真实 HTTP 服务</b>，验证连接器在真实网络栈上的
 * 行为——区别于 {@code HttpConnectorTest} 的 ExchangeFunction mock：
 * <ul>
 *   <li>真实 TCP 连接与 WebClient 响应解码；</li>
 *   <li>认证头在真实请求中确实送达服务端（服务端侧断言）；</li>
 *   <li>GET 查询参数拼装、POST JSON 体、非 2xx 状态映射为业务异常；</li>
 *   <li>healthCheck 走配置的 healthPath。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("HTTP 连接器真实端点联调测试")
class HttpConnectorLiveEndpointTest {

    private HttpServer server;

    private int port;

    /** 服务端收到的请求快照：path → (method, authorization, body)。 */
    private final Map<String, AtomicReference<String[]>> received = new ConcurrentHashMap<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        register("/api/echo", 200, "{\"ok\":true,\"source\":\"live\"}");
        register("/api/created", 201, "{\"id\":42}");
        register("/api/fail", 500, "{\"error\":\"boom\"}");
        register("/health", 200, "{\"status\":\"UP\"}");
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** 注册一个回声端点：记录请求并返回固定状态与正文。 */
    private void register(String path, int status, String body) {
        AtomicReference<String[]> snapshot = new AtomicReference<>(new String[] {"", "", ""});
        received.put(path, snapshot);
        server.createContext(path, exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            String custom = exchange.getRequestHeaders().getFirst("X-Custom");
            String requestBody;
            try (InputStream in = exchange.getRequestBody()) {
                requestBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            snapshot.set(new String[] {
                exchange.getRequestMethod(),
                auth == null ? "" : auth,
                "X-Custom=" + (custom == null ? "" : custom) + ";body=" + requestBody
            });
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    private HttpConnector connector() {
        return new HttpConnector();
    }

    private Map<String, Object> config(String path, Map<String, Object> auth) {
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("baseUrl", "http://127.0.0.1:" + port);
        config.put("path", path);
        config.put("method", "POST");
        config.put("timeoutMs", 5000);
        config.put("healthPath", "/health");
        if (auth != null) {
            config.put("auth", auth);
        }
        return config;
    }

    @Test
    @DisplayName("真实服务：bearer 认证头送达并解析 JSON 响应")
    void bearerAuthReachesRealServer() {
        Map<String, Object> auth = new java.util.HashMap<>();
        auth.put("type", "bearer");
        auth.put("secret", "live-token-123");

        StepVerifier.create(
                        connector().execute(config("/api/echo", auth), "POST /api/echo", Map.of("k", "v"), "idem-1"))
                .assertNext(payload -> assertThat(payload).containsEntry("ok", true))
                .verifyComplete();

        String[] snapshot = received.get("/api/echo").get();
        assertThat(snapshot[0]).isEqualTo("POST");
        assertThat(snapshot[1]).isEqualTo("Bearer live-token-123");
        assertThat(snapshot[2]).contains("\"k\":\"v\"");
    }

    @Test
    @DisplayName("真实服务：自定义请求头与 basic 认证送达")
    void customHeadersAndBasicAuth() {
        Map<String, Object> auth = new java.util.HashMap<>();
        auth.put("type", "basic");
        auth.put("username", "user");
        auth.put("secret", "pass");
        Map<String, Object> config = config("/api/created", auth);
        config.put("headers", Map.of("X-Custom", "custom-value"));

        StepVerifier.create(connector().execute(config, "POST /api/created", Map.of(), "idem-2"))
                .assertNext(payload -> assertThat(payload).containsEntry("id", 42))
                .verifyComplete();

        String[] snapshot = received.get("/api/created").get();
        String expected =
                "Basic " + java.util.Base64.getEncoder().encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));
        assertThat(snapshot[1]).isEqualTo(expected);
        assertThat(snapshot[2]).contains("X-Custom=custom-value");
    }

    @Test
    @DisplayName("真实服务：GET 查询参数拼装")
    void getFlattensPayloadToQuery() {
        Map<String, Object> config = config("/api/echo", null);
        StepVerifier.create(connector().execute(config, "GET /api/echo", Map.of("page", 2), "idem-3"))
                .assertNext(payload -> assertThat(payload).containsEntry("ok", true))
                .verifyComplete();
        assertThat(received.get("/api/echo").get()[0]).isEqualTo("GET");
    }

    @Test
    @DisplayName("真实服务：非 2xx 映射为业务异常")
    void serverErrorMapsToBusinessException() {
        StepVerifier.create(connector().execute(config("/api/fail", null), "POST /api/fail", Map.of(), "idem-4"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("真实服务：healthCheck 走配置的 healthPath")
    void healthCheckHitsHealthPath() {
        StepVerifier.create(connector().healthCheck(config("/api/echo", null))).verifyComplete();
        assertThat(received.get("/health").get()[0]).isEqualTo("GET");
    }

    @Test
    @DisplayName("真实服务：不可达端点以异常传播（不静默成功）")
    void unreachableEndpointPropagates() {
        Map<String, Object> config = config("/api/echo", null);
        config.put("baseUrl", "http://127.0.0.1:1");
        config.put("timeoutMs", 1500);
        StepVerifier.create(connector().execute(config, "POST /api/echo", Map.of(), "idem-5"))
                .expectError()
                .verify();
    }
}
