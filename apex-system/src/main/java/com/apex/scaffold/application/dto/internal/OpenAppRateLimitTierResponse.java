package com.apex.scaffold.application.dto.internal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 开放应用限流档内部查询响应（方案 D2 {@code rateLimitTier}）。
 *
 * @param clientId 客户端 ID
 * @param rateLimitTier 限流档编码；未声明/未知客户端为 {@code default}（fail-open 兜底）
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Schema(name = "OpenAppRateLimitTierResponse", description = "开放应用限流档内部查询响应")
public record OpenAppRateLimitTierResponse(
        @Schema(description = "客户端 ID", example = "partner-app") String clientId,
        @Schema(description = "限流档编码；未声明/未知客户端为 default（fail-open 兜底）", example = "standard") String rateLimitTier) {}
