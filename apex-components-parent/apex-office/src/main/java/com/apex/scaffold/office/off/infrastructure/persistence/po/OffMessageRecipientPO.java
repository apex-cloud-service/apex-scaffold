package com.apex.scaffold.office.off.infrastructure.persistence.po;

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
 * 办公消息接收人持久化对象。
 *
 * <p>对应 {@code off_message_recipient} 表，按消息维度记录每位接收人的标识、姓名、
 * 接收类型与已读状态（含已读时间），通过 messageId 关联 {@code off_message} 消息主表，
 * 并携带租户标识实现多租户隔离，支撑办公消息的送达与已读跟踪。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("off_message_recipient")
public class OffMessageRecipientPO extends BasePO {

    @TableField("message_id")
    private Long messageId;

    @TableField("recipient_id")
    private Long recipientId;

    @TableField("recipient_name")
    private String recipientName;

    @TableField("recipient_type")
    private String recipientType;

    @TableField("read_status")
    private Boolean readStatus;

    @TableField("read_time")
    private LocalDateTime readTime;

    @TableField("tenant_id")
    private String tenantId;
}
