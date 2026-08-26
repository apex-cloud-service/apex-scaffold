package com.apex.scaffold.infrastructure.persistence.moderation;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.moderation.ContentModerationReview;
import com.apex.scaffold.domain.moderation.ContentModerationReviewRepository;
import com.apex.scaffold.domain.moderation.ContentModerationReviewStatus;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@code biz_content_moderation_review} 内容人工复核任务仓储实现。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Repository
public class ContentModerationReviewRepositoryImpl implements ContentModerationReviewRepository {

    private final ContentModerationReviewMapper mapper;

    /**
     * 构造实例并初始化字段。
     * @param mapper 参数 mapper（类型 ContentModerationReviewMapper）
     */
    public ContentModerationReviewRepositoryImpl(ContentModerationReviewMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * 保存。
     * @param review 复核记录
     * @return 包含 ContentModerationReview 的响应式结果
     */
    @Override
    public Mono<ContentModerationReview> save(ContentModerationReview review) {
        return Mono.fromCallable(() -> {
                    ContentModerationReviewPO po = toPo(review);
                    if (po.getId() == null) {
                        po.setId(IdWorker.getId());
                    }
                    mapper.insertIgnore(po);
                    // INSERT IGNORE 命中唯一键时返回最近已存在的任务，避免重试产生多条。
                    ContentModerationReviewPO stored = mapper.selectActive(po.getId());
                    if (stored == null) {
                        stored = selectByHash(po);
                    }
                    return toDomain(stored);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 查询 by hash。
     * @param po 参数 po（类型 ContentModerationReviewPO）
     * @return 返回 ContentModerationReviewPO 类型结果
     */
    private ContentModerationReviewPO selectByHash(ContentModerationReviewPO po) {
        return mapper
                .selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                                ContentModerationReviewPO>()
                        .eq(ContentModerationReviewPO::getUserId, po.getUserId())
                        .eq(ContentModerationReviewPO::getSourceType, po.getSourceType())
                        .eq(ContentModerationReviewPO::getContentHash, po.getContentHash())
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * 查询 by ID。
     * @param id 主键 ID
     * @return 包含 ContentModerationReview 的响应式结果
     */
    @Override
    public Mono<ContentModerationReview> findById(Long id) {
        return Mono.fromCallable(() -> toDomain(mapper.selectActive(id))).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 查询 分页。
     * @param status 状态过滤
     * @param mine 是否仅查本人
     * @param reviewerOpenId 审核员 OpenId
     * @param offset 分页偏移量
     * @param limit 每页条数
     * @return 包含 List&lt;ContentModerationReview&gt; 的响应式结果
     */
    @Override
    public Mono<List<ContentModerationReview>> findPage(
            ContentModerationReviewStatus status, boolean mine, String reviewerOpenId, int offset, int limit) {
        return Mono.fromCallable(() -> {
                    mapper.reopenStaleClaims(toLocalDateTime(Instant.now().minusSeconds(1800)));
                    return mapper
                            .selectPage(status == null ? null : status.name(), mine, reviewerOpenId, offset, limit)
                            .stream()
                            .map(this::toDomain)
                            .toList();
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 执行 count 相关逻辑。
     * @param status 状态过滤
     * @param mine 是否仅查本人
     * @param reviewerOpenId 审核员 OpenId
     * @return 包含 Long 的响应式结果
     */
    @Override
    public Mono<Long> count(ContentModerationReviewStatus status, boolean mine, String reviewerOpenId) {
        return Mono.fromCallable(() -> mapper.count(status == null ? null : status.name(), mine, reviewerOpenId))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 认领。
     * @param id 主键 ID
     * @param reviewerOpenId 审核员 OpenId
     * @param expectedVersion 参数 expectedVersion（类型 long）
     * @param claimedAt 参数 claimedAt（类型 Instant）
     * @return 包含 ContentModerationReview 的响应式结果
     */
    @Override
    public Mono<ContentModerationReview> claim(
            Long id, String reviewerOpenId, long expectedVersion, Instant claimedAt) {
        return Mono.fromCallable(() -> {
                    int changed = mapper.casClaim(id, reviewerOpenId, expectedVersion, toLocalDateTime(claimedAt));
                    return changed == 1 ? toDomain(mapper.selectActive(id)) : null;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 裁决。
     * @param id 主键 ID
     * @param reviewerOpenId 审核员 OpenId
     * @param to 参数 to（类型 ContentModerationReviewStatus）
     * @param comment 参数 comment（类型 String）
     * @param expectedVersion 参数 expectedVersion（类型 long）
     * @return 包含 ContentModerationReview 的响应式结果
     */
    @Override
    public Mono<ContentModerationReview> decide(
            Long id, String reviewerOpenId, ContentModerationReviewStatus to, String comment, long expectedVersion) {
        return Mono.fromCallable(() -> {
                    int changed = mapper.casDecide(id, reviewerOpenId, to.name(), comment, expectedVersion);
                    return changed == 1 ? toDomain(mapper.selectActive(id)) : null;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 执行 reopenStaleClaims 相关逻辑。
     * @param claimedBefore 参数 claimedBefore（类型 Instant）
     * @return 包含 Integer 的响应式结果
     */
    @Override
    public Mono<Integer> reopenStaleClaims(Instant claimedBefore) {
        return Mono.fromCallable(() -> mapper.reopenStaleClaims(toLocalDateTime(claimedBefore)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 执行 toDomain 相关逻辑。
     * @param po 参数 po（类型 ContentModerationReviewPO）
     * @return 返回 ContentModerationReview 类型结果
     */
    private ContentModerationReview toDomain(ContentModerationReviewPO po) {
        if (po == null) {
            return null;
        }
        return new ContentModerationReview(
                po.getId(),
                po.getSourceType(),
                po.getUserId(),
                po.getContentText(),
                po.getMediaJson(),
                po.getProvider(),
                po.getProviderRequestId(),
                po.getFindingsJson(),
                po.getContentHash(),
                po.getStatus() == null
                        ? ContentModerationReviewStatus.PENDING
                        : ContentModerationReviewStatus.valueOf(po.getStatus()),
                po.getReviewerOpenId(),
                po.getReviewComment(),
                toInstant(po.getClaimedAt()),
                toInstant(po.getDecidedAt()),
                po.getVersion() == null ? 0L : po.getVersion());
    }

    /**
     * 执行 toPo 相关逻辑。
     * @param review 复核记录
     * @return 返回 ContentModerationReviewPO 类型结果
     */
    private static ContentModerationReviewPO toPo(ContentModerationReview review) {
        ContentModerationReviewPO po = new ContentModerationReviewPO();
        po.setId(review.id());
        po.setSourceType(review.sourceType());
        po.setUserId(review.userId());
        po.setContentText(review.contentText());
        po.setMediaJson(review.mediaJson());
        po.setProvider(review.provider());
        po.setProviderRequestId(review.providerRequestId());
        po.setFindingsJson(review.findingsJson());
        po.setContentHash(review.contentHash());
        po.setStatus(review.status().name());
        po.setReviewerOpenId(review.reviewerOpenId());
        po.setReviewComment(review.reviewComment());
        po.setClaimedAt(toLocalDateTime(review.claimedAt()));
        po.setDecidedAt(toLocalDateTime(review.decidedAt()));
        po.setVersion((int) review.version());
        return po;
    }

    /**
     * Instant 转 LocalDateTime（数据库 DATETIME 列使用系统默认时区）。
     *
     * @param instant 时间点；可为 null
     * @return LocalDateTime；入参为 null 时返回 null
     */
    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    /**
     * LocalDateTime 转 Instant（数据库 DATETIME 列使用系统默认时区）。
     *
     * @param localDateTime 数据库本地时间；可为 null
     * @return Instant；入参为 null 时返回 null
     */
    private static Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime == null
                ? null
                : localDateTime.atZone(ZoneId.systemDefault()).toInstant();
    }
}
