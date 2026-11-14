package com.apex.scaffold.office.off.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道投递记录 DTO，送达/失败状态与收件记录已读状态相互独立。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "渠道投递记录DTO")
public class MessageDeliveryDTO {

    @Schema(description = "投递记录ID")
    private Long id;

    @Schema(description = "消息ID")
    private Long messageId;

    @Schema(description = "接收人ID")
    private Long recipientId;

    @Schema(description = "投递渠道：INBOX/FEISHU/DINGTALK/WECOM")
    private String channel;

    @Schema(description = "投递状态：PENDING/SUCCESS/FAILED/UNKNOWN")
    private String deliveryStatus;

    @Schema(description = "第三方平台消息ID")
    private String platformMessageId;

    @Schema(description = "失败原因")
    private String failureReason;

    @Schema(description = "投递成功时间")
    private LocalDateTime deliveredAt;
}
