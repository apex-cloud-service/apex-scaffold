package com.apex.scaffold.domain.model.workflow;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 审批记录领域模型。
 *
 * <p>表示单次审批操作的记录。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class ApprovalRecord {

    private Long id;

    private Long processInstanceId;

    private Long taskId;

    private Long approverId;

    private String action;

    private String comment;

    private LocalDateTime createTime;

    private Integer deleted;
}
