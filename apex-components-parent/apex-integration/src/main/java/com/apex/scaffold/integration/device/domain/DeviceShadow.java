package com.apex.scaffold.integration.device.domain;

import java.time.LocalDateTime;

/**
 * 设备影子实体（方案 M5 / 批次 E3，表 {@code iot_device_shadow}）。
 *
 * <p>{@code desired} 由平台/业务写入，{@code reported} 随遥测上行更新；
 * 两者均为 JSON 字符串，{@code version} 为乐观锁版本（每次更新递增）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public class DeviceShadow {

    private Long id;

    private String tenantId;

    private String deviceKey;

    /** 期望状态（JSON，可空）。 */
    private String desired;

    /** 上报状态（JSON，可空）。 */
    private String reported;

    /** 影子版本（乐观锁）。 */
    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

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

    public String getDesired() {
        return desired;
    }

    public void setDesired(String desired) {
        this.desired = desired;
    }

    public String getReported() {
        return reported;
    }

    public void setReported(String reported) {
        this.reported = reported;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
