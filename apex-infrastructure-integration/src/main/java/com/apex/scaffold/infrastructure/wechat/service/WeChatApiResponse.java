package com.apex.scaffold.infrastructure.wechat.service;

import com.apex.scaffold.infrastructure.wechat.exception.WeChatApiException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信 JSON API 的类型化响应适配器。
 *
 * <p>微信不同接口可能把 {@code errcode} 返回为整数、长整数或字符串。该适配器在基础设施
 * 边界统一完成规范化，并保留原始 payload 供尚未完成 DTO 化的兼容接口返回。业务服务
 * 不再直接强转动态 Map 中的错误码。
 *
 * @param errorCode 规范化后的微信错误码；响应未提供时为空
 * @param errorMessage 微信错误信息；未提供时为空字符串
 * @param payload 原始不可变响应
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 */
public record WeChatApiResponse(Integer errorCode, String errorMessage, Map<String, Object> payload) {

    /** 微信 API 成功错误码。 */
    private static final int SUCCESS_CODE = 0;

    /**
     * 从动态 JSON Map 创建类型化响应。
     *
     * @param payload WebClient 反序列化的响应 Map
     * @return 类型化微信响应
     */
    public static WeChatApiResponse from(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("微信API响应不能为空");
        }
        Object message = payload.get("errmsg");
        return new WeChatApiResponse(
                parseErrorCode(payload.get("errcode")),
                message == null ? "" : String.valueOf(message),
                Collections.unmodifiableMap(new LinkedHashMap<>(payload)));
    }

    /**
     * 校验微信业务错误码并返回原始兼容响应。
     *
     * <p>部分查询成功响应不包含 {@code errcode}，因此空错误码按成功处理。非零错误码统一
     * 转换为 {@link WeChatApiException}。
     *
     * @param operation 业务操作名称，用于补充空错误消息
     * @return 原始不可变响应 Map
     */
    public Map<String, Object> requireSuccess(String operation) {
        if (errorCode != null && errorCode != SUCCESS_CODE) {
            String message = errorMessage == null || errorMessage.isBlank() ? operation + "失败" : errorMessage;
            throw new WeChatApiException(errorCode, message);
        }
        return payload;
    }

    /**
     * 将多种数字表示规范化为 Integer。
     *
     * @param value 原始 errcode 字段
     * @return 规范化错误码；字段不存在时为空
     */
    private static Integer parseErrorCode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("微信API返回无法识别的errcode: " + value, error);
        }
    }
}
