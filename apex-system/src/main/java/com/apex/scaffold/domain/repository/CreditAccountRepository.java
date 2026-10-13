package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.CreditAccountEntity;
import reactor.core.publisher.Mono;

/**
 * 积分账户仓储接口（领域层）
 *
 * <p>定义积分账户（CreditAccount）的持久化操作规范，
 * 由基础设施层的 {@code CreditAccountRepositoryImpl} 提供具体实现。
 *
 * <h2>并发控制</h2>
 * <p>支持乐观锁机制，通过version字段实现CAS操作，防止并发更新冲突。</p>
 *
 * @author Apex Team
 * @version 2.1.0
 * @since 1.0.0
 */
public interface CreditAccountRepository {

    /**
     * 根据用户ID查询积分账户
     *
     * @param userId 用户ID
     * @return 积分账户实体
     */
    Mono<CreditAccountEntity> findByUserId(Long userId);

    /**
     * 保存积分账户
     *
     * @param entity 积分账户实体
     * @return 保存后的积分账户实体
     */
    Mono<CreditAccountEntity> save(CreditAccountEntity entity);

    /**
     * 增加算力余额（带乐观锁版本控制）。
     *
     * <p>使用CAS（Compare-And-Swap）语义进行乐观锁更新：
     * 仅当数据库中的version与传入的version匹配时才执行更新，
     * 更新成功时version自动+1。若版本不匹配（并发冲突），返回false。</p>
     *
     * @param userId  用户ID
     * @param amount  增加金额
     * @param version 当前版本号（用于乐观锁冲突检测）
     * @return 是否增加成功（版本冲突返回false）
     */
    Mono<Boolean> incrementBalance(Long userId, Long amount, Long version);

    /**
     * 扣除算力余额（带乐观锁版本控制）。
     *
     * <p>使用CAS（Compare-And-Swap）语义进行乐观锁更新：
     * 仅当数据库中的version与传入的version匹配时才执行更新，
     * 更新成功时version自动+1。若版本不匹配（并发冲突），返回false。</p>
     *
     * @param userId  用户ID
     * @param amount  扣除金额
     * @param version 当前版本号（用于乐观锁冲突检测）
     * @return 是否扣除成功（版本冲突返回false）
     */
    Mono<Boolean> decrementBalance(Long userId, Long amount, Long version);

    /**
     * 不存在则创建账户
     *
     * @param userId 用户ID
     * @return 积分账户实体
     */
    Mono<CreditAccountEntity> createIfNotExists(Long userId);

    /**
     * 使用乐观锁更新登录奖励
     *
     * <p>使用CAS操作更新账户，确保并发安全。
     * 当version不匹配时返回false，表示更新失败。</p>
     *
     * @param userId 用户ID
     * @param currentVersion 当前版本号
     * @param newBalance 新余额
     * @return 是否更新成功
     */
    Mono<Boolean> updateLoginBonusWithVersion(Long userId, Long currentVersion, Long newBalance);

    /**
     * 使用乐观锁更新收藏奖励
     *
     * <p>使用CAS操作更新账户，确保并发安全。
     * 当version不匹配时返回false，表示更新失败。</p>
     *
     * @param userId 用户ID
     * @param currentVersion 当前版本号
     * @param newDailyTotal 新的当日收藏累计
     * @param dailyFavoriteDate 收藏统计日期
     * @param newBalance 新余额
     * @return 是否更新成功
     */
    Mono<Boolean> updateFavoriteRewardWithVersion(
            Long userId,
            Long currentVersion,
            Long newDailyTotal,
            java.time.LocalDate dailyFavoriteDate,
            Long newBalance);
}
