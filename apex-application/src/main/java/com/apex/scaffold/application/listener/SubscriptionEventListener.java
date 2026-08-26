package com.apex.scaffold.application.listener;

import com.apex.scaffold.domain.event.SubscriptionActivatedEvent;
import com.apex.scaffold.domain.event.SubscriptionExpiredEvent;
import com.apex.scaffold.domain.model.entity.SystemMessageEntity;
import com.apex.scaffold.domain.repository.SystemMessageRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 订阅事件监听器
 *
 * <p>处理既有订阅状态变更的通知。首版不提供付费购买、支付、退款或自动权益发放，
 * 因此监听器不会修改算力账户或高级功能权限。
 *
 * <p>P0-13修复: 从Stub实现升级为基础实现</p>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
public class SubscriptionEventListener {

    private final SystemMessageRepository systemMessageRepository;

    /**
     * 构造器注入依赖
     *
     * <p>依赖使用 {@link Optional} 接收，确保依赖未注册时服务仍可正常启动。</p>
     *
     * @param systemMessageRepository 系统消息仓储（可选）
     */
    public SubscriptionEventListener(Optional<SystemMessageRepository> systemMessageRepository) {
        this.systemMessageRepository = systemMessageRepository.orElse(null);
    }

    /**
     * 处理订阅激活事件
     *
     * <p>当外部管理流程把订阅状态变为活跃时，仅发送状态通知。
     *
     * @param event 订阅激活事件
     */
    @EventListener(SubscriptionActivatedEvent.class)
    public void handleSubscriptionActivatedEvent(SubscriptionActivatedEvent event) {
        try {
            log.info(
                    "处理订阅激活事件: subscriptionId={}, userId={}, level={}, startAt={}, endAt={}",
                    event.getSubscriptionId(),
                    event.getUserId(),
                    event.getLevel(),
                    event.getStartAt(),
                    event.getEndAt());

            if (systemMessageRepository != null) {
                SystemMessageEntity notification = new SystemMessageEntity();
                notification.setUserId(event.getUserId());
                notification.setMsgType("SUBSCRIPTION_ACTIVATED");
                notification.setContent("您的 " + event.getLevel() + " 订阅状态已更新，有效期至 " + event.getEndAt() + "。");
                notification.setRefType("SUBSCRIPTION");
                notification.setRefId(event.getSubscriptionId());
                notification.setCreateTime(LocalDateTime.now());
                notification.setUpdateTime(LocalDateTime.now());

                systemMessageRepository
                        .save(notification)
                        .doOnSuccess(v -> log.info(
                                "订阅激活通知发送成功: subscriptionId={}, userId={}",
                                event.getSubscriptionId(),
                                event.getUserId()))
                        .doOnError(e -> log.error(
                                "订阅激活通知发送失败: subscriptionId={}, userId={}",
                                event.getSubscriptionId(),
                                event.getUserId(),
                                e))
                        .block();
            } else {
                log.warn("SystemMessageRepository 未注入，跳过订阅激活通知: subscriptionId={}", event.getSubscriptionId());
            }

            log.info("订阅激活事件处理完成");
        } catch (Exception e) {
            log.error(
                    "handleSubscriptionActivatedEvent 异常: subscriptionId={}, userId={}",
                    event.getSubscriptionId(),
                    event.getUserId(),
                    e);
            throw new IllegalStateException("处理订阅激活事件失败", e);
        }
    }

    /**
     * 处理订阅过期事件
     *
     * <p>当既有订阅到期时，仅发送状态通知；当前版本不存在需要回收的付费权益。
     *
     * @param event 订阅过期事件
     */
    @EventListener(SubscriptionExpiredEvent.class)
    public void handleSubscriptionExpiredEvent(SubscriptionExpiredEvent event) {
        try {
            log.info(
                    "处理订阅过期事件: subscriptionId={}, userId={}, level={}",
                    event.getSubscriptionId(),
                    event.getUserId(),
                    event.getLevel());

            if (systemMessageRepository != null) {
                SystemMessageEntity notification = new SystemMessageEntity();
                notification.setUserId(event.getUserId());
                notification.setMsgType("SUBSCRIPTION_EXPIRED");
                notification.setContent("您的 " + event.getLevel() + " 订阅已到期，当前状态已恢复为普通模式。");
                notification.setRefType("SUBSCRIPTION");
                notification.setRefId(event.getSubscriptionId());
                notification.setCreateTime(LocalDateTime.now());
                notification.setUpdateTime(LocalDateTime.now());

                systemMessageRepository
                        .save(notification)
                        .doOnSuccess(v -> log.info(
                                "订阅过期通知发送成功: subscriptionId={}, userId={}",
                                event.getSubscriptionId(),
                                event.getUserId()))
                        .doOnError(e -> log.error(
                                "订阅过期通知发送失败: subscriptionId={}, userId={}",
                                event.getSubscriptionId(),
                                event.getUserId(),
                                e))
                        .block();
            } else {
                log.warn("SystemMessageRepository 未注入，跳过订阅过期通知: subscriptionId={}", event.getSubscriptionId());
            }

            log.info("订阅过期事件处理完成");
        } catch (Exception e) {
            log.error(
                    "handleSubscriptionExpiredEvent 异常: subscriptionId={}, userId={}",
                    event.getSubscriptionId(),
                    event.getUserId(),
                    e);
            throw new IllegalStateException("处理订阅过期事件失败", e);
        }
    }
}
