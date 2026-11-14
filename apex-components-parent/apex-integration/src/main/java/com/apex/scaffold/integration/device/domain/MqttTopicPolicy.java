package com.apex.scaffold.integration.device.domain;

import java.util.Arrays;

/**
 * MQTT 租户 topic 命名空间策略（方案 M5 / 批次 E3）。
 *
 * <p>命名空间：{@code apex/{tenantCode}/{deviceKey}/up|down[/...]}。上行遥测发布到
 * {@code up} 前缀，下行命令发布到 {@code down} 前缀；设备只能访问本租户本设备的
 * 命名空间（ACL 校验入口 {@link #isAllowed}，供认证钩子与桥接层复用）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public final class MqttTopicPolicy {

    private static final String NAMESPACE_ROOT = "apex";

    private static final String DIRECTION_UP = "up";

    private static final String DIRECTION_DOWN = "down";

    private MqttTopicPolicy() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 设备上行 topic。
     *
     * @param tenantCode 租户编码
     * @param deviceKey  设备标识
     * @return {@code apex/{tenantCode}/{deviceKey}/up}
     */
    public static String upTopic(String tenantCode, String deviceKey) {
        return namespace(tenantCode, deviceKey) + "/" + DIRECTION_UP;
    }

    /**
     * 设备下行 topic。
     *
     * @param tenantCode 租户编码
     * @param deviceKey  设备标识
     * @param suffix     下行子路径（可空；命令下发使用 commandId）
     * @return {@code apex/{tenantCode}/{deviceKey}/down[/suffix]}
     */
    public static String downTopic(String tenantCode, String deviceKey, String suffix) {
        String topic = namespace(tenantCode, deviceKey) + "/" + DIRECTION_DOWN;
        return suffix == null || suffix.isBlank() ? topic : topic + "/" + suffix.trim();
    }

    /**
     * 共享订阅的通配 topic 过滤器（上行消费，水平扩展）。
     *
     * @param sharedSubscriptionGroup 共享订阅组名
     * @return {@code $share/{group}/apex/+/+/up/#}
     */
    public static String sharedUpFilter(String sharedSubscriptionGroup) {
        return "$share/" + sharedSubscriptionGroup + "/" + NAMESPACE_ROOT + "/+/+/up/#";
    }

    /**
     * 校验 topic 是否落在指定租户与设备的命名空间内（ACL）。
     *
     * <p>规则：首段必须为 {@code apex}；第二、三段精确等于租户编码与设备标识；
     * 第四段必须为 {@code up} 或 {@code down}；任意段不得为空或含通配符
     * （{@code +} / {@code #}），杜绝跨租户/跨设备访问与通配注入。
     *
     * @param tenantCode 租户编码
     * @param deviceKey  设备标识
     * @param topic      待校验 topic
     * @return 是否允许
     */
    public static boolean isAllowed(String tenantCode, String deviceKey, String topic) {
        if (tenantCode == null
                || tenantCode.isBlank()
                || deviceKey == null
                || deviceKey.isBlank()
                || topic == null
                || topic.isBlank()) {
            return false;
        }
        String[] segments = topic.split("/");
        if (segments.length < 4 || !NAMESPACE_ROOT.equals(segments[0])) {
            return false;
        }
        if (!tenantCode.equals(segments[1]) || !deviceKey.equals(segments[2])) {
            return false;
        }
        if (!DIRECTION_UP.equals(segments[3]) && !DIRECTION_DOWN.equals(segments[3])) {
            return false;
        }
        return Arrays.stream(segments)
                .noneMatch(segment -> segment.isEmpty() || segment.contains("+") || segment.contains("#"));
    }

    private static String namespace(String tenantCode, String deviceKey) {
        return NAMESPACE_ROOT + "/" + tenantCode + "/" + deviceKey;
    }
}
