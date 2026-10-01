package com.apex.scaffold.infrastructure.outbox.service;

import com.apex.scaffold.infrastructure.outbox.entity.MessageOutboxEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 消息发件箱服务接口
 *
 * <p>提供基于Outbox模式的本地消息表操作,保证消息发送与业务事务的原子性。
 * 支持消息持久化、失败重试、状态管理和分布式锁控制。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>消息追加: 将待发送消息持久化到本地数据库</li>
 *   <li>消息调度: 批量获取待投递消息</li>
 *   <li>分布式锁: 确保多实例环境下消息不被重复投递</li>
 *   <li>状态管理: 标记消息投递成功或失败</li>
 *   <li>锁释放: 自动释放超时的消息锁</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>订单创建成功后发送订单消息</li>
 *   <li>用户注册成功后发送欢迎邮件</li>
 *   <li>支付成功后发送支付通知</li>
 * </ul>
 *
 * <h2>事务保证</h2>
 * <p>业务服务在数据库事务内调用append方法,将消息和业务数据在同一事务中提交:
 * <pre>
 * {@code
 * @Transactional
 * public Mono<Order> createOrder(CreateOrderRequest request) {
 *     return orderRepository.save(order)
 *         .flatMap(saved -> {
 *             MessageOutboxEntity message = buildMessage(saved);
 *             return messageOutboxService.append(message)
 *                 .thenReturn(saved);
 *         });
 * }
 * }
 * </pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see MessageOutboxEntity
 * @see com.apex.scaffold.infrastructure.outbox.dispatcher.MessageOutboxDispatcher
 */
public interface MessageOutboxService {

    /**
     * 追加消息到发件箱
     *
     * @param entity 消息实体
     * @return 保存后的消息实体
     */
    Mono<MessageOutboxEntity> append(MessageOutboxEntity entity);

    /**
     * 获取待发送的消息列表
     *
     * @param batchSize 批次大小
     * @return 待发送的消息流
     */
    Flux<MessageOutboxEntity> fetchPendingMessages(int batchSize);

    /**
     * 锁定消息
     *
     * @param messageId 消息ID
     * @param instanceId 实例ID
     * @return 是否锁定成功
     */
    Mono<Boolean> lockMessage(String messageId, String instanceId);

    /**
     * 标记消息为已投递
     *
     * @param messageId 消息ID
     * @return Mono<Void>
     */
    Mono<Void> markAsDelivered(String messageId);

    /**
     * 标记消息为投递失败
     *
     * @param messageId 消息ID
     * @param error 错误信息
     * @return Mono<Void>
     */
    Mono<Void> markAsFailed(String messageId, String error);

    /**
     * 释放过期的消息锁
     *
     * @return Mono<Void>
     */
    Mono<Void> releaseExpiredLocks();
}
