package com.apex.scaffold.domain.service;

import com.apex.scaffold.domain.model.entity.CreditAccountEntity;
import com.apex.scaffold.domain.repository.CreditAccountRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * 积分算力服务 v2.1
 *
 * <p>管理用户的积分账户，提供积分余额查询、增加（上限2000）、
 * 消费以及各类奖励发放功能（每日登录奖励、投票奖励、收藏奖励、邀请奖励）。</p>
 *
 * <h2>并发控制</h2>
 * <p>采用乐观锁机制（version字段）保证并发安全。在高并发场景下，
 * 通过CAS操作确保数据一致性，避免超发奖励问题。</p>
 *
 * <h2>时区处理</h2>
 * <p>所有日期计算使用Asia/Shanghai时区，确保跨时区用户的日期判断一致性。</p>
 *
 * @author Apex Team
 * @version 2.1.1
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditService {

    /**
     * 积分账户仓储
     *
     * <p>用于执行积分账户的持久化操作，支持乐观锁更新。</p>
     */
    private final CreditAccountRepository creditAccountRepository;

    /** 业务指标端口；只在积分实际成功变更后记录指标。 */
    private final BusinessMetricsPort businessMetricsPort;

    /**
     * 最大积分余额上限
     *
     * <p>用户积分余额的最大限制，超过此值时积分不再增加。</p>
     */
    private static final long MAX_BALANCE = 2000L;

    /**
     * 积分余额操作的乐观锁最大重试次数
     *
     * <p>在高并发下，多次余额变更可能连续命中版本冲突。这里保留额外重试窗口，
     * 避免少量瞬时竞争直接放大为业务失败。</p>
     */
    private static final long BALANCE_OPERATION_MAX_RETRIES = 5L;

    /**
     * 每日登录奖励积分
     */
    private static final long DAILY_LOGIN_BONUS = 3L;

    /**
     * 二选一投票奖励积分
     */
    private static final long VOTE_REWARD_CHOICE2 = 1L;

    /**
     * 四选二投票奖励积分（有正面照）
     */
    private static final long VOTE_REWARD_CHOICE4_FRONTAL = 3L;

    /**
     * 四选二投票奖励积分（无正面照）
     */
    private static final long VOTE_REWARD_CHOICE4_NO_FRONTAL = 2L;

    /** 二选一对局类型 */
    private static final String BATTLE_TYPE_CHOICE_2 = "CHOICE_2";

    /** 二选一自锚对局类型 */
    private static final String BATTLE_TYPE_CHOICE_2_SELF_ANCHOR = "CHOICE_2_SELF_ANCHOR";

    /** 二选一全局校准对局类型 */
    private static final String BATTLE_TYPE_CHOICE_2_GLOBAL_CALIBRATION = "CHOICE_2_GLOBAL_CALIBRATION";

    /**
     * 收藏奖励积分
     */
    private static final long FAVORITE_REWARD = 5L;

    /**
     * 收藏每日积分上限
     */
    private static final long FAVORITE_DAILY_LIMIT = 200L;

    /**
     * 邀请奖励积分
     */
    private static final long INVITE_REWARD = 100L;

    /**
     * 默认时区（上海时区）
     *
     * <p>用于日期计算，确保跨时区用户的日期判断一致性。</p>
     */
    private static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 查询用户积分余额
     *
     * <p>根据用户ID查询当前积分余额，不存在时返回0。</p>
     *
     * @param userId 用户ID
     * @return 当前积分余额
     */
    public Mono<Long> getBalance(Long userId) {
        log.debug("查询用户积分余额, userId={}", userId);

        return creditAccountRepository
                .findByUserId(userId)
                .map(CreditAccountEntity::getBalance)
                .defaultIfEmpty(0L)
                .doOnSuccess(balance -> log.info("查询用户积分余额成功, userId={}, balance={}", userId, balance))
                .doOnError(e -> log.error("查询用户积分余额失败, userId={}", userId, e));
    }

    /**
     * 增加积分（上限2000）
     *
     * <p>使用乐观锁机制确保并发安全。积分超过上限时自动截断至上限值。</p>
     *
     * @param userId 用户ID
     * @param amount 增加金额
     * @param reason 增加原因（用于日志记录）
     * @return 是否增加成功
     */
    public Mono<Boolean> addCredit(Long userId, Long amount, String reason) {
        log.debug("增加积分, userId={}, amount={}, reason={}", userId, amount, reason);

        return Mono.defer(
                        () -> creditAccountRepository.createIfNotExists(userId).flatMap(account -> {
                            long currentBalance = account.getBalance() != null ? account.getBalance() : 0L;
                            long increment = Math.min(amount, Math.max(0L, MAX_BALANCE - currentBalance));
                            if (increment <= 0L) {
                                log.info(
                                        "addCredit: userId={}, amount={}, reason={}, newBalance={}",
                                        userId,
                                        amount,
                                        reason,
                                        currentBalance);
                                return Mono.just(true);
                            }

                            return creditAccountRepository
                                    .incrementBalance(userId, increment, account.getVersion())
                                    .flatMap(updated -> {
                                        if (!updated) {
                                            return Mono.error(new OptimisticLockingFailureException(
                                                    "算力账户并发增加冲突, userId=" + userId));
                                        }

                                        long newBalance = Math.min(currentBalance + increment, MAX_BALANCE);
                                        log.info(
                                                "addCredit: userId={}, amount={}, reason={}, newBalance={}",
                                                userId,
                                                amount,
                                                reason,
                                                newBalance);
                                        recordCreditChange(reason, increment);
                                        return Mono.just(true);
                                    });
                        }))
                .retryWhen(Retry.backoff(BALANCE_OPERATION_MAX_RETRIES, Duration.ofMillis(50))
                        .filter(e -> e instanceof OptimisticLockingFailureException))
                .onErrorResume(e -> {
                    log.error("addCredit failed: userId={}, amount={}, reason={}", userId, amount, reason, e);
                    return Mono.just(false);
                });
    }

    /**
     * 消费积分
     *
     * <p>使用乐观锁机制确保并发安全。余额不足时返回false。</p>
     *
     * @param userId 用户ID
     * @param amount 消费金额
     * @return 是否消费成功
     */
    public Mono<Boolean> consumeCredit(Long userId, Long amount) {
        log.debug("消费积分, userId={}, amount={}", userId, amount);

        return Mono.defer(() -> creditAccountRepository
                        .findByUserId(userId)
                        .flatMap(account -> {
                            long currentBalance = account.getBalance() != null ? account.getBalance() : 0L;
                            if (currentBalance < amount) {
                                log.warn(
                                        "consumeCredit: insufficient balance, userId={}, balance={}, required={}",
                                        userId,
                                        currentBalance,
                                        amount);
                                return Mono.just(false);
                            }

                            return creditAccountRepository
                                    .decrementBalance(userId, amount, account.getVersion())
                                    .flatMap(updated -> {
                                        if (!updated) {
                                            return Mono.error(new OptimisticLockingFailureException(
                                                    "算力账户并发消费冲突, userId=" + userId));
                                        }

                                        long newBalance = currentBalance - amount;
                                        log.info(
                                                "consumeCredit: userId={}, amount={}, newBalance={}",
                                                userId,
                                                amount,
                                                newBalance);
                                        recordCreditChange("consume", amount);
                                        return Mono.just(true);
                                    });
                        })
                        .defaultIfEmpty(false))
                .retryWhen(Retry.backoff(BALANCE_OPERATION_MAX_RETRIES, Duration.ofMillis(50))
                        .filter(e -> e instanceof OptimisticLockingFailureException))
                .onErrorResume(e -> {
                    log.error("consumeCredit failed: userId={}, amount={}", userId, amount, e);
                    return Mono.just(false);
                });
    }

    /**
     * 发放照片上传奖励
     *
     * <p>用户发布照片时发放。根据照片是否为正面照发放不同额度：</p>
     * <ul>
     *   <li>正面照：+15</li>
     *   <li>非正面照：+5</li>
     * </ul>
     *
     * <p>该奖励发放到用户的 CreditAccount 余额（可用于算力消费）。</p>
     *
     * @param userId   用户ID
     * @param isFrontal 是否正面照
     * @return 是否发放成功
     */
    public Mono<Boolean> grantPhotoUploadReward(Long userId, boolean isFrontal) {
        long amount = isFrontal ? 15L : 5L;
        return addCredit(userId, amount, "PHOTO_UPLOAD_REWARD");
    }

    /**
     * 发放每日登录奖励（+3）
     *
     * <p>使用乐观锁机制确保并发安全，同一用户每日只能领取一次。</p>
     *
     * @param userId 用户ID
     * @return 是否发放成功
     */
    public Mono<Boolean> grantDailyLoginBonus(Long userId) {
        log.debug("发放每日登录奖励, userId={}", userId);

        return performLoginBonusUpdate(userId);
    }

    /**
     * 执行登录奖励更新（带乐观锁重试）
     *
     * <p>使用乐观锁机制，当并发冲突时自动重试。</p>
     *
     * @param userId 用户ID
     * @return 是否发放成功
     */
    private Mono<Boolean> performLoginBonusUpdate(Long userId) {
        return creditAccountRepository.createIfNotExists(userId).flatMap(account -> {
            if (Boolean.TRUE.equals(account.getTodayLoginBonus())) {
                log.debug("grantDailyLoginBonus: already claimed today, userId={}", userId);
                return Mono.just(false);
            }

            Long currentVersion = account.getVersion();
            long currentBalance = account.getBalance() != null ? account.getBalance() : 0L;
            final long newBalance = Math.min(currentBalance + DAILY_LOGIN_BONUS, MAX_BALANCE);

            return creditAccountRepository
                    .updateLoginBonusWithVersion(userId, currentVersion, newBalance)
                    .flatMap(updated -> {
                        if (updated) {
                            log.info(
                                    "grantDailyLoginBonus: userId={}, bonus={}, newBalance={}",
                                    userId,
                                    DAILY_LOGIN_BONUS,
                                    newBalance);
                            recordCreditChange("DAILY_LOGIN_BONUS", Math.max(0L, newBalance - currentBalance));
                            return Mono.just(true);
                        }
                        log.debug("grantDailyLoginBonus: version conflict, retrying, userId={}", userId);
                        return Mono.defer(() -> performLoginBonusUpdate(userId));
                    });
        });
    }

    /**
     * 发放投票奖励（v2.1）
     *
     * <p>二选一：+1；四选二有正面照：+3；四选二无正面照：+2。</p>
     *
     * <h3>对局类型说明</h3>
     * <ul>
     *   <li>CHOICE_2 - 二选一对局</li>
     *   <li>CHOICE_2_SELF_ANCHOR - 二选一锚点对局</li>
     *   <li>CHOICE_2_GLOBAL_CALIBRATION - 二选一全局校准</li>
     *   <li>其他 - 四选二对局</li>
     * </ul>
     *
     * <p>本方法纯计算加值（calculateVoteReward），不自行阻塞和线程调度；上游 {@code grantVoteReward} 调用者需 subscribeOn 专用池执行。</p>
     *
     * @param userId          用户ID
     * @param battleType      对局类型
     * @param hasFrontalPhoto 是否包含正面照
     * @return 是否发放成功
     */
    public Mono<Boolean> grantVoteReward(Long userId, String battleType, Boolean hasFrontalPhoto) {
        log.debug("发放投票奖励, userId={}, battleType={}, hasFrontalPhoto={}", userId, battleType, hasFrontalPhoto);

        return Mono.fromCallable(() -> calculateVoteReward(battleType, hasFrontalPhoto))
                .flatMap(reward -> addCredit(userId, reward, "VOTE_REWARD_" + battleType));
    }

    /**
     * 计算投票奖励积分
     *
     * @param battleType      对局类型
     * @param hasFrontalPhoto 是否包含正面照
     * @return 奖励积分
     */
    private long calculateVoteReward(String battleType, Boolean hasFrontalPhoto) {
        if (BATTLE_TYPE_CHOICE_2.equals(battleType)
                || BATTLE_TYPE_CHOICE_2_SELF_ANCHOR.equals(battleType)
                || BATTLE_TYPE_CHOICE_2_GLOBAL_CALIBRATION.equals(battleType)) {
            return VOTE_REWARD_CHOICE2;
        }
        return Boolean.TRUE.equals(hasFrontalPhoto) ? VOTE_REWARD_CHOICE4_FRONTAL : VOTE_REWARD_CHOICE4_NO_FRONTAL;
    }

    /**
     * 发放收藏奖励（+5，日上限200）
     *
     * <p>使用乐观锁机制确保并发安全，防止超发奖励。</p>
     *
     * <h3>业务规则</h3>
     * <ul>
     *   <li>每次收藏奖励5积分</li>
     *   <li>每日上限200积分</li>
     *   <li>跨日期自动重置计数</li>
     * </ul>
     *
     * @param userId 用户ID
     * @return 是否发放成功
     */
    public Mono<Boolean> grantFavoriteReward(Long userId) {
        log.debug("发放收藏奖励, userId={}", userId);

        return performFavoriteRewardUpdate(userId);
    }

    /**
     * 执行收藏奖励更新（带乐观锁重试）
     *
     * <p>使用乐观锁机制，当并发冲突时自动重试。</p>
     *
     * @param userId 用户ID
     * @return 是否发放成功
     */
    private Mono<Boolean> performFavoriteRewardUpdate(Long userId) {
        return creditAccountRepository.findByUserId(userId).flatMap(account -> {
            LocalDate today = LocalDate.now(DEFAULT_TIME_ZONE);

            if (account.getDailyFavoriteDate() == null || !today.equals(account.getDailyFavoriteDate())) {
                return handleDateResetForFavorite(userId, account, today);
            }

            long todayTotal = account.getDailyFavoriteTotal() != null ? account.getDailyFavoriteTotal() : 0L;
            if (todayTotal >= FAVORITE_DAILY_LIMIT) {
                log.debug("grantFavoriteReward: daily limit reached, userId={}", userId);
                return Mono.just(false);
            }

            Long currentVersion = account.getVersion();
            final long newDailyTotal = todayTotal + FAVORITE_REWARD;
            long currentBalance = account.getBalance() != null ? account.getBalance() : 0L;
            final long newBalance = Math.min(currentBalance + FAVORITE_REWARD, MAX_BALANCE);

            return creditAccountRepository
                    .updateFavoriteRewardWithVersion(userId, currentVersion, newDailyTotal, today, newBalance)
                    .flatMap(updated -> {
                        if (updated) {
                            log.info(
                                    "grantFavoriteReward: userId={}, reward={}, dailyTotal={}, newBalance={}",
                                    userId,
                                    FAVORITE_REWARD,
                                    newDailyTotal,
                                    newBalance);
                            recordCreditChange("FAVORITE_REWARD", Math.max(0L, newBalance - currentBalance));
                            return Mono.just(true);
                        }
                        log.debug("grantFavoriteReward: version conflict, retrying, userId={}", userId);
                        return Mono.defer(() -> performFavoriteRewardUpdate(userId));
                    });
        });
    }

    /**
     * 处理收藏奖励的日期重置
     *
     * <p>当日期发生变化时，重置当日收藏统计并发放奖励。</p>
     *
     * @param userId 用户ID
     * @param account 当前账户
     * @param today 今日日期
     * @return 是否发放成功
     */
    private Mono<Boolean> handleDateResetForFavorite(Long userId, CreditAccountEntity account, LocalDate today) {
        Long currentVersion = account.getVersion();
        final long newDailyTotal = FAVORITE_REWARD;
        long currentBalance = account.getBalance() != null ? account.getBalance() : 0L;
        final long newBalance = Math.min(currentBalance + FAVORITE_REWARD, MAX_BALANCE);

        return creditAccountRepository
                .updateFavoriteRewardWithVersion(userId, currentVersion, newDailyTotal, today, newBalance)
                .flatMap(updated -> {
                    if (updated) {
                        log.info(
                                "grantFavoriteReward (date reset): userId={}, reward={}, dailyTotal={}, newBalance={}",
                                userId,
                                FAVORITE_REWARD,
                                newDailyTotal,
                                newBalance);
                        recordCreditChange("FAVORITE_REWARD", Math.max(0L, newBalance - currentBalance));
                        return Mono.just(true);
                    }
                    log.debug("grantFavoriteReward (date reset): version conflict, retrying, userId={}", userId);
                    return Mono.defer(() -> performFavoriteRewardUpdate(userId));
                });
    }

    /**
     * 发放邀请奖励（+100）
     *
     * @param userId 用户ID
     * @return 是否发放成功
     */
    public Mono<Boolean> grantInviteReward(Long userId) {
        log.debug("发放邀请奖励, userId={}", userId);
        return addCredit(userId, INVITE_REWARD, "INVITE_REWARD");
    }

    /**
     * 检查用户积分余额是否已达上限
     *
     * @param userId 用户ID
     * @return true-未达上限（可继续增加），false-已达上限
     */
    public Mono<Boolean> checkBalanceLimit(Long userId) {
        log.debug("检查积分余额上限, userId={}", userId);

        return creditAccountRepository
                .findByUserId(userId)
                .map(account -> account.getBalance() < MAX_BALANCE)
                .defaultIfEmpty(true)
                .doOnSuccess(result -> log.debug("检查积分余额上限结果, userId={}, result={}", userId, result));
    }

    /**
     * 记录实际落库的积分变化。
     *
     * <p>金额使用数据库更新前后的实际差值，而不是配置奖励值，因此余额上限、空余额和并发重试
     * 不会产生虚假的积分变动。指标端口缺失或上报失败时仅记录日志，不能改变积分业务结果。</p>
     *
     * @param type 积分变动原因或类型
     * @param amount 实际增加的积分数量
     */
    private void recordCreditChange(String type, long amount) {
        if (businessMetricsPort == null || amount <= 0L) {
            return;
        }
        try {
            businessMetricsPort.recordCreditChange(type, amount);
        } catch (RuntimeException e) {
            log.warn("记录积分业务指标失败, type={}, amount={}, error={}", type, amount, e.getMessage(), e);
        }
    }
}
