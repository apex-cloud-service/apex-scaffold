package com.apex.scaffold.officeservice.interfaces.dto.office.message;

import com.apex.scaffold.office.domain.message.MessageStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询消息列表请求DTO
 *
 * <p>用于接收消息列表查询的请求参数。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "查询消息列表请求")
public class QueryMessageRequest {

    @Schema(description = "办公平台类型（可选）", example = "FEISHU")
    private String platform;

    @Schema(description = "消息状态（可选）", example = "SUCCESS")
    private MessageStatus status;

    @Min(value = 0, message = "页码必须大于等于0")
    @Schema(description = "页码（从0开始）", example = "0")
    private Integer page = 0;

    @Min(value = 1, message = "每页数量必须大于0")
    @Schema(description = "每页数量", example = "20")
    private Integer size = 20;
}
