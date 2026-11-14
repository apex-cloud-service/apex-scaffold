package com.apex.scaffold.integration.connector.domain;

import java.time.LocalDateTime;

/**
 * 出站调用审计记录（方案 M5 / 批次 E2）。
 *
 * <p>由出站门面在每次连接器调用后落库；{@code errorMessage} 已脱敏，
 * 不包含凭据或完整请求头。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public class OutboundCallLog {

    private Long id;

    private String tenantId;

    private String connectorType;

    private String operation;

    private String idempotencyKey;

    /** 状态（SUCCESS/FAILED）。 */
    private String status;

    private Long durationMs;

    private String errorMessage;

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

    public String getConnectorType() {
        return connectorType;
    }

    public void setConnectorType(String connectorType) {
        this.connectorType = connectorType;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
