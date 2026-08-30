package com.apex.scaffold.infrastructure.mq.listener;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 类型安全的用户 MQ 消息载荷。
 *
 * <p>RocketMQ 为保持与现有生产者兼容仍接收动态 Map，本值对象在监听器边界一次性完成
 * 字段读取和数字类型规范化。后续 Handler 不再重复字符串 key 与强制类型转换，同时
 * {@link #raw()} 保留未知扩展字段，确保生产者增加非关键字段时不会丢失兼容性。
 *
 * @param messageId 消息幂等标识
 * @param tag 消息路由标签
 * @param userId 用户 ID；缺失或无法转换时为空
 * @param nickName 用户昵称
 * @param clientIp 客户端 IP
 * @param deviceId 客户端设备标识
 * @param updatedFields 用户更新字段描述
 * @param raw 原始不可变消息快照
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 */
public record UserMessagePayload(
        String messageId,
        String tag,
        Long userId,
        String nickName,
        String clientIp,
        String deviceId,
        String updatedFields,
        Map<String, Object> raw) {

    /**
     * 从 RocketMQ 动态消息创建类型化载荷。
     *
     * @param message 原始消息；不可为空
     * @return 类型化且持有不可变原始快照的载荷
     */
    public static UserMessagePayload from(Map<String, Object> message) {
        if (message == null) {
            throw new IllegalArgumentException("用户MQ消息不能为空");
        }
        Map<String, Object> snapshot = Collections.unmodifiableMap(new LinkedHashMap<>(message));
        return new UserMessagePayload(
                text(snapshot, "messageId", null),
                text(snapshot, "tag", null),
                number(snapshot.get("userId")),
                text(snapshot, "nickName", "用户"),
                text(snapshot, "clientIp", null),
                text(snapshot, "deviceId", "unknown"),
                text(snapshot, "updatedFields", "unknown"),
                snapshot);
    }

    /**
     * 读取可选文本字段。
     *
     * @param source 消息字段
     * @param key 字段名
     * @param defaultValue 缺失时默认值
     * @return 字符串字段或默认值
     */
    private static String text(Map<String, Object> source, String key, String defaultValue) {
        Object value = source.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    /**
     * 将任意 Number 规范化为 Long。
     *
     * @param value 原始数字值
     * @return Long 值；类型不匹配时为空
     */
    private static Long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
