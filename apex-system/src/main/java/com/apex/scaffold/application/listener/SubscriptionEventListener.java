package com.apex.scaffold.application.listener;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.event.SubscriptionActivatedEvent;
import com.apex.scaffold.domain.event.SubscriptionExpiredEvent;
import com.apex.scaffold.domain.gateway.RealtimeNotificationPort;
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
 * 安装/释放事件 DTO 内携带的 tenantId（而非依赖发布线程 ThreadLocal）。系统消息
 * 落库成功后经 {@link RealtimeNotificationPort}（MQ 广播适配）触发各实例实时
 * SSE 扇出；推送失败不影响已完成的落库结果。</p>
 *
 * @author Apex Team
 * @version 3.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
public class SubscriptionEventListener {

    private final SystemMessageRepository systemMessageRepository;

    /** 站内通知实时推送端口（可选）：实现未装配时缺失，仅影响实时推送。 */
    private final RealtimeNotificationPort realtimeNotificationPort;

    /**
     * 创建监听器，系统消息仓储与实时推送端口可选注入，未注入时分别跳过通知写入与实时推送。
     *
     * @param systemMessageRepository  系统消息仓储，可选
     * @param realtimeNotificationPort 站内通知实时推送端口，可选
     */
    public SubscriptionEventListener(
            Optional<SystemMessageRepository> systemMessageRepository,
            Optional<RealtimeNotificationPort> realtimeNotificationPort) {
        this.systemMessageRepository = systemMessageRepository.orElse(null);
        this.realtimeNotificationPort = realtimeNotificationPort.orElse(null);
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
                                event.getTenantId(),
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
                                event.getTenantId(),
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
            String tenantId,
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
                .flatMap(saved -> publishRealtimeNotification(tenantId, userId, content))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    /**
     * 消息落库成功后经 MQ 广播触发各实例 SSE 扇出。
     *
     * <p>发布为阻塞调用，提交到专用阻塞池执行；失败边界由端口实现收敛
     * （推送失败仅记录日志，不向上游传播），不影响已完成的落库结果，
     * 也不中断监听器管道。租户取自事件 DTO，缺失时跳过推送
     * （实时通知不支持无租户广播）。</p>
     *
     * @param tenantId 事件携带的租户 ID
     * @param userId   接收用户 ID
     * @param content  消息内容
     * @return 发布完成的空 Mono
     */
    private Mono<Void> publishRealtimeNotification(String tenantId, Long userId, String content) {
        if (realtimeNotificationPort == null
                || tenantId == null
                || tenantId.isBlank()
                || userId == null
                || content == null
                || content.isBlank()) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> realtimeNotificationPort.publish(tenantId, String.valueOf(userId), content))
                .subscribeOn(BlockingIoSchedulers.event())
                .then();
    }
}
