package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 会员订阅信息响应 DTO。
 *
 * <p>对应 {@code GET /api/v1/subscription/me} 接口的 data 字段。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(description = "会员订阅信息")
public record SubscriptionInfoResponse(
        @Schema(description = "会员等级：NORMAL / PREMIUM / VIP", example = "NORMAL") String level,
        @Schema(description = "会员到期时间，null 表示无期限", example = "2027-06-30 23:59:59") String expiresAt,
        @Schema(description = "算力余额", example = "100") long computeCredits,
        @Schema(description = "算力速度倍率", example = "1.0") double speedMultiplier) {}
