package com.apex.scaffold.infrastructure.outbox.service.impl;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.infrastructure.outbox.entity.MessageOutboxEntity;
import com.apex.scaffold.infrastructure.outbox.mapper.MessageOutboxMapper;
import com.apex.scaffold.infrastructure.outbox.service.MessageOutboxService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 消息发件箱服务实现类
 *
 * <p>实现Outbox模式的核心服务,负责消息的持久化、查询、状态管理和锁控制。
 * 所有阻塞IO操作都在专用的JDBC调度器上执行,避免阻塞Reactor主线程。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>阻塞IO隔离: 所有数据库操作都在BlockingIoSchedulers.jdbc()上执行</li>
 *   <li>自动字段填充: 创建时自动填充默认值</li>
 *   <li>指数退避重试: 重试延迟按2的指数增长</li>
 *   <li>分布式锁支持: 基于CAS的消息锁定机制</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>重试延迟计算公式: delay = retry_delay_seconds * 2^(attempts - 1)
 * <ul>
 *   <li>第1次重试: 10秒后</li>
 *   <li>第2次重试: 20秒后</li>
 *   <li>第3次重试: 40秒后</li>
 *   <li>第4次重试: 80秒后</li>
 *   <li>默认第5次失败达到尝试上限，进入死信状态，不再安排重试</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see MessageOutboxService
 * @see MessageOutboxMapper
 * @see BlockingIoSchedulers
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageOutboxServiceImpl implements MessageOutboxService {

    private final MessageOutboxMapper messageOutboxMapper;

    /**
     * 订阅时补齐缺省时间、待投递状态及重试和锁超时参数，然后插入消息。
     *
     * <p>在 JDBC 调度器上修改并保存传入实体，不创建业务事务，也不自动继承调用线程的
     * JDBC 事务。需要与业务写入原子提交时，须在同一同步事务内直接完成消息插入。
     * 租户标识由调用方在可信作用域内设置，本方法不补全租户。
     *
     * @param entity 待保存消息，必须包含业务必需字段
     * @return 保存后的同一实体；数据库失败通过错误信号传播
     */
    @Override
    public Mono<MessageOutboxEntity> append(MessageOutboxEntity entity) {
        return Mono.fromCallable(() -> {
                    if (entity.getCreatedAt() == null) {
                        entity.setCreatedAt(LocalDateTime.now());
                    }
                    if (entity.getUpdatedAt() == null) {
                        entity.setUpdatedAt(LocalDateTime.now());
                    }
                    if (entity.getStatus() == null) {
                        entity.setStatus(MessageOutboxEntity.Status.PENDING);
                    }
                    if (entity.getAttempts() == null) {
                        entity.setAttempts(0);
                    }
                    if (entity.getMaxAttempts() == null) {
                        entity.setMaxAttempts(5);
                    }
                    if (entity.getRetryDelaySeconds() == null) {
                        entity.setRetryDelaySeconds(10);
                    }
                    if (entity.getNextAttemptAt() == null) {
                        entity.setNextAttemptAt(LocalDateTime.now());
                    }
                    if (entity.getLockTimeoutSeconds() == null) {
                        entity.setLockTimeoutSeconds(300);
                    }

                    messageOutboxMapper.insert(entity);
                    return entity;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 在 JDBC 调度器查询已到重试时刻的待投递消息，按创建时间升序返回。
     *
     * <p>查询不加锁；多个实例可能获取相同候选消息，投递前仍须调用 {@link #lockMessage}。
     *
     * @param batchSize 查询上限，调用方须提供合法的正数
     * @return 候选消息流，无候选时为空；查询失败通过错误信号传播
     */
    @Override
    public Flux<MessageOutboxEntity> fetchPendingMessages(int batchSize) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<MessageOutboxEntity> query = new LambdaQueryWrapper<>();
                    query.eq(MessageOutboxEntity::getStatus, MessageOutboxEntity.Status.PENDING)
                            .le(MessageOutboxEntity::getNextAttemptAt, LocalDateTime.now())
                            .orderByAsc(MessageOutboxEntity::getCreatedAt)
                            .last("LIMIT " + batchSize);

                    return messageOutboxMapper.selectList(query);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * 在 JDBC 调度器通过 Mapper 的条件更新竞争消息处理权。
     *
     * @param messageId 待锁定的消息标识
     * @param instanceId 本次投递实例标识，保存为锁持有者
     * @return 条件更新影响至少一行时为 {@code true}，否则为 {@code false}；数据库异常作为错误信号传播
     */
    @Override
    public Mono<Boolean> lockMessage(String messageId, String instanceId) {
        return Mono.fromCallable(() -> {
                    int rows = messageOutboxMapper.lockMessage(messageId, instanceId);
                    return rows > 0;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 在 JDBC 调度器按消息标识设置已投递状态和投递时间，不清空现有锁字段。
     *
     * @param messageId 已完成投递的消息标识
     */
    @Override
    public Mono<Void> markAsDelivered(String messageId) {
        return Mono.fromRunnable(() -> {
                    LambdaUpdateWrapper<MessageOutboxEntity> update = new LambdaUpdateWrapper<>();
                    update.eq(MessageOutboxEntity::getMessageId, messageId)
                            .set(MessageOutboxEntity::getStatus, MessageOutboxEntity.Status.DELIVERED)
                            .set(MessageOutboxEntity::getDeliveredAt, LocalDateTime.now())
                            .set(MessageOutboxEntity::getUpdatedAt, LocalDateTime.now());

                    messageOutboxMapper.update(null, update);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }

    /**
     * 在 JDBC 调度器记录一次失败，并按累计失败次数决定重试或进入死信状态。
     *
     * <p>未达到尝试上限时按基础延迟乘以 {@code 2^(累计失败次数 - 1)} 设置下次时间，并清除锁字段；
     * 达到上限时设置 DEAD，保留原有锁字段。读取与更新未由本方法的事务或版本条件保护，
     * 调用方须避免对同一消息并发报告失败。
     *
     * @param messageId 投递失败的消息标识
     * @param error 待持久化的错误摘要，调用方须先去除凭据等敏感信息
     */
    @Override
    public Mono<Void> markAsFailed(String messageId, String error) {
        return Mono.fromRunnable(() -> {
                    MessageOutboxEntity entity =
                            messageOutboxMapper.selectOne(new LambdaQueryWrapper<MessageOutboxEntity>()
                                    .eq(MessageOutboxEntity::getMessageId, messageId));

                    if (entity == null) {
                        return;
                    }

                    int nextAttempts = entity.getAttempts() + 1;
                    LocalDateTime nextAttemptAt = LocalDateTime.now()
                            .plusSeconds((long) entity.getRetryDelaySeconds() * (1L << (nextAttempts - 1)));

                    LambdaUpdateWrapper<MessageOutboxEntity> update = new LambdaUpdateWrapper<>();
                    update.eq(MessageOutboxEntity::getMessageId, messageId)
                            .set(MessageOutboxEntity::getAttempts, nextAttempts)
                            .set(MessageOutboxEntity::getLastError, error)
                            .set(MessageOutboxEntity::getUpdatedAt, LocalDateTime.now());

                    if (nextAttempts >= entity.getMaxAttempts()) {
                        update.set(MessageOutboxEntity::getStatus, MessageOutboxEntity.Status.DEAD);
                    } else {
                        update.set(MessageOutboxEntity::getStatus, MessageOutboxEntity.Status.PENDING)
                                .set(MessageOutboxEntity::getNextAttemptAt, nextAttemptAt)
                                .set(MessageOutboxEntity::getLockedBy, null)
                                .set(MessageOutboxEntity::getLockedAt, null);
                    }

                    messageOutboxMapper.update(null, update);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }

    /**
     * 在 JDBC 调度器清除处理中过期消息的锁字段，并记录受影响行数。
     *
     * <p>具体过期判断使用 Mapper 的数据库时钟按行各自的 lock_timeout_seconds 计算；
     * 该更新不把 PROCESSING 状态改回 PENDING，因而清锁本身不会使消息重新进入
     * 待投递查询结果；投递失败的重试与死信登记由 {@link #markAsFailed} 承担。
     */
    @Override
    public Mono<Void> releaseExpiredLocks() {
        return Mono.fromRunnable(() -> {
                    int rows = messageOutboxMapper.releaseExpiredLocks();
                    if (rows > 0) {
                        log.info("释放过期锁: {} 条消息", rows);
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }
}
