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

    /**
     * 创建待办任务实体
     *
     * @param tenantId 租户 ID
     * @param processInstanceId 所属流程实例 ID
     * @param nodeId 所属节点 ID
     * @param nodeName 所属节点名称
     * @param assigneeId 办理人用户 ID
     * @param assigneeName 办理人姓名
     * @return 初始状态为待处理（PENDING）且已初始化时间戳的任务实体
     */
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

    /**
     * 通过任务，记录审批意见和完成时间
     *
     * @param comment 审批意见
     */
    public void approve(String comment) {
        this.status = TaskStatus.APPROVED;
        this.comment = comment;
        this.completedTime = LocalDateTime.now();
        touch();
    }

    /**
     * 拒绝任务，记录审批意见和完成时间
     *
     * @param comment 审批意见
     */
    public void reject(String comment) {
        this.status = TaskStatus.REJECTED;
        this.comment = comment;
        this.completedTime = LocalDateTime.now();
        touch();
    }

    /**
     * 将任务转交给新的办理人
     *
     * @param newAssigneeId 新办理人用户 ID
     * @param newAssigneeName 新办理人姓名
     */
    public void transfer(Long newAssigneeId, String newAssigneeName) {
        this.status = TaskStatus.TRANSFERRED;
        this.assigneeId = newAssigneeId;
        this.assigneeName = newAssigneeName;
        touch();
    }

    /**
     * 取消任务，记录完成时间
     */
    public void cancel() {
        this.status = TaskStatus.CANCELLED;
        this.completedTime = LocalDateTime.now();
        touch();
    }

    /**
     * 设置任务截止时间
     *
     * @param dueTime 截止时间
     */
    public void setDueTime(LocalDateTime dueTime) {
        this.dueTime = dueTime;
        touch();
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
     * 设置所属节点 ID
     *
     * @param nodeId 所属节点 ID
     */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * 设置所属节点名称
     *
     * @param nodeName 所属节点名称
     */
    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    /**
     * 设置任务状态
     *
     * @param status 任务状态
     */
    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    /**
     * 设置办理人用户 ID
     *
     * @param assigneeId 办理人用户 ID
     */
    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    /**
     * 设置办理人姓名
     *
     * @param assigneeName 办理人姓名
     */
    public void setAssigneeName(String assigneeName) {
        this.assigneeName = assigneeName;
    }

    /**
     * 设置审批意见
     *
     * @param comment 审批意见
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * 设置任务完成时间
     *
     * @param completedTime 任务完成时间
     */
    public void setCompletedTime(LocalDateTime completedTime) {
        this.completedTime = completedTime;
    }
}
