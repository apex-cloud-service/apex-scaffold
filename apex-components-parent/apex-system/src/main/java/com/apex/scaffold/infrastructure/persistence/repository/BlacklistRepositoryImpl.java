package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.BlacklistEntity;
import com.apex.scaffold.domain.repository.BlacklistRepository;
import com.apex.scaffold.infrastructure.persistence.converter.BlacklistPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.BlacklistMapper;
import com.apex.scaffold.infrastructure.persistence.po.BlacklistPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 黑名单仓库实现
 *
 * <p>用于黑名单数据的持久化操作，提供黑名单的添加、移除和查询功能。
 * 黑名单机制用于限制用户之间的交互，如拉黑后无法发送消息等。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>upsert：添加或更新黑名单记录（拉黑/取消拉黑）</li>
 *   <li>listEnabledTargetUserIds：查询用户启用的黑名单目标用户ID列表</li>
 *   <li>isBlocked：检查两个用户之间是否存在拉黑关系</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>聊天过滤：发送消息前检查双方是否互相拉黑</li>
 *   <li>内容推荐：排除被拉黑用户的内容</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see BlacklistRepository
 * @see BlacklistMapper
 * @see BlacklistPoConverter
 */
@Repository
@RequiredArgsConstructor
public class BlacklistRepositoryImpl implements BlacklistRepository {

    private final BlacklistMapper mapper;
    private final BlacklistPoConverter blacklistPoConverter;

    /**
     * 添加或更新黑名单记录。
     *
     * <p>如果该用户对目标用户的黑名单记录不存在，则创建新记录；否则更新已有记录。
     * 可以通过enabled字段控制是拉黑（enabled=1）还是取消拉黑（enabled=0）。
     *
     * @param entity 黑名单实体，包含用户ID、目标用户ID和启用状态
     * @return 保存后的黑名单实体
     */
    @Override
    public Mono<BlacklistEntity> upsert(BlacklistEntity entity) {
        return Mono.fromCallable(() -> {
                    BlacklistPO existing = mapper.selectOne(new LambdaQueryWrapper<BlacklistPO>()
                            .eq(BlacklistPO::getUserId, entity.getUserId())
                            .eq(BlacklistPO::getTargetUserId, entity.getTargetUserId())
                            .last("limit 1"));
                    BlacklistPO po = existing == null ? blacklistPoConverter.toPo(entity) : existing;
                    if (existing != null) {
                        blacklistPoConverter.updatePo(entity, po);
                    }
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
                .map(blacklistPoConverter::toEntity);
    }

    /**
     * 查询用户启用的黑名单目标用户ID列表。
     *
     * <p>返回指定用户拉黑的所有目标用户ID列表（状态为enabled=1）。
     * 结果按更新时间倒序排列。
     *
     * @param userId 用户ID
     * @return 被该用户拉黑的目标用户ID列表
     */
    @Override
    public Mono<List<Long>> listEnabledTargetUserIds(Long userId) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<BlacklistPO>()
                        .eq(BlacklistPO::getUserId, userId)
                        .eq(BlacklistPO::getEnabled, 1)
                        .orderByDesc(BlacklistPO::getUpdateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(list -> list.stream().map(BlacklistPO::getTargetUserId).toList());
    }

    /**
     * 检查指定用户是否被目标用户拉黑。
     *
     * <p>检查用户A是否在用户B的黑名单中且状态为启用。
     * 即检查是否存在 userId=B, targetUserId=A, enabled=1 的记录。
     *
     * @param userId 用户A的ID（检查谁是否被拉黑）
     * @param targetUserId 用户B的ID（拉黑者）
     * @return 如果用户B拉黑了用户A则返回true，否则返回false
     */
    @Override
    public Mono<Boolean> isBlocked(Long userId, Long targetUserId) {
        return Mono.fromCallable(() -> mapper.selectCount(new LambdaQueryWrapper<BlacklistPO>()
                                .eq(BlacklistPO::getUserId, userId)
                                .eq(BlacklistPO::getTargetUserId, targetUserId)
                                .eq(BlacklistPO::getEnabled, 1))
                        > 0)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
