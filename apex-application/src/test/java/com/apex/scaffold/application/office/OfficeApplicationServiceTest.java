package com.apex.scaffold.application.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.application.service.login.LoginResponseHelper;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.office.*;
import com.apex.scaffold.domain.service.TokenService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OfficeApplicationServiceTest {
    private final OfficeOAuthClient client = mock(OfficeOAuthClient.class);
    private final OfficeState stateService = mock(OfficeState.class);
    private final OfficeRepository repository = mock(OfficeRepository.class);
    private final UserService users = mock(UserService.class);
    private final TokenService tokens = mock(TokenService.class);
    private final LoginResponseHelper responses = mock(LoginResponseHelper.class);
    private final OfficeApplicationService service =
            new OfficeApplicationService(List.of(client), stateService, repository, users, tokens, responses);
    private final OfficePlatformApp app = OfficePlatformApp.builder()
            .tenantId("tenant-a")
            .platform(OfficePlatform.FEISHU)
            .enabled(true)
            .deleted(0)
            .redirectUri("https://callback")
            .build();

    @BeforeEach
    void setup() {
        TenantContext.setTenantId("tenant-a");
        when(client.platform()).thenReturn(OfficePlatform.FEISHU);
        when(repository.findApp("tenant-a", OfficePlatform.FEISHU)).thenReturn(Mono.just(app));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createsAuthorizationUrlWithTenantBoundStateAtSubscription() {
        Mono<String> result = service.authorizationUrl(OfficePlatform.FEISHU);
        verify(repository, never()).findApp(anyString(), any());
        when(stateService.issue("tenant-a", "FEISHU", "https://callback", Duration.ofMinutes(5)))
                .thenReturn(Mono.just("issued-state"));
        when(client.authorizationUrl(app, "issued-state")).thenReturn(Mono.just("https://office/auth"));
        StepVerifier.create(result).expectNext("https://office/auth").verifyComplete();
    }

    @Test
    void missingTenantDoesNotIssueState() {
        TenantContext.clear();
        StepVerifier.create(service.authorizationUrl(OfficePlatform.FEISHU))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(stateService);
    }

    @Test
    void invalidStateMustNotContactPlatform() {
        when(stateService.consume("state", "tenant-a", "FEISHU", "https://callback"))
                .thenReturn(Mono.just(false));
        reject();
        verify(client, never()).authenticate(any(), anyString());
        verifyNoInteractions(tokens, users);
    }

    @Test
    void stateStorageFailureMustNotContactPlatform() {
        when(stateService.consume("state", "tenant-a", "FEISHU", "https://callback"))
                .thenReturn(Mono.error(new IllegalStateException("redis unavailable")));
        StepVerifier.create(login()).expectError(IllegalStateException.class).verify();
        verify(client, never()).authenticate(any(), anyString());
    }

    @Test
    void missingBindingCannotCreateUserOrIssueTokens() {
        identity();
        when(repository.findBinding("tenant-a", OfficePlatform.FEISHU, "external"))
                .thenReturn(Mono.empty());
        reject();
        verifyNoInteractions(users, tokens);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "REJECTED", "DISABLED"})
    void onlyAdministratorApprovedBindingCanLogin(String status) {
        identity();
        binding(status, "tenant-a", true, 0);
        reject();
        verifyNoInteractions(users, tokens);
    }

    @Test
    void crossTenantBindingCannotLogin() {
        identity();
        binding("APPROVED", "tenant-b", true, 0);
        reject();
        verifyNoInteractions(users, tokens);
    }

    @Test
    void disabledBindingCannotLogin() {
        identity();
        binding("APPROVED", "tenant-a", false, 0);
        reject();
        verifyNoInteractions(users, tokens);
    }

    @Test
    void deletedBindingCannotLogin() {
        identity();
        binding("APPROVED", "tenant-a", true, 1);
        reject();
        verifyNoInteractions(users, tokens);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "disabled", "deleted", "crossTenant", "missingSubject"})
    void invalidUserCannotLogin(String scenario) {
        identity();
        binding("APPROVED", "tenant-a", true, 0);
        UserEntity user = user();
        if (scenario.equals("disabled")) user.setStatus(0);
        if (scenario.equals("deleted")) user.setDeleted(1);
        if (scenario.equals("crossTenant")) user.setTenantId("tenant-b");
        if (scenario.equals("missingSubject")) user.setOpenId(null);
        when(users.getById(7L)).thenReturn(scenario.equals("missing") ? Mono.empty() : Mono.just(user));
        reject();
        verifyNoInteractions(tokens);
    }

    @Test
    void consumesStateBeforeIdentityAndStoresApexPairBeforeReturning() {
        identity();
        binding("APPROVED", "tenant-a", true, 0);
        UserEntity user = user();
        when(users.getById(7L)).thenReturn(Mono.just(user));
        when(tokens.generateAccessToken("apex-subject", "device")).thenReturn("apex-access");
        when(tokens.generateRefreshToken("apex-subject", "device")).thenReturn("apex-refresh");
        when(tokens.storeToken("apex-subject", "apex-access", "apex-refresh")).thenReturn(Mono.empty());
        when(users.updateLoginInfo(7L, "127.0.0.1")).thenReturn(Mono.just(true));
        LoginResponse response = new LoginResponse();
        response.setAccessToken("apex-access");
        response.setRefreshToken("apex-refresh");
        when(responses.build(user, "apex-access", "apex-refresh", false, null, "127.0.0.1"))
                .thenReturn(response);
        StepVerifier.create(login())
                .assertNext(value -> {
                    assertThat(value.getAccessToken()).isEqualTo("apex-access");
                    assertThat(value.getRefreshToken()).isEqualTo("apex-refresh");
                })
                .verifyComplete();
        org.mockito.InOrder order = inOrder(stateService, client, repository, users, tokens, responses);
        order.verify(repository).findApp("tenant-a", OfficePlatform.FEISHU);
        order.verify(stateService).consume("state", "tenant-a", "FEISHU", "https://callback");
        order.verify(client).authenticate(app, "code");
        order.verify(repository).findBinding("tenant-a", OfficePlatform.FEISHU, "external");
        order.verify(users).getById(7L);
        order.verify(tokens).generateAccessToken("apex-subject", "device");
        order.verify(tokens).generateRefreshToken("apex-subject", "device");
        order.verify(tokens).storeToken("apex-subject", "apex-access", "apex-refresh");
        order.verify(responses).build(user, "apex-access", "apex-refresh", false, null, "127.0.0.1");
    }

    private void identity() {
        when(stateService.consume("state", "tenant-a", "FEISHU", "https://callback"))
                .thenReturn(Mono.just(true));
        when(client.authenticate(app, "code")).thenReturn(Mono.just("external"));
    }

    private void binding(String status, String tenant, boolean enabled, int deleted) {
        when(repository.findBinding("tenant-a", OfficePlatform.FEISHU, "external"))
                .thenReturn(Mono.just(OfficeIdentityBinding.builder()
                        .tenantId(tenant)
                        .platform(OfficePlatform.FEISHU)
                        .externalUserId("external")
                        .userId(7L)
                        .status(status)
                        .enabled(enabled)
                        .deleted(deleted)
                        .build()));
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setTenantId("tenant-a");
        user.setOpenId("apex-subject");
        user.setStatus(1);
        user.setDeleted(0);
        return user;
    }

    private Mono<LoginResponse> login() {
        return service.login(OfficePlatform.FEISHU, "code", "state", "device", "127.0.0.1");
    }

    private void reject() {
        StepVerifier.create(login()).expectError(BusinessException.class).verify();
    }
}
