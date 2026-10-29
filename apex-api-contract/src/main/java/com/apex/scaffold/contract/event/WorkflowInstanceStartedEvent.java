package com.apex.scaffold.contract.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 流程实例启动事件（跨服务契约）.
 *
 * <p>由 workflow-service 在发起流程实例（{@code POST /api/v1/workflows/{workflowCode}/instances}）
 * 成功后发布，用于通知各订阅方"某流程已开始流转"。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>发布方：apex-workflow-service</li>
 *   <li>订阅方：system（生成待办/通知）、office（审批联动推送）</li>
 *   <li>Topic：{@code apex-workflow-events}</li>
 *   <li>用途：审批流启动广播，订阅方据此触发后续联动动作</li>
 * </ul>
 *
 * <p>事件信封字段由 {@link AbstractDomainEvent} 基类提供（eventId、createdAt、traceId、tenantId 等），
 * 本类仅携带启动动作的业务负载。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 * @see AbstractDomainEvent
 */
@Getter
public class WorkflowInstanceStartedEvent extends AbstractDomainEvent {

    /** 事件类型：流程实例启动。 */
    private static final String EVENT_TYPE = "WORKFLOW_INSTANCE_STARTED";

    /** 流程实例 ID（事件源/聚合根 ID）。 */
    private final Long processInstanceId;

    /** 流程定义编码（如 {@code leave_approval}）。 */
    private final String workflowCode;

    /** 业务主键（关联业务单据，便于订阅方回查）。 */
    private final String businessKey;

    /** 发起人账户 ID。 */
    private final Long initiatorId;

    /** 实例开始时间。 */
    private final LocalDateTime startTime;

    /**
     * 创建流程实例启动事件。
     *
     * @param processInstanceId 流程实例 ID
     * @param workflowCode      流程定义编码
     * @param businessKey       业务主键
     * @param initiatorId       发起人账户 ID
     * @param startTime         实例开始时间
     */
    @JsonCreator
    public WorkflowInstanceStartedEvent(
            @JsonProperty("processInstanceId") Long processInstanceId,
            @JsonProperty("workflowCode") String workflowCode,
            @JsonProperty("businessKey") String businessKey,
            @JsonProperty("initiatorId") Long initiatorId,
            @JsonProperty("startTime") LocalDateTime startTime) {
        super(EVENT_TYPE, null);
        this.processInstanceId = processInstanceId;
        this.workflowCode = workflowCode;
        this.businessKey = businessKey;
        this.initiatorId = initiatorId;
        this.startTime = startTime;
    }

    /**
     * 获取事件源 ID（聚合根 ID）。
     *
     * @return 流程实例 ID 的字符串表示；为空时返回 {@code null}
     */
    @Override
    public String getSourceId() {
        return processInstanceId == null ? null : String.valueOf(processInstanceId);
    }
}
