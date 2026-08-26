package com.apex.scaffold.application.listener;

import com.apex.scaffold.domain.event.CreditConsumedEvent;
import com.apex.scaffold.domain.event.CreditEarnedEvent;
import com.apex.scaffold.domain.model.entity.SystemMessageEntity;
import com.apex.scaffold.domain.repository.SystemMessageRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 积分事件监听器
 *
 * <p>处理积分相关的领域事件，包括积分消费、积分赚取等后续操作
 *
 * <p>P0-13修复: 从Stub实现升级为基础实现</p>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
public class CreditEventListener {

    /**
     * 低余额告警阈值。
     *
     * <p>当用户积分余额低于此值时触发低余额系统消息。</p>
     */
    private static final long LOW_BALANCE_THRESHOLD = 10L;

    /**
     * 里程碑积分节点。
     *
     * <p>当用户累计积分达到这些节点时发送里程碑成就消息。</p>
     */
    private static final long[] MILESTONE_POINTS = {100L, 500L, 1000L, 5000L};

    private final SystemMessageRepository systemMessageRepository;

    /**
     * 构造器注入依赖
     *
     * <p>依赖使用 {@link Optional} 接收，确保依赖未注册时服务仍可正常启动。</p>
     *
     * @param systemMessageRepository 系统消息仓储（可选）
     */
    public CreditEventListener(Optional<SystemMessageRepository> systemMessageRepository) {
        this.systemMessageRepository = systemMessageRepository.orElse(null);
    }

    /**
     * 处理积分消费事件
     *
     * <p>当用户消费积分时触发，记录消费日志并检查低余额阈值。
     *
     * @param event 积分消费事件
     */
    @EventListener(CreditConsumedEvent.class)
    public void handleCreditConsumedEvent(CreditConsumedEvent event) {
        try {
            log.info(
                    "处理积分消费事件: userId={}, amount={}, remainingBalance={}, scene={}",
                    event.getUserId(),
                    event.getAmount(),
                    event.getRemainingBalance(),
                    event.getScene());

            // 检查低余额阈值，发送提醒
            if (event.getRemainingBalance() != null
                    && event.getRemainingBalance() <= LOW_BALANCE_THRESHOLD
                    && systemMessageRepository != null) {
                SystemMessageEntity warning = new SystemMessageEntity();
                warning.setUserId(event.getUserId());
                warning.setMsgType("LOW_CREDIT");
                warning.setContent(
                        "您的积分余额已不足 " + LOW_BALANCE_THRESHOLD + "，当前余额: " + event.getRemainingBalance() + "，快去赚取更多积分吧！");
                warning.setRefType("CREDIT");
                warning.setCreateTime(LocalDateTime.now());
                warning.setUpdateTime(LocalDateTime.now());

                systemMessageRepository
                        .save(warning)
                        .doOnSuccess(v -> log.info(
                                "低余额提醒发送成功: userId={}, balance={}", event.getUserId(), event.getRemainingBalance()))
                        .doOnError(e -> log.error("低余额提醒发送失败: userId={}", event.getUserId(), e))
                        .block();
            } else if (systemMessageRepository == null) {
                log.debug("SystemMessageRepository 未注入，跳过低余额提醒");
            }

            log.info("积分消费事件处理完成");
        } catch (Exception e) {
            log.error("handleCreditConsumedEvent 异常: userId={}", event.getUserId(), e);
            throw new IllegalStateException("处理积分消费事件失败", e);
        }
    }

    /**
     * 处理积分赚取事件
     *
     * <p>当用户赚取积分时触发，记录赚取日志并检查里程碑奖励。
     *
     * @param event 积分赚取事件
     */
    @EventListener(CreditEarnedEvent.class)
    public void handleCreditEarnedEvent(CreditEarnedEvent event) {
        try {
            log.info(
                    "处理积分赚取事件: userId={}, amount={}, totalBalance={}, source={}",
                    event.getUserId(),
                    event.getAmount(),
                    event.getTotalBalance(),
                    event.getSource());

            // 检查是否达到里程碑节点
            if (event.getTotalBalance() != null && systemMessageRepository != null) {
                Long balance = event.getTotalBalance();
                for (long milestone : MILESTONE_POINTS) {
                    Long prev = balance - event.getAmount();
                    if (prev != null && prev < milestone && balance >= milestone) {
                        SystemMessageEntity milestoneMsg = new SystemMessageEntity();
                        milestoneMsg.setUserId(event.getUserId());
                        milestoneMsg.setMsgType("CREDIT_MILESTONE");
                        milestoneMsg.setContent("恭喜！您的积分已达到 " + milestone + "！继续加油！");
                        milestoneMsg.setRefType("CREDIT");
                        milestoneMsg.setCreateTime(LocalDateTime.now());
                        milestoneMsg.setUpdateTime(LocalDateTime.now());

                        systemMessageRepository
                                .save(milestoneMsg)
                                .doOnSuccess(v ->
                                        log.info("里程碑提醒发送成功: userId={}, milestone={}", event.getUserId(), milestone))
                                .doOnError(e -> log.error("里程碑提醒发送失败: userId={}", event.getUserId(), e))
                                .block();
                        break;
                    }
                }
            } else if (systemMessageRepository == null) {
                log.debug("SystemMessageRepository 未注入，跳过里程碑检测");
            }

            log.info("积分赚取事件处理完成");
        } catch (Exception e) {
            log.error("handleCreditEarnedEvent 异常: userId={}", event.getUserId(), e);
            throw new IllegalStateException("处理积分赚取事件失败", e);
        }
    }
}
