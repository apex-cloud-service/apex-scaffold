package com.apex.scaffold.common.event;

/**
 * 领域事件通道模式（方案 M3 / 批次 C8）。
 *
 * <p>对应配置 {@code apex.events.channel}：
 * <ul>
 *   <li>{@link #AUTO}（默认）——按运行环境自动选择：同进程存在 MQ 模板且启用 Outbox 时走
 *       {@link #REMOTE}，否则走 {@link #LOCAL}。组合部署（C9 apex-boot-all）下退化为
 *       {@code LOCAL}，拆分部署下自动走 {@code REMOTE}，业务代码零感知。</li>
 *   <li>{@link #LOCAL}——强制进程内派发（单进程拓扑、测试、离线验收）。</li>
 *   <li>{@link #REMOTE}——强制经 Outbox + MQ 投递（跨服务拓扑）。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public enum EventChannelMode {

    /** 按环境自动选择（默认） */
    AUTO,

    /** 进程内派发 */
    LOCAL,

    /** 经 Outbox + MQ 跨服务投递 */
    REMOTE;

    /**
     * 解析配置值（大小写不敏感，非法值回退 {@link #AUTO}）。
     *
     * @param raw 配置原文（可空）
     * @return 通道模式
     */
    public static EventChannelMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return AUTO;
        }
    }

    /**
     * 解析实际生效模式：{@code AUTO} 依据是否为拆分部署（存在 MQ 通道能力）选择。
     *
     * @param remoteCapable 当前环境是否具备跨服务投递能力（MQ 模板 + Outbox 启用）
     * @return 生效模式（{@link #LOCAL} 或 {@link #REMOTE}）
     */
    public EventChannelMode resolve(boolean remoteCapable) {
        return switch (this) {
            case LOCAL -> LOCAL;
            case REMOTE -> REMOTE;
            case AUTO -> remoteCapable ? REMOTE : LOCAL;
        };
    }
}
