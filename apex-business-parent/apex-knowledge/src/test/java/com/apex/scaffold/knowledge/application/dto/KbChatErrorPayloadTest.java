package com.apex.scaffold.knowledge.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class KbChatErrorPayloadTest {

    @Test
    void businessMessageIsEscapedWithoutExposingCause() throws Exception {
        var payload = KbChatErrorPayload.from(new BusinessException(
                ResultCode.PARAM_ERROR, "请调整\"提问\"\n内容\\格式", new IllegalStateException("private cause")));
        var json = new ObjectMapper().readTree(payload.toJson(new ObjectMapper()));
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.get("code").textValue()).isEqualTo("400");
        assertThat(json.get("message").textValue()).isEqualTo("请调整\"提问\"\n内容\\格式");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  \t\n"})
    void missingBusinessMessageIsNormalized(String message) {
        assertThat(KbChatErrorPayload.from(new BusinessException(ResultCode.PARAM_ERROR, message)))
                .isEqualTo(new KbChatErrorPayload("400", "问答服务暂时不可用，请稍后重试"));
    }

    @Test
    void technicalAndNullThrowableUseFallbackWithoutUnwrappingBusinessCause() {
        var fallback = new KbChatErrorPayload("-1", "问答服务暂时不可用，请稍后重试");
        assertThat(KbChatErrorPayload.from(null)).isEqualTo(fallback);
        assertThat(KbChatErrorPayload.from(new AssertionError("private details")))
                .isEqualTo(fallback);
        assertThat(KbChatErrorPayload.from(
                        new IllegalStateException(new BusinessException(ResultCode.PARAM_ERROR, "private cause"))))
                .isEqualTo(fallback);
    }

    @Test
    void missingBusinessCodeIsNormalized() {
        assertThat(KbChatErrorPayload.from(new BusinessException((Integer) null, "请调整提问"))
                        .code())
                .isEqualTo("-1");
    }

    @Test
    void checkedAndRuntimeSerializationFailuresReturnStableJson() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("private serialization details") {})
                .thenThrow(new IllegalStateException("private serialization details"));
        var payload = new KbChatErrorPayload("400", "请调整提问");
        assertFallbackJson(payload.toJson(mapper));
        assertFallbackJson(payload.toJson(mapper));
        assertFallbackJson(payload.toJson(null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  \t\n"})
    void emptySerializationResultReturnsStableJson(String result) throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenReturn(result);
        assertFallbackJson(new KbChatErrorPayload("400", "请调整提问").toJson(mapper));
    }

    private void assertFallbackJson(String data) throws Exception {
        var json = new ObjectMapper().readTree(data);
        assertThat(json.isObject()).isTrue();
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.get("code").isTextual()).isTrue();
        assertThat(json.get("code").textValue()).isEqualTo("-1");
        assertThat(json.get("message").isTextual()).isTrue();
        assertThat(json.get("message").textValue()).isEqualTo("问答服务暂时不可用，请稍后重试");
    }
}
