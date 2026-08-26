package com.apex.scaffold.application.dto.admin;

import com.apex.scaffold.domain.model.admin.ReviewReason;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 后台审核原因配置视图。
 *
 * <p>审核原因 ID 以字符串传输，避免雪花 ID 在浏览器端丢失精度。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(name = "AdminReviewReasonResponse", description = "后台审核原因")
public record AdminReviewReasonResponse(
        @Schema(type = "string") String id,
        String reviewType,
        String code,
        String label,
        String description,
        boolean enabled,
        boolean requireComment,
        int sortOrder,
        int version) {

    /**
     * 将审核原因领域对象转换为后台响应视图。
     *
     * @param reason 审核原因领域对象；不能为 {@code null}
     * @return 后台审核原因视图；ID 转换为字符串传输
     */
    public static AdminReviewReasonResponse from(ReviewReason reason) {
        return new AdminReviewReasonResponse(
                String.valueOf(reason.id()),
                reason.reviewType(),
                reason.code(),
                reason.label(),
                reason.description(),
                reason.enabled(),
                reason.requireComment(),
                reason.sortOrder(),
                reason.version());
    }
}
