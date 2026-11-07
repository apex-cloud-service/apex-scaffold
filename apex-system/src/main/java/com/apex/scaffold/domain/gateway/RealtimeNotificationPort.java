package com.apex.scaffold.domain.gateway;

/**
 * 站内通知实时推送端口。
 *
 * <p>system 侧事件监听器在系统消息落库成功后，通过该端口把通知发布到实时通道
 * （MQ 广播 → 各实例 SSE 扇出），不直接依赖基础设施 MQ 能力；实现负责条件化装配
 * 与失败边界（发布失败不向上游传播，仅记录日志）。</p>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public interface RealtimeNotificationPort {

    /**
     * 发布一条定向实时通知。
     *
     * @param tenantId 可信租户 ID
     * @param recipient 接收账户 ID 的字符串表示
     * @param content 消息内容
     */
    void publish(String tenantId, String recipient, String content);
}
