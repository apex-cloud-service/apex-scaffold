package com.apex.scaffold.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.apex.scaffold.application.office.OfficeApplicationService;
import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.infrastructure.office.spi.OfficeOAuthClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OfficeHandlerTest {
    @Test
    void loginEndpointsArePublicButStatusIsProtected() {
        assertThat(com.apex.scaffold.common.constant.SystemConstants.PUBLIC_PATHS)
                .contains("/api/v1/office/login", "/api/v1/office/callback")
                .doesNotContain("/api/v1/office/status", "/api/v1/office/**");
    }

    @Test
    void missingStateMustNotExchangeOrReturnPlatformToken() {
        OfficeApplicationService service = mock(OfficeApplicationService.class);
        OfficeOAuthClient client = mock(OfficeOAuthClient.class);
        when(client.platform()).thenReturn(OfficePlatform.FEISHU);
        when(client.exchange("code")).thenReturn(Mono.just("platform-secret"));
        OfficeHandler handler = new OfficeHandler(
                service,
                List.of(client),
                new com.apex.scaffold.infrastructure.office.config.OfficePlatformProperties());
        StepVerifier.create(handler.callback(MockServerRequest.builder()
                        .queryParam("platform", "FEISHU")
                        .queryParam("code", "code")
                        .build()))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(400))
                .verifyComplete();
        verify(client, never()).exchange(anyString());
    }

    @Test
    void callbackUsesServerTenantInsteadOfRequestTenant() {
        OfficeApplicationService service = mock(OfficeApplicationService.class);
        com.apex.scaffold.infrastructure.office.config.OfficePlatformProperties properties =
                new com.apex.scaffold.infrastructure.office.config.OfficePlatformProperties();
        properties.setEnabled(true);
        properties.setLoginTenantId("trusted-tenant");
        when(service.login(eq(OfficePlatform.FEISHU), eq("code"), eq("state"), any(), anyString()))
                .thenReturn(Mono.deferContextual(context -> {
                    assertThat(com.apex.scaffold.common.context.TenantContext.currentTenantId())
                            .contains("trusted-tenant");
                    return Mono.empty();
                }));
        OfficeHandler handler = new OfficeHandler(service, List.of(), properties);
        StepVerifier.create(handler.callback(MockServerRequest.builder()
                        .queryParam("platform", "FEISHU")
                        .queryParam("code", "code")
                        .queryParam("state", "state")
                        .queryParam("tenantId", "attacker-tenant")
                        .build()))
                .verifyComplete();
    }
}
