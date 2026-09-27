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
 * <h2>核心特性</h2>
 * <ul>
 *   <li>阻塞IO隔离: 所有数据库操作都在BlockingIoSchedulers.jdbc()上执行</li>
 *   <li>自动字段填充: 创建时自动填充默认值</li>
 *   <li>指数退避重试: 重试延迟按2的指数增长</li>
 *   <li>分布式锁支持: 基于CAS的消息锁定机制</li>
 * </ul>
 *
 * <h2>重试策略</h2>
 * <p>重试延迟计算公式: delay = retry_delay_seconds * 2^(attempts - 1)
 * <ul>
 *   <li>第1次重试: 10秒后</li>
 *   <li>第2次重试: 20秒后</li>
 *   <li>第3次重试: 40秒后</li>
 *   <li>第4次重试: 80秒后</li>
 *   <li>第5次重试: 160秒后</li>
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

    @Override
    public Mono<Boolean> lockMessage(String messageId, String instanceId) {
        return Mono.fromCallable(() -> {
                    int rows = messageOutboxMapper.lockMessage(messageId, instanceId);
                    return rows > 0;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

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
