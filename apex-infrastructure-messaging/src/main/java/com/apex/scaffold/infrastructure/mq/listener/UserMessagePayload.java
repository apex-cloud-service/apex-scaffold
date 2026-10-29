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
 * @param tenantId 租户 ID
 * @param userId 用户 ID；缺失或无法转换时为空
 * @param nickName 用户昵称
 * @param clientIp 客户端 IP
 * @param deviceId 客户端设备标识
 * @param updatedFields 用户更新字段描述
 * @param raw 原始不可变消息快照
 * @author Apex Team
 * @version 1.1.0
 * @since 3.3.0
 */
public record UserMessagePayload(
        String messageId,
        String tag,
        String tenantId,
        Long userId,
        String nickName,
        String clientIp,
        String deviceId,
        String updatedFields,
        Map<String, Object> raw) {

    /** 租户 ID 校验正则（字母、数字、点、下划线、连字符）。 */
    private static final String TENANT_ID_REGEX = "[A-Za-z0-9._-]{1,64}";
    /** 租户 ID 最大长度。 */
    private static final int MAX_TENANT_ID_LENGTH = 64;

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
        String tenantId = text(snapshot, "tenantId", null);
        if (tenantId == null || !tenantId.matches(TENANT_ID_REGEX)) {
            throw new IllegalArgumentException("用户MQ消息缺少合法租户ID");
        }
        return new UserMessagePayload(
                text(snapshot, "messageId", null),
                text(snapshot, "tag", null),
                tenantId,
                number(snapshot.get("userId")),
                text(snapshot, "nickName", "用户"),
                text(snapshot, "clientIp", null),
                text(snapshot, "deviceId", "unknown"),
                text(snapshot, "updatedFields", "unknown"),
                snapshot);
    }

    /**
     * 读取字段并转换为字符串，仅在字段缺失或值为 {@code null} 时使用默认值。
     *
     * <p>非空值通过 {@link String#valueOf(Object)} 转换，不修剪空白或替换空字符串。
     *
     * @param source 原始字段映射
     * @param key 字段名称
     * @param defaultValue 缺失或空值时的返回值，可为 {@code null}
     * @return 字段的字符串表示，或指定的默认值
     */
    private static String text(Map<String, Object> source, String key, String defaultValue) {
        Object value = source.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    /**
     * 将数字或可解析的非空白字符串转换为长整型。
     *
     * <p>数字按 {@link Number#longValue()} 转换，可能发生截断；字符串不修剪空白。
     * 字符串格式错误或超出长整型范围时捕获解析异常并返回 {@code null}。
     *
     * @param value 待转换的字段值，可为 {@code null}
     * @return 转换结果；空值、空白字符串、无效字符串或其他类型返回 {@code null}
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
