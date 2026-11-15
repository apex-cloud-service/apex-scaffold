package com.apex.scaffold.knowledge.application.dto;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 问答 SSE 错误载荷：仅业务异常消息可对外展示，技术错误使用固定中文提示。
 *
 * @param code 字符串错误码
 * @param message 安全的用户提示
 * @author Apex Team
 * @since 1.3.2
 */
@Slf4j
public record KbChatErrorPayload(String code, String message) {

    private static final String FALLBACK_MESSAGE = "问答服务暂时不可用，请稍后重试";

    private static final String FALLBACK_JSON = "{\"code\":\"-1\",\"message\":\"问答服务暂时不可用，请稍后重试\"}";

    /**
     * 归一化缺失错误码与空白消息，确保两个字段始终是非空字符串。
     *
     * @param code 字符串错误码
     * @param message 安全的用户提示
     */
    public KbChatErrorPayload {
        code = code == null || code.isBlank() ? String.valueOf(ResultCode.ERROR.getCode()) : code;
        message = message == null || message.isBlank() ? FALLBACK_MESSAGE : message;
    }

    /**
     * 仅使用最外层业务异常的错误码与安全消息，不透传技术异常或其原因链。
     *
     * @param error 异常，可为空
     * @return SSE 错误载荷
     */
    public static KbChatErrorPayload from(Throwable error) {
        if (error instanceof BusinessException business) {
            return new KbChatErrorPayload(
                    business.getCode() == null ? null : String.valueOf(business.getCode()), business.getMessage());
        }
        return new KbChatErrorPayload(null, null);
    }

    /**
     * 序列化错误载荷；序列化失败仍返回合法 JSON，不依赖二次序列化。
     *
     * @param objectMapper JSON 序列化器
     * @return 仅含 code/message 字符串字段的 JSON
     */
    public String toJson(ObjectMapper objectMapper) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("code", code, "message", message));
            return json == null || json.isBlank() ? FALLBACK_JSON : json;
        } catch (Exception error) {
            log.warn("知识问答错误载荷序列化失败", error);
            return FALLBACK_JSON;
        }
    }
}
