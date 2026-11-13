package com.apex.scaffold.officeservice.interfaces.dto.office.notify;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知投递留痕响应
 *
 * <p>返回统一消息门面按次落库的投递留痕，成功与失败均记录，
 * 接收目标与渲染参数以 JSON 文本原样透出，便于排障与补偿。
 *
 * @author Apex Scaffold Team
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通知投递留痕响应")
public class NotifyRequestRecordResponse {

    @Schema(description = "留痕ID", example = "1853274912345678902")
    private Long id;

    @Schema(description = "租户ID", example = "41")
    private String tenantId;

    @Schema(description = "实际投递通道", example = "SMS")
    private String channel;

    @Schema(description = "模板编码", example = "ORDER_SHIPPED")
    private String templateCode;

    @Schema(description = "接收目标（JSON 数组文本）", example = "[\"13800138000\"]")
    private String recipient;

    @Schema(description = "渲染参数（JSON 对象文本）", example = "{\"name\":\"张三\",\"orderId\":\"1001\"}")
    private String params;

    @Schema(description = "投递状态：PENDING/SUCCESS/FAILED", example = "SUCCESS")
    private String status;

    @Schema(description = "失败原因（成功时为空）", example = "channel down")
    private String errorMessage;

    @Schema(description = "关联的 Outbox 事件 ID（可空）", example = "1853274912345678903")
    private Long outboxEventId;

    @Schema(description = "创建时间", example = "2026-11-01T10:00:00")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2026-11-01T10:00:00")
    private LocalDateTime updateTime;
}
