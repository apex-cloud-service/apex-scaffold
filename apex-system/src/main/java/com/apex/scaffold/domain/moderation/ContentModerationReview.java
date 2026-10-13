package com.apex.scaffold.domain.moderation;

import java.time.Instant;
import java.util.Objects;

/**
 * 疑似（REVIEW）内容的人工复核任务。 <p>兴趣同频先审后发中，百度云返回“疑似”的内容不落业务库，而是固化为复核任务 进入后台人工审核队列：确认违规（REJECTED）留证备查，确认误判（APPROVED）留痕 供审核策略调优。内容快照与供应商命中详情均固化，不随原文变动。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record ContentModerationReview(
        Long id,
        String sourceType,
        Long userId,
        String contentText,
        String mediaJson,
        String provider,
        String providerRequestId,
        String findingsJson,
        String contentHash,
        ContentModerationReviewStatus status,
        String reviewerOpenId,
        String reviewComment,
        Instant claimedAt,
        Instant decidedAt,
        long version) {

    /**
     * 构造复核任务记录。
     *
     * @param id                主键 ID
     * @param sourceType        内容来源类型（如 POST 等）
     * @param userId            内容所属用户 ID
     * @param contentText       内容文本快照，可为空串
     * @param mediaJson         内容媒体信息 JSON 快照
     * @param provider          审核供应商标识
     * @param providerRequestId 审核供应商请求 ID
     * @param findingsJson      供应商命中详情 JSON
     * @param contentHash       内容哈希，用于去重
     * @param status            复核状态，为空时默认 PENDING
     * @param reviewerOpenId    审核员 OpenId
     * @param reviewComment     审核员裁决意见
     * @param claimedAt         认领时间
     * @param decidedAt         裁决时间
     * @param version           乐观锁版本号
     */
    public ContentModerationReview {
        sourceType = Objects.requireNonNull(sourceType, "sourceType");
        userId = Objects.requireNonNull(userId, "userId");
        contentHash = Objects.requireNonNull(contentHash, "contentHash");
        status = status == null ? ContentModerationReviewStatus.PENDING : status;
        contentText = contentText == null ? "" : contentText;
    }

    /**
     * 判断是否处于待处理状态。
     *
     * @return true 表示状态为 PENDING
     */
    public boolean pending() {
        return status == ContentModerationReviewStatus.PENDING;
    }

    /**
     * 判断是否处于复核中。
     *
     * @return true 表示状态为 REVIEWING
     */
    public boolean reviewing() {
        return status == ContentModerationReviewStatus.REVIEWING;
    }

    /**
     * 判断是否已关闭（已裁决）。
     *
     * @return true 表示状态为 APPROVED 或 REJECTED
     */
    public boolean closed() {
        return status == ContentModerationReviewStatus.APPROVED || status == ContentModerationReviewStatus.REJECTED;
    }
}
