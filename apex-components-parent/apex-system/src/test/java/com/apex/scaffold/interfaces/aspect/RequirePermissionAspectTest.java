package com.apex.scaffold.interfaces.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.service.PermissionService;
import com.apex.scaffold.common.annotation.Logical;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.PermissionEntity;
import com.apex.scaffold.interfaces.security.AuthenticatedUser;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RequirePermissionAspectTest {

    private final PermissionService permissionService = mock(PermissionService.class);
    private final RequirePermissionAspect aspect = new RequirePermissionAspect(permissionService);

    @Test
    void adminMustHaveExplicitPermission() throws Throwable {
        when(permissionService.getPermissionsByUserId(7L)).thenReturn(Mono.just(List.of()));
        ProceedingJoinPoint point = joinPoint(Mono.class, Mono.just("value"));
        deny(mono(point, "single", authenticated("trusted", "ROLE_ADMIN")), ResultCode.FORBIDDEN);
        verify(point, never()).proceed();
    }

    @Test
    void emptyPermissionDeclarationMustBeForbidden() throws Throwable {
        ProceedingJoinPoint point = joinPoint(Mono.class, Mono.just("value"));
        deny(mono(point, "empty", authenticated("trusted", "ROLE_USER")), ResultCode.FORBIDDEN);
        verify(point, never()).proceed();
        verifyNoInteractions(permissionService);
    }

    @Test
    void synchronousReturnMustBeForbiddenWithoutExecutingTarget() throws Throwable {
        ProceedingJoinPoint point = joinPoint(String.class, "value");
        assertThatThrownBy(() -> aspect.around(point, annotation("single")))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode())
                        .isEqualTo(ResultCode.FORBIDDEN.getCode()));
        verify(point, never()).proceed();
    }

    @Test
    void unauthenticatedTokenMustBeUnauthorized() throws Throwable {
        Authentication auth =
                new UsernamePasswordAuthenticationToken(new AuthenticatedUser(7L, "open-id", "trusted"), null);
        rejectAuthentication(auth);
    }

    @Test
    void anonymousTokenMustBeUnauthorized() throws Throwable {
        rejectAuthentication(
                new AnonymousAuthenticationToken("key", "open-id", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @Test
    void untrustedPrincipalMustBeUnauthorized() throws Throwable {
        rejectAuthentication(new UsernamePasswordAuthenticationToken(
                "open-id", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    void absentAuthenticationMustBeUnauthorized() throws Throwable {
        ProceedingJoinPoint point = joinPoint(Mono.class, Mono.just("value"));
        deny((Mono<?>) aspect.around(point, annotation("single")), ResultCode.UNAUTHORIZED);
        verify(point, never()).proceed();
    }

    @Test
    void nullAuthenticationMustBeUnauthorized() throws Throwable {
        ProceedingJoinPoint point = joinPoint(Mono.class, Mono.just("value"));
        deny(
                ((Mono<?>) aspect.around(point, annotation("single")))
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                                Mono.just(new SecurityContextImpl()))),
                ResultCode.UNAUTHORIZED);
        verify(point, never()).proceed();
    }

    @Test
    void missingTrustedTenantMustBeForbidden() throws Throwable {
        ProceedingJoinPoint point = joinPoint(Mono.class, Mono.just("value"));
        deny(mono(point, "single", authenticated(" ", "ROLE_USER")), ResultCode.FORBIDDEN);
        verifyNoInteractions(permissionService);
        verify(point, never()).proceed();
    }

    @Test
    void missingUserIdMustBeUnauthorized() throws Throwable {
        rejectAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(null, "open-id", "trusted"),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    void emptyPermissionPublisherMustBeForbiddenForMonoAndFlux() throws Throwable {
        when(permissionService.getPermissionsByUserId(7L)).thenReturn(Mono.empty());
        ProceedingJoinPoint monoPoint = joinPoint(Mono.class, Mono.just("value"));
        deny(mono(monoPoint, "single", authenticated("trusted", "ROLE_USER")), ResultCode.FORBIDDEN);
        ProceedingJoinPoint fluxPoint = joinPoint(Flux.class, Flux.just("value"));
        StepVerifier.create(((Flux<?>) aspect.around(fluxPoint, annotation("single")))
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                authenticated("trusted", "ROLE_USER"))))
                .expectErrorSatisfies(error -> assertCode(error, ResultCode.FORBIDDEN))
                .verify();
        verify(monoPoint, never()).proceed();
        verify(fluxPoint, never()).proceed();
    }

    @Test
    void trustedTenantMustReachPermissionQueryAndMonoTargetAtEachSubscription() throws Throwable {
        when(permissionService.getPermissionsByUserId(7L))
                .thenAnswer(invocation -> Mono.deferContextual(context -> {
                    assertThat(context.get(TenantContext.REACTOR_CONTEXT_KEY).toString())
                            .isEqualTo("trusted");
                    return Mono.just(List.of(permission("read")));
                }));
        ProceedingJoinPoint point = joinPoint(
                Mono.class,
                Mono.deferContextual(context ->
                        Mono.just(context.get(TenantContext.REACTOR_CONTEXT_KEY).toString())));
        Mono<Object> result = mono(point, "single", authenticated("trusted", "ROLE_USER"))
                .contextWrite(TenantContext.withTenant("forged"));
        verifyNoInteractions(permissionService);
        StepVerifier.create(result.repeat(1)).expectNext("trusted", "trusted").verifyComplete();
        verify(point, org.mockito.Mockito.times(2)).proceed();
    }

    @Test
    void trustedTenantMustReachFluxTarget() throws Throwable {
        when(permissionService.getPermissionsByUserId(7L)).thenReturn(Mono.just(List.of(permission("read"))));
        ProceedingJoinPoint point = joinPoint(
                Flux.class,
                Flux.deferContextual(context ->
                        Flux.just(context.get(TenantContext.REACTOR_CONTEXT_KEY).toString())));
        StepVerifier.create(((Flux<?>) aspect.around(point, annotation("single")))
                        .contextWrite(
                                ReactiveSecurityContextHolder.withAuthentication(authenticated("trusted", "ROLE_USER")))
                        .contextWrite(TenantContext.withTenant("forged")))
                .assertNext(value -> assertThat(value).isEqualTo("trusted"))
                .verifyComplete();
    }

    @Test
    void andRequiresAllCodesWhileOrAllowsOneCode() throws Throwable {
        when(permissionService.getPermissionsByUserId(7L)).thenReturn(Mono.just(List.of(permission("read"))));
        ProceedingJoinPoint point = joinPoint(Mono.class, Mono.just("value"));
        deny(mono(point, "and", authenticated("trusted", "ROLE_USER")), ResultCode.FORBIDDEN);
        verify(point, never()).proceed();
        StepVerifier.create(mono(point, "or", authenticated("trusted", "ROLE_USER")))
                .expectNext("value")
                .verifyComplete();
    }

    @Test
    void permissionQueryFailureMustNotExecuteTarget() throws Throwable {
        when(permissionService.getPermissionsByUserId(7L))
                .thenReturn(Mono.error(new IllegalStateException("unavailable")));
        ProceedingJoinPoint point = joinPoint(Mono.class, Mono.just("value"));
        StepVerifier.create(mono(point, "single", authenticated("trusted", "ROLE_USER")))
                .expectErrorMessage("unavailable")
                .verify();
        verify(point, never()).proceed();
    }

    @Test
    void propagatesTargetFailuresWithoutDuplicatingSensitiveLogs() throws Throwable {
        when(permissionService.getPermissionsByUserId(7L)).thenReturn(Mono.just(List.of(permission("read"))));
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(RequirePermissionAspect.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (Class<?> returnType : List.of(Mono.class, Flux.class)) {
                ProceedingJoinPoint point = joinPoint(returnType, null);
                IllegalStateException failure = new IllegalStateException("password=secret-value");
                when(point.proceed()).thenThrow(failure);
                org.reactivestreams.Publisher<?> result =
                        (org.reactivestreams.Publisher<?>) aspect.around(point, annotation("single"));
                StepVerifier.create(Flux.from(result)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                        authenticated("trusted", "ROLE_USER"))))
                        .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                        .verify();
            }
            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private void rejectAuthentication(Authentication authentication) throws Throwable {
        ProceedingJoinPoint point = joinPoint(Mono.class, Mono.just("value"));
        deny(mono(point, "single", authentication), ResultCode.UNAUTHORIZED);
        verify(point, never()).proceed();
        verifyNoInteractions(permissionService);
    }

    private Authentication authenticated(String tenant, String role) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(7L, "open-id", tenant), null, List.of(new SimpleGrantedAuthority(role)));
    }

    @SuppressWarnings("unchecked")
    private Mono<Object> mono(ProceedingJoinPoint point, String method, Authentication authentication)
            throws Exception {
        return ((Mono<Object>) aspect.around(point, annotation(method)))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private void deny(Mono<?> result, ResultCode code) {
        StepVerifier.create(result)
                .expectErrorSatisfies(error -> assertCode(error, code))
                .verify();
    }

    private void assertCode(Throwable error, ResultCode code) {
        assertThat(error).isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getCode())
                .isEqualTo(code.getCode()));
    }

    private PermissionEntity permission(String code) {
        PermissionEntity permission = new PermissionEntity();
        permission.setCode(code);
        return permission;
    }

    private ProceedingJoinPoint joinPoint(Class<?> returnType, Object result) throws Throwable {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getReturnType()).thenReturn(returnType);
        when(point.getSignature()).thenReturn(signature);
        org.mockito.Mockito.lenient().when(point.proceed()).thenReturn(result);
        return point;
    }

    private RequirePermission annotation(String method) throws NoSuchMethodException {
        return SecuredMethods.class.getDeclaredMethod(method).getAnnotation(RequirePermission.class);
    }

    private static final class SecuredMethods {
        @RequirePermission("read")
        private void single() {}

        @RequirePermission(" , , ")
        private void empty() {}

        @RequirePermission(value = " read, write,read ", logical = Logical.AND)
        private void and() {}

        @RequirePermission(value = "read,write", logical = Logical.OR)
        private void or() {}
    }
}
