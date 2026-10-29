package com.apex.scaffold.infrastructure.outbox.service;

import com.apex.scaffold.infrastructure.outbox.entity.MessageOutboxEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 消息发件箱服务接口
 *
 * <p>提供基于 Outbox 模式的本地消息表操作；消息发送与数据库提交属于不同边界，
 * 调用方仍需处理重复投递和发送失败。
 * 支持消息持久化、失败重试、状态管理和分布式锁控制。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>消息追加: 将待发送消息持久化到本地数据库</li>
 *   <li>消息调度: 批量获取待投递消息</li>
 *   <li>分布式锁: 通过条件更新竞争处理权，消费方仍须处理重复投递</li>
 *   <li>状态管理: 标记消息投递成功或失败</li>
 *   <li>锁释放: 自动释放超时的消息锁</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>订单创建成功后发送订单消息</li>
 *   <li>用户注册成功后发送欢迎邮件</li>
 *   <li>支付成功后发送支付通知</li>
 * </ul>
 *
 * <h2>线程安全性</h2>
 * <p>默认实现将数据库操作延迟到订阅时，并切换到专用 JDBC 调度器；
 * 调用线程的同步 JDBC 事务不会因此自动传播。不要在返回 Mono 的方法上添加
 * JDBC {@code @Transactional} 来假定业务写入与 {@code append} 原子提交。
 * 需要同事务写入时，须在同一同步事务回调内完成业务操作及消息插入，不能使用此响应式
 * 追加方法跨线程写入。领域事件可参考
 * {@link com.apex.scaffold.infrastructure.outbox.TransactionalOutboxExecutor}；
 * 该执行器写入领域事件发件箱，不写入本接口的消息表。租户标识须由可信上下文提供。
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
     */
    Mono<Void> markAsDelivered(String messageId);

    /**
     * 标记消息为投递失败
     *
     * @param messageId 消息ID
     * @param error 去除敏感信息后的错误摘要
     */
    Mono<Void> markAsFailed(String messageId, String error);

    /**
     * 释放过期的消息锁，默认实现仅清除锁字段，不恢复待投递状态。
     */
    Mono<Void> releaseExpiredLocks();
}
