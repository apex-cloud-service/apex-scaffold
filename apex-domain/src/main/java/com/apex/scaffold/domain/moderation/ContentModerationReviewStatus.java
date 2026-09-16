package com.apex.scaffold.domain.moderation;

/**
 * 内容人工复核任务生命周期。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum ContentModerationReviewStatus {
    /** 待处理 */
    PENDING,
    /** 审核中 */
    REVIEWING,
    /** 已通过 */
    APPROVED,
    /** 已拒绝 */
    REJECTED
}
