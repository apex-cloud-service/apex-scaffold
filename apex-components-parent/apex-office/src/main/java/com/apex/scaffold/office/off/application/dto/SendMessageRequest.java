package com.apex.scaffold.office.off.application.dto;

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

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 200)
    @Schema(description = "消息主题", required = true)
    private String subject;

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 16000)
    @Schema(description = "消息内容", required = true)
    private String content;

    @jakarta.validation.constraints.Size(max = 32)
    @jakarta.validation.constraints.Pattern(regexp = "[A-Z][A-Z_]*")
    @Schema(description = "消息类型(SYSTEM/NOTICE/TASK/ALERT)", example = "NOTICE")
    private String messageType;

    @jakarta.validation.constraints.Pattern(regexp = "LOW|NORMAL|HIGH|URGENT")
    @Schema(description = "优先级(LOW/NORMAL/HIGH/URGENT)", example = "NORMAL")
    private String priority;

    @jakarta.validation.constraints.NotEmpty
    @jakarta.validation.constraints.Size(max = 100)
    @Schema(description = "接收系统用户ID列表，须绑定本租户未删除且ACTIVE的办公员工（不是员工主键）", required = true)
    private java.util.List<@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long>
            recipientIds;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "模板ID")
    private Long templateId;
}
