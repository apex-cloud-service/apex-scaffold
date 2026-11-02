package com.apex.scaffold.integration.device.domain.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import java.io.Serial;

/**
 * 设备遥测上行事件（方案 M5 / 批次 E3）。
 *
 * <p>设备校验通过、影子 reported 更新后发布（Outbox 通道由宿主装配决定）；
 * {@code metric} 为原始 JSON 指标串，由规则引擎 / 业务订阅方解析。
 * 事件类型：{@code iot.device.telemetry.received}。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public class DeviceTelemetryReceived extends AbstractDomainEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件类型标识。 */
    public static final String EVENT_TYPE = "iot.device.telemetry.received";

    private final String tenantId;

    private final String deviceKey;

    private final String metric;

    private final String recordedAt;

    /**
     * 构造遥测上行事件。
     *
     * @param tenantId   租户编码
     * @param deviceKey  设备标识
     * @param metric     遥测指标 JSON
     * @param recordedAt 上报时间（ISO-8601 文本）
     */
    public DeviceTelemetryReceived(String tenantId, String deviceKey, String metric, String recordedAt) {
        super(EVENT_TYPE, null);
        this.tenantId = tenantId;
        this.deviceKey = deviceKey;
        this.metric = metric;
        this.recordedAt = recordedAt;
    }

    @Override
    public String getSourceId() {
        return deviceKey;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getDeviceKey() {
        return deviceKey;
    }

    public String getMetric() {
        return metric;
    }

    public String getRecordedAt() {
        return recordedAt;
    }
}
