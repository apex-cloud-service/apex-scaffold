package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.CreditAccountEntity;
import com.apex.scaffold.domain.repository.CreditAccountRepository;
import com.apex.scaffold.infrastructure.persistence.converter.CreditAccountPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.CreditAccountMapper;
import com.apex.scaffold.infrastructure.persistence.po.CreditAccountPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 信用账户仓储实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link CreditAccountRepository} 接口定义的信用账户持久化操作。
 * 提供用户信用账户的查询、创建、余额增减等核心功能。</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li>根据用户ID查询信用账户</li>
 *   <li>保存或更新信用账户信息</li>
 *   <li>递增/递减账户余额（带边界保护）</li>
 *   <li>按需创建不存在的信用账户</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class CreditAccountRepositoryImpl implements CreditAccountRepository {
    private static final Logger log = LoggerFactory.getLogger(CreditAccountRepositoryImpl.class);

    private final CreditAccountMapper mapper;
    private final CreditAccountPoConverter creditAccountPoConverter;

    /**
     * 根据用户 ID 查询信用账户。
     *
     * @param userId 用户 ID
     * @return 信用账户实体；若不存在则返回空 Mono
     */
    @Override
    public Mono<CreditAccountEntity> findByUserId(Long userId) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<CreditAccountPO>()
                        .eq(CreditAccountPO::getUserId, userId)
                        .last("limit 1")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(creditAccountPoConverter::toEntity);
    }

    /**
     * 保存或更新信用账户。
     *
     * <p>如果实体 ID 为空则执行 insert，否则执行 updateById。
     * 保存时自动设置创建时间和更新时间，并确保 deleted 标记为 0。</p>
     *
     * @param entity 信用账户实体
     * @return 保存后的信用账户实体
     */
    @Override
    public Mono<CreditAccountEntity> save(CreditAccountEntity entity) {
        return Mono.fromCallable(() -> {
                    CreditAccountPO po = creditAccountPoConverter.toPo(entity);
                    po.setDeleted(0);
                    LocalDateTime now = LocalDateTime.now();
                    if (po.getCreateTime() == null) {
                        po.setCreateTime(now);
                    }
                    po.setUpdateTime(now);
                    if (po.getId() == null) {
                        if (mapper.insert(po) != 1) {
                            throw new IllegalStateException("创建算力账户失败, userId=" + entity.getUserId());
                        }
                    } else {
                        if (mapper.updateById(po) != 1) {
                            throw new OptimisticLockingFailureException("算力账户并发更新冲突, accountId=" + po.getId());
                        }
                    }
                    return mapper.selectById(po.getId());
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(creditAccountPoConverter::toEntity);
    }

    /**
     * 按需创建信用账户。
     *
     * <p>如果指定用户的信用账户不存在，则创建一个余额为 0 的新账户；
     * 如果已存在则直接返回现有账户。</p>
     *
     * @param userId 用户 ID
     * @return 信用账户实体
     */
    @Override
    public Mono<CreditAccountEntity> createIfNotExists(Long userId) {
        return Mono.fromCallable(() -> {
                    CreditAccountPO existing = mapper.selectOne(new LambdaQueryWrapper<CreditAccountPO>()
                            .eq(CreditAccountPO::getUserId, userId)
                            .last("limit 1"));
                    if (existing != null) {
                        return existing;
                    }
                    CreditAccountPO po = new CreditAccountPO();
                    po.setUserId(userId);
                    po.setBalance(0L);
                    po.setVersion(0);
                    po.setDeleted(0);
                    LocalDateTime now = LocalDateTime.now();
                    po.setCreateTime(now);
                    po.setUpdateTime(now);
                    mapper.insert(po);
                    return mapper.selectById(po.getId());
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(creditAccountPoConverter::toEntity);
    }

    /**
     * 递增账户余额（带乐观锁版本号，保证并发安全）。
     *
     * <p>使用 CAS（Compare-And-Swap）乐观锁，仅在版本号匹配时更新余额。
     * 更新成功后版本号自动加 1。用于防止并发场景下的余额更新丢失。</p>
     *
     * @param userId  用户 ID
     * @param amount  递增金额
     * @param version 当前版本号（乐观锁）
     * @return 递增成功返回 true，失败返回 false
     * @since 2.1.0
     */
    @Override
    public Mono<Boolean> incrementBalance(Long userId, Long amount, Long version) {
        return Mono.fromCallable(() -> {
                    Integer ver = version != null ? version.intValue() : 0;
                    int updated = mapper.update(
                            null,
                            new LambdaUpdateWrapper<CreditAccountPO>()
                                    .eq(CreditAccountPO::getUserId, userId)
                                    .eq(CreditAccountPO::getVersion, ver)
                                    .setSql("balance = balance + " + amount)
                                    .set(CreditAccountPO::getVersion, ver + 1)
                                    .set(CreditAccountPO::getUpdateTime, LocalDateTime.now()));
                    return updated > 0;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 递减账户余额（带乐观锁版本号，保证并发安全）。
     *
     * <p>使用 CAS 乐观锁，仅在版本号匹配且余额充足时更新。
     * 更新成功后版本号自动加 1。余额最小为 0，不会出现负数。</p>
     *
     * @param userId  用户 ID
     * @param amount  递减金额
     * @param version 当前版本号（乐观锁）
     * @return 递减成功返回 true，失败返回 false
     * @since 2.1.0
     */
    @Override
    public Mono<Boolean> decrementBalance(Long userId, Long amount, Long version) {
        return Mono.fromCallable(() -> {
                    Integer ver = version != null ? version.intValue() : 0;
                    int updated = mapper.update(
                            null,
                            new LambdaUpdateWrapper<CreditAccountPO>()
                                    .eq(CreditAccountPO::getUserId, userId)
                                    .eq(CreditAccountPO::getVersion, ver)
                                    .ge(CreditAccountPO::getBalance, amount)
                                    .setSql("balance = balance - " + amount)
                                    .set(CreditAccountPO::getVersion, ver + 1)
                                    .set(CreditAccountPO::getUpdateTime, LocalDateTime.now()));
                    return updated > 0;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 乐观锁更新登录奖励。
     *
     * <p>使用乐观锁（version 字段）原子性地更新用户登录奖励余额，
     * 并标记当日已领取登录奖励。如果版本号不匹配则更新失败。</p>
     *
     * @param userId         用户 ID
     * @param currentVersion 当前版本号（乐观锁）
     * @param newBalance     新的账户余额
     * @return 更新成功返回 true，版本冲突返回 false
     */
    @Override
    public Mono<Boolean> updateLoginBonusWithVersion(Long userId, Long currentVersion, Long newBalance) {
        return Mono.fromCallable(() -> {
                    Integer version = currentVersion != null ? currentVersion.intValue() : 0;
                    int updated = mapper.update(
                            null,
                            new LambdaUpdateWrapper<CreditAccountPO>()
                                    .eq(CreditAccountPO::getUserId, userId)
                                    .eq(CreditAccountPO::getVersion, version)
                                    .set(CreditAccountPO::getBalance, newBalance)
                                    .set(CreditAccountPO::getTodayLoginBonus, true)
                                    .set(CreditAccountPO::getVersion, version + 1)
                                    .set(CreditAccountPO::getUpdateTime, LocalDateTime.now()));
                    boolean success = updated > 0;
                    if (success) {
                        log.debug("updateLoginBonusWithVersion: success, userId={}, version={}", userId, version);
                    } else {
                        log.debug(
                                "updateLoginBonusWithVersion: version conflict, userId={}, expectedVersion={}",
                                userId,
                                version);
                    }
                    return success;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 乐观锁更新收藏奖励。
     *
     * <p>使用乐观锁（version 字段）原子性地更新用户收藏奖励相关的
     * 当日收藏总数、收藏日期和账户余额。如果版本号不匹配则更新失败。</p>
     *
     * @param userId             用户 ID
     * @param currentVersion     当前版本号（乐观锁）
     * @param newDailyTotal      新的当日收藏总数
     * @param dailyFavoriteDate  当日收藏日期
     * @param newBalance         新的账户余额
     * @return 更新成功返回 true，版本冲突返回 false
     */
    @Override
    public Mono<Boolean> updateFavoriteRewardWithVersion(
            Long userId, Long currentVersion, Long newDailyTotal, LocalDate dailyFavoriteDate, Long newBalance) {
        return Mono.fromCallable(() -> {
                    Integer version = currentVersion != null ? currentVersion.intValue() : 0;
                    int updated = mapper.update(
                            null,
                            new LambdaUpdateWrapper<CreditAccountPO>()
                                    .eq(CreditAccountPO::getUserId, userId)
                                    .eq(CreditAccountPO::getVersion, version)
                                    .set(CreditAccountPO::getDailyFavoriteTotal, newDailyTotal)
                                    .set(CreditAccountPO::getDailyFavoriteDate, dailyFavoriteDate)
                                    .set(CreditAccountPO::getBalance, newBalance)
                                    .set(CreditAccountPO::getVersion, version + 1)
                                    .set(CreditAccountPO::getUpdateTime, LocalDateTime.now()));
                    boolean success = updated > 0;
                    if (success) {
                        log.debug(
                                "updateFavoriteRewardWithVersion: success, userId={}, version={}, dailyTotal={}",
                                userId,
                                version,
                                newDailyTotal);
                    } else {
                        log.debug(
                                "updateFavoriteRewardWithVersion: version conflict, userId={}, expectedVersion={}",
                                userId,
                                version);
                    }
                    return success;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
