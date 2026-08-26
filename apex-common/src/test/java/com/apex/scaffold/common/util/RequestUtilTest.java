package com.apex.scaffold.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

/**
 * {@link RequestUtil} 的单元测试类
 *
 * <p>测试客户端真实IP获取逻辑，覆盖代理场景、直连场景和异常边界条件。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("RequestUtil 单元测试")
class RequestUtilTest {

    @Nested
    @DisplayName("getClientIp 方法")
    class GetClientIpTests {

        /**
         * 执行 {@code shouldReturnFirstForwardedIpWhenHeaderExists} 操作。
         */
        @Test
        @DisplayName("X-Forwarded-For 存在时优先返回第一个IP")
        void shouldReturnFirstForwardedIpWhenHeaderExists() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                    .header("X-Forwarded-For", "192.168.1.100, 10.0.0.1, 172.16.0.1")
                    .build();

            assertThat(RequestUtil.getClientIp(request)).isEqualTo("192.168.1.100");
        }

        /**
         * 执行 {@code shouldReturnSingleForwardedIp} 操作。
         */
        @Test
        @DisplayName("X-Forwarded-For 为单个IP时直接返回")
        void shouldReturnSingleForwardedIp() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                    .header("X-Forwarded-For", "8.8.8.8")
                    .build();

            assertThat(RequestUtil.getClientIp(request)).isEqualTo("8.8.8.8");
        }

        /**
         * 执行 {@code shouldFallbackToXRealIpWhenForwardedForIsEmpty} 操作。
         */
        @Test
        @DisplayName("X-Forwarded-For 为空时回退到 X-Real-IP")
        void shouldFallbackToXRealIpWhenForwardedForIsEmpty() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                    .header("X-Forwarded-For", "")
                    .header("X-Real-IP", "10.10.10.10")
                    .build();

            assertThat(RequestUtil.getClientIp(request)).isEqualTo("10.10.10.10");
        }

        /**
         * 执行 {@code shouldFallbackToRemoteAddressWhenNoProxyHeaders} 操作。
         */
        @Test
        @DisplayName("代理头均不存在时回退到 remoteAddress")
        void shouldFallbackToRemoteAddressWhenNoProxyHeaders() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                    .remoteAddress(new java.net.InetSocketAddress("127.0.0.1", 8080))
                    .build();

            assertThat(RequestUtil.getClientIp(request)).isEqualTo("127.0.0.1");
        }

        /**
         * 执行 {@code shouldReturnUnknownWhenAllSourcesUnavailable} 操作。
         */
        @Test
        @DisplayName("所有来源均不可用时返回 unknown")
        void shouldReturnUnknownWhenAllSourcesUnavailable() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();

            assertThat(RequestUtil.getClientIp(request)).isEqualTo("unknown");
        }

        /**
         * 执行 {@code shouldReturnXRealIpWhenOnlyPresent} 操作。
         */
        @Test
        @DisplayName("仅 X-Real-IP 存在时直接返回")
        void shouldReturnXRealIpWhenOnlyPresent() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                    .header("X-Real-IP", "172.16.0.1")
                    .build();

            assertThat(RequestUtil.getClientIp(request)).isEqualTo("172.16.0.1");
        }
    }
}
