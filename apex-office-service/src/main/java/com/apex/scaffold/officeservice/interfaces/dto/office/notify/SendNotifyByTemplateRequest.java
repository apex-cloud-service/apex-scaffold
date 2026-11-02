package com.apex.scaffold.officeservice.interfaces.dto.office.notify;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按模板投递通知请求（内部契约）
 *
 * <p>集群内服务直连调用统一消息门面时的请求体。租户标识由调用方
 * （内部服务）按其已验证的租户上下文传入，网关层对 {@code /internal/**}
 * 拒绝公网流量，不暴露给终端客户端。
 *
 * @author Apex Scaffold Team
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "按模板投递通知请求（内部）")
public class SendNotifyByTemplateRequest {

    @NotBlank(message = "租户不能为空")
    @Size(max = 64, message = "租户长度不能超过64")
    @Schema(description = "租户标识（调用方从其可信上下文传入）", example = "41", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tenantId;

    @NotBlank(message = "模板编码不能为空")
    @Size(max = 64, message = "模板编码长度不能超过64")
    @Schema(description = "模板编码", example = "ORDER_SHIPPED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateCode;

    @Size(max = 16, message = "通知通道长度不能超过16")
    @Schema(description = "通道标识（可空，缺省用模板默认通道）", example = "SMS")
    private String channel;

    @NotBlank(message = "通知目标不能为空")
    @Size(max = 255, message = "通知目标长度不能超过255")
    @Schema(
            description = "投递目标（用户 ID / 外部 ID / 手机号 / 邮箱）",
            example = "13800138000",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String target;

    @Schema(description = "渲染参数", example = "{\"name\":\"张三\",\"orderId\":\"1001\"}")
    private Map<String, Object> params;
}
