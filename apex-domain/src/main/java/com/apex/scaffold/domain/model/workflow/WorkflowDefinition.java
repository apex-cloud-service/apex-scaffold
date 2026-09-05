package com.apex.scaffold.domain.model.workflow;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 工作流定义领域模型。
 *
 * <p>表示工作流模板的定义信息。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class WorkflowDefinition {

    private Long id;

    private String workflowCode;

    private String workflowName;

    private String description;

    private Integer version;

    private String dagJson;

    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;

    private Integer deleted;
}
