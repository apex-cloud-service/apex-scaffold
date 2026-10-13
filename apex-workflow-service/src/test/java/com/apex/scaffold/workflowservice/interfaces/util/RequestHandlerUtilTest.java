package com.apex.scaffold.workflowservice.interfaces.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link RequestHandlerUtil} 单元测试.
 *
 * <p>覆盖工作流服务接口层的参数校验与响应构建能力：请求体校验的通过与失败分支、
 * 响应式链校验操作符、成功/错误响应构建以及统一错误处理的分支处理，
 * 确保与单应用时代的请求处理语义保持一致。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@DisplayName("RequestHandlerUtil 单元测试")
class RequestHandlerUtilTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private final RequestHandlerUtil requestHandlerUtil = new RequestHandlerUtil(validator);

    /**
     * 携带校验约束的测试请求体。
     *
     * @param name 名称，不能为空白
     */
    record ValidatedPayload(@NotBlank String name) {}

    @Nested
    @DisplayName("handleRequestBody - 请求体校验")
    class HandleRequestBodyTest {

        @Test
        @DisplayName("校验通过时返回原始请求体")
        void shouldReturnBodyWhenValid() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.bodyToMono(ValidatedPayload.class)).thenReturn(Mono.just(new ValidatedPayload("ok")));

            StepVerifier.create(requestHandlerUtil.handleRequestBody(request, ValidatedPayload.class))
                    .assertNext(body -> assertThat(body.name()).isEqualTo("ok"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("校验失败时抛出携带 PARAM_ERROR 的 BusinessException")
        void shouldThrowWhenInvalid() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.bodyToMono(ValidatedPayload.class)).thenReturn(Mono.just(new ValidatedPayload("")));

            StepVerifier.create(requestHandlerUtil.handleRequestBody(request, ValidatedPayload.class))
                    .expectErrorSatisfies(error -> assertThat(error)
                            .isInstanceOf(BusinessException.class)
                            .extracting(e -> ((BusinessException) e).getCode())
                            .isEqualTo(ResultCode.PARAM_ERROR.getCode()))
                    .verify();
        }
    }

    @Nested
    @DisplayName("validateAndThrow - 响应式链校验")
    class ValidateAndThrowTest {

        @Test
        @DisplayName("校验通过时返回原始对象")
        void shouldReturnBodyWhenValid() {
            StepVerifier.create(requestHandlerUtil.validateAndThrow(new ValidatedPayload("ok")))
                    .assertNext(body -> assertThat(body.name()).isEqualTo("ok"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("校验失败时返回 error Mono")
        void shouldReturnErrorWhenInvalid() {
            StepVerifier.create(requestHandlerUtil.validateAndThrow(new ValidatedPayload(" ")))
                    .expectError(BusinessException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("响应构建与错误处理")
    class ResponseBuildingTest {

        @Test
        @DisplayName("buildSuccessResponse - 构建 HTTP 200 响应")
        void shouldBuildSuccessResponse() {
            StepVerifier.create(requestHandlerUtil.buildSuccessResponse("data"))
                    .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.OK))
                    .verifyComplete();
        }

        @Test
        @DisplayName("buildErrorResponse - 基于代码构建 HTTP 400 响应")
        void shouldBuildErrorResponseByCode() {
            StepVerifier.create(requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR.getCode(), "参数错误"))
                    .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                    .verifyComplete();
        }

        @Test
        @DisplayName("buildErrorResponse - 基于 ResultCode 构建 HTTP 400 响应")
        void shouldBuildErrorResponseByResultCode() {
            StepVerifier.create(requestHandlerUtil.buildErrorResponse(ResultCode.BUSINESS_ERROR, "业务失败"))
                    .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                    .verifyComplete();
        }

        @Test
        @DisplayName("handleError - 业务异常透传其错误码")
        void shouldHandleBusinessException() {
            StepVerifier.create(requestHandlerUtil.handleError(new BusinessException(ResultCode.PARAM_ERROR, "参数错误")))
                    .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                    .verifyComplete();
        }

        @Test
        @DisplayName("handleError - 非业务异常回退为系统错误")
        void shouldHandleNonBusinessException() {
            StepVerifier.create(requestHandlerUtil.handleError(new IllegalStateException("boom")))
                    .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                    .verifyComplete();
        }
    }
}
