package com.apex.scaffold.workflow.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 审批记录持久化对象（PO）。
 *
 * <p>映射数据库表 {@code wf_approval_record}，存储审批操作的历史记录。
 * 继承 {@link BasePO} 获得主键、创建/更新时间、乐观锁版本与逻辑删除等通用字段。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.workflow.domain.model.ApprovalRecord
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("wf_approval_record")
public class ApprovalRecordPO extends BasePO {

    /** 租户 ID，用于多租户数据隔离。 */
    private Long tenantId;

    /** 所属流程实例 ID。 */
    private Long processInstanceId;

    /** 关联的任务 ID。 */
    private Long taskId;

    /** 节点编码，本次操作所在的流程节点。 */
    private String nodeCode;

    /** 操作人用户 ID。 */
    private Long approverId;

    /** 处理动作，典型取值见领域模型 {@code ApprovalRecord.action} 说明。 */
    private String action;

    /** 处理意见，操作人填写的备注。 */
    private String opinion;

    /** 备注信息。 */
    private String remark;
}