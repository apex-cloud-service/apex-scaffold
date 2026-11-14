package com.apex.scaffold.office.notify.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 统一通知投递请求留痕持久化对象。
 *
 * <p>对应 {@code off_notify_request} 表。投递留痕为追加式记录：只有创建与查询，
 * 不提供逻辑删除与乐观锁列，因此不继承 {@code BasePO}，仅携带审计时间列
 * （插入时由数据库默认值填充）。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@TableName("off_notify_request")
public class NotifyRequestRecordPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 使用 MyBatis-Plus 雪花算法生成的全局唯一主键。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("tenant_id")
    private String tenantId;

    @TableField("channel")
    private String channel;

    @TableField("template_code")
    private String templateCode;

    @TableField("recipient")
    private String recipient;

    @TableField("params")
    private String params;

    @TableField("status")
    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField("outbox_event_id")
    private Long outboxEventId;

    /** 首次插入时填充（无元对象处理器时回落数据库默认值）。 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时填充（本表为追加式留痕，实际不更新）。 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
