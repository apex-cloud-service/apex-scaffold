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
     * 保存复核记录。
     *
     * @param review 复核记录
     * @return 已保存的复核记录
     */
    Mono<ContentModerationReview> save(ContentModerationReview review);

    /**
     * 根据主键 ID 查询复核记录。
     *
     * @param id 主键 ID
     * @return 匹配的复核记录；不存在时返回空
     */
    Mono<ContentModerationReview> findById(Long id);

    /**
     * 分页查询复核记录。
     *
     * @param status         状态过滤，为空表示不过滤
     * @param mine           是否仅查本人（审核员视角）
     * @param reviewerOpenId 审核员 OpenId
     * @param offset         分页偏移量
     * @param limit          每页条数
     * @return 分页后的复核记录列表
     */
    Mono<List<ContentModerationReview>> findPage(
            ContentModerationReviewStatus status, boolean mine, String reviewerOpenId, int offset, int limit);

    /**
     * 统计复核记录数量。
     *
     * @param status         状态过滤，为空表示不过滤
     * @param mine           是否仅查本人（审核员视角）
     * @param reviewerOpenId 审核员 OpenId
     * @return 符合条件的记录数量
     */
    Mono<Long> count(ContentModerationReviewStatus status, boolean mine, String reviewerOpenId);

    /**
     * 认领任务：PENDING → REVIEWING，乐观锁 CAS，并发下仅一人成功。
     *
     * @param id 任务ID
     * @param reviewerOpenId 审核员OpenId
     * @param expectedVersion 预期版本号（乐观锁）
     * @param claimedAt 认领时间
     * @return 认领后的复核记录
     */
    Mono<ContentModerationReview> claim(Long id, String reviewerOpenId, long expectedVersion, Instant claimedAt);

    /**
     * 裁决任务：REVIEWING → APPROVED/REJECTED，仅认领人可裁决，乐观锁 CAS。
     *
     * @param id 任务ID
     * @param reviewerOpenId 审核员OpenId
     * @param to 目标状态（APPROVED或REJECTED）
     * @param comment 裁决意见
     * @param expectedVersion 预期版本号（乐观锁）
     * @return 裁决后的复核记录
     */
    Mono<ContentModerationReview> decide(
            Long id, String reviewerOpenId, ContentModerationReviewStatus to, String comment, long expectedVersion);

    /**
     * 把认领超过租约时长仍未裁决的任务重新开放为 PENDING。
     *
     * @param claimedBefore 认领时间截止点，早于此时间的未裁决任务将被重新开放
     * @return 重新开放的任务数量
     */
    Mono<Integer> reopenStaleClaims(Instant claimedBefore);
}
