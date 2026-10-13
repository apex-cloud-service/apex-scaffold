package com.apex.scaffold.workflowservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.context.TenantContext;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link IdentityRelayAuthenticationFilter} 单元测试.
 *
 * <p>覆盖网关身份中继过滤器的核心分支：缺失用户 ID 头、非法用户 ID 头（数字解析失败）、
 * 合法用户 ID 携带或不携带租户头的场景，验证只有可信且合法的网关注入头才会构建
 * Spring Security 认证上下文与响应式租户上下文。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@DisplayName("IdentityRelayAuthenticationFilter 单元测试")
class IdentityRelayAuthenticationFilterTest {

    private final IdentityRelayAuthenticationFilter filter = new IdentityRelayAuthenticationFilter();

    /**
     * 执行过滤器并捕获注入到响应式上下文中的认证主体与租户 ID。
     *
     * @param request 模拟请求
     * @return 捕获结果（认证主体可能为 null）
     */
    private CapturedContexts filterAndCapture(MockServerHttpRequest request) {
        AtomicReference<Authentication> authRef = new AtomicReference<>();
        AtomicReference<String> tenantRef = new AtomicReference<>();

        WebFilterChain chain = exchange -> Mono.deferContextual(contextView -> {
            tenantRef.set(contextView.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null));
            return ReactiveSecurityContextHolder.getContext()
                    .map(SecurityContext::getAuthentication)
                    .doOnNext(authRef::set)
                    .then();
        });

        StepVerifier.create(filter.filter(MockServerWebExchange.from(request), chain))
                .verifyComplete();

        return new CapturedContexts(authRef.get(), tenantRef.get());
    }

    @Test
    @DisplayName("缺失 X-User-Id - 不写入任何认证信息直接放行")
    void shouldPassThroughWhenUserIdMissing() {
        CapturedContexts captured =
                filterAndCapture(MockServerHttpRequest.get("/x").build());

        assertThat(captured.authentication()).isNull();
        assertThat(captured.tenantId()).isNull();
    }

    @Test
    @DisplayName("X-User-Id 非法 - 数字解析失败时不写入认证信息")
    void shouldPassThroughWhenUserIdNotNumeric() {
        CapturedContexts captured = filterAndCapture(MockServerHttpRequest.get("/x")
                .header(IdentityRelayAuthenticationFilter.HEADER_USER_ID, "abc")
                .build());

        assertThat(captured.authentication()).isNull();
        assertThat(captured.tenantId()).isNull();
    }

    @Test
    @DisplayName("合法用户与租户头 - 同时写入认证与租户上下文")
    void shouldBuildAuthenticationAndTenant() {
        CapturedContexts captured = filterAndCapture(MockServerHttpRequest.get("/x")
                .header(IdentityRelayAuthenticationFilter.HEADER_USER_ID, " 100 ")
                .header(IdentityRelayAuthenticationFilter.HEADER_TENANT_ID, "default")
                .header(IdentityRelayAuthenticationFilter.HEADER_CLIENT_TYPE, "web")
                .build());

        assertThat(captured.authentication()).isNotNull();
        assertThat(captured.authentication().getPrincipal()).isEqualTo(new AuthenticatedUser(100L, null, "default"));
        assertThat(captured.authentication().getDetails()).isEqualTo("web");
        assertThat(captured.tenantId()).isEqualTo("default");
    }

    @Test
    @DisplayName("合法用户但无租户头 - 仅写入认证上下文")
    void shouldBuildAuthenticationWithoutTenant() {
        CapturedContexts captured = filterAndCapture(MockServerHttpRequest.get("/x")
                .header(IdentityRelayAuthenticationFilter.HEADER_USER_ID, "100")
                .build());

        assertThat(captured.authentication()).isNotNull();
        assertThat(captured.tenantId()).isNull();
    }

    /**
     * 过滤器执行后从响应式上下文中捕获的信息。
     *
     * @param authentication 认证主体，未注入时为 null
     * @param tenantId 租户 ID，未注入时为 null
     */
    private record CapturedContexts(Authentication authentication, String tenantId) {}
}
