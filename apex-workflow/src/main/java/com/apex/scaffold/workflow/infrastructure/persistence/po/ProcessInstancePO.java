package com.apex.scaffold.workflow.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 流程实例持久化对象（PO）。
 *
 * <p>映射数据库表 {@code wf_process_instance}，存储工作流流程实例的核心信息。
 * 继承 {@link BasePO} 获得主键、创建/更新时间、乐观锁版本与逻辑删除等通用字段。</p>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>{@code suspended}：挂起标志，对应数据库字段 {@code suspended}（TINYINT 类型）。</li>
 *   <li>{@code formData}：表单数据 JSON，对应数据库字段 {@code form_data}。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.0.0
 * @see com.apex.scaffold.workflow.domain.model.ProcessInstance
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_instance")
public class ProcessInstancePO extends BasePO {

    /** 租户 ID，用于多租户数据隔离。 */
    private String tenantId;

    /** 所属流程定义 ID，指向 {@code wf_workflow_definition} 表主键。 */
    private Long workflowDefinitionId;

    /** 业务主键，用于与业务单据关联。 */
    private String businessKey;

    /** 发起人用户 ID。 */
    private Long initiatorId;

    /** 当前所处节点标识，对应数据库字段 {@code current_node_key}。 */
    @TableField("current_node_key")
    private String currentNodeCode;

    /** 流程实例状态，取值参见领域模型 {@code ProcessInstance} 状态说明。 */
    private String status;

    /** 表单数据 JSON，保存流程运行期的业务变量。 */
    private String formData;

    /** 流程开始时间。 */
    private LocalDateTime startTime;

    /** 流程结束时间，未结束时为 {@code null}。 */
    private LocalDateTime endTime;

    /** 备注信息（兼容字段，常被复用为流程标题）。 */
    private String remark;

    /** 挂起标志，{@code true} 表示已挂起。 */
    private Boolean suspended;

    /**
     * 获取流程编码（兼容方法，实际返回 {@link #businessKey} 字段值）。
     *
     * @return 流程编码
     */
    public String getProcessCode() {
        return businessKey;
    }

    /**
     * 设置流程编码（兼容方法，实际写入 {@link #businessKey} 字段）。
     *
     * @param processCode 流程编码
     */
    public void setProcessCode(String processCode) {
        this.businessKey = processCode;
    }

    /**
     * 获取流程标题（兼容方法，实际返回 {@link #remark} 字段值）。
     *
     * @return 流程标题
     */
    public String getTitle() {
        return remark;
    }

    /**
     * 设置流程标题（兼容方法，实际写入 {@link #remark} 字段）。
     *
     * @param title 流程标题
     */
    public void setTitle(String title) {
        this.remark = title;
    }
}
