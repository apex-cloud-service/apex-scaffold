package com.apex.scaffold.officeservice.interfaces.dto.office.message;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息状态响应 DTO
 *
 * <p>返回消息的详细状态信息，包含发送时间、重试次数等。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "消息状态响应")
public class MessageStatusResponse {

    /**
     * 消息ID
     */
    @Schema(description = "消息ID", example = "123456")
    private Long messageId;

    /**
     * 办公平台类型
     */
    @Schema(description = "办公平台类型", example = "FEISHU")
    private String platform;

    /**
     * 消息类型
     */
    @Schema(description = "消息类型", example = "TEXT")
    private String messageType;

    /**
     * 发送状态
     */
    @Schema(description = "发送状态", example = "SUCCESS")
    private String sendStatus;

    /**
     * 平台消息ID
     */
    @Schema(description = "平台消息ID", example = "feishu_msg_1234567890")
    private String platformMessageId;

    /**
     * 失败原因
     */
    @Schema(description = "失败原因", example = "用户不存在")
    private String failureReason;

    /**
     * 重试次数
     */
    @Schema(description = "重试次数", example = "0")
    private Integer retryCount;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2026-09-14T10:30:00")
    private LocalDateTime createdAt;

    /**
     * 发送时间
     */
    @Schema(description = "发送时间", example = "2026-09-14T10:30:05")
    private LocalDateTime sentAt;
}
