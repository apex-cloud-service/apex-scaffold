package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.SystemMessageEntity;
import com.apex.scaffold.domain.repository.SystemMessageRepository;
import com.apex.scaffold.infrastructure.persistence.converter.SystemMessagePoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.SystemMessageMapper;
import com.apex.scaffold.infrastructure.persistence.po.SystemMessagePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 系统消息仓库实现
 *
 * <p>用于系统消息的持久化操作，提供系统消息的保存和查询功能。
 * 系统消息用于向用户推送通知、活动提醒、功能更新等各类系统信息。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>消息保存：创建或更新系统消息记录</li>
 *   <li>消息查询：分页查询用户接收到的系统消息列表</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SystemMessageRepository
 * @see SystemMessageMapper
 * @see SystemMessagePoConverter
 */
@Repository
@RequiredArgsConstructor
public class SystemMessageRepositoryImpl implements SystemMessageRepository {

    private final SystemMessageMapper mapper;
    private final SystemMessagePoConverter converter;

    /**
     * 保存或更新系统消息。
     *
     * <p>如果消息ID为空，则创建新消息；否则更新已有消息。
     * 保存时自动设置创建时间和更新时间，并确保deleted标记为0（未删除）。
     *
     * @param entity 系统消息实体对象
     * @return 保存后的系统消息实体
     */
    @Override
    public Mono<SystemMessageEntity> save(SystemMessageEntity entity) {
        return Mono.fromCallable(() -> {
                    SystemMessagePO po = converter.toPo(entity);
                    po.setDeleted(0);
                    LocalDateTime now = LocalDateTime.now();
                    if (po.getCreateTime() == null) {
                        po.setCreateTime(now);
                    }
                    po.setUpdateTime(now);
                    if (po.getId() == null) {
                        mapper.insert(po);
                    } else {
                        mapper.updateById(po);
                    }
                    return mapper.selectById(po.getId());
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(converter::toEntity);
    }

    /**
     * 分页查询用户系统消息列表。
     *
     * <p>按创建时间倒序排列，查询指定用户的系统消息列表。</p>
     *
     * @param userId 用户 ID
     * @param offset 偏移量
     * @param limit  返回记录数量上限
     * @return 系统消息实体列表
     */
    @Override
    public Mono<List<SystemMessageEntity>> listByUserId(Long userId, int offset, int limit) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<SystemMessagePO>()
                        .eq(SystemMessagePO::getUserId, userId)
                        .orderByDesc(SystemMessagePO::getCreateTime)
                        .last("limit " + limit + " offset " + offset)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(converter::toEntityList);
    }

    /**
     * 统计指定用户的系统消息数量。
     *
     * <p>按用户 ID 精确匹配，在 JDBC 弹性调度器上执行 COUNT 查询。</p>
     *
     * @param userId 用户 ID
     * @return 系统消息数量
     */
    @Override
    public Mono<Long> countByUserId(Long userId) {
        return Mono.fromCallable(() -> mapper.selectCount(
                        new LambdaQueryWrapper<SystemMessagePO>().eq(SystemMessagePO::getUserId, userId)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 分页查询用户站内信列表。
     *
     * <p>按创建时间倒序分页返回指定用户的消息，支持仅返回未读消息的过滤。
     * 结果按 {@link PageResponse} 规范包装，包含总记录数、总页数与下一页标记。</p>
     *
     * @param userId     用户 ID
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @param unreadOnly 是否仅返回未读消息
     * @return 分页结果
     */
    @Override
    public Mono<PageResponse<SystemMessageEntity>> pageByUser(Long userId, int page, int size, boolean unreadOnly) {
        return Mono.fromCallable(() -> {
                    long total = mapper.selectCount(baseWrapper(userId, unreadOnly));
                    int offset = Math.max(page - 1, 0) * size;
                    List<SystemMessagePO> pos = mapper.selectList(
                            baseWrapper(userId, unreadOnly).last("limit " + size + " offset " + offset));
                    return PageResponse.of(converter.toEntityList(pos), total, page, size);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 构造按用户过滤、可选仅未读、按创建时间倒序的查询条件（供分页复用）。
     *
     * @param userId     用户 ID
     * @param unreadOnly 是否仅返回未读消息
     * @return 查询条件
     */
    private LambdaQueryWrapper<SystemMessagePO> baseWrapper(Long userId, boolean unreadOnly) {
        LambdaQueryWrapper<SystemMessagePO> wrapper = new LambdaQueryWrapper<SystemMessagePO>()
                .eq(SystemMessagePO::getUserId, userId)
                .orderByDesc(SystemMessagePO::getCreateTime);
        if (unreadOnly) {
            wrapper.isNull(SystemMessagePO::getReadAt);
        }
        return wrapper;
    }

    /**
     * 统计用户未读站内信数量。
     *
     * @param userId 用户 ID
     * @return 未读消息数量
     */
    @Override
    public Mono<Long> countUnread(Long userId) {
        return Mono.fromCallable(() -> mapper.selectCount(new LambdaQueryWrapper<SystemMessagePO>()
                        .eq(SystemMessagePO::getUserId, userId)
                        .isNull(SystemMessagePO::getReadAt)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 将指定用户的单条消息标记为已读（幂等）。
     *
     * <p>仅当消息属于该用户且尚未标记已读时执行更新。已读时返回 {@code false}，
     * 使重复调用幂等返回成功而不产生额外副作用。</p>
     *
     * @param userId    用户 ID
     * @param messageId 消息 ID
     * @return 是否实际更新了记录
     */
    @Override
    public Mono<Boolean> markRead(Long userId, Long messageId) {
        return Mono.fromCallable(() -> mapper.update(
                        null,
                        new LambdaUpdateWrapper<SystemMessagePO>()
                                .set(SystemMessagePO::getReadAt, LocalDateTime.now())
                                .set(SystemMessagePO::getUpdateTime, LocalDateTime.now())
                                .eq(SystemMessagePO::getId, messageId)
                                .eq(SystemMessagePO::getUserId, userId)
                                .isNull(SystemMessagePO::getReadAt)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(rows -> rows > 0);
    }

    /**
     * 将指定用户的全部未读消息标记为已读。
     *
     * @param userId 用户 ID
     * @return 实际标记已读的消息数量
     */
    @Override
    public Mono<Long> markAllRead(Long userId) {
        return Mono.fromCallable(() -> mapper.update(
                        null,
                        new LambdaUpdateWrapper<SystemMessagePO>()
                                .set(SystemMessagePO::getReadAt, LocalDateTime.now())
                                .set(SystemMessagePO::getUpdateTime, LocalDateTime.now())
                                .eq(SystemMessagePO::getUserId, userId)
                                .isNull(SystemMessagePO::getReadAt)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(rows -> rows.longValue());
    }
}
