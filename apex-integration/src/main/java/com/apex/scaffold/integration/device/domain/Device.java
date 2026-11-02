package com.apex.scaffold.integration.device.domain;

import java.time.LocalDateTime;

/**
 * 物联网设备实体（方案 M5 / 批次 E3，表 {@code iot_device}）。
 *
 * <p>{@code secretHash} 为 PBKDF2 哈希（明文仅在注册响应出现一次，不落库不落日志）；
 * {@code certFingerprint} 为可选的 X.509 证书指纹（设备侧凭据二选一或并存）。
 * {@code status} 仅两种取值：{@link #STATUS_ONLINE} / {@link #STATUS_OFFLINE}。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public class Device {

    /** 在线。 */
    public static final String STATUS_ONLINE = "online";

    /** 离线。 */
    public static final String STATUS_OFFLINE = "offline";

    private Long id;

    private String tenantId;

    /** 设备标识（租户内唯一，MQTT topic 命名空间段）。 */
    private String deviceKey;

    /** 设备凭据哈希（PBKDF2，格式见 {@code SecretHasher}）。 */
    private String secretHash;

    /** X.509 证书指纹（可空）。 */
    private String certFingerprint;

    /** 状态（online/offline）。 */
    private String status;

    /** 固件版本（可空）。 */
    private String firmwareVersion;

    /** 标签（JSON 对象字符串）。 */
    private String labels;

    /** 最后活跃时间。 */
    private LocalDateTime lastActiveAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;

    /** 是否在线。 */
    public boolean isOnline() {
        return STATUS_ONLINE.equals(status);
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

    public String getDeviceKey() {
        return deviceKey;
    }

    public void setDeviceKey(String deviceKey) {
        this.deviceKey = deviceKey;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    public String getCertFingerprint() {
        return certFingerprint;
    }

    public void setCertFingerprint(String certFingerprint) {
        this.certFingerprint = certFingerprint;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public void setFirmwareVersion(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
    }

    public String getLabels() {
        return labels;
    }

    public void setLabels(String labels) {
        this.labels = labels;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
