package com.apex.scaffold.knowledgeservice.interfaces.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.knowledgeservice.interfaces.security.AuthenticatedUser;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.test.StepVerifier;

/**
 * 请求处理工具类测试：校验、身份提取、错误响应与安全参数解析。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
class KnowledgeRequestHandlerUtilTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private final KnowledgeRequestHandlerUtil util = new KnowledgeRequestHandlerUtil(validator);

    /** 校验用样例请求体（record 供 Jackson 反序列化）。 */
    record SampleBody(@jakarta.validation.constraints.NotBlank(message = "名称不能为空") String name) {}

    @Test
    void validBodyPassesThrough() {
        ServerRequest request = buildRequest("{\"name\":\"知识库\"}");
        StepVerifier.create(util.handleRequestBody(request, SampleBody.class))
                .assertNext(body -> assertThat(body.name()).isEqualTo("知识库"))
                .verifyComplete();
    }

    @Test
    void invalidBodyIsRejectedWithParamError() {
        ServerRequest request = buildRequest("{\"name\":\"  \"}");
        StepVerifier.create(util.handleRequestBody(request, SampleBody.class))
                .expectError(BusinessException.class)
                .verify();
    }

    /**
     * 构建携带 JSON 正文的 ServerRequest。
     *
     * @param json 请求体
     * @return ServerRequest
     */
    private ServerRequest buildRequest(String json) {
        MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/api/v1/kb/test")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(json);
        MockServerWebExchange exchange = new MockServerWebExchange.Builder(httpRequest).build();
        ServerCodecConfigurer codecs = ServerCodecConfigurer.create();
        return ServerRequest.create(exchange, codecs.getReaders());
    }

    @Test
    void currentUserExtractsAuthenticatedUser() {
        AuthenticatedUser user = new AuthenticatedUser(7L, "t1");
        StepVerifier.create(util.currentUser()
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                new UsernamePasswordAuthenticationToken(user, null, java.util.List.of()))))
                .assertNext(extracted -> assertThat(extracted.userId()).isEqualTo(7L))
                .verifyComplete();
    }

    @Test
    void currentUserWithoutIdentityIsRejected() {
        StepVerifier.create(util.currentUser())
                .expectError(com.apex.scaffold.common.exception.BusinessException.class)
                .verify();
    }

    @Test
    void handleErrorMapsBusinessAndUnknownErrors() {
        var businessResponse = util.handleError(new BusinessException(ResultCode.PARAM_ERROR, "参数错误"))
                .block();
        assertThat(businessResponse).isNotNull();
        assertThat(businessResponse.statusCode().value()).isEqualTo(400);

        var unknownResponse =
                util.handleError(new IllegalStateException("内部细节")).block();
        assertThat(unknownResponse).isNotNull();
        assertThat(unknownResponse.statusCode().value()).isEqualTo(500);
    }

    @Test
    void buildErrorEventPreservesBusinessCodeAndEscapedMessage() throws Exception {
        var event = util.buildErrorEvent(new BusinessException(ResultCode.PARAM_ERROR, "请调整\"提问\"\n内容\\格式"));
        assertErrorEvent(event, "400", "请调整\"提问\"\n内容\\格式");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"  \t\n"})
    void buildErrorEventUsesFallbackForEmptyBusinessMessage(String message) throws Exception {
        assertErrorEvent(
                util.buildErrorEvent(new BusinessException(ResultCode.PARAM_ERROR, message)), "400", "问答服务暂时不可用，请稍后重试");
    }

    @Test
    void buildErrorEventDoesNotExposeTechnicalThrowableOrBusinessCause() throws Exception {
        assertErrorEvent(
                util.buildErrorEvent(new IllegalStateException(
                        "private upstream", new BusinessException(ResultCode.PARAM_ERROR, "private cause"))),
                "-1",
                "问答服务暂时不可用，请稍后重试");
        assertErrorEvent(util.buildErrorEvent(new AssertionError("private technical detail")), "-1", "问答服务暂时不可用，请稍后重试");
        assertErrorEvent(util.buildErrorEvent(null), "-1", "问答服务暂时不可用，请稍后重试");
    }

    @Test
    void buildErrorEventUsesSystemCodeWhenBusinessCodeIsNull() throws Exception {
        assertErrorEvent(util.buildErrorEvent(new BusinessException((Integer) null, "请调整提问")), "-1", "请调整提问");
    }

    @Test
    void buildErrorEventSerializationFailureStillReturnsStableJson() throws Exception {
        var mapper = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.ObjectMapper.class);
        org.mockito.Mockito.when(mapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("private serialization details") {});
        try (var json = org.mockito.Mockito.mockStatic(com.apex.scaffold.common.util.JsonUtil.class)) {
            json.when(com.apex.scaffold.common.util.JsonUtil::getObjectMapper).thenReturn(mapper);
            assertErrorEvent(
                    util.buildErrorEvent(new BusinessException(ResultCode.PARAM_ERROR, "请调整提问")),
                    "-1",
                    "问答服务暂时不可用，请稍后重试");
        }
    }

    private void assertErrorEvent(
            org.springframework.http.codec.ServerSentEvent<String> event, String code, String message)
            throws Exception {
        assertThat(event.event()).isEqualTo("error");
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(event.data());
        assertThat(json.isObject()).isTrue();
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.get("code").isTextual()).isTrue();
        assertThat(json.get("code").textValue()).isEqualTo(code);
        assertThat(json.get("message").isTextual()).isTrue();
        assertThat(json.get("message").textValue()).isEqualTo(message);
    }

    @Test
    void safeParsersHandleInvalidInput() {
        assertThat(KnowledgeRequestHandlerUtil.safeParseLong("42")).isEqualTo(42L);
        assertThat(KnowledgeRequestHandlerUtil.safeParseLong("abc")).isNull();
        assertThat(KnowledgeRequestHandlerUtil.safeParseLong("")).isNull();
        assertThat(KnowledgeRequestHandlerUtil.safeParseInt("5", 1)).isEqualTo(5);
        assertThat(KnowledgeRequestHandlerUtil.safeParseInt("-3", 1)).isEqualTo(1);
        assertThat(KnowledgeRequestHandlerUtil.safeParseInt("abc", 1)).isEqualTo(1);
    }
}
