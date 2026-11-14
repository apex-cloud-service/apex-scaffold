package com.apex.scaffold.office.off.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 收件箱增量游标条目，{@code recipientRecordId} 作为下一页游标。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "收件箱增量游标条目")
public class InboxCursorItemDTO {

    @Schema(description = "接收记录ID，作为增量游标")
    private Long recipientRecordId;

    @Schema(description = "消息ID")
    private Long messageId;

    @Schema(description = "消息编码")
    private String messageCode;

    @Schema(description = "消息主题")
    private String subject;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "消息类型")
    private String messageType;

    @Schema(description = "优先级")
    private String priority;

    @Schema(description = "发送人ID")
    private Long senderId;

    @Schema(description = "发送人姓名")
    private String senderName;

    @Schema(description = "发送时间")
    private LocalDateTime sendTime;

    @Schema(description = "已读状态")
    private Boolean readStatus;

    @Schema(description = "已读时间")
    private LocalDateTime readTime;
}
