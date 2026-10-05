package com.apex.scaffold.workflow.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 工作流定义持久化对象（PO）。
 *
 * <p>映射数据库表 {@code wf_workflow_definition}，存储工作流模板定义信息。
 * 继承 {@link BasePO} 获得主键、创建/更新时间、乐观锁版本与逻辑删除等通用字段。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.workflow.domain.model.WorkflowDefinition
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("wf_workflow_definition")
public class WorkflowDefinitionPO extends BasePO {

    /** 租户 ID，用于多租户数据隔离。 */
    private Long tenantId;

    /** 流程编码，业务上唯一标识一类流程。 */
    private String workflowCode;

    /** 流程名称，面向用户展示的可读名称。 */
    private String workflowName;

    /** 流程描述，说明该流程的业务用途。 */
    private String description;

    /**
     * 节点定义 JSON。
     *
     * <p>以 JSON 字符串保存流程的节点与连线定义，对应上下文中的流程 DAG。</p>
     */
    private String nodeDefinition;

    /** 流程版本号，同一编码下可存在多个版本。 */
    private Integer version;

    /** 启用标志，1 表示启用、0 表示停用。 */
    private Integer enabled;

    /** 备注信息。 */
    private String remark;

    /**
     * 获取工作流编码（兼容方法，实际返回 {@link #workflowCode} 字段值）。
     *
     * @return 工作流编码
     */
    public String getCode() {
        return workflowCode;
    }

    /**
     * 设置工作流编码（兼容方法，实际写入 {@link #workflowCode} 字段）。
     *
     * @param code 工作流编码
     */
    public void setCode(String code) {
        this.workflowCode = code;
    }

    /**
     * 获取工作流名称（兼容方法，实际返回 {@link #workflowName} 字段值）。
     *
     * @return 工作流名称
     */
    public String getName() {
        return workflowName;
    }

    /**
     * 设置工作流名称（兼容方法，实际写入 {@link #workflowName} 字段）。
     *
     * @param name 工作流名称
     */
    public void setName(String name) {
        this.workflowName = name;
    }
}