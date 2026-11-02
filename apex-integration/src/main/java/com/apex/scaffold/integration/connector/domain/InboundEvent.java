package com.apex.scaffold.integration.connector.domain;

import java.time.LocalDateTime;

/**
 * 入站 webhook 事件记录（方案 M5 / 批次 E2）。
 *
 * <p>签名校验失败的投递同样落库（{@code signatureValid=0}）供审计与重放排查。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public class InboundEvent {

    private Long id;

    private String tenantId;

    private String connectorType;

    private String eventType;

    /** 事件载荷（JSON 原文）。 */
    private String payload;

    /** 签名校验（0-失败，1-通过）。 */
    private Integer signatureValid;

    /** 处理状态（0-待处理，1-已处理）。 */
    private Integer processed;

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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Integer getSignatureValid() {
        return signatureValid;
    }

    public void setSignatureValid(Integer signatureValid) {
        this.signatureValid = signatureValid;
    }

    public Integer getProcessed() {
        return processed;
    }

    public void setProcessed(Integer processed) {
        this.processed = processed;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
