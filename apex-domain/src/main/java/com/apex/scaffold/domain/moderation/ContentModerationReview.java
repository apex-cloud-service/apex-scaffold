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
     * 构造实例并初始化字段。
     * @param id 主键 ID
     * @param sourceType 参数 sourceType（类型 String）
     * @param userId 用户 ID
     * @param contentText 参数 contentText（类型 String）
     * @param mediaJson 参数 mediaJson（类型 String）
     * @param provider 参数 provider（类型 String）
     * @param providerRequestId 参数 providerRequestId（类型 String）
     * @param findingsJson 参数 findingsJson（类型 String）
     * @param contentHash 参数 contentHash（类型 String）
     * @param status 状态过滤
     * @param reviewerOpenId 审核员 OpenId
     * @param reviewComment 参数 reviewComment（类型 String）
     * @param claimedAt 参数 claimedAt（类型 Instant）
     * @param decidedAt 参数 decidedAt（类型 Instant）
     * @param version 参数 version（类型 long）
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
     * @return 判断结果
     */
    public boolean pending() {
        return status == ContentModerationReviewStatus.PENDING;
    }

    /**
     * 判断是否处于复核中。
     * @return 判断结果
     */
    public boolean reviewing() {
        return status == ContentModerationReviewStatus.REVIEWING;
    }

    /**
     * 判断是否已关闭。
     * @return 判断结果
     */
    public boolean closed() {
        return status == ContentModerationReviewStatus.APPROVED || status == ContentModerationReviewStatus.REJECTED;
    }
}
