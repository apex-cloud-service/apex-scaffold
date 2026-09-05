package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流定义持久化对象。
 *
 * <p>映射数据库表 wf_workflow_definition，存储工作流模板定义信息。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_workflow_definition")
public class WorkflowDefinitionPO extends BasePO {

    public WorkflowDefinitionPO() {}

    private Long tenantId;

    private String workflowCode;

    private String workflowName;

    private String description;

    private String nodeDefinition;

    private Integer version;

    private Integer enabled;

    private String remark;
}
