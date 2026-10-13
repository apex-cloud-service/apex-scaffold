package com.apex.scaffold.officeservice.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.application.OfficeApplicationService;
import com.apex.scaffold.office.application.dto.LoginResponse;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.infrastructure.config.OfficePlatformProperties;
import com.apex.scaffold.office.infrastructure.spi.OfficeOAuthClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("OfficeHandler 单元测试")
class OfficeHandlerTest {

    private OfficeApplicationService service;
    private List<OfficeOAuthClient> clients;
    private OfficePlatformProperties properties;
    private OfficeHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = mock(OfficeApplicationService.class);
        clients = mock(List.class);
        properties = mock(OfficePlatformProperties.class);
        handler = new OfficeHandler(service, clients, properties);
    }

    @Nested
    @DisplayName("status")
    class StatusTest {
        @Test
        @DisplayName("happy path 返回平台名称列表 HTTP 200")
        void happyPath() {
            OfficeOAuthClient feishu = mock(OfficeOAuthClient.class);
            OfficeOAuthClient dingtalk = mock(OfficeOAuthClient.class);
            when(feishu.platform()).thenReturn(OfficePlatform.FEISHU);
            when(dingtalk.platform()).thenReturn(OfficePlatform.DINGTALK);
            when(clients.stream()).thenReturn(List.of(feishu, dingtalk).stream());

            StepVerifier.create(handler.status(MockServerRequest.builder().build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("空客户端列表返回 HTTP 200")
        void emptyClients() {
            when(clients.stream()).thenReturn(List.<OfficeOAuthClient>of().stream());

            StepVerifier.create(handler.status(MockServerRequest.builder().build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("start")
    class StartTest {
        @BeforeEach
        void enabled() {
            when(properties.isEnabled()).thenReturn(true);
            when(properties.getLoginTenantId()).thenReturn("tenant-1");
        }

        @Test
        @DisplayName("happy path 返回授权 URL HTTP 200")
        void happyPath() {
            when(service.authorizationUrl(OfficePlatform.FEISHU)).thenReturn(Mono.just("http://auth"));

            StepVerifier.create(handler.start(MockServerRequest.builder()
                            .queryParam("platform", "feishu")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("缺少 platform 参数时以错误结束（PARAM_ERROR）")
        void missingPlatform() {
            StepVerifier.create(handler.start(MockServerRequest.builder().build()))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(BusinessException.class);
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                    })
                    .verify();
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("非法 platform 参数时以错误结束（PARAM_ERROR）")
        void invalidPlatform() {
            StepVerifier.create(handler.start(MockServerRequest.builder()
                            .queryParam("platform", "unknown")
                            .build()))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(BusinessException.class))
                    .verify();
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("租户未启用时以错误结束（LOGIN_FAILED）")
        void tenantDisabled() {
            when(properties.isEnabled()).thenReturn(false);

            StepVerifier.create(handler.start(MockServerRequest.builder()
                            .queryParam("platform", "feishu")
                            .build()))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(BusinessException.class);
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.LOGIN_FAILED.getCode());
                    })
                    .verify();
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("loginTenantId 为空时以错误结束（LOGIN_FAILED）")
        void tenantIdBlank() {
            when(properties.getLoginTenantId()).thenReturn("  ");

            StepVerifier.create(handler.start(MockServerRequest.builder()
                            .queryParam("platform", "feishu")
                            .build()))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(BusinessException.class))
                    .verify();
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("服务异常时以错误结束")
        void serviceError() {
            when(service.authorizationUrl(OfficePlatform.FEISHU))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.start(MockServerRequest.builder()
                            .queryParam("platform", "feishu")
                            .build()))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalStateException.class))
                    .verify();
        }
    }

    @Nested
    @DisplayName("callback")
    class CallbackTest {
        @BeforeEach
        void enabled() {
            when(properties.isEnabled()).thenReturn(true);
            when(properties.getLoginTenantId()).thenReturn("tenant-1");
        }

        @Test
        @DisplayName("happy path 返回登录响应 HTTP 200")
        void happyPath() {
            when(service.login(eq(OfficePlatform.FEISHU), eq("code1"), eq("state1"), any(), any()))
                    .thenReturn(Mono.just(
                            LoginResponse.builder().accessToken("token").build()));

            StepVerifier.create(handler.callback(MockServerRequest.builder()
                            .queryParam("platform", "feishu")
                            .queryParam("code", "code1")
                            .queryParam("state", "state1")
                            .header("X-Device-Id", "device-1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("code 为空时返回 HTTP 400")
        void blankCode() {
            StepVerifier.create(handler.callback(MockServerRequest.builder()
                            .queryParam("platform", "feishu")
                            .queryParam("code", "")
                            .queryParam("state", "state1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("state 为空时返回 HTTP 400")
        void blankState() {
            StepVerifier.create(handler.callback(MockServerRequest.builder()
                            .queryParam("platform", "feishu")
                            .queryParam("code", "code1")
                            .queryParam("state", "")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("platform 缺失时以错误结束（PARAM_ERROR）")
        void missingPlatform() {
            StepVerifier.create(handler.callback(MockServerRequest.builder()
                            .queryParam("code", "code1")
                            .queryParam("state", "state1")
                            .build()))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(BusinessException.class))
                    .verify();
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("租户未启用时以错误结束（LOGIN_FAILED）")
        void tenantDisabled() {
            when(properties.isEnabled()).thenReturn(false);

            StepVerifier.create(handler.callback(MockServerRequest.builder()
                            .queryParam("platform", "feishu")
                            .queryParam("code", "code1")
                            .queryParam("state", "state1")
                            .build()))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(BusinessException.class);
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.LOGIN_FAILED.getCode());
                    })
                    .verify();
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("服务异常时以错误结束")
        void serviceError() {
            when(service.login(eq(OfficePlatform.FEISHU), eq("code1"), eq("state1"), any(), any()))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.callback(MockServerRequest.builder()
                            .queryParam("platform", "feishu")
                            .queryParam("code", "code1")
                            .queryParam("state", "state1")
                            .build()))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalStateException.class))
                    .verify();
        }
    }
}
