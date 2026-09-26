package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新审核原因配置的请求。
 *
 * <p>支持调整展示文案、启用状态、强制备注与排序；期望版本用于乐观锁并发控制。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(name = "ReviewReasonUpdateRequest", description = "更新审核拒绝原因配置请求（乐观锁并发控制）")
public record ReviewReasonUpdateRequest(
        @NotBlank
                @Size(max = 128)
                @Schema(description = "原因展示文案", example = "照片模糊不清", requiredMode = Schema.RequiredMode.REQUIRED)
                String label,
        @Size(max = 512) @Schema(description = "原因补充说明（可为空）", example = "上传的照片无法辨识主体") String description,
        @Schema(description = "是否启用（停用后审核时不可再选择该原因）", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
                boolean enabled,
        @Schema(description = "审核选择该原因时是否强制要求填写备注", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
                boolean requireComment,
        @Min(0)
                @Max(10000)
                @Schema(
                        description = "展示排序值（越小越靠前，0-10000）",
                        example = "10",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int sortOrder,
        @Min(0)
                @Schema(
                        description = "期望版本号（乐观锁，需与当前版本一致，不一致时更新失败）",
                        example = "3",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int expectedVersion) {}
