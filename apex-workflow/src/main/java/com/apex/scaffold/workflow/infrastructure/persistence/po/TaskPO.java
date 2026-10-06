package com.apex.scaffold.workflow.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 流程任务持久化对象（PO）。
 *
 * <p>映射数据库表 {@code wf_task}，存储工作流待办任务的完整信息。
 * 继承 {@link BasePO} 获得主键、创建/更新时间、乐观锁版本与逻辑删除等通用字段。</p>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>{@code timeoutAt}：超时时间，对应数据库字段 {@code timeout_at}。</li>
 *   <li>{@code timeoutStrategy}：超时策略，对应数据库字段 {@code timeout_strategy}。</li>
 *   <li>{@code transferredFrom}：转办来源，对应数据库字段 {@code transferred_from}。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.0.0
 * @see com.apex.scaffold.workflow.domain.model.Task
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("wf_task")
public class TaskPO extends BasePO {

    /** 租户 ID，用于多租户数据隔离。 */
    private Long tenantId;

    /** 所属流程实例 ID。 */
    private Long processInstanceId;

    /** 节点编码，该任务所处的流程节点。 */
    private String nodeCode;

    /** 任务名称，当前节点的展示名称。 */
    private String taskName;

    /** 待办人用户 ID。 */
    private Long assigneeId;

    /** 任务状态，取值参见领域模型 {@code Task} 状态说明。 */
    private String status;

    /** 处理截止时间，对应数据库字段 {@code due_time}。 */
    private LocalDateTime dueTime;

    /** 完成时间，对应数据库字段 {@code complete_time}。 */
    private LocalDateTime completeTime;

    /** 备注信息。 */
    private String remark;

    /** 超时时间点。 */
    private LocalDateTime timeoutAt;

    /** 超时策略，取值参见领域模型 {@code Task} 超时策略说明。 */
    private String timeoutStrategy;

    /** 转办来源用户 ID，未转办时为 {@code null}。 */
    private Long transferredFrom;

    /**
     * 获取节点 ID（兼容方法，实际返回 {@link #nodeCode} 字段值）。
     *
     * @return 节点 ID
     */
    public String getNodeId() {
        return nodeCode;
    }

    /**
     * 设置节点 ID（兼容方法，实际写入 {@link #nodeCode} 字段）。
     *
     * @param nodeId 节点 ID
     */
    public void setNodeId(String nodeId) {
        this.nodeCode = nodeId;
    }
}
