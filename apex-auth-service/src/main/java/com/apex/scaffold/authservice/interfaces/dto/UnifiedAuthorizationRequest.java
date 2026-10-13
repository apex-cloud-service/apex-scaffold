package com.apex.scaffold.authservice.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 统一授权查询参数。
 *
 * @param userId 中心用户 ID
 * @author Apex Scaffold Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Schema(description = "统一授权查询参数")
public record UnifiedAuthorizationRequest(
        @NotNull
                @Positive
                @Schema(
                        description = "中心用户 ID，仅允许查询当前可信租户内的用户",
                        example = "99",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long userId) {}
