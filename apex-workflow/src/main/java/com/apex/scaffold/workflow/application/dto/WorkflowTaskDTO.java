package com.apex.scaffold.workflow.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流任务数据传输对象
 *
 * <p>用于前端展示待办任务和任务列表，承载单个任务节点的核心信息。
 * 支持用户任务和服务任务，提供任务办理、转派、委派等操作所需的数据。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>待办列表：展示当前用户需要处理的任务</li>
 *   <li>任务详情：查看任务的详细信息和历史记录</li>
 *   <li>任务监控：管理员查看所有任务的处理情况</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code USER_TASK}：人工任务，需要用户手动审批或处理</li>
 *   <li>{@code SERVICE_TASK}：服务任务，由系统自动执行</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code PENDING}：待处理，任务已创建但未开始</li>
 *   <li>{@code IN_PROGRESS}：处理中，任务正在被处理</li>
 *   <li>{@code COMPLETED}：已完成，任务处理完成</li>
 *   <li>{@code REJECTED}：已拒绝，任务被驳回</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工作流任务DTO，用于前端展示待办任务和任务列表，承载单个任务节点的核心信息")
public class WorkflowTaskDTO {

    /**
     * 任务 ID
     *
     * <p>任务的唯一标识。
     */
    @Schema(description = "任务ID", example = "5001")
    private Long id;

    /**
     * 流程实例 ID
     *
     * <p>任务所属的流程实例标识，用于关联整个流程的上下文。
     */
    @Schema(description = "所属流程实例ID", example = "3001")
    private Long instanceId;

    /**
     * 任务名称
     *
     * <p>任务节点的名称，通常描述该节点要做什么。
     *
     * <p>示例：{@code 部门经理审批}、{@code 财务复核}、{@code 总经理审批}
     */
    @Schema(description = "任务名称，如 部门经理审批、财务复核", example = "部门经理审批")
    private String taskName;

    /**
     * 任务类型
     *
     * <p>标识任务是人工任务还是服务任务。
     *
     * <p>可选值：
     * <ul>
     *   <li>{@code USER_TASK}：人工任务，需要用户审批</li>
     *   <li>{@code SERVICE_TASK}：服务任务，系统自动执行</li>
     * </ul>
     */
    @Schema(description = "任务类型：USER_TASK(人工任务)/SERVICE_TASK(服务任务)", example = "USER_TASK")
    private String taskType;

    /**
     * 任务状态
     *
     * <p>任务的当前处理状态。
     *
     * <p>可选值：
     * <ul>
     *   <li>{@code PENDING}：待处理</li>
     *   <li>{@code IN_PROGRESS}：处理中</li>
     *   <li>{@code COMPLETED}：已完成</li>
     *   <li>{@code REJECTED}：已拒绝</li>
     * </ul>
     */
    @Schema(description = "任务状态：PENDING(待处理)/IN_PROGRESS(处理中)/COMPLETED(已完成)/REJECTED(已拒绝)", example = "PENDING")
    private String status;

    /**
     * 办理人 ID
     *
     * <p>当前任务的处理人用户 ID。
     * 对于待办任务，此字段为分配到的处理人；对于已完成任务，此字段为实际办理人。
     */
    @Schema(description = "办理人用户ID，待办任务为被分配人，已完成任务为实际办理人", example = "2001")
    private Long assigneeId;

    /**
     * 办理人姓名
     *
     * <p>当前任务处理人的显示名称，用于前端展示。
     *
     * <p>示例：{@code 张三}、{@code 李经理}
     */
    @Schema(description = "办理人姓名", example = "李经理")
    private String assigneeName;

    /**
     * 开始时间
     *
     * <p>任务开始处理的时间，通常是用户点击"办理"或任务被分配的时间。
     */
    @Schema(description = "任务开始处理时间", example = "2026-09-26T10:05:00")
    private LocalDateTime startTime;

    /**
     * 完成时间
     *
     * <p>任务完成的时间，包括审批通过、驳回等所有终态的时间。
     * 未完成任务此字段为 null。
     */
    @Schema(description = "任务完成时间（含通过、驳回等终态），未完成为空", example = "2026-09-26T11:00:00")
    private LocalDateTime completedTime;

    /**
     * 审批意见
     *
     * <p>办理人在处理任务时填写的意见或备注。
     *
     * <p>示例：{@code 同意}、{@code 需要补充材料}、{@code 金额超出审批范围，建议调整}
     */
    @Schema(description = "审批意见，办理人处理任务时填写的意见或备注", example = "同意")
    private String comment;

    /**
     * 节点 ID
     *
     * <p>任务在流程定义中的节点标识，用于关联流程定义中的节点配置。
     */
    @Schema(description = "流程定义中的节点ID", example = "node_2")
    private String nodeId;

    /**
     * 创建时间
     *
     * <p>任务记录首次创建的时间。
     */
    @Schema(description = "任务创建时间", example = "2026-09-26T10:00:00")
    private LocalDateTime createTime;
}
