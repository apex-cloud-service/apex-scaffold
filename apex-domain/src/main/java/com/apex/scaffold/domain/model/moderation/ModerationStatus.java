package com.apex.scaffold.domain.model.moderation;

/**
 * 照片内容审核生命周期状态枚举。
 *
 * <p>状态流转：PENDING_MACHINE → WAITING_REVIEW → IN_REVIEW → APPROVED/REJECTED；
 * 机器审核失败时可直接进入FAILED状态。</p>
 *
 * @author weiqiang.ling
 * @version 1.0.0
 * @since 1.0.0
 */
/**
 * ModerationStatus 组件。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum ModerationStatus {

    /** 等待机器审核 */
    PENDING_MACHINE,

    /** 等待人工复核 */
    WAITING_REVIEW,

    /** 人工复核中 */
    IN_REVIEW,

    /** 审核通过 */
    APPROVED,

    /** 审核拒绝 */
    REJECTED,

    /** 审核失败 */
    FAILED
}
