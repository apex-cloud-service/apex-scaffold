package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 办公平台审批流程持久化对象。
 *
 * <p>对应 {@code office_approval_process} 表，记录在钉钉、飞书、企业微信等办公平台
 * 发起的审批流程实例：平台与平台实例标识、平台流程实例 ID、审批模板、标题、
 * 发起人、状态、表单数据与审批记录、当前待审批人，以及发起/完成时间。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("office_approval_process")
public class OfficeApprovalProcessPO extends BasePO {

    @TableField("tenant_id")
    private String tenantId;

    @TableField("platform")
    private String platform;

    @TableField("platform_instance_id")
    private String platformInstanceId;

    @TableField("process_instance_id")
    private String processInstanceId;

    @TableField("template_id")
    private String templateId;

    @TableField("title")
    private String title;

    @TableField("initiator_id")
    private String initiatorId;

    @TableField("initiator_name")
    private String initiatorName;

    @TableField("status")
    private String status;

    @TableField("form_data")
    private String formData;

    @TableField("records")
    private String records;

    @TableField("current_approver_ids")
    private String currentApproverIds;

    @TableField("initiate_time")
    private LocalDateTime initiateTime;

    @TableField("complete_time")
    private LocalDateTime completeTime;
}
