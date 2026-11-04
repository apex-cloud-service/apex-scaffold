package com.apex.scaffold.common.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 内部接口过滤器测试：放行公开路径、拒匿名/伪造/重放内部调用。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("内部接口签名过滤器")
class InternalAuthWebFilterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private InternalAuthProperties properties() {
        InternalAuthProperties properties = new InternalAuthProperties();
        properties.setEnabled(true);
        properties.setSecret(SECRET);
        properties.setServiceName("apex-system-service");
        return properties;
    }

    private InternalAuthWebFilter filter(InternalAuthProperties properties) {
        return new InternalAuthWebFilter(
                properties, new InternalServiceSigner(properties.getSecret(), Duration.ofSeconds(60), CLOCK), false);
    }

    @Test
    @DisplayName("EMQX 钩子路径默认豁免签名（由设备凭据约束）")
    void mqttHookPathsExemptByDefault() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/internal/v1/iot/mqtt/auth").build());

        StepVerifier.create(filter(properties()).filter(exchange, e -> Mono.empty()))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("非内部路径直接放行")
    void nonInternalPathPasses() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users").build());

        StepVerifier.create(filter(properties()).filter(exchange, e -> Mono.empty()))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("匿名内部调用返回 401")
    void anonymousInternalRejected() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/v1/tenants/t-1/status").build());

        StepVerifier.create(filter(properties()).filter(exchange, e -> Mono.empty()))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("签名正确时放行")
    void signedInternalPasses() {
        InternalAuthProperties properties = properties();
        InternalServiceSigner signer = new InternalServiceSigner(SECRET, Duration.ofSeconds(60), CLOCK);
        Map<String, String> headers =
                signer.sign("apex-system-service", "GET", "/internal/v1/tenants/t-1/status", null);
        MockServerHttpRequest request = MockServerHttpRequest.get("/internal/v1/tenants/t-1/status")
                .header(InternalServiceSigner.HEADER_SERVICE, headers.get(InternalServiceSigner.HEADER_SERVICE))
                .header(InternalServiceSigner.HEADER_TIMESTAMP, headers.get(InternalServiceSigner.HEADER_TIMESTAMP))
                .header(InternalServiceSigner.HEADER_NONCE, headers.get(InternalServiceSigner.HEADER_NONCE))
                .header(InternalServiceSigner.HEADER_SIGNATURE, headers.get(InternalServiceSigner.HEADER_SIGNATURE))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter(properties).filter(exchange, e -> Mono.empty()))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("启用但未配置密钥时返回 503，避免误以为受保护")
    void unconfiguredSecretRejectsWith503() {
        InternalAuthProperties properties = new InternalAuthProperties();
        properties.setEnabled(true);
        properties.setSecret("");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/v1/overview").build());

        StepVerifier.create(filter(properties).filter(exchange, e -> Mono.empty()))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("显式关闭时跳过校验（仅限本地开发）")
    void disabledSkipsValidation() {
        InternalAuthProperties properties = new InternalAuthProperties();
        properties.setEnabled(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/v1/overview").build());

        StepVerifier.create(filter(properties).filter(exchange, e -> Mono.empty()))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
