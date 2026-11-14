package com.apex.scaffold.officeservice.interfaces.dto.office.message;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送消息请求 DTO
 *
 * <p>用于接收客户端的消息发送请求参数。
 * 支持指定平台、目标、消息类型和内容。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "发送消息请求")
public class SendMessageRequest {

    /**
     * 办公平台类型
     */
    @NotBlank(message = "平台类型不能为空")
    @Schema(
            description = "办公平台类型",
            example = "FEISHU",
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"FEISHU", "DINGTALK", "WECOM"})
    private String platform;

    /**
     * 目标类型
     */
    @NotBlank(message = "目标类型不能为空")
    @Schema(
            description = "目标类型",
            example = "USER",
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"USER", "GROUP"})
    private String targetType;

    /**
     * 接收用户ID列表（平台用户ID）
     */
    @Schema(description = "接收用户ID列表", example = "[\"user_001\", \"user_002\"]")
    private List<String> userIds;

    /**
     * 接收群组ID列表（平台群组ID）
     */
    @Schema(description = "接收群组ID列表", example = "[\"group_001\"]")
    private List<String> groupIds;

    /**
     * 消息类型
     */
    @NotBlank(message = "消息类型不能为空")
    @Schema(
            description = "消息类型",
            example = "TEXT",
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"TEXT", "MARKDOWN", "CARD"})
    private String messageType;

    /**
     * 消息内容（根据消息类型不同，格式不同）
     */
    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "消息内容", example = "Hello, World!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    /**
     * 消息标题（可选，用于某些消息类型）
     */
    @Schema(description = "消息标题", example = "通知")
    private String title;

    /**
     * 卡片JSON（当消息类型为CARD时必填）
     */
    @Schema(description = "卡片JSON（消息类型为CARD时使用）", example = "{\"header\":{\"title\":\"卡片标题\"}}")
    private String cardJson;
}
