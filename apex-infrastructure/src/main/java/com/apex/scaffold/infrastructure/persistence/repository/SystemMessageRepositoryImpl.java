package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.SystemMessageEntity;
import com.apex.scaffold.domain.repository.SystemMessageRepository;
import com.apex.scaffold.infrastructure.persistence.converter.SystemMessagePoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.SystemMessageMapper;
import com.apex.scaffold.infrastructure.persistence.po.SystemMessagePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * <h2>核心功能</h2>
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
    /**
     * 保存
     *
     * @param entity 参数
     *
     * @return 结果
     */
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
    /**
     * 列表ByUserId
     *
     * @param userId 参数
     *
     * @param offset 参数
     *
     * @param limit 参数
     *
     * @return 结果
     */
    public Mono<List<SystemMessageEntity>> listByUserId(Long userId, int offset, int limit) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<SystemMessagePO>()
                        .eq(SystemMessagePO::getUserId, userId)
                        .orderByDesc(SystemMessagePO::getCreateTime)
                        .last("limit " + limit + " offset " + offset)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(converter::toEntityList);
    }

    /**
     * 执行countByUserId操作
     *
     * <p>详细说明</p>
     *
     * @param userId 参数说明
     * @return 返回值说明
     */
    @Override
    /**
     * 计数ByUserId
     *
     * @param userId 参数
     *
     * @return 结果
     */
    public Mono<Long> countByUserId(Long userId) {
        return Mono.fromCallable(() -> mapper.selectCount(
                        new LambdaQueryWrapper<SystemMessagePO>().eq(SystemMessagePO::getUserId, userId)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
