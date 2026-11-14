package com.apex.scaffold.officeservice.interfaces.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.officeservice.interfaces.dto.office.approval.InitiateApprovalRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("RequestHandlerUtil 单元测试")
class RequestHandlerUtilTest {

    private Validator validator;
    private RequestHandlerUtil util;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        util = new RequestHandlerUtil(validator);
    }

    @Nested
    @DisplayName("handleRequestBody / validateAndMap")
    class ValidateMapTest {

        @Test
        @DisplayName("合法请求体返回原对象")
        void validBody() {
            InitiateApprovalRequest body = InitiateApprovalRequest.builder()
                    .platform(OfficePlatform.FEISHU)
                    .appId(1L)
                    .processDefinitionId("tpl-1")
                    .initiatorId("user-1")
                    .formData("{}")
                    .build();

            StepVerifier.create(util.validateAndMap(
                            MockServerRequest.builder().body(Mono.just(body)), InitiateApprovalRequest.class))
                    .expectNext(body)
                    .verifyComplete();
        }

        @Test
        @DisplayName("非法请求体抛出 PARAM_ERROR")
        void invalidBody() {
            StepVerifier.create(util.validateAndMap(
                            MockServerRequest.builder()
                                    .body(Mono.just(
                                            InitiateApprovalRequest.builder().build())),
                            InitiateApprovalRequest.class))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(BusinessException.class);
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                    })
                    .verify();
        }

        @Test
        @DisplayName("带自定义校验器时执行回调")
        void customValidator() {
            InitiateApprovalRequest body = InitiateApprovalRequest.builder()
                    .platform(OfficePlatform.FEISHU)
                    .appId(1L)
                    .processDefinitionId("tpl-1")
                    .initiatorId("user-1")
                    .formData("{}")
                    .build();
            AtomicReference<InitiateApprovalRequest> captured = new AtomicReference<>();

            StepVerifier.create(util.validateAndMap(
                            MockServerRequest.builder().body(Mono.just(body)),
                            InitiateApprovalRequest.class,
                            captured::set))
                    .expectNext(body)
                    .verifyComplete();
            assertThat(captured.get()).isSameAs(body);
        }
    }

    @Nested
    @DisplayName("validateAndThrow")
    class ValidateAndThrowTest {

        @Test
        @DisplayName("合法对象返回含原对象的 Mono")
        void valid() {
            InitiateApprovalRequest body = InitiateApprovalRequest.builder()
                    .platform(OfficePlatform.FEISHU)
                    .appId(1L)
                    .processDefinitionId("tpl-1")
                    .initiatorId("user-1")
                    .formData("{}")
                    .build();

            StepVerifier.create(util.validateAndThrow(body)).expectNext(body).verifyComplete();
        }

        @Test
        @DisplayName("非法对象返回 BusinessException")
        void invalid() {
            StepVerifier.create(util.validateAndThrow(
                            InitiateApprovalRequest.builder().build()))
                    .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(BusinessException.class))
                    .verify();
        }
    }

    @Nested
    @DisplayName("响应构建")
    class ResponseTest {

        @Test
        @DisplayName("buildSuccessResponse 返回 HTTP 200")
        void success() {
            StepVerifier.create(util.buildSuccessResponse("data"))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("ok 返回 HTTP 200")
        void ok() {
            StepVerifier.create(util.ok(Result.success("data")))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("buildErrorResponse(code,message) 返回 HTTP 400")
        void errorCodeMessage() {
            StepVerifier.create(util.buildErrorResponse(400, "bad"))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("buildErrorResponse(ResultCode,message) 返回 HTTP 400")
        void errorResultCode() {
            StepVerifier.create(util.buildErrorResponse(ResultCode.NOT_FOUND, "缺失"))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("buildUnauthorizedResponse 返回 HTTP 401")
        void unauthorized() {
            StepVerifier.create(util.buildUnauthorizedResponse("未登录"))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(401))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getTenantId")
    class GetTenantIdTest {

        @Test
        @DisplayName("从 X-Tenant-Id 请求头读取租户")
        void fromHeader() {
            MockServerRequest request = MockServerRequest.builder()
                    .header("X-Tenant-Id", "tenant-9")
                    .build();
            assertThat(util.getTenantId(request)).isEqualTo("tenant-9");
        }

        @Test
        @DisplayName("无请求头返回 null")
        void missingHeader() {
            assertThat(util.getTenantId(MockServerRequest.builder().build())).isNull();
        }
    }

    @Nested
    @DisplayName("safeParseLong")
    class SafeParseLongTest {

        @Test
        @DisplayName("null 返回 null")
        void nullValue() {
            assertThat(RequestHandlerUtil.safeParseLong(null)).isNull();
        }

        @Test
        @DisplayName("空白返回 null")
        void blankValue() {
            assertThat(RequestHandlerUtil.safeParseLong("  ")).isNull();
        }

        @Test
        @DisplayName("合法数字返回对应值")
        void validValue() {
            assertThat(RequestHandlerUtil.safeParseLong(" 42 ")).isEqualTo(42L);
        }

        @Test
        @DisplayName("非数字返回 null")
        void invalidValue() {
            assertThat(RequestHandlerUtil.safeParseLong("abc")).isNull();
        }
    }

    @Nested
    @DisplayName("safeParseInt")
    class SafeParseIntTest {

        @Test
        @DisplayName("null 返回默认值")
        void nullValue() {
            assertThat(RequestHandlerUtil.safeParseInt(null, 5)).isEqualTo(5);
        }

        @Test
        @DisplayName("空白返回默认值")
        void blankValue() {
            assertThat(RequestHandlerUtil.safeParseInt(" ", 5)).isEqualTo(5);
        }

        @Test
        @DisplayName("合法数字返回对应值")
        void validValue() {
            assertThat(RequestHandlerUtil.safeParseInt("7", 5)).isEqualTo(7);
        }

        @Test
        @DisplayName("负数返回默认值")
        void negativeValue() {
            assertThat(RequestHandlerUtil.safeParseInt("-3", 5)).isEqualTo(5);
        }

        @Test
        @DisplayName("非数字返回默认值")
        void invalidValue() {
            assertThat(RequestHandlerUtil.safeParseInt("xyz", 5)).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("handleError")
    class HandleErrorTest {

        @Test
        @DisplayName("BusinessException 使用其错误码返回 HTTP 400")
        void businessException() {
            StepVerifier.create(util.handleError(new BusinessException(ResultCode.NOT_FOUND, "不存在")))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("普通异常返回 HTTP 400")
        void genericException() {
            StepVerifier.create(util.handleError(new IllegalStateException("boom")))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("handleRequestBody 使用 GET 请求体不影响处理")
    void handleRequestBodyWithMethod() {
        InitiateApprovalRequest body = InitiateApprovalRequest.builder()
                .platform(OfficePlatform.FEISHU)
                .appId(1L)
                .processDefinitionId("tpl-1")
                .initiatorId("user-1")
                .formData("{}")
                .build();
        StepVerifier.create(util.handleRequestBody(
                        MockServerRequest.builder().method(HttpMethod.POST).body(Mono.just(body)),
                        InitiateApprovalRequest.class))
                .expectNext(body)
                .verifyComplete();
    }
}
