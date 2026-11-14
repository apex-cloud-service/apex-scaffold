package com.apex.scaffold.application.dto.admin;

import com.apex.scaffold.domain.model.admin.ReviewReason;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 后台审核原因配置视图。
 *
 * <p>审核原因 ID 以字符串传输，避免雪花 ID 在浏览器端丢失精度。</p>
 *
 * @param id 原因ID（以字符串传输，避免前端精度丢失）
 * @param reviewType 审核类型
 * @param code 原因编码，创建后不可修改
 * @param label 原因展示文案
 * @param description 原因补充说明
 * @param enabled 是否启用（停用后审核时不可再选择）
 * @param requireComment 选择该原因时是否强制填写备注
 * @param sortOrder 展示排序值（越小越靠前）
 * @param version 乐观锁版本号
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
