package com.apex.scaffold.domain.model.entity;

import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 办公审批流程实体
 *
 * <p>用于表示来自第三方办公平台（如飞书、钉钉）的审批流程实例，记录审批的完整生命周期和状态。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class OfficeApprovalProcessEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String platform;
    private String platformInstanceId;
    private String processInstanceId;
    private String templateId;
    private String title;
    private String initiatorId;
    private String initiatorName;
    private String status;
    private String formData;
    private String records;
    private String currentApproverIds;
    private LocalDateTime initiateTime;
    private LocalDateTime completeTime;
    private String tenantId;

    public OfficeApprovalProcessEntity() {
        super();
    }

    /**
     * 设置审批来源的办公平台类型，并刷新实体更新时间。
     *
     * @param platform 办公平台类型（feishu/dingtalk/wecom）
     */
    public void setPlatform(String platform) {
        this.platform = platform;
        touch();
    }

    /**
     * 设置第三方平台中的审批实例 ID，并刷新实体更新时间。
     *
     * @param platformInstanceId 来源平台侧的审批实例唯一标识
     */
    public void setPlatformInstanceId(String platformInstanceId) {
        this.platformInstanceId = platformInstanceId;
        touch();
    }

    /**
     * 设置业务流程实例 ID，并刷新实体更新时间。
     *
     * @param processInstanceId 业务系统中的流程实例 ID，用于与本地业务流程关联
     */
    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
        touch();
    }

    /**
     * 设置审批模板 ID，并刷新实体更新时间。
     *
     * @param templateId 审批所使用的流程模板 ID
     */
    public void setTemplateId(String templateId) {
        this.templateId = templateId;
        touch();
    }

    /**
     * 设置审批标题，并刷新实体更新时间。
     *
     * @param title 审批流程的标题
     */
    public void setTitle(String title) {
        this.title = title;
        touch();
    }

    /**
     * 设置审批发起人 ID，并刷新实体更新时间。
     *
     * @param initiatorId 审批发起人的用户 ID
     */
    public void setInitiatorId(String initiatorId) {
        this.initiatorId = initiatorId;
        touch();
    }

    /**
     * 设置审批发起人姓名，并刷新实体更新时间。
     *
     * @param initiatorName 审批发起人的姓名
     */
    public void setInitiatorName(String initiatorName) {
        this.initiatorName = initiatorName;
        touch();
    }

    /**
     * 设置审批状态，并刷新实体更新时间。
     *
     * @param status 审批状态（PENDING/IN_PROGRESS/APPROVED/REJECTED/CANCELLED/TRANSFERRED）
     */
    public void setStatus(String status) {
        this.status = status;
        touch();
    }

    /**
     * 设置审批表单数据，并刷新实体更新时间。
     *
     * @param formData 审批表单数据（JSON 格式存储）
     */
    public void setFormData(String formData) {
        this.formData = formData;
        touch();
    }

    /**
     * 设置审批记录，并刷新实体更新时间。
     *
     * @param records 审批记录列表（JSON 格式存储）
     */
    public void setRecords(String records) {
        this.records = records;
        touch();
    }

    /**
     * 设置当前审批人 ID 列表，并刷新实体更新时间。
     *
     * @param currentApproverIds 当前待处理的审批人 ID 列表（JSON 格式存储）
     */
    public void setCurrentApproverIds(String currentApproverIds) {
        this.currentApproverIds = currentApproverIds;
        touch();
    }

    /**
     * 设置审批发起时间，并刷新实体更新时间。
     *
     * @param initiateTime 审批流程的发起时间
     */
    public void setInitiateTime(LocalDateTime initiateTime) {
        this.initiateTime = initiateTime;
        touch();
    }

    /**
     * 设置审批完成时间，并刷新实体更新时间。
     *
     * @param completeTime 审批流程的结束时间，未完成时为空
     */
    public void setCompleteTime(LocalDateTime completeTime) {
        this.completeTime = completeTime;
        touch();
    }

    /**
     * 设置租户 ID，并刷新实体更新时间。
     *
     * @param tenantId 审批流程所属租户的唯一标识
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        touch();
    }
}
