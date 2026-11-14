package com.apex.scaffold.integration.device.domain;

import java.time.LocalDateTime;

/**
 * 设备下行命令实体（方案 M5 / 批次 E3，表 {@code iot_command}）。
 *
 * <p>状态机：{@code PENDING → SENT → ACK}；超时扫描将过期的
 * {@code PENDING/SENT} 置为 {@code TIMEOUT}。{@code commandId} 为调用方提供的
 * 业务幂等键（租户内唯一），重复下发返回既有命令。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public class DeviceCommand {

    /** 待下发。 */
    public static final String STATUS_PENDING = "PENDING";

    /** 已下发（broker 已接收）。 */
    public static final String STATUS_SENT = "SENT";

    /** 设备已确认。 */
    public static final String STATUS_ACK = "ACK";

    /** 超时（未在截止时间前确认）。 */
    public static final String STATUS_TIMEOUT = "TIMEOUT";

    private Long id;

    private String tenantId;

    /** 业务命令 ID（租户内唯一，幂等键）。 */
    private String commandId;

    private String deviceKey;

    /** 命令载荷（JSON 对象字符串）。 */
    private String payload;

    /** 状态（PENDING/SENT/ACK/TIMEOUT）。 */
    private String status;

    /** 下发时间。 */
    private LocalDateTime sentAt;

    /** 确认时间。 */
    private LocalDateTime ackedAt;

    /** 超时截止时间。 */
    private LocalDateTime timeoutAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 是否终态（ACK/TIMEOUT）。 */
    public boolean isTerminal() {
        return STATUS_ACK.equals(status) || STATUS_TIMEOUT.equals(status);
    }

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

    public String getCommandId() {
        return commandId;
    }

    public void setCommandId(String commandId) {
        this.commandId = commandId;
    }

    public String getDeviceKey() {
        return deviceKey;
    }

    public void setDeviceKey(String deviceKey) {
        this.deviceKey = deviceKey;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getAckedAt() {
        return ackedAt;
    }

    public void setAckedAt(LocalDateTime ackedAt) {
        this.ackedAt = ackedAt;
    }

    public LocalDateTime getTimeoutAt() {
        return timeoutAt;
    }

    public void setTimeoutAt(LocalDateTime timeoutAt) {
        this.timeoutAt = timeoutAt;
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
