package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 任务持久化对象。
 *
 * <p>映射数据库表 wf_task，存储工作流待办任务的完整信息。
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li><code>timeoutAt</code>：超时时间，对应数据库字段 timeout_at</li>
 *   <li><code>timeoutStrategy</code>：超时策略，对应数据库字段 timeout_strategy</li>
 *   <li><code>transferredFrom</code>：转办来源，对应数据库字段 transferred_from</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("wf_task")
/**n * TaskPO - 持久化对象n *n * <p>对应数据库表：TODO: 补充表名n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class TaskPO extends BasePO {

    private Long tenantId;

    private Long processInstanceId;

    private String nodeCode;

    public String getNodeId() {
        return nodeCode;
    }

    public void setNodeId(String nodeId) {
        this.nodeCode = nodeId;
    }

    private String taskName;

    private Long assigneeId;

    private String status;

    private LocalDateTime dueTime;

    private LocalDateTime completeTime;

    private String remark;

    private LocalDateTime timeoutAt;

    private String timeoutStrategy;

    private Long transferredFrom;
}
