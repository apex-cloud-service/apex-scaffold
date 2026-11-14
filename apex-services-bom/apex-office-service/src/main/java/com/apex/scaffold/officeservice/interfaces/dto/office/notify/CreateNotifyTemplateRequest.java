package com.apex.scaffold.officeservice.interfaces.dto.office.notify;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建通知模板请求
 *
 * <p>用于新增租户内的统一通知模板。模板编码在租户内唯一，正文以
 * {@code ${key}} 占位符声明渲染参数，状态未显式指定时按启用处理。
 *
 * @author Apex Scaffold Team
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建通知模板请求")
public class CreateNotifyTemplateRequest {

    @NotBlank(message = "模板编码不能为空")
    @Size(max = 64, message = "模板编码长度不能超过64")
    @Schema(description = "模板编码（租户内唯一）", example = "ORDER_SHIPPED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateCode;

    @NotBlank(message = "通知通道不能为空")
    @Size(max = 16, message = "通知通道长度不能超过16")
    @Schema(
            description = "默认投递通道：INBOX/FEISHU/DINGTALK/WECOM/SMS/EMAIL",
            example = "SMS",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String channel;

    @Size(max = 255, message = "通知主题长度不能超过255")
    @Schema(description = "通知主题（可含占位符）", example = "订单 ${orderId} 已发货")
    private String title;

    @NotBlank(message = "模板正文不能为空")
    @Schema(
            description = "正文模板（含 ${key} 占位符）",
            example = "您好 ${name}，您的订单已发出",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String body;

    @Size(max = 1024, message = "参数契约长度不能超过1024")
    @Schema(description = "参数契约 JSON（供管理端提示，可空）", example = "{\"name\":\"收件人姓名\"}")
    private String paramsSchema;

    @Size(max = 16, message = "状态长度不能超过16")
    @Schema(description = "状态：ENABLED/DISABLED，缺省为 ENABLED", example = "ENABLED")
    private String status;
}
