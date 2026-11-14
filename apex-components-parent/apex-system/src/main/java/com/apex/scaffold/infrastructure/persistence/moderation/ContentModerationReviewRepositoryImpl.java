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
     * 使用指定的 Mapper 构造仓储实现。
     *
     * @param mapper 复核任务 Mapper，不可为空
     */
    public ContentModerationReviewRepositoryImpl(ContentModerationReviewMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * 保存复核记录；重复内容命中唯一键时返回已存在的任务。
     *
     * @param review 复核记录
     * @return 已保存（或已存在）的复核记录
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
     * 按 userId / sourceType / contentHash 组合查询已存在任务。
     *
     * @param po 待查询的复核任务持久化对象
     * @return 命中的持久化对象；不存在时返回 null
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
     * 根据主键 ID 查询复核记录。
     *
     * @param id 主键 ID
     * @return 匹配的复核记录；不存在时返回空
     */
    @Override
    public Mono<ContentModerationReview> findById(Long id) {
        return Mono.fromCallable(() -> toDomain(mapper.selectActive(id))).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 分页查询复核记录，查询前自动重新开放已超时的认领任务。
     *
     * @param status         状态过滤，为空表示不过滤
     * @param mine           是否仅查本人（审核员视角）
     * @param reviewerOpenId 审核员 OpenId
     * @param offset         分页偏移量
     * @param limit          每页条数
     * @return 分页后的复核记录列表
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
     * 统计复核记录数量。
     *
     * @param status         状态过滤，为空表示不过滤
     * @param mine           是否仅查本人（审核员视角）
     * @param reviewerOpenId 审核员 OpenId
     * @return 符合条件的记录数量
     */
    @Override
    public Mono<Long> count(ContentModerationReviewStatus status, boolean mine, String reviewerOpenId) {
        return Mono.fromCallable(() -> mapper.count(status == null ? null : status.name(), mine, reviewerOpenId))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 认领任务：PENDING → REVIEWING，乐观锁 CAS，并发下仅一人成功。
     *
     * @param id              主键 ID
     * @param reviewerOpenId  审核员 OpenId
     * @param expectedVersion 期望的版本号（乐观锁）
     * @param claimedAt       认领时间
     * @return 认领成功后的复核记录；CAS 失败时返回空
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
     * 裁决任务：REVIEWING → APPROVED/REJECTED，仅认领人可裁决，乐观锁 CAS。
     *
     * @param id              主键 ID
     * @param reviewerOpenId  审核员 OpenId（须与认领人一致）
     * @param to              目标状态
     * @param comment         裁决意见
     * @param expectedVersion 期望的版本号（乐观锁）
     * @return 裁决后的复核记录；CAS 失败时返回空
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
     * 领域对象转持久化对象。
     *
     * @param review 复核记录领域对象
     * @return 复核任务持久化对象
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
