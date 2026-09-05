package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
@EqualsAndHashCode(callSuper = true)
@TableName("wf_task")
public class TaskPO extends BasePO {

    public TaskPO() {}

    private Long tenantId;

    private Long processInstanceId;

    private String nodeCode;

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
