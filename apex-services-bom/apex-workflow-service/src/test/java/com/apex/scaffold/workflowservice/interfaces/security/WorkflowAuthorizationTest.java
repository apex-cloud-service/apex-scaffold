package com.apex.scaffold.workflowservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.annotation.Logical;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 工作流本地权限接线用例：授权快照查询与 {@link WorkflowSecurityAspect} 的拒绝、放行和降级边界。
 *
 * <p>覆盖配置无效、身份或租户不匹配、业务码失败、权限码缺失、超时、HTTP 错误，
 * 以及注解未声明权限码、AND/OR 组合和目标返回类型不受支持等路径。
 */
class WorkflowAuthorizationTest {
    private final AuthenticatedUser user = new AuthenticatedUser(42L, null, "tenant-a");
    private final AtomicInteger requests = new AtomicInteger();

    @Test
    void strictMapperAcceptsAuthRolesAndUnknownFutureFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        var snapshot = mapper.readValue(
                """
                {"tenantId":"tenant-a","userId":42,"permissions":["workflow:definition:read"],
                 "roles":[{"code":"ADMIN","dataScope":"ALL"}],"futureField":{"version":2}}
                """,
                WorkflowPermissionClient.Snapshot.class);
        assertThat(snapshot)
                .isEqualTo(new WorkflowPermissionClient.Snapshot("tenant-a", 42L, List.of("workflow:definition:read")));
    }

    @Test
    void returnsExactPermissionsForMatchingSnapshot() {
        StepVerifier.create(client(response(0, 42, "tenant-a", "\"workflow:definition:read\""))
                        .permissions(user))
                .assertNext(codes -> assertThat(codes).containsExactly("workflow:definition:read"))
                .verifyComplete();
        assertThat(requests).hasValue(1);
    }

    @Test
    void acceptsEmptyPermissionListWithoutGrantingAnything() {
        StepVerifier.create(client(response(0, 42, "tenant-a", "")).permissions(user))
                .assertNext(codes -> assertThat(codes).isEmpty())
                .verifyComplete();
        Endpoint proxy = proxy(new Endpoint(), client(response(0, 42, "tenant-a", "")));
        StepVerifier.create(authenticated(proxy.read()))
                .expectError(AccessDeniedException.class)
                .verify();
        assertThat(proxy.target.calls).hasValue(0);
    }

    @Test
    void rejectsMismatchedIdentityTenantBusinessCodeAndBlankCodes() {
        for (String body : List.of(
                response(0, 43, "tenant-a", "\"workflow:definition:read\""),
                response(0, 42, "tenant-b", "\"workflow:definition:read\""),
                response(500, 42, "tenant-a", "\"workflow:definition:read\""),
                response(0, 42, "tenant-a", "\" \""),
                "{\"code\":0}")) {
            StepVerifier.create(client(body).permissions(user))
                    .expectError(AccessDeniedException.class)
                    .verify();
        }
    }

    @Test
    void rejectsHttpErrorAndTimeout() {
        WorkflowPermissionClient forbidden = new WorkflowPermissionClient(
                properties(),
                WebClient.builder()
                        .exchangeFunction(request -> Mono.just(
                                ClientResponse.create(HttpStatus.FORBIDDEN).build())));
        StepVerifier.create(forbidden.permissions(user))
                .expectError(AccessDeniedException.class)
                .verify();

        WorkflowPermissionClient slow = new WorkflowPermissionClient(
                properties(properties -> properties.setTimeout(Duration.ofMillis(10))),
                WebClient.builder().exchangeFunction(request -> Mono.never()));
        StepVerifier.create(slow.permissions(user))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void rejectsInvalidConfigurationWithoutQueryingRemote() {
        List<Consumer<WorkflowPermissionProperties>> invalid = List.of(
                properties -> properties.setSharedUserNamespace(false),
                properties -> properties.setBaseUrl(null),
                properties -> properties.setBaseUrl("http://auth.example.test"),
                properties -> properties.setBaseUrl("https://user@auth.example.test"),
                properties -> properties.setBaseUrl("https://auth.example.test?scope=all"),
                properties -> properties.setBaseUrl("https://auth.example.test#fragment"),
                properties -> properties.setBaseUrl("https:///nohost"),
                properties -> properties.getReaderTokens().clear(),
                properties -> properties.getReaderTokens().put("tenant-a", " "),
                properties -> properties.setTimeout(null),
                properties -> properties.setTimeout(Duration.ofSeconds(-1)),
                properties -> properties.setTimeout(Duration.ZERO),
                properties -> properties.setTimeout(Duration.ofSeconds(31)));
        for (Consumer<WorkflowPermissionProperties> mutation : invalid) {
            WorkflowPermissionClient client = new WorkflowPermissionClient(
                    properties(mutation), WebClient.builder().exchangeFunction(request -> {
                        throw new AssertionError("must not query");
                    }));
            StepVerifier.create(client.permissions(user))
                    .expectError(AccessDeniedException.class)
                    .verify();
        }
        assertThat(requests).hasValue(0);
    }

    @Test
    void rejectsInvalidIdentityBeforeQueryingRemote() {
        WorkflowPermissionClient client =
                new WorkflowPermissionClient(properties(), WebClient.builder().exchangeFunction(request -> {
                    throw new AssertionError("must not query");
                }));
        for (AuthenticatedUser candidate : List.of(
                new AuthenticatedUser(null, null, "tenant-a"),
                new AuthenticatedUser(0L, null, "tenant-a"),
                new AuthenticatedUser(42L, null, null),
                new AuthenticatedUser(42L, null, " "))) {
            StepVerifier.create(client.permissions(candidate))
                    .expectError(AccessDeniedException.class)
                    .verify();
        }
        assertThat(requests).hasValue(0);
    }

    @Test
    void deniesWhenSecurityContextOrPrincipalIsMissing() {
        Endpoint proxy = proxy(new Endpoint(), client(response(0, 42, "tenant-a", "\"workflow:definition:read\"")));
        StepVerifier.create(proxy.read())
                .expectError(AccessDeniedException.class)
                .verify();
        StepVerifier.create(proxy.read()
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                new UsernamePasswordAuthenticationToken("anonymous", null, List.of()))))
                .expectError(AccessDeniedException.class)
                .verify();
        assertThat(proxy.target.calls).hasValue(0);
    }

    @Test
    void deniesUnsupportedReturnTypeSynchronously() {
        Endpoint proxy = proxy(new Endpoint(), client(response(0, 42, "tenant-a", "\"workflow:definition:read\"")));
        assertThatThrownBy(proxy::blocking).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void andRequiresEveryCodeWhileOrAcceptsAny() {
        Endpoint proxy = proxy(new Endpoint(), client(response(0, 42, "tenant-a", "\"workflow:definition:read\"")));
        StepVerifier.create(authenticated(proxy.and()))
                .expectError(AccessDeniedException.class)
                .verify();
        StepVerifier.create(authenticated(proxy.or())).expectNextCount(1).verifyComplete();
    }

    @Test
    void rejectsEmptyDeclaredCodesAndConvertsSynchronousFailure() {
        Endpoint proxy = proxy(new Endpoint(), client(response(0, 42, "tenant-a", "\"workflow:definition:read\"")));
        StepVerifier.create(authenticated(proxy.blank()))
                .expectError(AccessDeniedException.class)
                .verify();
        StepVerifier.create(authenticated(proxy.throwsSynchronously()))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void interceptsOnlyAnnotatedMethodsWithPermissionCodes() throws Throwable {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(Endpoint.class.getMethod("read"));
        when(point.getSignature()).thenReturn(signature);
        Mono<Object> handled = Mono.just("handled");
        when(point.proceed()).thenReturn(handled);
        WorkflowSecurityAspect aspect =
                new WorkflowSecurityAspect(client(response(0, 42, "tenant-a", "\"workflow:definition:read\"")));
        Object intercepted = aspect.intercept(point);
        assertThat(intercepted).isInstanceOf(Mono.class);
        StepVerifier.create(authenticated(Mono.from(org.reactivestreams.Publisher.class.cast(intercepted))))
                .assertNext(value -> assertThat(value).isEqualTo("handled"))
                .verifyComplete();
    }

    @Test
    void exposesSingleConstructorForContextAssembly() {
        assertThat(WorkflowPermissionClient.class.getDeclaredConstructors()).hasSize(1);
    }

    private Endpoint proxy(Endpoint target, WorkflowPermissionClient client) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new WorkflowSecurityAspect(client));
        Endpoint proxy = factory.getProxy();
        proxy.target = target;
        return proxy;
    }

    private <T> Mono<T> authenticated(Mono<T> result) {
        return result.contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of())));
    }

    private WorkflowPermissionClient client(String body) {
        return new WorkflowPermissionClient(properties(), WebClient.builder().exchangeFunction(request -> {
            requests.incrementAndGet();
            assertThat(request.url().getPath()).isEqualTo("/auth/v1/authorization/users/42");
            assertThat(request.headers().getFirst("Authorization")).isEqualTo("Bearer reader-secret");
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .build());
        }));
    }

    private WorkflowPermissionProperties properties() {
        return properties(properties -> {});
    }

    private WorkflowPermissionProperties properties(Consumer<WorkflowPermissionProperties> mutation) {
        WorkflowPermissionProperties properties = new WorkflowPermissionProperties();
        properties.setSharedUserNamespace(true);
        properties.setBaseUrl("https://auth.example.test");
        properties.getReaderTokens().put("tenant-a", "reader-secret");
        mutation.accept(properties);
        return properties;
    }

    private String response(int code, long id, String tenant, String permissions) {
        return "{\"code\":" + code + ",\"data\":{\"userId\":" + id + ",\"tenantId\":\"" + tenant
                + "\",\"permissions\":[" + permissions + "]}}";
    }

    /** 供切面代理调用的目标对象，记录实际执行次数。 */
    public static class Endpoint {
        final AtomicInteger calls = new AtomicInteger();
        Endpoint target = this;

        @RequirePermission("workflow:definition:read")
        public Mono<String> read() {
            calls.incrementAndGet();
            return Mono.just("read");
        }

        @RequirePermission(value = "workflow:definition:read,workflow:definition:edit", logical = Logical.AND)
        public Mono<String> and() {
            return read();
        }

        @RequirePermission("workflow:definition:read,workflow:definition:edit")
        public Mono<String> or() {
            return read();
        }

        @RequirePermission(" , ")
        public Mono<String> blank() {
            return read();
        }

        @RequirePermission("workflow:definition:read")
        public String blocking() {
            return "blocking";
        }

        @RequirePermission("workflow:definition:read")
        public Mono<String> throwsSynchronously() {
            throw new IllegalStateException("boom");
        }
    }
}
