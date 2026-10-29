package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 举报响应 DTO。
 *
 * <p>对应 {@code POST /api/v1/moderation/reports} 接口的 data 字段。</p>
 *
 * @param ticketId 举报工单 ID
 * @param status 工单状态
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(description = "举报工单结果")
public record ModerationReportResponse(
        @Schema(description = "举报工单 ID", example = "ticket_abc123") String ticketId,
        @Schema(description = "工单状态", example = "PENDING") String status) {}
