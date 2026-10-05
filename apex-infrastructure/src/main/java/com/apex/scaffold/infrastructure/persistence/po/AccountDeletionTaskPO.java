package com.apex.scaffold.infrastructure.persistence.po;

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
 * 账号注销异步清理任务持久化对象。
 *
 * <p>映射 {@code sys_account_deletion_task} 表，记录清理任务的状态、
 * 重试次数、执行锁与完成时间，供调度器竞争执行。</p>
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
@TableName("sys_account_deletion_task")
public class AccountDeletionTaskPO extends BasePO {

    @TableField("user_id")
    private Long userId;

    @TableField("request_id")
    private String requestId;

    @TableField("status")
    private String status;

    @TableField("attempts")
    private Integer attempts;

    @TableField("last_error")
    private String lastError;

    @TableField("locked_at")
    private LocalDateTime lockedAt;

    @TableField("locked_by")
    private String lockedBy;

    @TableField("completed_at")
    private LocalDateTime completedAt;
}
