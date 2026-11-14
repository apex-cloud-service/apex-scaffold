package com.apex.scaffold.workflow.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流实例 DTO。
 *
 * <p>用于流程实例列表展示。</p>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工作流实例DTO，用于流程实例列表展示")
public class WorkflowInstanceDTO {

    /** 流程实例 ID */
    @Schema(description = "流程实例ID", example = "3001")
    private Long id;

    /** 流程定义 ID */
    @Schema(description = "流程定义ID", example = "1001")
    private Long definitionId;

    /** 流程编码 */
    @Schema(description = "流程编码", example = "leave_approval")
    private String processCode;

    /** 流程标题 */
    @Schema(description = "流程标题", example = "张三的请假申请")
    private String title;

    /** 流程状态：DRAFT/RUNNING/APPROVED/REJECTED/CANCELLED/TERMINATED/SUSPENDED */
    @Schema(
            description = "流程状态：DRAFT(草稿)/RUNNING(运行中)/APPROVED(已通过)/REJECTED(已驳回)"
                    + "/CANCELLED(已取消)/TERMINATED(已终止)/SUSPENDED(已挂起)",
            example = "RUNNING")
    private String status;

    /** 是否已挂起 */
    @Schema(description = "是否已挂起：true-挂起，false-正常", example = "false")
    private Boolean suspended;

    /** 发起人 ID */
    @Schema(description = "发起人用户ID", example = "1001")
    private Long initiatorId;

    /** 发起人姓名 */
    @Schema(description = "发起人姓名", example = "张三")
    private String initiatorName;

    /** 业务键 */
    @Schema(description = "业务键，关联发起流程的业务单据标识", example = "LEAVE-20260926-0001")
    private String businessKey;

    /** 当前节点 ID */
    @Schema(description = "当前节点ID", example = "node_2")
    private String currentNodeId;

    /** 开始时间 */
    @Schema(description = "流程开始时间", example = "2026-09-26T10:00:00")
    private LocalDateTime startTime;

    /** 结束时间 */
    @Schema(description = "流程结束时间，未结束为空", example = "2026-09-26T15:30:00")
    private LocalDateTime endTime;

    /** 创建时间 */
    @Schema(description = "实例创建时间", example = "2026-09-26T10:00:00")
    private LocalDateTime createTime;
}
