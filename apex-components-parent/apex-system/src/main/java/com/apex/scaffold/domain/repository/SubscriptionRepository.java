package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.SubscriptionEntity;
import reactor.core.publisher.Mono;

/**
 * 订阅记录仓储接口（领域层）
 *
 * <p>定义用户订阅（Subscription）的持久化操作规范，
 * 由基础设施层的 {@code SubscriptionRepositoryImpl} 提供具体实现。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface SubscriptionRepository {
    /**
     * 按用户ID查询当前有效订阅。
     *
     * <p>根据用户ID查询状态为 ACTIVE 的订阅记录，按过期时间倒序取最新一条，
     * 若不存在则返回空信号。
     *
     * @param userId 用户ID
     * @return 订阅实体的 Mono 包装，可能为空
     */
    Mono<SubscriptionEntity> findActiveByUserId(Long userId);

    /**
     * 查询结束时间尚未到达的订阅权益。
     *
     * <p>已取消但尚在有效期内的订阅同样阻止注销。</p>
     *
     * @param userId 用户 ID
     * @return 任一未过期订阅；没有则为空
     */
    default Mono<SubscriptionEntity> findUnexpiredByUserId(Long userId) {
        return Mono.empty();
    }

    /**
     * 创建或更新订阅记录。
     *
     * <p>若实体未分配 id 则执行插入，否则按 id 更新已有记录，
     * 操作完成后返回数据库中最新状态的实体。
     *
     * @param entity 订阅实体
     * @return 保存后最新状态的订阅实体
     */
    Mono<SubscriptionEntity> save(SubscriptionEntity entity);
}
