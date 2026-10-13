package com.apex.scaffold.authservice.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.apex.scaffold.auth.application.service.UnifiedAuthorizationQueryService;
import com.apex.scaffold.authservice.interfaces.util.RequestHandlerUtil;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UnifiedAuthorizationHandlerTest {
    @Test
    void unauthenticatedQueryIsDenied() {
        UnifiedAuthorizationQueryService service = mock(UnifiedAuthorizationQueryService.class);
        UnifiedAuthorizationHandler handler = handler(service);
        StepVerifier.create(handler.query(
                        MockServerRequest.builder().pathVariable("userId", "99").build()))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(403))
                .verifyComplete();
        verifyNoInteractions(service);
    }

    @Test
    void ordinaryUserCannotQuery() {
        UnifiedAuthorizationQueryService service = mock(UnifiedAuthorizationQueryService.class);
        var auth = new UsernamePasswordAuthenticationToken("2", "", List.of());
        auth.setDetails("center");
        StepVerifier.create(handler(service)
                        .query(MockServerRequest.builder()
                                .pathVariable("userId", "99")
                                .build())
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(403))
                .verifyComplete();
        verifyNoInteractions(service);
    }

    @Test
    void readerUsesTokenTenant() {
        UnifiedAuthorizationQueryService service = mock(UnifiedAuthorizationQueryService.class);
        RequestHandlerUtil responses = mock(RequestHandlerUtil.class);
        var data = new UnifiedAuthorizationQueryService.Authorization("center", 99L, List.of(), List.of());
        when(responses.validateAndMap(
                        any(com.apex.scaffold.authservice.interfaces.dto.UnifiedAuthorizationRequest.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(service.queryForReader("center", 1L, 99L)).thenReturn(Mono.just(data));
        when(responses.buildSuccessResponse(any()))
                .thenReturn(org.springframework.web.reactive.function.server.ServerResponse.ok()
                        .build());
        var auth = new UsernamePasswordAuthenticationToken("1", "", List.of());
        auth.setDetails("center");
        var handler = new UnifiedAuthorizationHandler(
                service,
                new MockEnvironment().withProperty("auth-center.authorization-reader-user-id", "1"),
                responses);
        StepVerifier.create(handler.query(MockServerRequest.builder()
                                .pathVariable("userId", "99")
                                .header("X-Tenant-Id", "foreign")
                                .build())
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(200))
                .verifyComplete();
        verify(service).queryForReader("center", 1L, 99L);
    }

    private UnifiedAuthorizationHandler handler(UnifiedAuthorizationQueryService service) {
        return new UnifiedAuthorizationHandler(
                service,
                new MockEnvironment().withProperty("auth-center.authorization-reader-user-id", "1"),
                mock(RequestHandlerUtil.class));
    }
}
