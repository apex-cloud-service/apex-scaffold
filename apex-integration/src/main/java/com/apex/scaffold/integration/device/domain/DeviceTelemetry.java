package com.apex.scaffold.integration.device.domain;

import java.time.LocalDateTime;

/**
 * 设备遥测记录实体（方案 M5 / 批次 E3，表 {@code iot_telemetry}）。
 *
 * <p>{@code metric} 为 JSON 对象字符串；高频上行批量落库（jdbc 池隔离），
 * 按租户 + 设备 + 上报时间建索引利查询。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public class DeviceTelemetry {

    private Long id;

    private String tenantId;

    private String deviceKey;

    /** 遥测指标（JSON 对象字符串）。 */
    private String metric;

    /** 上报时间。 */
    private LocalDateTime recordedAt;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getDeviceKey() {
        return deviceKey;
    }

    public void setDeviceKey(String deviceKey) {
        this.deviceKey = deviceKey;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
