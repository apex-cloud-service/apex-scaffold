package com.apex.scaffold.domain.moderation;

import java.time.Instant;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 内容人工复核任务仓储端口。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface ContentModerationReviewRepository {

    /**
     * 保存。
     * @param review 复核记录
     * @return 包含 ContentModerationReview 的响应式结果
     */
    Mono<ContentModerationReview> save(ContentModerationReview review);

    /**
     * 查询 by ID。
     * @param id 主键 ID
     * @return 包含 ContentModerationReview 的响应式结果
     */
    Mono<ContentModerationReview> findById(Long id);

    /**
     * 查询 分页。
     * @param status 状态过滤
     * @param mine 是否仅查本人
     * @param reviewerOpenId 审核员 OpenId
     * @param offset 分页偏移量
     * @param limit 每页条数
     * @return 包含 List&lt;ContentModerationReview&gt; 的响应式结果
     */
    Mono<List<ContentModerationReview>> findPage(
            ContentModerationReviewStatus status, boolean mine, String reviewerOpenId, int offset, int limit);

    /**
     * 执行 count 相关逻辑。
     * @param status 状态过滤
     * @param mine 是否仅查本人
     * @param reviewerOpenId 审核员 OpenId
     * @return 包含 Long 的响应式结果
     */
    Mono<Long> count(ContentModerationReviewStatus status, boolean mine, String reviewerOpenId);

    /** 认领任务：PENDING → REVIEWING，乐观锁 CAS，并发下仅一人成功。 */
    Mono<ContentModerationReview> claim(Long id, String reviewerOpenId, long expectedVersion, Instant claimedAt);

    /** 裁决任务：REVIEWING → APPROVED/REJECTED，仅认领人可裁决，乐观锁 CAS。 */
    Mono<ContentModerationReview> decide(
            Long id, String reviewerOpenId, ContentModerationReviewStatus to, String comment, long expectedVersion);

    /** 把认领超过租约时长仍未裁决的任务重新开放为 PENDING。 */
    Mono<Integer> reopenStaleClaims(Instant claimedBefore);
}
