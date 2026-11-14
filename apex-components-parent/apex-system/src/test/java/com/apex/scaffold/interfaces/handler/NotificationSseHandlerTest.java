package com.apex.scaffold.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.infrastructure.notification.NotificationService;
import com.apex.scaffold.interfaces.security.AuthenticatedUser;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import reactor.test.StepVerifier;

class NotificationSseHandlerTest {
    private final NotificationSseHandler handler = new NotificationSseHandler(
            new NotificationService(new SimpleMeterRegistry()), mock(MessageUtils.class), mock(Validator.class));

    @Test
    void rejectsPrincipalWithoutTrustedTenantDespiteClientTenant() {
        var request = MockServerRequest.builder()
                .header("X-Tenant-Id", "tenant-a")
                .queryParam("tenantId", "tenant-a")
                .principal(new UsernamePasswordAuthenticationToken("42", null, List.of()))
                .build();
        StepVerifier.create(handler.stream(request))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void rejectsAuthenticatedAccountWithMissingTenant() {
        var request = MockServerRequest.builder()
                .principal(new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(42L, "same-open-id", null), null, List.of()))
                .build();
        StepVerifier.create(handler.stream(request))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void rejectsMissingAccountEvenWhenOpenIdExists() {
        var request = MockServerRequest.builder()
                .principal(new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(null, "same-open-id", "tenant-a"), null, List.of()))
                .build();
        StepVerifier.create(handler.stream(request))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void acceptsTrustedAccountWithoutOpenId() {
        var request = MockServerRequest.builder()
                .principal(new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(42L, null, "tenant-a"), null, List.of()))
                .build();
        StepVerifier.create(handler.stream(request))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(200))
                .verifyComplete();
    }
}
