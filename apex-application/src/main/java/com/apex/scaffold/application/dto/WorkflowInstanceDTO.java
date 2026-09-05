package com.apex.scaffold.application.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流实例 DTO。
 *
 * <p>用于流程实例列表展示。</p>
 *
 * @author Apex Team
 * @version 1.4.0
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstanceDTO {

    /** 流程实例 ID */
    private Long id;

    /** 流程定义 ID */
    private Long definitionId;

    /** 流程编码 */
    private String processCode;

    /** 流程标题 */
    private String title;

    /** 流程状态：DRAFT/RUNNING/APPROVED/REJECTED/CANCELLED/TERMINATED */
    private String status;

    /** 发起人 ID */
    private Long initiatorId;

    /** 发起人姓名 */
    private String initiatorName;

    /** 业务键 */
    private String businessKey;

    /** 当前节点 ID */
    private String currentNodeId;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
