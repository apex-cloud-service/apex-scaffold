package com.apex.scaffold.application.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.event.SubscriptionActivatedEvent;
import com.apex.scaffold.domain.event.SubscriptionExpiredEvent;
import com.apex.scaffold.domain.model.entity.BaseEntity.LogicalDeletion;
import com.apex.scaffold.domain.model.entity.CreditAccountEntity;
import com.apex.scaffold.domain.model.entity.CreditTxEntity;
import com.apex.scaffold.domain.model.entity.SubscriptionEntity;
import com.apex.scaffold.domain.repository.CreditAccountRepository;
import com.apex.scaffold.domain.repository.CreditTxRepository;
import com.apex.scaffold.domain.repository.SubscriptionRepository;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 会员订阅服务应用类
 *
 * <p>提供会员订阅状态查询、算力账户管理、算力流水记录等核心功能
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>订阅状态查询：获取用户当前会员等级、到期时间等</li>
 *   <li>算力余额查询：获取用户当前算力余额</li>
 *   <li>算力流水查询：分页查询用户算力变动记录</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>NORMAL：普通会员（默认）</li>
 *   <li>PREMIUM：高级会员</li>
 *   <li>VIP：VIP会员</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see UserService
 * @see SubscriptionRepository
 * @see CreditAccountRepository
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    /**
     * 用户应用服务，用于根据 openId 查询当前用户信息。
     */
    private final UserService userService;

    /**
     * 订阅信息仓储，查询用户当前有效的订阅记录（会员等级、到期时间等）。
     *
     * <p>若用户尚无订阅记录，返回默认的 NORMAL/EXPIRED 兜底值。</p>
     */
    private final SubscriptionRepository subscriptionRepository;

    /**
     * 算力账户仓储，查询和管理用户算力余额。
     *
     * <p>算力用于照片曝光权重系统，用户可通过登录奖励、投票奖励、
     * 邀请奖励等途径获取。</p>
     */
    private final CreditAccountRepository creditAccountRepository;

    /**
     * 算力流水仓储，记录用户算力的每一次变动（增加/消费），
     * 支持分页查询用于前端账单展示。
     */
    private final CreditTxRepository creditTxRepository;

    /**
     * 领域事件发布器。
     */
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 获取当前用户的会员状态与算力余额。
     *
     * <p>聚合查询当前用户的有效订阅信息与算力账户余额：
     * <ul>
     *   <li>订阅信息：优先返回当前有效订阅；若不存在，则返回默认订阅（NORMAL/EXPIRED）</li>
     *   <li>算力账户：优先返回账户记录；若不存在，则返回默认账户（balance=0）</li>
     * </ul>
     *
     * @param openId 当前用户的 openId
     * @return 包含 level/expiresAt/computeCredits/speedMultiplier 的 Map
     * @throws BusinessException 当用户不存在时抛出
     *
     */
    public Mono<Map<String, Object>> getMe(String openId) {
        return userService
                .getByOpenId(openId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在")))
                .flatMap(user -> Mono.zip(
                                subscriptionRepository
                                        .findActiveByUserId(user.getId())
                                        .defaultIfEmpty(defaultSubscription(user.getId())),
                                creditAccountRepository
                                        .findByUserId(user.getId())
                                        .defaultIfEmpty(defaultAccount(user.getId())))
                        .map(tuple -> {
                            SubscriptionEntity sub = tuple.getT1();
                            CreditAccountEntity acc = tuple.getT2();
                            return Map.<String, Object>of(
                                    "level",
                                    sub.getLevel(),
                                    "expiresAt",
                                    sub.getEndAt() == null
                                            ? null
                                            : sub.getEndAt().toString(),
                                    "computeCredits",
                                    acc.getBalance(),
                                    "speedMultiplier",
                                    1.0);
                        }));
    }

    /**
     * 分页查询当前用户的算力流水。
     *
     * <p>按用户查询算力变动记录列表，并基于 pageNum/pageSize 计算 offset。
     * 当前实现返回的 total 为本次查询返回条数，pages 固定为 1（用于前端兼容的临时口径）。
     *
     * @param openId 当前用户的 openId
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页数量
     * @return 包含 list/total/pageNum/pageSize/pages 的 Map
     * @throws BusinessException 当用户不存在时抛出
     *
     */
    public Mono<Map<String, Object>> listCreditTx(String openId, int pageNum, int pageSize) {
        return userService
                .getByOpenId(openId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在")))
                .flatMap(user -> {
                    int offset = Math.max(pageNum - 1, 0) * pageSize;
                    Mono<List<CreditTxEntity>> listMono =
                            creditTxRepository.listByUserId(user.getId(), offset, pageSize);
                    Mono<Long> countMono = creditTxRepository.countByUserId(user.getId());
                    return Mono.zip(listMono, countMono).map(tuple -> {
                        List<CreditTxEntity> list = tuple.getT1();
                        long total = tuple.getT2();
                        int pages = (int) ((total + pageSize - 1) / pageSize);
                        return Map.<String, Object>of(
                                "list", list,
                                "total", total,
                                "pageNum", pageNum,
                                "pageSize", pageSize,
                                "pages", pages);
                    });
                });
    }

    /**
     * 构建默认订阅实体。
     *
     * <p>用于当用户尚无订阅记录时的兜底展示。
     *
     * @param userId 用户 ID
     * @return 默认订阅实体（NORMAL/EXPIRED）
     */
    private SubscriptionEntity defaultSubscription(Long userId) {
        SubscriptionEntity s = new SubscriptionEntity();
        s.setUserId(userId);
        s.setLevel("NORMAL");
        s.setStartAt(LocalDateTime.now());
        s.setEndAt(LocalDateTime.now());
        s.setStatus("EXPIRED");
        s.setCreateTime(LocalDateTime.now());
        s.setUpdateTime(LocalDateTime.now());
        s.setDeleted(LogicalDeletion.NOT_DELETED.getCode());
        return s;
    }

    /**
     * 构建默认算力账户实体。
     *
     * <p>用于当用户尚无账户记录时的兜底展示。
     *
     * @param userId 用户 ID
     * @return 默认账户实体（balance=0）
     */
    private CreditAccountEntity defaultAccount(Long userId) {
        CreditAccountEntity a = new CreditAccountEntity();
        a.setUserId(userId);
        a.setBalance(0L);
        a.setCreateTime(LocalDateTime.now());
        a.setUpdateTime(LocalDateTime.now());
        a.setDeleted(LogicalDeletion.NOT_DELETED.getCode());
        return a;
    }

    /**
     * 发布订阅激活领域事件，通知下游服务会员等级变更。
     *
     * @param sub 已激活的订阅实体
     * @throws IllegalStateException 当领域事件持久化失败时
     */
    private void publishSubscriptionActivatedEvent(SubscriptionEntity sub) {
        try {
            domainEventPublisher
                    .publish(new SubscriptionActivatedEvent(
                            sub.getId(), sub.getUserId(), sub.getLevel(), sub.getStartAt(), sub.getEndAt()))
                    .block();
        } catch (Exception e) {
            log.error("发布SubscriptionActivatedEvent异常: {}", e.getMessage(), e);
            throw new IllegalStateException("订阅激活事件持久化失败", e);
        }
    }

    /**
     * 发布订阅过期领域事件，通知下游服务会员等级变更。
     *
     * @param sub 已过期的订阅实体
     * @throws IllegalStateException 当领域事件持久化失败时
     */
    private void publishSubscriptionExpiredEvent(SubscriptionEntity sub) {
        try {
            domainEventPublisher
                    .publish(new SubscriptionExpiredEvent(sub.getId(), sub.getUserId(), sub.getLevel()))
                    .block();
        } catch (Exception e) {
            log.error("发布SubscriptionExpiredEvent异常: {}", e.getMessage(), e);
            throw new IllegalStateException("订阅过期事件持久化失败", e);
        }
    }
}
