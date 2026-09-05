package com.apex.scaffold.domain.model.workflow;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务领域模型。
 *
 * <p>表示工作流中的待办任务，包含任务状态、审批人、超时时间等完整信息。
 *
 * <h2>状态说明</h2>
 * <ul>
 *   <li><strong>PENDING</strong>：待办</li>
 *   <li><strong>COMPLETED</strong>：已完成</li>
 *   <li><strong>REJECTED</strong>：已驳回</li>
 *   <li><strong>ROLLED_BACK</strong>：已回退</li>
 *   <li><strong>DELEGATED</strong>：已委派</li>
 *   <li><strong>CANCELLED</strong>：已取消</li>
 * </ul>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li><code>nodeCode</code>：节点编码，对应流程定义中的节点</li>
 *   <li><code>timeoutAt</code>：超时时间，超过此时间任务未完成则触发超时处理</li>
 *   <li><code>timeoutStrategy</code>：超时策略，ESCALATE(升级)/AUTO_COMPLETE(自动完成)/NOTIFY(通知)</li>
 *   <li><code>transferredFrom</code>：转办来源，记录原审批人ID（转办/委派场景）</li>
 *   <li><code>createdTime</code>：任务创建时间</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.0.0
 */
@Data
public class Task {

    private Long id;

    private Long processInstanceId;

    private String nodeKey;

    private String nodeCode;

    private String nodeName;

    private Long assigneeId;

    private String status;

    private LocalDateTime dueDate;

    private LocalDateTime completedTime;

    private LocalDateTime createTime;

    private LocalDateTime createdTime;

    private LocalDateTime updateTime;

    private Integer deleted;

    private LocalDateTime timeoutAt;

    private String timeoutStrategy;

    private Long transferredFrom;
}
