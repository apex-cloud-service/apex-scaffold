package com.apex.scaffold.officeservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("IdentityRelayAuthenticationFilter 单元测试")
class IdentityRelayAuthenticationFilterTest {

    private IdentityRelayAuthenticationFilter filter;
    private WebFilterChain chain;
    private AtomicReference<Authentication> capturedAuth;

    @BeforeEach
    void setUp() {
        filter = new IdentityRelayAuthenticationFilter();
        chain = mock(WebFilterChain.class);
        capturedAuth = new AtomicReference<>();
        when(chain.filter(any())).thenAnswer(invocation -> ReactiveSecurityContextHolder.getContext()
                .doOnNext(ctx -> capturedAuth.set(ctx.getAuthentication()))
                .then());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ServerWebExchange exchange(HttpHeaders headers) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/office/status")
                .headers(headers)
                .build();
        return MockServerWebExchange.from(request);
    }

    @Nested
    @DisplayName("filter")
    class FilterTest {

        @Test
        @DisplayName("无 X-User-Id 请求头时透传且不写入认证")
        void noUserIdHeader() {
            StepVerifier.create(filter.filter(exchange(new HttpHeaders()), chain))
                    .verifyComplete();
            assertThat(capturedAuth.get()).isNull();
        }

        @Test
        @DisplayName("X-User-Id 非数字时透传且不写入认证")
        void nonNumericUserId() {
            HttpHeaders headers = new HttpHeaders();
            headers.add(IdentityRelayAuthenticationFilter.HEADER_USER_ID, "abc");

            StepVerifier.create(filter.filter(exchange(headers), chain)).verifyComplete();
            assertThat(capturedAuth.get()).isNull();
        }

        @Test
        @DisplayName("合法 X-User-Id 时写入认证上下文")
        void validUserId() {
            HttpHeaders headers = new HttpHeaders();
            headers.add(IdentityRelayAuthenticationFilter.HEADER_USER_ID, " 100 ");
            headers.add(IdentityRelayAuthenticationFilter.HEADER_CLIENT_TYPE, "WEB");

            StepVerifier.create(filter.filter(exchange(headers), chain)).verifyComplete();

            Authentication auth = capturedAuth.get();
            assertThat(auth).isNotNull();
            assertThat(auth.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
            AuthenticatedUser principal = (AuthenticatedUser) auth.getPrincipal();
            assertThat(principal.userId()).isEqualTo(100L);
            assertThat(auth.getDetails()).isEqualTo("WEB");
        }

        @Test
        @DisplayName("携带租户头时安装租户上下文")
        void withTenantHeader() {
            HttpHeaders headers = new HttpHeaders();
            headers.add(IdentityRelayAuthenticationFilter.HEADER_USER_ID, "100");
            headers.add(IdentityRelayAuthenticationFilter.HEADER_TENANT_ID, "tenant-1");

            AtomicReference<String> tenantSeen = new AtomicReference<>();
            when(chain.filter(any()))
                    .thenAnswer(invocation -> Mono.defer(() -> {
                        tenantSeen.set(TenantContext.currentTenantId().orElse(null));
                        return Mono.empty();
                    }));

            StepVerifier.create(filter.filter(exchange(headers), chain)).verifyComplete();
            assertThat(tenantSeen.get()).isEqualTo("tenant-1");
        }

        @Test
        @DisplayName("无租户头时不安装租户上下文")
        void withoutTenantHeader() {
            HttpHeaders headers = new HttpHeaders();
            headers.add(IdentityRelayAuthenticationFilter.HEADER_USER_ID, "100");

            AtomicReference<String> tenantSeen = new AtomicReference<>("unset");
            when(chain.filter(any()))
                    .thenAnswer(invocation -> Mono.defer(() -> {
                        tenantSeen.set(TenantContext.currentTenantId().orElse(null));
                        return Mono.empty();
                    }));

            StepVerifier.create(filter.filter(exchange(headers), chain)).verifyComplete();
            assertThat(tenantSeen.get()).isNull();
        }
    }
}
