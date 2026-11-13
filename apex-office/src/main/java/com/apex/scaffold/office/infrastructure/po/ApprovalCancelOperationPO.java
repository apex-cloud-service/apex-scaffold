package com.apex.scaffold.office.infrastructure.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批撤销操作编排状态持久化对象。
 *
 * <p>对应数据库表 off_approval_cancel_operation。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("off_approval_cancel_operation")
public class ApprovalCancelOperationPO {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 业务流程实例ID
     */
    private String processInstanceId;

    /**
     * 幂等键（租户内唯一）
     */
    private String idempotencyKey;

    /**
     * 编排状态。
     */
    private String status;

    /**
     * 最近一次失败原因。
     */
    private String errorMessage;

    /**
     * 创建时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 版本号（乐观锁）
     */
    @Version
    private Integer version;

    /**
     * 逻辑删除标志。
     */
    @TableLogic
    private Boolean deleted;
}
