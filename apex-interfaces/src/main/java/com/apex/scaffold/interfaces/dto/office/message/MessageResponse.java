package com.apex.scaffold.interfaces.dto.office.message;

import com.apex.scaffold.domain.office.message.MessageStatus;
import com.apex.scaffold.domain.office.message.MessageType;
import com.apex.scaffold.domain.office.message.RecipientType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息响应DTO
 *
 * <p>用于返回消息的详细信息。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "消息响应")
public class MessageResponse {

    @Schema(description = "消息ID", example = "1")
    private Long id;

    @Schema(description = "租户ID", example = "1")
    private Long tenantId;

    @Schema(description = "办公平台类型", example = "FEISHU")
    private String platform;

    @Schema(description = "应用ID", example = "1")
    private Long appId;

    @Schema(description = "消息类型", example = "TEXT")
    private MessageType messageType;

    @Schema(description = "接收者类型", example = "USER")
    private RecipientType recipientType;

    @Schema(description = "接收者ID列表", example = "[\"user001\", \"user002\"]")
    private List<String> recipientIds;

    @Schema(description = "消息标题", example = "系统通知")
    private String title;

    @Schema(description = "消息内容", example = "您有一条新的待办任务")
    private String content;

    @Schema(description = "消息附加数据", example = "{\"url\": \"https://example.com\"}")
    private String extraData;

    @Schema(description = "发送状态", example = "SUCCESS")
    private MessageStatus status;

    @Schema(description = "平台返回的消息ID", example = "msg_xxxxx")
    private String platformMessageId;

    @Schema(description = "发送结果消息", example = "发送成功")
    private String resultMessage;

    @Schema(description = "重试次数", example = "0")
    private Integer retryCount;

    @Schema(description = "最大重试次数", example = "3")
    private Integer maxRetryCount;

    @Schema(description = "发送时间", example = "2024-01-01T10:00:00")
    private LocalDateTime sentAt;

    @Schema(description = "创建时间", example = "2024-01-01T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间", example = "2024-01-01T10:00:00")
    private LocalDateTime updatedAt;
}
