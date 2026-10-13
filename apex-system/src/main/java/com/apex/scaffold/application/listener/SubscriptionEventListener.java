package com.apex.scaffold.application.listener;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.event.SubscriptionActivatedEvent;
import com.apex.scaffold.domain.event.SubscriptionExpiredEvent;
import com.apex.scaffold.domain.model.entity.SystemMessageEntity;
import com.apex.scaffold.domain.repository.SystemMessageRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 订阅事件监听器
 *
 * <p>处理既有订阅状态变更的通知（激活/过期系统消息）。与 C2 并发修复一致：
 * 所有 Repository 操作合并为 Mono 链，不 .block()；在阻塞池 jdbc() 上运行时显式
 * 安装/释放事件 DTO 内携带的 tenantId（而非依赖发布线程 ThreadLocal）。</p>
 *
 * @author Apex Team
 * @version 3.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
public class SubscriptionEventListener {

    private final SystemMessageRepository systemMessageRepository;

    /** 创建监听器，系统消息仓储可选注入，未注入时跳过通知写入。 */
    public SubscriptionEventListener(Optional<SystemMessageRepository> systemMessageRepository) {
        this.systemMessageRepository = systemMessageRepository.orElse(null);
    }

    /**
     * 监听订阅激活事件，异步写入订阅激活系统消息通知。
     *
     * @param event 订阅激活领域事件
     */
    @EventListener(SubscriptionActivatedEvent.class)
    public void handleSubscriptionActivatedEvent(SubscriptionActivatedEvent event) {
        log.info(
                "处理订阅激活事件: subscriptionId={}, userId={}, level={}",
                event.getSubscriptionId(),
                event.getUserId(),
                event.getLevel());
        runWithTenantFromEvent(
                        event,
                        () -> sendNotification(
                                "SUBSCRIPTION_ACTIVATED",
                                event.getUserId(),
                                "您的 " + event.getLevel() + " 订阅状态已更新，有效期至 " + event.getEndAt() + "。",
                                "SUBSCRIPTION",
                                event.getSubscriptionId(),
                                "订阅激活通知发送成功: subscriptionId=" + event.getSubscriptionId(),
                                "订阅激活通知发送失败: subscriptionId=" + event.getSubscriptionId()))
                .doOnSuccess(v -> log.info("订阅激活事件处理完成: subscriptionId={}", event.getSubscriptionId()))
                .doOnError(e -> log.error(
                        "handleSubscriptionActivatedEvent 异常: subscriptionId={}", event.getSubscriptionId(), e))
                .subscribe();
    }

    /**
     * 监听订阅过期事件，异步写入订阅过期系统消息通知。
     *
     * @param event 订阅过期领域事件
     */
    @EventListener(SubscriptionExpiredEvent.class)
    public void handleSubscriptionExpiredEvent(SubscriptionExpiredEvent event) {
        log.info(
                "处理订阅过期事件: subscriptionId={}, userId={}, level={}",
                event.getSubscriptionId(),
                event.getUserId(),
                event.getLevel());
        runWithTenantFromEvent(
                        event,
                        () -> sendNotification(
                                "SUBSCRIPTION_EXPIRED",
                                event.getUserId(),
                                "您的 " + event.getLevel() + " 订阅已到期，当前状态已恢复为普通模式。",
                                "SUBSCRIPTION",
                                event.getSubscriptionId(),
                                "订阅过期通知发送成功: subscriptionId=" + event.getSubscriptionId(),
                                "订阅过期通知发送失败: subscriptionId=" + event.getSubscriptionId()))
                .doOnSuccess(v -> log.info("订阅过期事件处理完成: subscriptionId={}", event.getSubscriptionId()))
                .doOnError(e ->
                        log.error("handleSubscriptionExpiredEvent 异常: subscriptionId={}", event.getSubscriptionId(), e))
                .subscribe();
    }

    private Mono<Void> runWithTenantFromEvent(Object event, java.util.function.Supplier<Mono<Void>> task) {
        String tenantId = (event instanceof com.apex.scaffold.common.event.BaseDomainEvent baseEvent)
                ? baseEvent.getTenantId()
                : null;
        // usingWhen 的 resource 不能为 null，用 String[1] 作 previous 非空 holder。
        String[] holder = new String[1];
        return Mono.usingWhen(
                        Mono.fromCallable(() -> {
                            holder[0] = TenantContext.setTenantId(tenantId);
                            return holder;
                        }),
                        ignored -> task.get(),
                        ignored -> Mono.fromRunnable(() -> TenantContext.restoreTenantId(holder[0])),
                        (ignored, err) -> Mono.fromRunnable(() -> TenantContext.restoreTenantId(holder[0])),
                        ignored -> Mono.fromRunnable(() -> TenantContext.restoreTenantId(holder[0])))
                .subscribeOn(BlockingIoSchedulers.event());
    }

    private Mono<Void> sendNotification(
            String msgType,
            Long userId,
            String content,
            String refType,
            Object refId,
            String successLog,
            String errorLog) {
        if (systemMessageRepository == null) {
            log.warn("SystemMessageRepository 未注入，跳过: {}, userId={}", msgType, userId);
            return Mono.empty();
        }
        SystemMessageEntity notification = new SystemMessageEntity();
        notification.setUserId(userId);
        notification.setMsgType(msgType);
        notification.setContent(content);
        notification.setRefType(refType);
        if (refId != null) {
            if (refId instanceof Long longRefId) {
                notification.setRefId(longRefId);
            } else {
                try {
                    notification.setRefId(Long.valueOf(refId.toString()));
                } catch (NumberFormatException e) {
                    log.warn("无法将 refId 转为 Long，已跳过写入: {}", refId);
                }
            }
        }
        notification.setCreateTime(LocalDateTime.now());
        notification.setUpdateTime(LocalDateTime.now());
        return systemMessageRepository
                .save(notification)
                .doOnSuccess(v -> log.info("{}", successLog))
                .doOnError(e -> log.error("{}, userId={}", errorLog, userId, e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}
