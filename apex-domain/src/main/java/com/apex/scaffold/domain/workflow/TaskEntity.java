package com.apex.scaffold.domain.workflow;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 任务实体。
 *
 * <p>表示工作流中的一个待办任务。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public class TaskEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private Long processInstanceId;
    private String nodeId;
    private String nodeName;
    private TaskStatus status;
    private Long assigneeId;
    private String assigneeName;
    private String comment;
    private LocalDateTime dueTime;
    private LocalDateTime completedTime;

    protected TaskEntity() {
        super();
    }

    public static TaskEntity create(
            Long tenantId,
            Long processInstanceId,
            String nodeId,
            String nodeName,
            Long assigneeId,
            String assigneeName) {
        TaskEntity task = new TaskEntity();
        task.tenantId = tenantId;
        task.processInstanceId = processInstanceId;
        task.nodeId = nodeId;
        task.nodeName = nodeName;
        task.status = TaskStatus.PENDING;
        task.assigneeId = assigneeId;
        task.assigneeName = assigneeName;
        task.initTimestamps();
        return task;
    }

    public void approve(String comment) {
        this.status = TaskStatus.APPROVED;
        this.comment = comment;
        this.completedTime = LocalDateTime.now();
        touch();
    }

    public void reject(String comment) {
        this.status = TaskStatus.REJECTED;
        this.comment = comment;
        this.completedTime = LocalDateTime.now();
        touch();
    }

    public void transfer(Long newAssigneeId, String newAssigneeName) {
        this.status = TaskStatus.TRANSFERRED;
        this.assigneeId = newAssigneeId;
        this.assigneeName = newAssigneeName;
        touch();
    }

    public void cancel() {
        this.status = TaskStatus.CANCELLED;
        this.completedTime = LocalDateTime.now();
        touch();
    }

    public void setDueTime(LocalDateTime dueTime) {
        this.dueTime = dueTime;
        touch();
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public void setProcessInstanceId(Long processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    public void setAssigneeName(String assigneeName) {
        this.assigneeName = assigneeName;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void setCompletedTime(LocalDateTime completedTime) {
        this.completedTime = completedTime;
    }
}
