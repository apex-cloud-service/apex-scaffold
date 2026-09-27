package com.apex.scaffold.domain.workflow;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import lombok.Getter;

/**
 * 审批记录实体。
 *
 * <p>记录单次审批操作的详细信息。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public class ApprovalRecordEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private Long processInstanceId;
    private Long taskId;
    private String nodeId;
    private String nodeName;
    private Long approverId;
    private String approverName;
    private TaskStatus action;
    private String comment;

    protected ApprovalRecordEntity() {
        super();
    }

    /**
     * 创建审批记录实体
     *
     * @param tenantId 租户 ID
     * @param processInstanceId 所属流程实例 ID
     * @param taskId 关联的任务 ID
     * @param nodeId 审批节点 ID
     * @param nodeName 审批节点名称
     * @param approverId 审批人用户 ID
     * @param approverName 审批人姓名
     * @param action 审批动作（通过/拒绝/转交等任务状态）
     * @param comment 审批意见
     * @return 已初始化时间戳的审批记录实体
     */
    public static ApprovalRecordEntity create(
            Long tenantId,
            Long processInstanceId,
            Long taskId,
            String nodeId,
            String nodeName,
            Long approverId,
            String approverName,
            TaskStatus action,
            String comment) {
        ApprovalRecordEntity record = new ApprovalRecordEntity();
        record.tenantId = tenantId;
        record.processInstanceId = processInstanceId;
        record.taskId = taskId;
        record.nodeId = nodeId;
        record.nodeName = nodeName;
        record.approverId = approverId;
        record.approverName = approverName;
        record.action = action;
        record.comment = comment;
        record.initTimestamps();
        return record;
    }

    /**
     * 设置租户 ID
     *
     * @param tenantId 租户 ID
     */
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * 设置所属流程实例 ID
     *
     * @param processInstanceId 所属流程实例 ID
     */
    public void setProcessInstanceId(Long processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    /**
     * 设置关联的任务 ID
     *
     * @param taskId 关联的任务 ID
     */
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    /**
     * 设置审批节点 ID
     *
     * @param nodeId 审批节点 ID
     */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * 设置审批节点名称
     *
     * @param nodeName 审批节点名称
     */
    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    /**
     * 设置审批人用户 ID
     *
     * @param approverId 审批人用户 ID
     */
    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    /**
     * 设置审批人姓名
     *
     * @param approverName 审批人姓名
     */
    public void setApproverName(String approverName) {
        this.approverName = approverName;
    }

    /**
     * 设置审批动作
     *
     * @param action 审批动作（通过/拒绝/转交等任务状态）
     */
    public void setAction(TaskStatus action) {
        this.action = action;
    }

    /**
     * 设置审批意见
     *
     * @param comment 审批意见
     */
    public void setComment(String comment) {
        this.comment = comment;
    }
}
