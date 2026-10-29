package com.apex.scaffold.officeservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.annotation.Logical;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.audit.AuditLogService;
import com.apex.scaffold.common.result.Result;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OfficeAuthorizationTest {
    private final AuthenticatedUser user = new AuthenticatedUser(42L, null, "tenant-a");
    private final AtomicInteger requests = new AtomicInteger();
    private final AuditLogService audit = mock(AuditLogService.class);

    @Test
    void permissionClientUsesSingleImplicitConstructorAndContextAssembly() {
        assertThat(OfficePermissionClient.class.getDeclaredConstructors()).hasSize(1);
        assertThat(OfficePermissionClient.class.getDeclaredConstructors()[0].getDeclaredAnnotations())
                .isEmpty();
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(OfficePermissionProperties.class, this::properties);
            context.registerBean(WebClient.Builder.class, WebClient::builder);
            context.register(OfficePermissionClient.class);
            context.refresh();
            assertThat(context.getBean(OfficePermissionClient.class)).isNotNull();
        }
    }

    @Test
    void publicOfficeStatusRemainsAnonymousAndAudited() throws Throwable {
        var point = mock(org.aspectj.lang.ProceedingJoinPoint.class);
        var signature = mock(org.aspectj.lang.reflect.MethodSignature.class);
        var method = com.apex.scaffold.officeservice.interfaces.handler.OfficeHandler.class.getMethod(
                "status", org.springframework.web.reactive.function.server.ServerRequest.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(point.getArgs()).thenReturn(new Object[0]);
        when(point.proceed()).thenReturn(ServerResponse.ok().build());
        when(audit.log(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        var aspect = new OfficeSecurityAspect(client(response(0, 42, "tenant-a", "other")), audit);
        StepVerifier.create((Mono<?>) aspect.intercept(point))
                .expectNextCount(1)
                .verifyComplete();
        verify(audit)
                .log(
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.eq("SELECT"),
                        org.mockito.ArgumentMatchers.eq("OfficeHandler.status"),
                        org.mockito.ArgumentMatchers.eq("HTTP_RESPONSE_READY"),
                        any());
        assertThat(requests).hasValue(0);
    }

    @Test
    void rejectsMissingPermissionBeforeHandlerInvocation() {
        Endpoint target = new Endpoint();
        Endpoint proxy = proxy(target, client(response(0, 42, "tenant-a", "other")));
        Mono<ServerResponse> result = proxy.write();
        assertThat(requests).hasValue(0);
        assertThat(target.calls).hasValue(0);
        StepVerifier.create(authenticated(result))
                .expectError(AccessDeniedException.class)
                .verify();
        assertThat(target.calls).hasValue(0);
    }

    @Test
    void administratorWithDeclaredPermissionPassesAndFalseResultIsNotBusinessSuccess() {
        Endpoint target = new Endpoint();
        Endpoint proxy = proxy(target, client(response(0, 42, "tenant-a", "office:app:update")));
        StepVerifier.create(authenticated(proxy.write()))
                .assertNext(value -> assertThat(value.statusCode().value()).isEqualTo(200))
                .verifyComplete();
        assertThat(target.calls).hasValue(1);
        verify(audit)
                .log(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        org.mockito.ArgumentMatchers.eq("HTTP_RESPONSE_READY"),
                        any());
    }

    @Test
    void rejectsWrongUserTenantBusinessCodeAndHttpError() {
        for (String body : List.of(
                response(0, 43, "tenant-a", "office:app:update"),
                response(0, 42, "tenant-b", "office:app:update"),
                response(500, 42, "tenant-a", "office:app:update"),
                "{}")) {
            StepVerifier.create(client(body).permissions(user))
                    .expectError(AccessDeniedException.class)
                    .verify();
        }
        OfficePermissionClient failing = new OfficePermissionClient(
                properties(),
                WebClient.builder()
                        .exchangeFunction(request -> Mono.just(
                                ClientResponse.create(HttpStatus.FORBIDDEN).build())));
        StepVerifier.create(failing.permissions(user))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void missingReaderAndOtherTenantDenyWithoutRemoteRequest() {
        OfficePermissionProperties properties = properties();
        properties.getReaderTokens().clear();
        OfficePermissionClient client =
                new OfficePermissionClient(properties, WebClient.builder().exchangeFunction(request -> {
                    requests.incrementAndGet();
                    return Mono.empty();
                }));
        StepVerifier.create(client.permissions(user))
                .expectError(AccessDeniedException.class)
                .verify();
        StepVerifier.create(client.permissions(new AuthenticatedUser(42L, null, "tenant-b")))
                .expectError(AccessDeniedException.class)
                .verify();
        assertThat(requests).hasValue(0);
    }

    @Test
    void timeoutAndMissingAuthenticationDeny() {
        OfficePermissionProperties properties = properties();
        properties.setTimeout(Duration.ofMillis(10));
        OfficePermissionClient client =
                new OfficePermissionClient(properties, WebClient.builder().exchangeFunction(request -> Mono.never()));
        StepVerifier.create(client.permissions(user))
                .expectError(AccessDeniedException.class)
                .verify();
        StepVerifier.create(proxy(new Endpoint(), client).write())
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void logicalAndRequiresEveryPermission() {
        Endpoint proxy = proxy(new Endpoint(), client(response(0, 42, "tenant-a", "office:app:update")));
        StepVerifier.create(authenticated(proxy.and()))
                .expectError(AccessDeniedException.class)
                .verify();
        StepVerifier.create(authenticated(proxy.or())).expectNextCount(1).verifyComplete();
    }

    @Test
    void namespaceMustBeExplicitlyConfirmed() {
        OfficePermissionProperties properties = properties();
        properties.setSharedUserNamespace(false);
        OfficePermissionClient client =
                new OfficePermissionClient(properties, WebClient.builder().exchangeFunction(request -> {
                    throw new AssertionError("must not query");
                }));
        StepVerifier.create(client.permissions(user))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void swallowedFailureIsOnlyResponseReadyAndSensitiveMetadataIsOmitted() {
        Endpoint proxy = proxy(new Endpoint(), client(response(0, 42, "tenant-a", "office:app:update")));
        StepVerifier.create(authenticated(proxy.swallowed())).expectNextCount(1).verifyComplete();
        org.mockito.ArgumentCaptor<java.util.Map<String, Object>> details =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(audit)
                .log(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        org.mockito.ArgumentMatchers.eq("HTTP_RESPONSE_READY"),
                        details.capture());
        assertThat(details.getValue()).containsOnlyKeys("module", "description");
    }

    @Test
    void propagatedErrorIsAuditedWithoutExceptionMessage() {
        Endpoint proxy = proxy(new Endpoint(), client(response(0, 42, "tenant-a", "office:app:update")));
        StepVerifier.create(authenticated(proxy.failure()))
                .expectError(IllegalStateException.class)
                .verify();
        org.mockito.ArgumentCaptor<java.util.Map<String, Object>> details =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(audit)
                .log(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        org.mockito.ArgumentMatchers.eq("ERROR"),
                        details.capture());
        assertThat(details.getValue()).containsEntry("errorType", "IllegalStateException");
        assertThat(details.getValue().toString()).doesNotContain("sensitive-secret");
    }

    private Endpoint proxy(Endpoint target, OfficePermissionClient client) {
        when(audit.log(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new OfficeSecurityAspect(client, audit));
        return factory.getProxy();
    }

    private <T> Mono<T> authenticated(Mono<T> result) {
        return result.contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of())));
    }

    private OfficePermissionClient client(String body) {
        return new OfficePermissionClient(properties(), WebClient.builder().exchangeFunction(request -> {
            requests.incrementAndGet();
            assertThat(request.url().getPath()).isEqualTo("/auth/v1/authorization/users/42");
            assertThat(request.headers().getFirst("Authorization")).isEqualTo("Bearer reader-secret");
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .build());
        }));
    }

    private OfficePermissionProperties properties() {
        OfficePermissionProperties properties = new OfficePermissionProperties();
        properties.setSharedUserNamespace(true);
        properties.setBaseUrl("https://auth.example.test");
        properties.getReaderTokens().put("tenant-a", "reader-secret");
        return properties;
    }

    private String response(int code, long id, String tenant, String permission) {
        return "{\"code\":" + code + ",\"data\":{\"userId\":" + id + ",\"tenantId\":\"" + tenant
                + "\",\"permissions\":[\"" + permission
                + "\"],\"roles\":[{\"code\":\"ADMIN\",\"dataScope\":\"ALL\"}]}}";
    }

    public static class Endpoint {
        final AtomicInteger calls = new AtomicInteger();

        @RequirePermission("office:app:update")
        @OperationLog(module = "office", saveRequest = false, saveResponse = false)
        public Mono<ServerResponse> write() {
            calls.incrementAndGet();
            return ServerResponse.ok().bodyValue(Result.success(false));
        }

        @OperationLog(module = "office", saveRequest = false, saveResponse = false)
        public Mono<ServerResponse> swallowed() {
            return Mono.<ServerResponse>error(new IllegalStateException("sensitive-secret"))
                    .onErrorResume(error -> ServerResponse.ok().bodyValue(Result.error("failed")));
        }

        @OperationLog(module = "office", saveRequest = false, saveResponse = false)
        public Mono<ServerResponse> failure() {
            return Mono.error(new IllegalStateException("sensitive-secret"));
        }

        @RequirePermission(value = "office:app:update,office:app:delete", logical = Logical.AND)
        public Mono<ServerResponse> and() {
            return write();
        }

        @RequirePermission("office:app:update,office:app:delete")
        public Mono<ServerResponse> or() {
            return write();
        }
    }
}
