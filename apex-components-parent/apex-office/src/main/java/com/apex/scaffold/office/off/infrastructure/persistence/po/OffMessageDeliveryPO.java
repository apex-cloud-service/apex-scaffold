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
 * 消息渠道投递记录持久化对象。
 *
 * <p>对应 {@code off_message_delivery} 表，按（消息、接收人、渠道）唯一维度记录
 * 投递状态、平台侧消息标识与失败原因，通过 messageId 关联 {@code off_message}，
 * 并携带租户标识实现多租户隔离。</p>
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("off_message_delivery")
public class OffMessageDeliveryPO extends BasePO {

    @TableField("message_id")
    private Long messageId;

    @TableField("recipient_id")
    private Long recipientId;

    @TableField("channel")
    private String channel;

    @TableField("delivery_status")
    private String deliveryStatus;

    @TableField("platform_message_id")
    private String platformMessageId;

    @TableField("failure_reason")
    private String failureReason;

    @TableField("delivered_at")
    private LocalDateTime deliveredAt;

    @TableField("tenant_id")
    private String tenantId;
}
