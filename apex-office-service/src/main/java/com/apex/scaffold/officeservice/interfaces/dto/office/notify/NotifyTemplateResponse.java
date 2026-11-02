package com.apex.scaffold.officeservice.interfaces.dto.office.notify;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知模板响应
 *
 * <p>返回统一通知模板的完整定义，包含正文占位符模板与参数契约。
 *
 * @author Apex Scaffold Team
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通知模板响应")
public class NotifyTemplateResponse {

    @Schema(description = "模板ID", example = "1853274912345678901")
    private Long id;

    @Schema(description = "租户ID", example = "41")
    private String tenantId;

    @Schema(description = "模板编码", example = "ORDER_SHIPPED")
    private String templateCode;

    @Schema(description = "默认投递通道：INBOX/FEISHU/DINGTALK/WECOM/SMS/EMAIL", example = "SMS")
    private String channel;

    @Schema(description = "通知主题", example = "订单 ${orderId} 已发货")
    private String title;

    @Schema(description = "正文模板（含 ${key} 占位符）", example = "您好 ${name}，您的订单已发出")
    private String body;

    @Schema(description = "参数契约 JSON", example = "{\"name\":\"收件人姓名\"}")
    private String paramsSchema;

    @Schema(description = "状态：ENABLED/DISABLED", example = "ENABLED")
    private String status;

    @Schema(description = "创建时间", example = "2026-11-01T10:00:00")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2026-11-01T10:00:00")
    private LocalDateTime updateTime;
}
