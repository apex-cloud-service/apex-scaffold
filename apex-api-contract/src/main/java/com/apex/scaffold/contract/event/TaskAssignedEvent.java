package com.apex.scaffold.contract.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 任务分配事件（跨服务契约）.
 *
 * <p>由 workflow-service 在流程流转产生新待办任务（回退重建、转办、委派及初始节点分配）后发布，
 * 用于通知订阅方"某审批节点已分配给指定办理人"。
 *
 * <h2>契约说明（迁移方案 §6.4 / 执行计划 Phase 3.4）</h2>
 * <ul>
 *   <li>发布方：apex-workflow-service</li>
 *   <li>订阅方：system（生成待办）、office（审批推送）</li>
 *   <li>Topic：{@code apex-workflow-events}</li>
 *   <li>用途：审批任务分派广播，订阅方据此新增待办/推送提醒</li>
 * </ul>
 *
 * <p>事件信封字段由 {@link AbstractDomainEvent} 基类提供（eventId、createdAt、traceId、tenantId 等），
 * 本类仅携带任务分派的业务负载。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 * @see AbstractDomainEvent
 */
@Getter
public class TaskAssignedEvent extends AbstractDomainEvent {

    /** 事件类型：任务分配。 */
    private static final String EVENT_TYPE = "TASK_ASSIGNED";

    /** 任务 ID（事件源/聚合根 ID）。 */
    private final Long taskId;

    /** 所属流程实例 ID。 */
    private final Long processInstanceId;

    /** 节点编码（任务所在审批节点）。 */
    private final String nodeCode;

    /** 办理人账户 ID（任务分配给谁）。 */
    private final Long assigneeId;

    /** 分配人账户 ID（谁发起的分派，可为 {@code null} 表示系统自动分配）。 */
    private final Long assignedBy;

    /**
     * 创建任务分配事件。
     *
     * @param taskId            任务 ID
     * @param processInstanceId 所属流程实例 ID
     * @param nodeCode          节点编码
     * @param assigneeId        办理人账户 ID
     * @param assignedBy        分配人账户 ID
     */
    @JsonCreator
    public TaskAssignedEvent(
            @JsonProperty("taskId") Long taskId,
            @JsonProperty("processInstanceId") Long processInstanceId,
            @JsonProperty("nodeCode") String nodeCode,
            @JsonProperty("assigneeId") Long assigneeId,
            @JsonProperty("assignedBy") Long assignedBy) {
        super(EVENT_TYPE, null);
        this.taskId = taskId;
        this.processInstanceId = processInstanceId;
        this.nodeCode = nodeCode;
        this.assigneeId = assigneeId;
        this.assignedBy = assignedBy;
    }

    /**
     * 获取事件源 ID（聚合根 ID）。
     *
     * @return 任务 ID 的字符串表示；为空时返回 {@code null}
     */
    @Override
    public String getSourceId() {
        return taskId == null ? null : String.valueOf(taskId);
    }
}
