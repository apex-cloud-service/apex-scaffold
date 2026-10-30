package com.apex.scaffold.auth.infrastructure.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.auth.config.AuthTrustedProxyProperties;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerRequest;

@DisplayName("AuthClientIpResolver 单元测试")
class AuthClientIpResolverTest {

    @Test
    @DisplayName("默认不信任任何代理时忽略伪造转发头")
    void shouldIgnoreForwardedHeadersByDefault() {
        AuthClientIpResolver resolver = resolverWith();

        assertThat(resolver.resolve(address("203.0.113.7"), List.of("1.1.1.1, 2.2.2.2"), "3.3.3.3"))
                .isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("直连地址不在可信网段时不采信转发头")
    void shouldIgnoreForwardedHeadersFromUntrustedPeer() {
        AuthClientIpResolver resolver = resolverWith("10.0.0.0/8");

        assertThat(resolver.resolve(address("203.0.113.7"), List.of("198.51.100.9"), "198.51.100.10"))
                .isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("可信代理按代理链从右向左取首个非可信地址")
    void shouldResolveFirstUntrustedAddressFromRight() {
        AuthClientIpResolver resolver = resolverWith("10.0.0.0/8");

        assertThat(resolver.resolve(address("10.0.0.5"), List.of("198.51.100.9, 10.0.0.6, 10.0.0.7"), null))
                .isEqualTo("198.51.100.9");
    }

    @Test
    @DisplayName("代理链全部为可信代理时回退到 X-Real-IP")
    void shouldFallbackToRealIpWhenChainFullyTrusted() {
        AuthClientIpResolver resolver = resolverWith("10.0.0.0/8");

        assertThat(resolver.resolve(address("10.0.0.5"), List.of("10.0.0.6"), "198.51.100.11"))
                .isEqualTo("198.51.100.11");
    }

    @Test
    @DisplayName("无可用转发头时回退到直连地址")
    void shouldFallbackToRemoteAddress() {
        AuthClientIpResolver resolver = resolverWith("10.0.0.0/8");

        assertThat(resolver.resolve(address("10.0.0.5"), null, "  ")).isEqualTo("10.0.0.5");
    }

    @Test
    @DisplayName("多级代理链中的非法地址被跳过")
    void shouldSkipIllegalChainEntries() {
        AuthClientIpResolver resolver = resolverWith("10.0.0.0/8");

        assertThat(resolver.resolve(address("10.0.0.5"), List.of("not-an-ip, 10.0.0.6"), null))
                .isEqualTo("10.0.0.5");
    }

    @Test
    @DisplayName("直连地址不可用时返回 unknown")
    void shouldReturnUnknownWithoutRemoteAddress() {
        AuthClientIpResolver resolver = resolverWith("10.0.0.0/8");

        assertThat(resolver.resolve(null, List.of("198.51.100.9"), "198.51.100.10"))
                .isEqualTo(AuthClientIpResolver.UNKNOWN_IP);
    }

    @Test
    @DisplayName("非法 CIDR 配置不匹配任何地址")
    void shouldIgnoreIllegalCidr() {
        AuthClientIpResolver resolver = resolverWith("10.0.0.0/64", "invalid-cidr");

        assertThat(resolver.resolve(address("10.0.0.5"), List.of("198.51.100.9"), null))
                .isEqualTo("10.0.0.5");
    }

    @Test
    @DisplayName("函数式请求解析命中可信代理网段")
    void shouldResolveFromServerRequest() {
        AuthClientIpResolver resolver = resolverWith("10.0.0.0/8");
        ServerRequest request = ServerRequest.create(
                MockServerWebExchange.from(MockServerHttpRequest.get("/oauth2/authorize")
                        .remoteAddress(address("10.0.0.5"))
                        .header("X-Forwarded-For", "198.51.100.9, 10.0.0.6")),
                java.util.List.of());

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.9");
    }

    private static AuthClientIpResolver resolverWith(String... cidrs) {
        AuthTrustedProxyProperties properties = new AuthTrustedProxyProperties();
        properties.setTrustedProxyCidrs(List.of(cidrs));
        return new AuthClientIpResolver(properties);
    }

    private static InetSocketAddress address(String ip) {
        return new InetSocketAddress(ip, 8080);
    }
}
