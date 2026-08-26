package com.apex.scaffold.interfaces.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.test.StepVerifier;

/**
 * 管理端静态资源安全过滤器 {@code AdminFrontendSecurityFilter} 的单元测试。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class AdminFrontendSecurityFilterTest {

    private final AdminFrontendSecurityFilter filter = new AdminFrontendSecurityFilter();
    private final WebFilterChain chain = exchange -> exchange.getResponse().setComplete();

    /**
     * 验证管理端静态资源响应被写入严格的安全响应头，覆盖下游较弱的默认
     * Content-Security-Policy，并拒绝 unsafe-inline 与 unsafe-eval。
     */
    @Test
    void addsStrictBrowserHeadersToAdminAssets() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin/index.html").build());
        WebFilterChain downstreamWithWeakerDefault = current -> {
            current.getResponse()
                    .getHeaders()
                    .set("Content-Security-Policy", "default-src 'self'; style-src 'unsafe-inline'");
            return current.getResponse().setComplete();
        };

        StepVerifier.create(filter.filter(exchange, downstreamWithWeakerDefault))
                .verifyComplete();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store, max-age=0");
        assertThat(headers.getFirst("Content-Security-Policy"))
                .contains("default-src 'none'", "script-src 'self'", "frame-ancestors 'none'");
        assertThat(headers.getFirst("Content-Security-Policy")).doesNotContain("unsafe-inline", "unsafe-eval");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    /**
     * 验证过滤器作用于 {@code /admin} 短入口，但不会拦截路径相似的
     * {@code /admin-api/**} 接口前缀。
     */
    @Test
    void appliesToShortAdminEntryButNotSimilarApiPrefix() {
        MockServerWebExchange admin =
                MockServerWebExchange.from(MockServerHttpRequest.get("/admin").build());
        MockServerWebExchange adminApi = MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin-api/v1/photo-reviews").build());

        StepVerifier.create(filter.filter(admin, chain)).verifyComplete();
        StepVerifier.create(filter.filter(adminApi, chain)).verifyComplete();

        assertThat(admin.getResponse().getHeaders().getFirst("Content-Security-Policy"))
                .isNotBlank();
        assertThat(adminApi.getResponse().getHeaders().containsKey("Content-Security-Policy"))
                .isFalse();
    }
}
