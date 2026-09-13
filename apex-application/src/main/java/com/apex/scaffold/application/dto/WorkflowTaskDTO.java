package com.apex.scaffold.application.dto;

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
 * <h2>使用场景</h2>
 * <ul>
 *   <li>待办列表：展示当前用户需要处理的任务</li>
 *   <li>任务详情：查看任务的详细信息和历史记录</li>
 *   <li>任务监控：管理员查看所有任务的处理情况</li>
 * </ul>
 *
 * <h2>任务类型</h2>
 * <ul>
 *   <li>{@code USER_TASK}：人工任务，需要用户手动审批或处理</li>
 *   <li>{@code SERVICE_TASK}：服务任务，由系统自动执行</li>
 * </ul>
 *
 * <h2>任务状态</h2>
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
/**n * WorkflowTaskDTOn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class WorkflowTaskDTO {

    /**
     * 任务 ID
     *
     * <p>任务的唯一标识。
     */
    private Long id;

    /**
     * 流程实例 ID
     *
     * <p>任务所属的流程实例标识，用于关联整个流程的上下文。
     */
    private Long instanceId;

    /**
     * 任务名称
     *
     * <p>任务节点的名称，通常描述该节点要做什么。
     *
     * <p>示例：{@code 部门经理审批}、{@code 财务复核}、{@code 总经理审批}
     */
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
    private String status;

    /**
     * 办理人 ID
     *
     * <p>当前任务的处理人用户 ID。
     * 对于待办任务，此字段为分配到的处理人；对于已完成任务，此字段为实际办理人。
     */
    private Long assigneeId;

    /**
     * 办理人姓名
     *
     * <p>当前任务处理人的显示名称，用于前端展示。
     *
     * <p>示例：{@code 张三}、{@code 李经理}
     */
    private String assigneeName;

    /**
     * 开始时间
     *
     * <p>任务开始处理的时间，通常是用户点击"办理"或任务被分配的时间。
     */
    private LocalDateTime startTime;

    /**
     * 完成时间
     *
     * <p>任务完成的时间，包括审批通过、驳回等所有终态的时间。
     * 未完成任务此字段为 null。
     */
    private LocalDateTime completedTime;

    /**
     * 审批意见
     *
     * <p>办理人在处理任务时填写的意见或备注。
     *
     * <p>示例：{@code 同意}、{@code 需要补充材料}、{@code 金额超出审批范围，建议调整}
     */
    private String comment;

    /**
     * 节点 ID
     *
     * <p>任务在流程定义中的节点标识，用于关联流程定义中的节点配置。
     */
    private String nodeId;

    /**
     * 创建时间
     *
     * <p>任务记录首次创建的时间。
     */
    private LocalDateTime createTime;
}
