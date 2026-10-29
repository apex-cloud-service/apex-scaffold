package com.apex.scaffold.workflowservice.infrastructure.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 轻量级工作流事件发布器（本地降级实现）.
 *
 * <p>当 {@code apex.events.outbox.enabled=false}（如 basic/H2 环境）或运行期不存在
 * {@link org.apache.rocketmq.spring.core.RocketMQTemplate} Bean 时启用。本实现不投递任何
 * 消息，仅在 DEBUG 级别记录事件摘要后立即返回完成信号，保证服务在无消息中间件的环境下
 * 仍可正常启动与完成审批流程。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>不投递</strong>：避免在无 RocketMQ 的环境中因发送失败阻断主流程</li>
 *   <li><strong>发送即忘</strong>：遵循 {@link WorkflowEventPublisher} 的异步语义，返回 {@code Mono.empty()}</li>
 *   <li><strong>可观测</strong>：DEBUG 日志保留事件类型与 ID，便于本地排查</li>
 * </ul>
 *
 * <p>生产环境（MySQL/PostgreSQL + RocketMQ）应保持 {@code apex.events.outbox.enabled=true}，
 * 由 {@link MqWorkflowEventPublisher} 提供真实投递。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 * @see WorkflowEventPublisher
 * @see MqWorkflowEventPublisher
 */
@Slf4j
public class LocalWorkflowEventPublisher implements WorkflowEventPublisher {

    /**
     * 本地发布工作流事件。
     *
     * <p>仅记录事件摘要，不进行投递，直接返回完成信号。</p>
     *
     * @param event 工作流领域事件对象，不能为 {@code null}
     */
    @Override
    public Mono<Void> publish(AbstractDomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("待发布的工作流事件不能为空");
        }
        log.debug(
                "本地发布工作流事件（未启用消息投递）: type={}, eventId={}, tenantId={}",
                event.getClass().getSimpleName(),
                event.getEventId(),
                event.getTenantId());
        return Mono.empty();
    }
}
