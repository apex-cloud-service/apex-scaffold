package com.apex.scaffold.contract.dto.internal;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流实例状态（内部 API 契约）.
 *
 * <p>由 workflow-service 的 {@code GET /internal/v1/instances/{id}/status} 提供，
 * 供 office-service（审批联动推送）、system-service（待办/通知）查询审批实例的当前状态，
 * 仅返回状态维度字段，不暴露流程变量明细。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstanceStatusDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流程实例 ID */
    private String instanceId;

    /** 流程定义 Key（如 {@code leave_approval}） */
    private String processDefinitionKey;

    /** 业务单据 ID（关联业务主键，便于消费方回查） */
    private String businessKey;

    /** 实例状态（沿用 workflow 域枚举语义，如 {@code RUNNING} / {@code COMPLETED} / {@code TERMINATED}） */
    private String status;

    /** 当前活动节点名称（进行中时返回，便于展示"当前环节"） */
    private String currentActivity;

    /** 发起人账户 ID */
    private Long startAccountId;

    /** 租户 ID */
    private Long tenantId;

    /** 实例开始时间 */
    private LocalDateTime startTime;

    /** 实例结束时间（未结束时为 {@code null}） */
    private LocalDateTime endTime;
}
