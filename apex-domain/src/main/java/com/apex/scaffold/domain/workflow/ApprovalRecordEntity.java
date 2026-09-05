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

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public void setProcessInstanceId(Long processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    public void setApproverName(String approverName) {
        this.approverName = approverName;
    }

    public void setAction(TaskStatus action) {
        this.action = action;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
