package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 流程实例持久化对象。
 *
 * <p>映射数据库表 wf_process_instance，存储工作流流程实例的核心信息。
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li><code>suspended</code>：挂起标志，对应数据库字段 suspended（TINYINT类型）</li>
 *   <li><code>formData</code>：表单数据 JSON，对应数据库字段 form_data</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_instance")
/**n * ProcessInstancePO - 持久化对象n *n * <p>对应数据库表：TODO: 补充表名n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class ProcessInstancePO extends BasePO {

    private String tenantId;

    private Long workflowDefinitionId;

    private String businessKey;

    public String getProcessCode() {
        return businessKey;
    }

    public void setProcessCode(String processCode) {
        this.businessKey = processCode;
    }

    public String getTitle() {
        return remark;
    }

    public void setTitle(String title) {
        this.remark = title;
    }

    private Long initiatorId;

    @TableField("current_node_key")
    private String currentNodeCode;

    private String status;

    private String formData;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String remark;

    private Boolean suspended;
}
