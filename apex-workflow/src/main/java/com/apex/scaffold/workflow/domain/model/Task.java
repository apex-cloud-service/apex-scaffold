package com.apex.scaffold.workflow.domain.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 流程任务领域模型。
 *
 * <p>任务是流程运行中分配给具体审批人的执行单元，记录任务所属实例、节点信息、
 * 待办人、状态以及超时策略等。</p>
 *
 * <h2>状态说明</h2>
 * <ul>
 *   <li>{@code PENDING}：待处理，任务已生成、等待审批人操作。</li>
 *   <li>{@code COMPLETED}：已完成，审批人完成了任务。</li>
 *   <li>{@code REJECTED}：已驳回，审批人驳回了任务。</li>
 *   <li>{@code ROLLED_BACK}：已回退，任务被回滚到之前的节点。</li>
 *   <li>{@code DELEGATED}：已委派，任务被委派给他人处理。</li>
 *   <li>{@code CANCELLED}：已取消，任务因流程终止等原因被取消。</li>
 * </ul>
 *
 * <h2>超时策略</h2>
 * <ul>
 *   <li>{@code ESCALATE}：升级，任务超时后升级给上级或管理员。</li>
 *   <li>{@code AUTO_COMPLETE}：自动完成，任务超时后自动通过。</li>
 *   <li>{@code NOTIFY}：通知，任务超时后仅发送提醒通知。</li>
 * </ul>
 *
 * <p>该模型位于领域层，仅表达业务概念，不依赖任何持久化或框架实现。</p>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.0.0
 */
@Data
public class Task {

    /** 任务主键 ID，由持久化层分配，新建时为 {@code null}。 */
    private Long id;

    /** 所属流程实例 ID，指向 {@link ProcessInstance}。 */
    private Long processInstanceId;

    /**
     * 节点标识。
     *
     * <p>该任务在流程 DAG 中对应的节点 key。</p>
     */
    private String nodeKey;

    /** 节点编码，节点的业务编码，用于回退等场景定位。 */
    private String nodeCode;

    /** 节点名称，当前节点的展示名称。 */
    private String nodeName;

    /**
     * 待办人用户 ID。
     *
     * <p>当前可处理该任务的用户；若任务被转办或委派，则为当前的实际处理人。</p>
     */
    private Long assigneeId;

    /**
     * 任务状态。
     *
     * <p>取值参见类注释中的状态说明。</p>
     */
    private String status;

    /**
     * 处理截止时间。
     *
     * <p>任务应在该时间前完成，用于超时检测与提醒。</p>
     */
    private LocalDateTime dueDate;

    /** 完成时间，任务被处理完成的时刻；未完成时为 {@code null}。 */
    private LocalDateTime completedTime;

    /** 创建时间，记录任务生成的时刻。 */
    private LocalDateTime createTime;

    /** 创建时间（兼容旧字段），与 {@link #createTime} 含义一致。 */
    private LocalDateTime createdTime;

    /** 更新时间，记录该任务最近一次被修改的时刻。 */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标志。
     *
     * <p>取值 0 表示未删除、1 表示已删除。</p>
     */
    private Integer deleted;

    /**
     * 超时时间点。
     *
     * <p>任务达到该时间时触发超时逻辑，优先级高于 {@link #dueDate}。</p>
     */
    private LocalDateTime timeoutAt;

    /**
     * 超时策略。
     *
     * <p>取值参见类注释中的超时策略说明：{@code ESCALATE}、
     * {@code AUTO_COMPLETE}、{@code NOTIFY}。</p>
     */
    private String timeoutStrategy;

    /**
     * 转办来源用户 ID。
     *
     * <p>当任务被转办时，记录原处理人；未被转办时为 {@code null}。</p>
     */
    private Long transferredFrom;
}