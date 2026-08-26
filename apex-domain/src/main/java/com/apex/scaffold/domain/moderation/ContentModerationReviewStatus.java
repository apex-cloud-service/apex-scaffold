package com.apex.scaffold.domain.moderation;

/**
 * 内容人工复核任务生命周期。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum ContentModerationReviewStatus {
    PENDING,
    REVIEWING,
    APPROVED,
    REJECTED
}
