package com.apex.scaffold.workflow.domain.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 审批记录领域模型。
 *
 * <p>审批记录是流程任务处理过程的一次留痕，记录在某一次审批/驳回等操作中，
 * 由哪位用户针对哪个任务做出了何种处理及处理意见。</p>
 *
 * <p>该模型位于领域层，仅表达业务概念，不依赖任何持久化或框架实现。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class ApprovalRecord {

    /** 审批记录主键 ID，由持久化层分配，新建时为 {@code null}。 */
    private Long id;

    /** 所属流程实例 ID，指向 {@link ProcessInstance}。 */
    private Long processInstanceId;

    /** 关联的任务 ID，指向本次被处理的 {@link Task}。 */
    private Long taskId;

    /** 操作人用户 ID，即执行本次审批操作的用户。 */
    private Long approverId;

    /**
     * 处理动作。
     *
     * <p>典型取值：{@code APPROVE}（同意）、{@code REJECT}（驳回）、
     * {@code TRANSFER}（转办）、{@code DELEGATE}（委派）、
     * {@code ROLLBACK}（回退）等。</p>
     */
    private String action;

    /** 审批意见，操作人填写的备注或审批说明，可为空。 */
    private String comment;

    /** 创建时间，记录该审批动作发生的时刻。 */
    private LocalDateTime createTime;

    /**
     * 逻辑删除标志。
     *
     * <p>取值 0 表示未删除、1 表示已删除。</p>
     */
    private Integer deleted;
}
