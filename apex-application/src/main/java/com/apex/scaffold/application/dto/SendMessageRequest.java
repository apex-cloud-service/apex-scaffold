package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送消息请求。
 *
 * <p>办公消息业务域的消息发送入参载体，包含消息主题、内容、消息类型
 * （SYSTEM/NOTICE/TASK/ALERT）、优先级（LOW/NORMAL/HIGH/URGENT）、
 * 接收人 ID 列表、过期时间及可选的模板 ID，供 {@code OffMessageService}
 * 发送消息用例使用。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "发送消息请求")
public class SendMessageRequest {

    @Schema(description = "消息主题", required = true)
    private String subject;

    @Schema(description = "消息内容", required = true)
    private String content;

    @Schema(description = "消息类型(SYSTEM/NOTICE/TASK/ALERT)", example = "NOTICE")
    private String messageType;

    @Schema(description = "优先级(LOW/NORMAL/HIGH/URGENT)", example = "NORMAL")
    private String priority;

    @Schema(description = "接收人ID列表", required = true)
    private java.util.List<Long> recipientIds;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "模板ID")
    private Long templateId;
}
