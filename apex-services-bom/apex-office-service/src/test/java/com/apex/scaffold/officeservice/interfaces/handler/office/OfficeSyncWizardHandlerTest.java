package com.apex.scaffold.officeservice.interfaces.handler.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.office.application.OfficeOrganizationSyncService;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.officeservice.interfaces.dto.office.organization.SyncPreflightRequest;
import com.apex.scaffold.officeservice.interfaces.security.AuthenticatedUser;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import jakarta.validation.Validation;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("办公组织同步向导租户与参数接线测试")
class OfficeSyncWizardHandlerTest {

    @Mock
    private OfficeOrganizationSyncService organizationSyncService;

    private OfficeSyncWizardHandler handler;

    @BeforeEach
    void setUp() {
        RequestHandlerUtil requestHandlerUtil =
                new RequestHandlerUtil(Validation.buildDefaultValidatorFactory().getValidator());
        handler = new OfficeSyncWizardHandler(organizationSyncService, requestHandlerUtil);
    }

    @Test
    @DisplayName("预检使用安全上下文租户调用服务，不接受请求头租户")
    void preflight_usesTrustedTenantFromPrincipal() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "open-1", "t1");
        ServerRequest request = mockRequest(principal, requestWithPlatform("FEISHU"));
        when(organizationSyncService.preflight("t1", OfficePlatform.FEISHU)).thenReturn(Mono.empty());

        StepVerifier.create(handler.preflight(request)).verifyComplete();

        verify(organizationSyncService).preflight("t1", OfficePlatform.FEISHU);
    }

    @Test
    @DisplayName("principal 不是已认证用户时拒绝并返回错误响应")
    void preflight_rejectsNonAuthenticatedPrincipal() {
        Principal anonymous = () -> "anonymous";
        ServerRequest request = Mockito.mock(ServerRequest.class);
        Mockito.doReturn(Mono.just(anonymous)).when(request).principal();

        StepVerifier.create(handler.preflight(request))
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .verifyComplete();

        verify(organizationSyncService, Mockito.never()).preflight(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("未知平台返回参数错误而不是 500")
    void preflight_rejectsUnknownPlatform() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "open-1", "t1");
        ServerRequest request = mockRequest(principal, requestWithPlatform("SLACK"));

        StepVerifier.create(handler.preflight(request))
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .verifyComplete();

        verify(organizationSyncService, Mockito.never()).preflight(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("空白平台被 Bean Validation 拦截")
    void preflight_rejectsBlankPlatform() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "open-1", "t1");
        ServerRequest request = mockRequest(principal, requestWithPlatform(" "));

        StepVerifier.create(handler.preflight(request))
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .verifyComplete();

        verify(organizationSyncService, Mockito.never()).preflight(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("重试接口使用安全上下文租户并解析路径中的任务 ID")
    void retryTask_usesTrustedTenantAndParsesTaskId() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "open-1", "t1");
        ServerRequest request = Mockito.mock(ServerRequest.class);
        Mockito.doReturn(Mono.just(principal)).when(request).principal();
        when(request.pathVariable("taskId")).thenReturn("9");
        when(organizationSyncService.retrySyncTask("t1", 9L)).thenReturn(Mono.empty());

        StepVerifier.create(handler.retryTask(request)).verifyComplete();

        verify(organizationSyncService).retrySyncTask("t1", 9L);
    }

    @Test
    @DisplayName("重试接口传入非数字任务 ID 时返回参数错误而不是 500")
    void retryTask_rejectsNonNumericTaskId() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "open-1", "t1");
        ServerRequest request = Mockito.mock(ServerRequest.class);
        Mockito.doReturn(Mono.just(principal)).when(request).principal();
        when(request.pathVariable("taskId")).thenReturn("abc");

        StepVerifier.create(handler.retryTask(request))
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .verifyComplete();

        verify(organizationSyncService, Mockito.never()).retrySyncTask(Mockito.any(), Mockito.any());
    }

    private ServerRequest mockRequest(Principal principal, SyncPreflightRequest body) {
        ServerRequest request = Mockito.mock(ServerRequest.class);
        Mockito.doReturn(Mono.just(principal)).when(request).principal();
        when(request.bodyToMono(SyncPreflightRequest.class)).thenReturn(Mono.just(body));
        return request;
    }

    private SyncPreflightRequest requestWithPlatform(String platform) {
        SyncPreflightRequest request = new SyncPreflightRequest();
        request.setPlatform(platform);
        return request;
    }
}
