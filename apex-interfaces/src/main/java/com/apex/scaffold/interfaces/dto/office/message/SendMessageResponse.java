package com.apex.scaffold.interfaces.dto.office.message;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送消息响应 DTO
 *
 * <p>返回消息发送的结果信息，包含消息ID和发送状态。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "发送消息响应")
public class SendMessageResponse {
    
    /**
     * 消息ID
     */
    @Schema(description = "消息ID", example = "123456")
    private Long messageId;
    
    /**
     * 发送状态
     */
    @Schema(description = "发送状态", example = "SUCCESS", allowableValues = {"PENDING", "SUCCESS", "FAILED"})
    private String sendStatus;
    
    /**
     * 平台消息ID
     */
    @Schema(description = "平台消息ID", example = "feishu_msg_1234567890")
    private String platformMessageId;
    
    /**
     * 失败原因（仅当状态为FAILED时有值）
     */
    @Schema(description = "失败原因", example = "用户不存在")
    private String failureReason;
}
