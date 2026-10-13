package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.SubscriptionEntity;
import com.apex.scaffold.domain.repository.SubscriptionRepository;
import com.apex.scaffold.infrastructure.persistence.converter.SubscriptionPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.SubscriptionMapper;
import com.apex.scaffold.infrastructure.persistence.po.SubscriptionPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 订阅仓库实现（基础设施层）。
 *
 * <p>负责 {@link SubscriptionEntity} 的持久化读写，并通过 {@link SubscriptionPoConverter}
 * 完成领域实体与持久化对象之间的转换。
 * 阻塞型数据库访问封装在 {@link Mono#fromCallable(java.util.concurrent.Callable)} 中并调度到
 * 专用阻塞 I/O 调度器执行。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SubscriptionRepository
 */
@Repository
@RequiredArgsConstructor
public class SubscriptionRepositoryImpl implements SubscriptionRepository {

    private final SubscriptionMapper mapper;
    private final SubscriptionPoConverter subscriptionPoConverter;

    /**
     * 查询用户当前有效订阅。
     *
     * <p>按 userId、status=ACTIVE 和未过期条件过滤，并按 endAt 倒序取第一条（limit 1）。
     *
     * @param userId 用户 ID
     * @return 当前有效订阅；若不存在则返回空 Mono
     */
    @Override
    public Mono<SubscriptionEntity> findActiveByUserId(Long userId) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<SubscriptionPO>()
                        .eq(SubscriptionPO::getUserId, userId)
                        .eq(SubscriptionPO::getStatus, "ACTIVE")
                        .gt(SubscriptionPO::getEndAt, LocalDateTime.now())
                        .orderByDesc(SubscriptionPO::getEndAt)
                        .last("limit 1")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(subscriptionPoConverter::toEntity);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<SubscriptionEntity> findUnexpiredByUserId(Long userId) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<SubscriptionPO>()
                        .eq(SubscriptionPO::getUserId, userId)
                        .gt(SubscriptionPO::getEndAt, LocalDateTime.now())
                        .orderByDesc(SubscriptionPO::getEndAt)
                        .last("limit 1")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(subscriptionPoConverter::toEntity);
    }

    /**
     * 保存订阅记录（新增或更新）。
     *
     * <p>当 id 为空时执行 insert；否则执行 updateById。
     * 保存时会设置 deleted=0，并维护 createTime/updateTime。
     *
     * @param entity 订阅实体
     * @return 保存后的订阅实体
     */
    @Override
    public Mono<SubscriptionEntity> save(SubscriptionEntity entity) {
        return Mono.fromCallable(() -> {
                    SubscriptionPO po = subscriptionPoConverter.toPo(entity);
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
                .map(subscriptionPoConverter::toEntity);
    }
}
