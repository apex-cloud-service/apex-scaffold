package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("wf_workflow_definition")
public class WorkflowDefinitionPO extends BasePO {

    private Long tenantId;

    private String workflowCode;

    /**
     * 获取工作流编码（兼容方法，实际返回 workflowCode 字段值）。
     *
     * @return 工作流编码
     */
    public String getCode() {
        return workflowCode;
    }

    /**
     * 设置工作流编码（兼容方法，实际写入 workflowCode 字段）。
     *
     * @param code 工作流编码
     */
    public void setCode(String code) {
        this.workflowCode = code;
    }

    private String workflowName;

    /**
     * 获取工作流名称（兼容方法，实际返回 workflowName 字段值）。
     *
     * @return 工作流名称
     */
    public String getName() {
        return workflowName;
    }

    /**
     * 设置工作流名称（兼容方法，实际写入 workflowName 字段）。
     *
     * @param name 工作流名称
     */
    public void setName(String name) {
        this.workflowName = name;
    }

    private String description;

    private String nodeDefinition;

    private Integer version;

    private Integer enabled;

    private String remark;
}
