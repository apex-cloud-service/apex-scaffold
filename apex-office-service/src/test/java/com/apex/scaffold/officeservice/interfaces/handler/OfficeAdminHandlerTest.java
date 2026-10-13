package com.apex.scaffold.officeservice.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.application.OfficeAdminService;
import com.apex.scaffold.office.application.OfficeAppCommand;
import com.apex.scaffold.office.domain.OfficeIdentityBinding;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.OfficePlatformApp;
import com.apex.scaffold.officeservice.interfaces.assembler.OfficeAssembler;
import com.apex.scaffold.officeservice.interfaces.dto.office.OfficeAppRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.OfficeAppResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.OfficeBindingResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.OfficeBindingReviewRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("OfficeAdminHandler 单元测试")
class OfficeAdminHandlerTest {

    private OfficeAdminService service;
    private OfficeAssembler assembler;
    private OfficeAdminHandler handler;

    @BeforeEach
    void setUp() {
        service = mock(OfficeAdminService.class);
        assembler = mock(OfficeAssembler.class);
        handler = new OfficeAdminHandler(service, assembler);
    }

    private OfficePlatformApp app() {
        return OfficePlatformApp.builder().platform(OfficePlatform.FEISHU).build();
    }

    private OfficeIdentityBinding binding() {
        return OfficeIdentityBinding.builder().platform(OfficePlatform.FEISHU).build();
    }

    @Nested
    @DisplayName("listApps")
    class ListAppsTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(service.listApps()).thenReturn(Mono.just(List.of(app())));
            when(assembler.toAppResponse(any(OfficePlatformApp.class))).thenReturn(mock(OfficeAppResponse.class));

            StepVerifier.create(handler.listApps(MockServerRequest.builder().build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("空列表返回 HTTP 200")
        void emptyList() {
            when(service.listApps()).thenReturn(Mono.just(List.of()));

            StepVerifier.create(handler.listApps(MockServerRequest.builder().build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时以错误结束")
        void serviceError() {
            when(service.listApps()).thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.listApps(MockServerRequest.builder().build()))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalStateException.class))
                    .verify();
        }
    }

    @Nested
    @DisplayName("getApp")
    class GetAppTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(service.getApp(OfficePlatform.FEISHU)).thenReturn(Mono.just(app()));
            when(assembler.toAppResponse(any(OfficePlatformApp.class))).thenReturn(mock(OfficeAppResponse.class));

            StepVerifier.create(handler.getApp(MockServerRequest.builder()
                            .pathVariable("platform", "feishu")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("非法平台参数时以错误结束（PARAM_ERROR）")
        void invalidPlatform() {
            StepVerifier.create(handler.getApp(MockServerRequest.builder()
                            .pathVariable("platform", "unknown")
                            .build()))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(BusinessException.class);
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                    })
                    .verify();
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("服务异常时以错误结束")
        void serviceError() {
            when(service.getApp(OfficePlatform.FEISHU)).thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.getApp(MockServerRequest.builder()
                            .pathVariable("platform", "feishu")
                            .build()))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalStateException.class))
                    .verify();
        }
    }

    @Nested
    @DisplayName("createApp")
    class CreateAppTest {
        private OfficeAppRequest request() {
            return OfficeAppRequest.builder()
                    .appId("app1")
                    .secretReference("ref1")
                    .redirectUri("http://cb")
                    .enabled(true)
                    .build();
        }

        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(assembler.toCommand(any(OfficeAppRequest.class))).thenReturn(new OfficeAppCommand());
            when(service.createApp(eq(OfficePlatform.FEISHU), any(OfficeAppCommand.class)))
                    .thenReturn(Mono.just(app()));
            when(assembler.toAppResponse(any(OfficePlatformApp.class))).thenReturn(mock(OfficeAppResponse.class));

            StepVerifier.create(handler.createApp(MockServerRequest.builder()
                            .pathVariable("platform", "feishu")
                            .body(Mono.just(request()))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("非法平台参数时以错误结束（PARAM_ERROR）")
        void invalidPlatform() {
            StepVerifier.create(handler.createApp(MockServerRequest.builder()
                            .pathVariable("platform", "unknown")
                            .body(Mono.just(request()))))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(BusinessException.class))
                    .verify();
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("服务异常时以错误结束")
        void serviceError() {
            when(assembler.toCommand(any(OfficeAppRequest.class))).thenReturn(new OfficeAppCommand());
            when(service.createApp(eq(OfficePlatform.FEISHU), any(OfficeAppCommand.class)))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.createApp(MockServerRequest.builder()
                            .pathVariable("platform", "feishu")
                            .body(Mono.just(request()))))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalStateException.class))
                    .verify();
        }
    }

    @Nested
    @DisplayName("updateApp")
    class UpdateAppTest {
        private OfficeAppRequest request() {
            return OfficeAppRequest.builder()
                    .appId("app1")
                    .secretReference("ref1")
                    .redirectUri("http://cb")
                    .enabled(true)
                    .build();
        }

        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(assembler.toCommand(any(OfficeAppRequest.class))).thenReturn(new OfficeAppCommand());
            when(service.updateApp(eq(OfficePlatform.FEISHU), any(OfficeAppCommand.class)))
                    .thenReturn(Mono.just(app()));
            when(assembler.toAppResponse(any(OfficePlatformApp.class))).thenReturn(mock(OfficeAppResponse.class));

            StepVerifier.create(handler.updateApp(MockServerRequest.builder()
                            .pathVariable("platform", "feishu")
                            .body(Mono.just(request()))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("非法平台参数时以错误结束（PARAM_ERROR）")
        void invalidPlatform() {
            StepVerifier.create(handler.updateApp(MockServerRequest.builder()
                            .pathVariable("platform", "unknown")
                            .body(Mono.just(request()))))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(BusinessException.class))
                    .verify();
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("服务异常时以错误结束")
        void serviceError() {
            when(assembler.toCommand(any(OfficeAppRequest.class))).thenReturn(new OfficeAppCommand());
            when(service.updateApp(eq(OfficePlatform.FEISHU), any(OfficeAppCommand.class)))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.updateApp(MockServerRequest.builder()
                            .pathVariable("platform", "feishu")
                            .body(Mono.just(request()))))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalStateException.class))
                    .verify();
        }
    }

    @Nested
    @DisplayName("deleteApp")
    class DeleteAppTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(service.deleteApp(OfficePlatform.FEISHU, 3)).thenReturn(Mono.just(true));

            StepVerifier.create(handler.deleteApp(MockServerRequest.builder()
                            .pathVariable("platform", "feishu")
                            .queryParam("version", "3")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("缺少 version 参数时传 null 仍返回 HTTP 200")
        void missingVersion() {
            when(service.deleteApp(OfficePlatform.FEISHU, null)).thenReturn(Mono.just(true));

            StepVerifier.create(handler.deleteApp(MockServerRequest.builder()
                            .pathVariable("platform", "feishu")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("非法平台参数时以错误结束（PARAM_ERROR）")
        void invalidPlatform() {
            StepVerifier.create(handler.deleteApp(MockServerRequest.builder()
                            .pathVariable("platform", "unknown")
                            .build()))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(BusinessException.class))
                    .verify();
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("version 非数字时以 NumberFormatException 结束")
        void invalidVersion() {
            StepVerifier.create(handler.deleteApp(MockServerRequest.builder()
                            .pathVariable("platform", "feishu")
                            .queryParam("version", "abc")
                            .build()))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(NumberFormatException.class))
                    .verify();
        }

        @Test
        @DisplayName("服务异常时以错误结束")
        void serviceError() {
            when(service.deleteApp(OfficePlatform.FEISHU, 3)).thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.deleteApp(MockServerRequest.builder()
                            .pathVariable("platform", "feishu")
                            .queryParam("version", "3")
                            .build()))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalStateException.class))
                    .verify();
        }
    }

    @Nested
    @DisplayName("listBindings")
    class ListBindingsTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(service.listBindings(OfficePlatform.FEISHU, 1, 20)).thenReturn(Mono.just(List.of(binding())));
            when(assembler.toBindingResponse(any(OfficeIdentityBinding.class)))
                    .thenReturn(mock(OfficeBindingResponse.class));

            StepVerifier.create(handler.listBindings(MockServerRequest.builder()
                            .queryParam("platform", "feishu")
                            .queryParam("page", "1")
                            .queryParam("size", "20")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("缺省分页参数时使用默认值返回 HTTP 200")
        void defaultPaging() {
            when(service.listBindings(OfficePlatform.FEISHU, 1, 20)).thenReturn(Mono.just(List.of()));

            StepVerifier.create(handler.listBindings(MockServerRequest.builder()
                            .queryParam("platform", "feishu")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("缺少 platform 参数时以错误结束（PARAM_ERROR）")
        void missingPlatform() {
            StepVerifier.create(handler.listBindings(MockServerRequest.builder().build()))
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
            StepVerifier.create(handler.listBindings(MockServerRequest.builder()
                            .queryParam("platform", "unknown")
                            .build()))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(BusinessException.class))
                    .verify();
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("服务异常时以错误结束")
        void serviceError() {
            when(service.listBindings(OfficePlatform.FEISHU, 1, 20))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.listBindings(MockServerRequest.builder()
                            .queryParam("platform", "feishu")
                            .build()))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalStateException.class))
                    .verify();
        }
    }

    @Nested
    @DisplayName("listBindingsPage")
    class ListBindingsPageTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(service.listBindingsPage(OfficePlatform.FEISHU, "PENDING", 1, 20))
                    .thenReturn(Mono.just(PageResponse.of(List.of(binding()), 1L, 1, 20)));
            when(assembler.toBindingResponse(any(OfficeIdentityBinding.class)))
                    .thenReturn(mock(OfficeBindingResponse.class));

            StepVerifier.create(handler.listBindingsPage(MockServerRequest.builder()
                            .queryParam("platform", "feishu")
                            .queryParam("status", "PENDING")
                            .queryParam("page", "1")
                            .queryParam("size", "20")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("缺省参数时 status 为 null 且使用默认分页返回 HTTP 200")
        void defaultParams() {
            when(service.listBindingsPage(OfficePlatform.FEISHU, null, 1, 20))
                    .thenReturn(Mono.just(PageResponse.of(List.of(), 0L, 1, 20)));

            StepVerifier.create(handler.listBindingsPage(MockServerRequest.builder()
                            .queryParam("platform", "feishu")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("缺少 platform 参数时以错误结束（PARAM_ERROR）")
        void missingPlatform() {
            StepVerifier.create(
                            handler.listBindingsPage(MockServerRequest.builder().build()))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(BusinessException.class))
                    .verify();
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("服务异常时以错误结束")
        void serviceError() {
            when(service.listBindingsPage(OfficePlatform.FEISHU, null, 1, 20))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.listBindingsPage(MockServerRequest.builder()
                            .queryParam("platform", "feishu")
                            .build()))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalStateException.class))
                    .verify();
        }
    }

    @Nested
    @DisplayName("reviewBinding")
    class ReviewBindingTest {
        private OfficeBindingReviewRequest review() {
            return OfficeBindingReviewRequest.builder()
                    .approved(true)
                    .version(0)
                    .build();
        }

        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(service.reviewBinding(100L, 0, true)).thenReturn(Mono.just(true));

            StepVerifier.create(handler.reviewBinding(MockServerRequest.builder()
                            .pathVariable("id", "100")
                            .body(Mono.just(review()))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("id 非数字时以 NumberFormatException 结束")
        void invalidId() {
            assertThrows(
                    NumberFormatException.class,
                    () -> handler.reviewBinding(MockServerRequest.builder()
                            .pathVariable("id", "abc")
                            .body(Mono.just(review()))));
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("服务异常时以错误结束")
        void serviceError() {
            when(service.reviewBinding(100L, 0, true)).thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.reviewBinding(MockServerRequest.builder()
                            .pathVariable("id", "100")
                            .body(Mono.just(review()))))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalStateException.class))
                    .verify();
        }
    }

    @Nested
    @DisplayName("unbind")
    class UnbindTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(service.unbind(100L, 5)).thenReturn(Mono.just(true));

            StepVerifier.create(handler.unbind(MockServerRequest.builder()
                            .pathVariable("id", "100")
                            .queryParam("version", "5")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("缺少 version 参数时传 null 仍返回 HTTP 200")
        void missingVersion() {
            when(service.unbind(100L, null)).thenReturn(Mono.just(true));

            StepVerifier.create(handler.unbind(MockServerRequest.builder()
                            .pathVariable("id", "100")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("id 非数字时以 NumberFormatException 结束")
        void invalidId() {
            assertThrows(
                    NumberFormatException.class,
                    () -> handler.unbind(MockServerRequest.builder()
                            .pathVariable("id", "abc")
                            .build()));
            verifyNoInteractions(service);
        }

        @Test
        @DisplayName("服务异常时以错误结束")
        void serviceError() {
            when(service.unbind(100L, 5)).thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.unbind(MockServerRequest.builder()
                            .pathVariable("id", "100")
                            .queryParam("version", "5")
                            .build()))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(IllegalStateException.class))
                    .verify();
        }
    }
}
