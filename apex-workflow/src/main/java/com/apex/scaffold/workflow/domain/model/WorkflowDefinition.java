package com.apex.scaffold.workflow.domain.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 工作流定义领域模型。
 *
 * <p>工作流定义是流程模板的抽象，描述一条业务流程应如何流转。它保存流程编码、
 * 名称、版本以及以 JSON 形式表达的流程有向无环图（DAG，包含节点与连线）。
 * 一个流程编码可以存在多个版本，运行时按版本选择具体的定义进行实例化。</p>
 *
 * <h2>核心属性</h2>
 * <ul>
 *   <li>{@link #workflowCode}：流程编码，业务上唯一标识一类流程（同一编码可多版本）。</li>
 *   <li>{@link #version}：流程版本号，用于流程定义的多版本管理。</li>
 *   <li>{@link #dagJson}：流程 DAG 的 JSON 描述，包含节点、连线及节点配置。</li>
 *   <li>{@link #enabled}：启用标志，只有启用的定义才能被实例化。</li>
 * </ul>
 *
 * <h2>生命周期</h2>
 * <p>流程定义由管理员创建、编辑与发布；发布后的定义通常不再直接修改，而是通过
 * 提升 {@code version} 生成新版本，以保证已运行实例的稳定性。</p>
 *
 * <p>该模型位于领域层，仅表达业务概念与规则，不依赖任何持久化或框架实现。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class WorkflowDefinition {

    /** 流程定义主键 ID，由持久化层分配，新建时为 {@code null}。 */
    private Long id;

    /**
     * 流程编码。
     *
     * <p>业务上用于唯一标识一类流程（如 {@code leave_approval}）。实例化流程时
     * 依据该编码定位流程定义。</p>
     */
    private String workflowCode;

    /** 流程名称，面向用户展示的可读名称（如“请假审批流程”）。 */
    private String workflowName;

    /** 流程描述，用于说明该流程的业务用途与适用范围。 */
    private String description;

    /**
     * 流程版本号。
     *
     * <p>同一 {@link #workflowCode} 下可存在多个版本，数值越大表示越新的版本。</p>
     */
    private Integer version;

    /**
     * 流程 DAG 的 JSON 描述。
     *
     * <p>以 JSON 字符串形式保存流程的节点（{@code nodes}）与连线（{@code edges}）
     * 及其配置，是流程运行与前端流程图渲染的数据来源。</p>
     */
    private String dagJson;

    /**
     * 启用标志。
     *
     * <p>取值 1 表示已启用、0 表示已停用。只有启用的流程定义才允许被实例化。</p>
     */
    private Integer enabled;

    /**
     * 生命周期状态。
     *
     * <p>草稿定义可编辑但不可实例化；发布后的定义内容不可变；停用仅阻止新的实例化。</p>
     */
    private WorkflowDefinitionStatus status;

    /** 发布时刻，仅在发布后写入。 */
    private LocalDateTime publishedAt;

    /** 发布人用户 ID，仅在发布后写入。 */
    private Long publishedBy;

    /** 创建时间，记录该流程定义首次创建的时刻。 */
    private LocalDateTime createTime;

    /** 更新时间，记录该流程定义最近一次被修改的时刻。 */
    private LocalDateTime updateTime;

    /** 创建人用户 ID。 */
    private Long createBy;

    /** 更新人用户 ID。 */
    private Long updateBy;

    /**
     * 逻辑删除标志。
     *
     * <p>取值 0 表示未删除、1 表示已删除。采用逻辑删除以便审计与数据追溯。</p>
     */
    private Integer deleted;

    /**
     * 判断是否为草稿状态。
     *
     * @return 草稿返回 {@code true}
     */
    public boolean isDraft() {
        return status == WorkflowDefinitionStatus.DRAFT;
    }

    /**
     * 判断是否为已发布状态。
     *
     * @return 已发布返回 {@code true}
     */
    public boolean isPublished() {
        return status == WorkflowDefinitionStatus.PUBLISHED;
    }

    /**
     * 判断是否允许编辑。
     *
     * <p>只有草稿可以编辑，已发布或已停用的定义内容不可变。</p>
     *
     * @return 可编辑返回 {@code true}
     */
    public boolean isEditable() {
        return isDraft();
    }

    /**
     * 发布当前定义。
     *
     * <p>发布后内容不可变，实例化时固定使用该版本。</p>
     *
     * @param operatorId 发布人用户 ID
     * @param now        发布时刻
     * @throws IllegalStateException 当定义已处于发布状态时
     */
    public void publish(Long operatorId, LocalDateTime now) {
        if (status == WorkflowDefinitionStatus.PUBLISHED) {
            throw new IllegalStateException("流程定义已发布");
        }
        this.status = WorkflowDefinitionStatus.PUBLISHED;
        this.enabled = 1;
        this.publishedAt = now;
        this.publishedBy = operatorId;
    }

    /**
     * 启用当前定义，使其允许被实例化。
     *
     * @throws IllegalStateException 当定义仍为草稿时
     */
    public void enable() {
        if (status == WorkflowDefinitionStatus.DRAFT) {
            throw new IllegalStateException("流程定义尚未发布，不能启用");
        }
        this.status = WorkflowDefinitionStatus.PUBLISHED;
        this.enabled = 1;
    }

    /**
     * 停用当前定义，阻止新的实例化。
     *
     * @throws IllegalStateException 当定义仍为草稿时
     */
    public void disable() {
        if (status == WorkflowDefinitionStatus.DRAFT) {
            throw new IllegalStateException("流程定义尚未发布，不能停用");
        }
        this.status = WorkflowDefinitionStatus.DISABLED;
        this.enabled = 0;
    }
}
