package com.apex.scaffold.domain.model.workflow;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 流程实例领域模型。
 *
 * <p>表示工作流的运行实例，包含流程的完整生命周期状态。
 *
 * <h2>状态说明</h2>
 * <ul>
 *   <li><strong>RUNNING</strong>：运行中</li>
 *   <li><strong>SUSPENDED</strong>：已挂起</li>
 *   <li><strong>COMPLETED</strong>：已完成</li>
 *   <li><strong>REJECTED</strong>：已驳回</li>
 *   <li><strong>TERMINATED</strong>：已终止</li>
 * </ul>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li><code>suspended</code>：挂起标志，true 表示流程已挂起，所有任务无法审批</li>
 *   <li><code>contextJson</code>：流程上下文 JSON，存储流程相关的业务数据</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.0.0
 */
@Data
public class ProcessInstance {

    private Long id;

    private Long workflowDefinitionId;

    private String businessKey;

    private String currentNodeKey;

    private String status;

    private Long initiatorId;

    private String contextJson;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;

    private Boolean suspended;
}
