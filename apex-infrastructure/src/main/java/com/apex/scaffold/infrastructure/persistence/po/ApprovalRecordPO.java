package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 审批记录持久化对象。
 *
 * <p>映射数据库表 wf_approval_record，存储审批操作的历史记录。</p>
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
@TableName("wf_approval_record")
/**n * ApprovalRecordPO - 持久化对象n *n * <p>对应数据库表：TODO: 补充表名n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class ApprovalRecordPO extends BasePO {

    private Long tenantId;

    private Long processInstanceId;

    private Long taskId;

    private String nodeCode;

    private Long approverId;

    private String action;

    private String opinion;

    private String remark;
}
