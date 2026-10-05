package com.apex.scaffold.workflow.domain.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 流程实例领域模型。
 *
 * <p>流程实例是某条工作流定义在运行期的一次具体执行。它记录当前所处节点、
 * 运行状态、发起人及流程上下文（context），并关联业务主键以便与业务单据对应。</p>
 *
 * <h2>状态说明</h2>
 * <ul>
 *   <li>{@code RUNNING}：运行中，流程正在推进。</li>
 *   <li>{@code SUSPENDED}：已挂起，流程被暂停，不能继续流转。</li>
 *   <li>{@code COMPLETED}：已完成，流程正常结束。</li>
 *   <li>{@code REJECTED}：已驳回，流程被审批人驳回而结束。</li>
 *   <li>{@code TERMINATED}：已终止，流程被强制结束。</li>
 * </ul>
 *
 * <p>该模型位于领域层，仅表达业务概念，不依赖任何持久化或框架实现。</p>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.0.0
 */
@Data
public class ProcessInstance {

    /** 流程实例主键 ID，由持久化层分配，新建时为 {@code null}。 */
    private Long id;

    /** 所属流程定义 ID，指向实例化所使用的 {@link WorkflowDefinition}。 */
    private Long workflowDefinitionId;

    /**
     * 业务主键。
     *
     * <p>用于将流程实例与业务单据（订单、工单、报销单等）关联，便于反查业务数据。</p>
     */
    private String businessKey;

    /**
     * 当前所处节点标识。
     *
     * <p>指向流程 DAG 中当前激活的节点 key，流程推进时会随之更新。</p>
     */
    private String currentNodeKey;

    /**
     * 流程实例状态。
     *
     * <p>取值参见类注释中的状态说明，典型值：{@code RUNNING}、{@code SUSPENDED}、
     * {@code COMPLETED}、{@code REJECTED}、{@code TERMINATED}。</p>
     */
    private String status;

    /** 发起人用户 ID，即发起该流程实例的用户。 */
    private Long initiatorId;

    /**
     * 流程上下文 JSON。
     *
     * <p>以 JSON 字符串保存流程运行期所需的业务变量（如表单数据、审批意见等），
     * 供节点条件判断与后续审批人查看。</p>
     */
    private String contextJson;

    /** 流程开始时间，即实例被创建并开始运行的时刻。 */
    private LocalDateTime startTime;

    /**
     * 流程结束时间。
     *
     * <p>流程到达终态（完成、驳回或终止）时的时刻；运行中为 {@code null}。</p>
     */
    private LocalDateTime endTime;

    /** 创建时间，记录记录写入的时刻。 */
    private LocalDateTime createTime;

    /** 更新时间，记录该实例最近一次被修改的时刻。 */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标志。
     *
     * <p>取值 0 表示未删除、1 表示已删除。</p>
     */
    private Integer deleted;

    /**
     * 挂起标志。
     *
     * <p>{@code true} 表示实例已挂起（与状态 {@code SUSPENDED} 对应），
     * 挂起期间不允许任务推进。</p>
     */
    private Boolean suspended;
}
