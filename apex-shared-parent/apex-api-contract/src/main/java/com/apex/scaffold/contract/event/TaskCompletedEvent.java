package com.apex.scaffold.contract.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 任务完成事件（跨服务契约）.
 *
 * <p>由 workflow-service 在审批人完成（通过/驳回）待办任务后发布，用于通知订阅方
 * "某审批节点已办结"，订阅方据此更新待办状态、推送结果通知或触发下一节点联动。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>发布方：apex-workflow-service</li>
 *   <li>订阅方：system（更新待办/通知）、office（审批结果推送）</li>
 *   <li>Topic：{@code apex-workflow-events}</li>
 *   <li>用途：审批办结广播，订阅方据此收敛待办并按 {@code action} 处理结果</li>
 * </ul>
 *
 * <p>事件信封字段由 {@link AbstractDomainEvent} 基类提供（eventId、createdAt、traceId、tenantId 等），
 * 本类仅携带任务办结的业务负载。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 * @see AbstractDomainEvent
 */
@Getter
public class TaskCompletedEvent extends AbstractDomainEvent {

    /** 事件类型：任务完成。 */
    private static final String EVENT_TYPE = "TASK_COMPLETED";

    /** 审批动作：通过。 */
    public static final String ACTION_APPROVE = "APPROVE";

    /** 审批动作：驳回。 */
    public static final String ACTION_REJECT = "REJECT";

    /** 任务 ID（事件源/聚合根 ID）。 */
    private final Long taskId;

    /** 所属流程实例 ID。 */
    private final Long processInstanceId;

    /** 审批人账户 ID。 */
    private final Long approverId;

    /** 审批动作：{@link #ACTION_APPROVE} 或 {@link #ACTION_REJECT}。 */
    private final String action;

    /** 审批意见。 */
    private final String comment;

    /** 办结时间。 */
    private final LocalDateTime completedAt;

    /**
     * 创建任务完成事件。
     *
     * @param taskId            任务 ID
     * @param processInstanceId 所属流程实例 ID
     * @param approverId        审批人账户 ID
     * @param action            审批动作（APPROVE / REJECT）
     * @param comment           审批意见
     * @param completedAt       办结时间
     */
    @JsonCreator
    public TaskCompletedEvent(
            @JsonProperty("taskId") Long taskId,
            @JsonProperty("processInstanceId") Long processInstanceId,
            @JsonProperty("approverId") Long approverId,
            @JsonProperty("action") String action,
            @JsonProperty("comment") String comment,
            @JsonProperty("completedAt") LocalDateTime completedAt) {
        super(EVENT_TYPE, null);
        this.taskId = taskId;
        this.processInstanceId = processInstanceId;
        this.approverId = approverId;
        this.action = action;
        this.comment = comment;
        this.completedAt = completedAt;
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
