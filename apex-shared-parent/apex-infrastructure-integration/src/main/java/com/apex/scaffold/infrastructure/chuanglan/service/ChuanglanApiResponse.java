package com.apex.scaffold.infrastructure.chuanglan.service;

import com.apex.scaffold.infrastructure.chuanglan.exception.ChuanglanApiException;
import java.util.Map;

/**
 * 创蓝 JSON API 的类型化响应适配器。
 *
 * <p>短信 v1、短信 v2 与闪验接口使用不同成功码和错误字段。本适配器统一读取响应码，
 * 并由各子系统显式提供期望成功码，避免业务 Client 反复从动态 Map 强制转换字段。
 * 原始 payload 在基础设施兼容期继续返回给上层 Gateway。
 *
 * @param code 规范化字符串响应码
 * @param payload 原始响应 Map
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 */
public record ChuanglanApiResponse(String code, Map<String, Object> payload) {

    /**
     * 从动态响应创建类型化适配器。
     *
     * @param payload WebClient 响应
     * @return 类型化创蓝响应
     */
    public static ChuanglanApiResponse from(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("创蓝API响应不能为空");
        }
        return new ChuanglanApiResponse(String.valueOf(payload.get("code")), payload);
    }

    /**
     * 校验子系统期望的成功码。
     *
     * @param expectedCode 当前 API 的成功码
     * @param operation 操作名称
     * @param messageField 当前 API 的错误消息字段
     * @return 原始响应 Map
     */
    public Map<String, Object> requireSuccess(String expectedCode, String operation, String messageField) {
        if (!expectedCode.equals(code)) {
            String message = String.valueOf(payload.getOrDefault(messageField, "未知错误"));
            throw new ChuanglanApiException(code, operation + "失败: " + message);
        }
        return payload;
    }
}
