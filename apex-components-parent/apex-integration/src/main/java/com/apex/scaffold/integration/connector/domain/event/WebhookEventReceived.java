package com.apex.scaffold.integration.connector.domain.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import java.io.Serial;

/**
 * 入站 webhook 事件（方案 M5 / 批次 E2）。
 *
 * <p>签名校验通过且未命中去重时发布；{@code payload} 为原始 JSON 载荷，
 * 由订阅方（规则引擎 / 业务消费方）解析。事件类型：{@code integration.webhook.received}。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public class WebhookEventReceived extends AbstractDomainEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件类型标识。 */
    public static final String EVENT_TYPE = "integration.webhook.received";

    private final String tenantId;

    private final String connectorType;

    private final String eventType;

    private final String payload;

    /**
     * 构造入站 webhook 事件。
     *
     * @param tenantId      租户编码
     * @param connectorType 连接器类型（即入站 webhook 路径标识）
     * @param eventType     业务事件类型（来自投递方或默认 inbound）
     * @param payload       原始 JSON 载荷
     * @param eventId       业务事件 ID（用于跨重试幂等；可空）
     */
    public WebhookEventReceived(
            String tenantId, String connectorType, String eventType, String payload, String eventId) {
        super(EVENT_TYPE, null, eventId);
        this.tenantId = tenantId;
        this.connectorType = connectorType;
        this.eventType = eventType;
        this.payload = payload;
    }

    @Override
    public String getSourceId() {
        return connectorType;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }
}
