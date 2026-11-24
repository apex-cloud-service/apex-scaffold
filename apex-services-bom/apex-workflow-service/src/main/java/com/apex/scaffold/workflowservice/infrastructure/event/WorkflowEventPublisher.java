package com.apex.scaffold.workflowservice.infrastructure.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import reactor.core.publisher.Mono;

/**
 * 工作流事件发布器（出端口）.
 *
 * <p>工作流服务对外发布审批领域事件的统一出口。事件 DTO 定义于共享契约模块
 * {@code apex-api-contract}（{@code WorkflowInstanceStartedEvent} / {@code TaskAssignedEvent} /
 * {@code TaskCompletedEvent}），以 RocketMQ 直发方式投递到主题 {@code apex-workflow-events}，
 * 供 system / office 等订阅方消费（迁移方案 §6.4 / 执行计划 Phase 3.4）。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@link MqWorkflowEventPublisher}：生产环境实现，经 {@code RocketMQTemplate}
 *       异步发送（{@code @ConditionalOnProperty} 依据 {@code apex.events.outbox.enabled}）。</li>
 *   <li>{@link LocalWorkflowEventPublisher}：basic/H2 环境的本地兜底实现，仅记录日志，
 *       不依赖消息中间件。</li>
 * </ul>
 *
 * <p>发布语义：响应式非阻塞，调用方通常以 {@code doOnSuccess} 在业务动作完成后触发，
 * 发布失败仅记录日志、不阻断主流程（事件为最终一致的旁路信号）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 * @see com.apex.scaffold.contract.event.WorkflowInstanceStartedEvent
 * @see com.apex.scaffold.contract.event.TaskAssignedEvent
 * @see com.apex.scaffold.contract.event.TaskCompletedEvent
 */
public interface WorkflowEventPublisher {

    /**
     * 发布工作流领域事件。
     *
     * @param event 待发布的领域事件（不应为 {@code null}）
     * @return 操作完成信号，失败时传播错误
     */
    Mono<Void> publish(AbstractDomainEvent event);
}
