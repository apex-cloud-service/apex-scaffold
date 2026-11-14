package com.apex.scaffold.integration.connector.domain;

import java.time.LocalDateTime;

/**
 * 连接器配置实体（方案 M5 / 批次 E1）。
 *
 * <p>{@code configValue} 为 JSON 配置（端点、超时、重试、凭据引用等）；
 * 敏感值不在本实体中，仅以凭据引用（credentialKey）表示，实际密文存于凭据表。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public class ConnectorDefinition {

    private Long id;

    private String tenantId;

    private String connectorType;

    private String configKey;

    private String configValue;

    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer version;

    private Integer deleted;

    /** 是否启用。 */
    public boolean isActive() {
        return status != null && status == 1;
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

    public String getConnectorType() {
        return connectorType;
    }

    public void setConnectorType(String connectorType) {
        this.connectorType = connectorType;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
