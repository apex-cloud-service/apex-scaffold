package com.apex.scaffold.workflowservice.infrastructure.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 工作流事件本地进程内发布器.
 *
 * <p>当 {@code apex.events.channel=local} 时启用：把事件交给同进程的
 * {@link org.springframework.context.ApplicationEventPublisher}，由本地
 * {@code @EventListener} 订阅者实际消费；没有订阅者时仅记录事件摘要，
 * 不阻断审批主流程。</p>
 *
 * <p>本地模式不跨服务投递。需要跨服务必达的拓扑应使用 remote 通道，
 * 由 Outbox 重放器在 broker 确认后标记完成。</p>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.3.1
 * @see WorkflowEventPublisher
 * @see MqWorkflowEventPublisher
 */
@Slf4j
public class LocalWorkflowEventPublisher implements WorkflowEventPublisher {

    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * 注入本地事件总线。
     *
     * @param eventPublisher Spring 进程内事件发布器
     */
    public LocalWorkflowEventPublisher(org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 在订阅时把事件派发给本地监听器。
     *
     * @param event 工作流领域事件对象，不能为 {@code null}
     */
    @Override
    public Mono<Void> publish(AbstractDomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("待发布的工作流事件不能为空");
        }
        return Mono.fromRunnable(() -> {
            eventPublisher.publishEvent(event);
            log.debug(
                    "本地派发工作流事件: type={}, eventId={}, tenantId={}",
                    event.getClass().getSimpleName(),
                    event.getEventId(),
                    event.getTenantId());
        });
    }
}
