package com.apex.scaffold.common.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 内部服务签名测试：签发、验签、过期、重放与篡改拒绝。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("内部服务签名")
class InternalServiceSignerTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("合法签名可通过校验")
    void validSignatureAccepted() {
        InternalServiceSigner signer = new InternalServiceSigner(SECRET, Duration.ofSeconds(60), fixedClock());
        Map<String, String> headers = signer.sign("apex-gateway", "GET", "/internal/v1/tenants/t-1/status", "t-1");

        assertThat(signer.verify(
                        headers.get(InternalServiceSigner.HEADER_SERVICE),
                        headers.get(InternalServiceSigner.HEADER_TIMESTAMP),
                        headers.get(InternalServiceSigner.HEADER_NONCE),
                        headers.get(InternalServiceSigner.HEADER_SIGNATURE),
                        "GET",
                        "/internal/v1/tenants/t-1/status",
                        "t-1"))
                .isTrue();
    }

    @Test
    @DisplayName("篡改路径或租户应拒绝")
    void tamperedRequestRejected() {
        InternalServiceSigner signer = new InternalServiceSigner(SECRET, Duration.ofSeconds(60), fixedClock());
        Map<String, String> headers = signer.sign("apex-gateway", "GET", "/internal/v1/tenants/t-1/status", "t-1");

        assertThat(signer.verify(
                        headers.get(InternalServiceSigner.HEADER_SERVICE),
                        headers.get(InternalServiceSigner.HEADER_TIMESTAMP),
                        headers.get(InternalServiceSigner.HEADER_NONCE),
                        headers.get(InternalServiceSigner.HEADER_SIGNATURE),
                        "GET",
                        "/internal/v1/tenants/t-2/status",
                        "t-1"))
                .isFalse();
        assertThat(signer.verify(
                        headers.get(InternalServiceSigner.HEADER_SERVICE),
                        headers.get(InternalServiceSigner.HEADER_TIMESTAMP),
                        headers.get(InternalServiceSigner.HEADER_NONCE),
                        headers.get(InternalServiceSigner.HEADER_SIGNATURE),
                        "GET",
                        "/internal/v1/tenants/t-1/status",
                        "t-2"))
                .isFalse();
    }

    @Test
    @DisplayName("重复 nonce 应作为重放拒绝")
    void replayRejected() {
        InternalServiceSigner signer = new InternalServiceSigner(SECRET, Duration.ofSeconds(60), fixedClock());
        Map<String, String> headers = signer.sign("apex-system-service", "GET", "/internal/v1/overview", null);
        boolean first = signer.verify(
                headers.get(InternalServiceSigner.HEADER_SERVICE),
                headers.get(InternalServiceSigner.HEADER_TIMESTAMP),
                headers.get(InternalServiceSigner.HEADER_NONCE),
                headers.get(InternalServiceSigner.HEADER_SIGNATURE),
                "GET",
                "/internal/v1/overview",
                null);
        boolean second = signer.verify(
                headers.get(InternalServiceSigner.HEADER_SERVICE),
                headers.get(InternalServiceSigner.HEADER_TIMESTAMP),
                headers.get(InternalServiceSigner.HEADER_NONCE),
                headers.get(InternalServiceSigner.HEADER_SIGNATURE),
                "GET",
                "/internal/v1/overview",
                null);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    @DisplayName("超出时间窗应拒绝")
    void expiredTimestampRejected() {
        Clock issued = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        InternalServiceSigner signer = new InternalServiceSigner(SECRET, Duration.ofSeconds(30), issued);
        Map<String, String> headers = signer.sign("apex-file-service", "GET", "/internal/v1/file/stats", "t-1");

        InternalServiceSigner later =
                new InternalServiceSigner(SECRET, Duration.ofSeconds(30), Clock.offset(issued, Duration.ofMinutes(5)));
        assertThat(later.verify(
                        headers.get(InternalServiceSigner.HEADER_SERVICE),
                        headers.get(InternalServiceSigner.HEADER_TIMESTAMP),
                        headers.get(InternalServiceSigner.HEADER_NONCE),
                        headers.get(InternalServiceSigner.HEADER_SIGNATURE),
                        "GET",
                        "/internal/v1/file/stats",
                        "t-1"))
                .isFalse();
    }

    @Test
    @DisplayName("未配置密钥时拒绝签名与验签")
    void unconfiguredSecretFailsClosed() {
        InternalServiceSigner signer = new InternalServiceSigner("", Duration.ofSeconds(60), fixedClock());

        assertThat(signer.configured()).isFalse();
        assertThat(signer.verify("svc", "1", "n", "s", "GET", "/internal/v1/x", null))
                .isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> signer.sign("svc", "GET", "/internal/v1/x", null))
                .isInstanceOf(IllegalStateException.class);
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    }
}
