package com.apex.scaffold.integration.device.domain;

import java.time.LocalDateTime;

/**
 * 轻量规则实体（方案 M5 / 批次 E3，表 {@code iot_rule}）。
 *
 * <p>{@code conditionJson} 为结构化条件（字段/操作符/阈值的 JSON，支持数组与
 * 单对象两种形态，数组为 AND 语义）；仅做声明式比较，不做任何脚本执行。
 * 动作类型 {@link #ACTION_NOTIFY} / {@link #ACTION_WEBHOOK} / {@link #ACTION_EVENT}。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public class DeviceRule {

    /** 通知动作（经可选 NotifyActionPort，接 D1 消息门面）。 */
    public static final String ACTION_NOTIFY = "NOTIFY";

    /** webhook 出站动作（接 E2 connector 出站）。 */
    public static final String ACTION_WEBHOOK = "WEBHOOK";

    /** 事件转发动作（经 DomainEventPublisher）。 */
    public static final String ACTION_EVENT = "EVENT";

    private Long id;

    private String tenantId;

    /** 规则名（租户内唯一）。 */
    private String ruleName;

    /** 结构化条件（JSON）。 */
    private String conditionJson;

    /** 动作类型（NOTIFY/WEBHOOK/EVENT）。 */
    private String actionType;

    /** 动作配置（JSON，可空；WEBHOOK 需 connectorKey）。 */
    private String actionConfig;

    /** 启停（0-停用，1-启用）。 */
    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;

    /** 是否启用。 */
    public boolean isEnabled() {
        return enabled != null && enabled == 1;
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

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getConditionJson() {
        return conditionJson;
    }

    public void setConditionJson(String conditionJson) {
        this.conditionJson = conditionJson;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getActionConfig() {
        return actionConfig;
    }

    public void setActionConfig(String actionConfig) {
        this.actionConfig = actionConfig;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
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
