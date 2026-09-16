package com.apex.scaffold.infrastructure.office.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批流程持久化对象
 *
 * <p>对应数据库表 office_approval_process，存储审批流程的完整信息。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("office_approval_process")
public class ApprovalProcessPO {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 办公平台类型
     */
    private String platform;

    /**
     * 应用ID
     */
    private Long appId;

    /**
     * 流程定义ID
     */
    private String processDefinitionId;

    /**
     * 流程实例ID
     */
    private String processInstanceId;

    /**
     * 平台流程实例ID
     */
    private String platformInstanceId;

    /**
     * 流程名称
     */
    private String processName;

    /**
     * 发起人ID
     */
    private String initiatorId;

    /**
     * 发起人姓名
     */
    private String initiatorName;

    /**
     * 审批状态
     */
    private String status;

    /**
     * 当前审批节点
     */
    private String currentNode;

    /**
     * 当前审批人列表（JSON数组）
     */
    private String currentApproverIds;

    /**
     * 表单数据（JSON）
     */
    private String formData;

    /**
     * 审批记录列表（JSON数组）
     */
    private String records;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 版本号（乐观锁）
     */
    @Version
    private Integer version;

    /**
     * 逻辑删除标志
     */
    @TableLogic
    private Boolean deleted;
}
