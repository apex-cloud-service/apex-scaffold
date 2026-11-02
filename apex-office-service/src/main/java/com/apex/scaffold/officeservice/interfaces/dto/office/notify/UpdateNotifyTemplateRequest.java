package com.apex.scaffold.officeservice.interfaces.dto.office.notify;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新通知模板请求
 *
 * <p>局部变更语义：仅提交的非空字段生效，未提交字段保持原值。
 *
 * @author Apex Scaffold Team
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新通知模板请求（仅非空字段生效）")
public class UpdateNotifyTemplateRequest {

    @Size(max = 64, message = "模板编码长度不能超过64")
    @Schema(description = "模板编码（租户内唯一，变更时校验冲突）", example = "ORDER_SHIPPED_V2")
    private String templateCode;

    @Size(max = 16, message = "通知通道长度不能超过16")
    @Schema(description = "默认投递通道：INBOX/FEISHU/DINGTALK/WECOM/SMS/EMAIL", example = "EMAIL")
    private String channel;

    @Size(max = 255, message = "通知主题长度不能超过255")
    @Schema(description = "通知主题", example = "订单 ${orderId} 已发货")
    private String title;

    @Schema(description = "正文模板（含 ${key} 占位符）", example = "您好 ${name}，您的订单已发出")
    private String body;

    @Size(max = 1024, message = "参数契约长度不能超过1024")
    @Schema(description = "参数契约 JSON", example = "{\"name\":\"收件人姓名\"}")
    private String paramsSchema;

    @Size(max = 16, message = "状态长度不能超过16")
    @Schema(description = "状态：ENABLED/DISABLED", example = "DISABLED")
    private String status;
}
