package com.apex.scaffold.officeservice.interfaces.off.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.office.off.application.dto.MessageTemplateDTO;
import com.apex.scaffold.office.off.application.service.OffMessageTemplateService;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("MessageTemplateHandler 单元测试")
class MessageTemplateHandlerTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();
    private final RequestHandlerUtil requestHandlerUtil = new RequestHandlerUtil(validator);

    private OffMessageTemplateService templateService;
    private MessageTemplateHandler handler;

    @BeforeEach
    void setUp() {
        templateService = mock(OffMessageTemplateService.class);
        handler = new MessageTemplateHandler(templateService, requestHandlerUtil);
    }

    private MessageTemplateDTO dto() {
        return MessageTemplateDTO.builder()
                .id(1L)
                .templateCode("CODE_1")
                .templateName("模板")
                .templateContent("内容")
                .build();
    }

    @Nested
    @DisplayName("getTemplateByCode")
    class GetTemplateByCodeTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(templateService.getTemplateByCode("CODE_1")).thenReturn(Mono.just(dto()));

            StepVerifier.create(handler.getTemplateByCode(MockServerRequest.builder()
                            .pathVariable("templateCode", "CODE_1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("模板不存在返回 HTTP 400")
        void notFound() {
            when(templateService.getTemplateByCode("CODE_1")).thenReturn(Mono.empty());

            StepVerifier.create(handler.getTemplateByCode(MockServerRequest.builder()
                            .pathVariable("templateCode", "CODE_1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(templateService.getTemplateByCode("CODE_1")).thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.getTemplateByCode(MockServerRequest.builder()
                            .pathVariable("templateCode", "CODE_1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getEnabledTemplates")
    class GetEnabledTemplatesTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(templateService.getAllEnabledTemplates()).thenReturn(Flux.just(dto()));

            StepVerifier.create(handler.getEnabledTemplates(
                            MockServerRequest.builder().build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("空列表返回 HTTP 200")
        void emptyList() {
            when(templateService.getAllEnabledTemplates()).thenReturn(Flux.empty());

            StepVerifier.create(handler.getEnabledTemplates(
                            MockServerRequest.builder().build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(templateService.getAllEnabledTemplates()).thenReturn(Flux.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.getEnabledTemplates(
                            MockServerRequest.builder().build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("createTemplate")
    class CreateTemplateTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            MessageTemplateDTO body = dto();
            when(templateService.createTemplate(any(MessageTemplateDTO.class))).thenReturn(Mono.just(dto()));

            StepVerifier.create(
                            handler.createTemplate(MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            MessageTemplateDTO body = dto();
            when(templateService.createTemplate(any(MessageTemplateDTO.class)))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(
                            handler.createTemplate(MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("updateTemplate")
    class UpdateTemplateTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            MessageTemplateDTO body = dto();
            when(templateService.updateTemplate(eq(9L), any(MessageTemplateDTO.class)))
                    .thenReturn(Mono.just(dto()));

            StepVerifier.create(handler.updateTemplate(MockServerRequest.builder()
                            .pathVariable("templateId", "9")
                            .body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("模板不存在返回 HTTP 400")
        void notFound() {
            MessageTemplateDTO body = dto();
            when(templateService.updateTemplate(eq(9L), any(MessageTemplateDTO.class)))
                    .thenReturn(Mono.empty());

            StepVerifier.create(handler.updateTemplate(MockServerRequest.builder()
                            .pathVariable("templateId", "9")
                            .body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("模板ID非法返回 HTTP 400")
        void invalidId() {
            StepVerifier.create(handler.updateTemplate(MockServerRequest.builder()
                            .pathVariable("templateId", "abc")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("deleteTemplate")
    class DeleteTemplateTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(templateService.deleteTemplate(9L)).thenReturn(Mono.empty());

            StepVerifier.create(handler.deleteTemplate(MockServerRequest.builder()
                            .pathVariable("templateId", "9")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("模板ID非法返回 HTTP 400")
        void invalidId() {
            StepVerifier.create(handler.deleteTemplate(MockServerRequest.builder()
                            .pathVariable("templateId", "abc")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }
}
