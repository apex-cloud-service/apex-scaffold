package com.apex.scaffold.integration.device.domain.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import java.io.Serial;

/**
 * 规则命中事件（方案 M5 / 批次 E3）。
 *
 * <p>规则引擎以 EVENT 动作转发命中结果时发布；订阅方可继续驱动业务流程。
 * 事件类型：{@code iot.device.rule.triggered}。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public class DeviceRuleTriggered extends AbstractDomainEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件类型标识。 */
    public static final String EVENT_TYPE = "iot.device.rule.triggered";

    private final String tenantId;

    private final Long ruleId;

    private final String ruleName;

    private final String deviceKey;

    private final String metric;

    /**
     * 构造规则命中事件。
     *
     * @param tenantId  租户编码
     * @param ruleId    规则主键
     * @param ruleName  规则名
     * @param deviceKey 设备标识
     * @param metric    命中时的遥测指标 JSON
     */
    public DeviceRuleTriggered(String tenantId, Long ruleId, String ruleName, String deviceKey, String metric) {
        super(EVENT_TYPE, null);
        this.tenantId = tenantId;
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.deviceKey = deviceKey;
        this.metric = metric;
    }

    @Override
    public String getSourceId() {
        return ruleName;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getDeviceKey() {
        return deviceKey;
    }

    public String getMetric() {
        return metric;
    }
}
