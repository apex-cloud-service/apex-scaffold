package com.apex.scaffold.interfaces.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.interfaces.security.AuthenticatedUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

@DisplayName("TenantAspect tenant trust boundary")
class TenantAspectTest {

    private final TenantAspect aspect = new TenantAspect();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("认证用户租户与请求租户一致时设置上下文并执行")
    void appliesTrustedTenant() throws Throwable {
        ServerRequest request = request("tenant-a");
        request.exchange().getAttributes().put("apex.authenticatedUser", user("tenant-a"));
        ProceedingJoinPoint joinPoint = joinPoint(request);
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            assertThat(TenantContext.currentTenantId()).contains("tenant-a");
            return Mono.empty();
        });

        aspect.applyTenant(joinPoint);

        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    @DisplayName("Flux订阅期间可读取租户并在线程切换后正常完成时清理")
    void keepsTenantContextUntilFluxCompletionAcrossThreadSwitch() throws Throwable {
        ServerRequest request = request("tenant-a");
        request.exchange().getAttributes().put("apex.authenticatedUser", user("tenant-a"));
        ProceedingJoinPoint joinPoint = joinPoint(request);
        when(joinPoint.proceed())
                .thenReturn(Flux.defer(
                        () -> Flux.just(TenantContext.currentTenantId().orElse("missing"))));

        Object result = aspect.applyTenant(joinPoint);

        assertThat(result).isInstanceOf(Flux.class);
        StepVerifier.create(((Flux<String>) result).subscribeOn(Schedulers.boundedElastic()))
                .expectNext("tenant-a")
                .verifyComplete();
        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    @DisplayName("Flux异常终止后清理租户上下文")
    void clearsTenantContextAfterFluxError() throws Throwable {
        ServerRequest request = request("tenant-a");
        request.exchange().getAttributes().put("apex.authenticatedUser", user("tenant-a"));
        ProceedingJoinPoint joinPoint = joinPoint(request);
        when(joinPoint.proceed())
                .thenReturn(Flux.defer(() -> Flux.concat(
                        Mono.just(TenantContext.currentTenantId().orElse("missing")),
                        Mono.error(new IllegalStateException("boom")))));

        Object result = aspect.applyTenant(joinPoint);

        StepVerifier.create((Flux<String>) result)
                .expectNext("tenant-a")
                .expectErrorMessage("boom")
                .verify();
        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    @DisplayName("Flux取消后清理租户上下文")
    void clearsTenantContextAfterFluxCancellation() throws Throwable {
        ServerRequest request = request("tenant-a");
        request.exchange().getAttributes().put("apex.authenticatedUser", user("tenant-a"));
        ProceedingJoinPoint joinPoint = joinPoint(request);
        when(joinPoint.proceed())
                .thenReturn(Flux.defer(() ->
                        Flux.concat(Mono.just(TenantContext.currentTenantId().orElse("missing")), Flux.never())));

        Object result = aspect.applyTenant(joinPoint);

        StepVerifier.create((Flux<String>) result)
                .expectNext("tenant-a")
                .thenCancel()
                .verify();
        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    @DisplayName("请求头为空白时使用认证用户租户")
    void usesAuthenticatedTenantWhenHeaderIsBlank() throws Throwable {
        ServerRequest request = request("   ");
        request.exchange().getAttributes().put("apex.authenticatedUser", user("tenant-a"));
        ProceedingJoinPoint joinPoint = joinPoint(request);
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            assertThat(TenantContext.currentTenantId()).contains("tenant-a");
            return Mono.empty();
        });

        aspect.applyTenant(joinPoint);
    }

    @Test
    @DisplayName("请求头租户 ID 超过 64 个字符时拒绝执行")
    void rejectsTenantIdLongerThan64Characters() throws Throwable {
        String tenantId = "a".repeat(65);
        ServerRequest request = request(tenantId);
        request.exchange().getAttributes().put("apex.authenticatedUser", user(tenantId));
        ProceedingJoinPoint joinPoint = joinPoint(request);

        assertThatThrownBy(() -> aspect.applyTenant(joinPoint)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("请求头租户 ID 包含非法字符时拒绝执行")
    void rejectsTenantIdWithIllegalCharacters() throws Throwable {
        String tenantId = "tenant/a";
        ServerRequest request = request(tenantId);
        request.exchange().getAttributes().put("apex.authenticatedUser", user(tenantId));
        ProceedingJoinPoint joinPoint = joinPoint(request);

        assertThatThrownBy(() -> aspect.applyTenant(joinPoint)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("没有 ServerRequest 或可信租户参数时拒绝执行")
    void rejectsMissingServerRequestAndTrustedTenant() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[] {"tenant-a"});

        assertThatThrownBy(() -> aspect.applyTenant(joinPoint)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("请求租户与认证用户租户不一致时拒绝执行")
    void rejectsDifferentTenant() throws Throwable {
        ServerRequest request = request("tenant-b");
        request.exchange().getAttributes().put("apex.authenticatedUser", user("tenant-a"));
        ProceedingJoinPoint joinPoint = joinPoint(request);

        assertThatThrownBy(() -> aspect.applyTenant(joinPoint)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("缺少认证用户租户时拒绝执行")
    void rejectsMissingTrustedTenant() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint(request("tenant-a"));

        assertThatThrownBy(() -> aspect.applyTenant(joinPoint)).isInstanceOf(BusinessException.class);
    }

    private static ServerRequest request(String tenantId) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/tenant-aware")
                .header(TenantAspect.TENANT_HEADER, tenantId)
                .build());
        return ServerRequest.create(exchange, java.util.List.of());
    }

    private static AuthenticatedUser user(String tenantId) {
        return new AuthenticatedUser(1L, "open-id", tenantId);
    }

    private static ProceedingJoinPoint joinPoint(ServerRequest request) {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[] {request});
        return joinPoint;
    }
}
