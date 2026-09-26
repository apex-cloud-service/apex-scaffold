package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息 DTO。
 *
 * <p>办公消息业务域的消息数据载体，承载消息编码、主题、内容、类型、优先级、
 * 发送人、已读状态等信息，用于 {@code OffMessageService} 消息查询与管理
 * 用例中实体与服务层之间的数据传输。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "消息DTO")
public class MessageDTO {

    @Schema(description = "消息ID")
    private Long id;

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

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "已读状态")
    private Boolean readStatus;

    @Schema(description = "已读时间")
    private LocalDateTime readTime;
}
