package com.apex.scaffold.interfaces.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 安全响应头注入过滤器单元测试
 *
 * <p>验证 SecurityHeaderWebFilter 的安全响应头注入功能，
 * 包括 X-Content-Type-Options、HSTS 等头信息的正确设置
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityHeaderWebFilter 安全响应头注入过滤器测试")
class SecurityHeaderWebFilterTest {

    @Mock
    private SecurityHeadersProperties securityHeadersProperties;

    @Mock
    private WebFilterChain chain;

    @InjectMocks
    private SecurityHeaderWebFilter filter;

    /**
     * 执行 {@code shouldInjectAllSecurityHeaders} 操作。
     */
    @Test
    @DisplayName("应该注入全部安全响应头")
    void shouldInjectAllSecurityHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(securityHeadersProperties.isEnabled()).thenReturn(true);
        when(securityHeadersProperties.getHstsMaxAge()).thenReturn(31536000L);
        when(securityHeadersProperties.isHstsIncludeSubdomains()).thenReturn(true);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        var headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("X-XSS-Protection")).isEqualTo("0");
        assertThat(headers.getFirst("Strict-Transport-Security")).isEqualTo("max-age=31536000; includeSubDomains");
        assertThat(headers.getFirst("Cache-Control")).isEqualTo("no-store, no-cache, must-revalidate");
        assertThat(headers.getFirst("Pragma")).isEqualTo("no-cache");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(headers.getFirst("Permissions-Policy")).isEqualTo("geolocation=(), camera=(), microphone=()");
    }

    /**
     * 执行 {@code shouldNotInjectHeadersWhenDisabled} 操作。
     */
    @Test
    @DisplayName("禁用时不应该注入任何安全头")
    void shouldNotInjectHeadersWhenDisabled() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(securityHeadersProperties.isEnabled()).thenReturn(false);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        var headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("X-Content-Type-Options")).isNull();
        assertThat(headers.getFirst("Strict-Transport-Security")).isNull();
        assertThat(headers.getFirst("Cache-Control")).isNull();
    }

    /**
     * 执行 {@code shouldInjectHstsWithSubdomains} 操作。
     */
    @Test
    @DisplayName("应该注入带子域名的 HSTS 头")
    void shouldInjectHstsWithSubdomains() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        when(securityHeadersProperties.isEnabled()).thenReturn(true);
        when(securityHeadersProperties.getHstsMaxAge()).thenReturn(31536000L);
        when(securityHeadersProperties.isHstsIncludeSubdomains()).thenReturn(true);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst("Strict-Transport-Security"))
                .isEqualTo("max-age=31536000; includeSubDomains");
    }
}
