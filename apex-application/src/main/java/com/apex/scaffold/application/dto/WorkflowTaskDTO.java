package com.apex.scaffold.application.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流任务 DTO。
 *
 * <p>用于前端展示待办任务和任务列表。</p>
 *
 * @author Apex Team
 * @version 1.4.0
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTaskDTO {

    /** 任务 ID */
    private Long id;

    /** 流程实例 ID */
    private Long instanceId;

    /** 任务名称 */
    private String taskName;

    /** 任务类型：USER_TASK/SERVICE_TASK */
    private String taskType;

    /** 任务状态：PENDING/IN_PROGRESS/COMPLETED/REJECTED */
    private String status;

    /** 办理人 ID */
    private Long assigneeId;

    /** 办理人姓名 */
    private String assigneeName;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 完成时间 */
    private LocalDateTime completedTime;

    /** 审批意见 */
    private String comment;

    /** 节点 ID */
    private String nodeId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
