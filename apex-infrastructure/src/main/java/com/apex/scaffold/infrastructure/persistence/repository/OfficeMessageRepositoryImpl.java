package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.office.message.OfficeMessage;
import com.apex.scaffold.domain.office.message.OfficeMessageRepository;
import com.apex.scaffold.infrastructure.persistence.converter.OfficeMessageConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.OfficeMessageMapper;
import com.apex.scaffold.infrastructure.persistence.po.OfficeMessagePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 办公平台消息仓储实现
 *
 * <p>实现领域层定义的 {@link OfficeMessageRepository} 接口，
 * 提供消息的持久化操作。使用 MyBatis-Plus 进行数据访问，
 * 所有阻塞 I/O 操作通过 {@link BlockingIoSchedulers} 隔离到专用线程池。
 *
 * <p>关键特性：
 * <ul>
 *   <li>响应式编程：所有方法返回 Mono</li>
 *   <li>阻塞 I/O 隔离：JDBC 操作在专用调度器执行</li>
 *   <li>租户隔离：自动应用租户过滤</li>
 *   <li>乐观锁：支持并发更新控制</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OfficeMessageRepositoryImpl implements OfficeMessageRepository {

    private final OfficeMessageMapper messageMapper;
    private final OfficeMessageConverter messageConverter;
    private final BlockingIoSchedulers blockingIoSchedulers;

    /**
     * 保存消息
     *
     * <p>将消息实体持久化到数据库。所有 JDBC 操作在阻塞 I/O 调度器执行，
     * 避免阻塞响应式事件循环线程。
     *
     * @param message 消息实体
     * @return 保存后的消息（包含生成的ID）
     */
    @Override
    public Mono<OfficeMessage> save(OfficeMessage message) {
        return Mono.fromCallable(() -> {
                    log.debug("保存消息记录，平台：{}，消息类型：{}", message.getPlatform(), message.getMessageType());

                    OfficeMessagePO po = messageConverter.toMybatisPO(message);
                    messageMapper.insert(po);

                    log.debug("消息记录已保存，ID：{}", po.getId());
                    return messageConverter.toDomain(po);
                })
                .subscribeOn(blockingIoSchedulers.jdbc());
    }

    /**
     * 根据ID查询消息
     *
     * @param id 消息ID
     * @return 消息实体
     */
    @Override
    public Mono<OfficeMessage> findById(Long id) {
        return Mono.fromCallable(() -> {
                    log.debug("查询消息，ID：{}", id);

                    OfficeMessagePO po = messageMapper.selectById(id);
                    if (po == null) {
                        log.warn("消息不存在，ID：{}", id);
                        return null;
                    }

                    return messageConverter.toDomain(po);
                })
                .subscribeOn(blockingIoSchedulers.jdbc());
    }

    /**
     * 根据平台消息ID查询
     *
     * @param platformMessageId 平台消息ID
     * @return 消息实体
     */
    @Override
    public Mono<OfficeMessage> findByPlatformMessageId(String platformMessageId) {
        return Mono.fromCallable(() -> {
                    log.debug("根据平台消息ID查询，platformMessageId：{}", platformMessageId);

                    LambdaQueryWrapper<OfficeMessagePO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(OfficeMessagePO::getPlatformMessageId, platformMessageId);

                    OfficeMessagePO po = messageMapper.selectOne(wrapper);
                    if (po == null) {
                        log.warn("消息不存在，platformMessageId：{}", platformMessageId);
                        return null;
                    }

                    return messageConverter.toDomain(po);
                })
                .subscribeOn(blockingIoSchedulers.jdbc());
    }

    /**
     * 更新消息状态
     *
     * <p>更新消息的发送状态、平台消息ID、失败原因等字段。
     * 使用乐观锁机制防止并发更新冲突。
     *
     * @param message 消息实体
     * @return 更新后的消息
     */
    @Override
    public Mono<OfficeMessage> updateStatus(OfficeMessage message) {
        return Mono.fromCallable(() -> {
                    log.debug("更新消息状态，ID：{}，状态：{}", message.getId(), message.getSendStatus());

                    OfficeMessagePO po = messageConverter.toMybatisPO(message);
                    int rows = messageMapper.updateById(po);

                    if (rows == 0) {
                        log.warn("消息更新失败（乐观锁冲突或记录不存在），ID：{}", message.getId());
                        throw new IllegalStateException("消息更新失败");
                    }

                    log.debug("消息状态已更新，ID：{}", message.getId());
                    return messageConverter.toDomain(messageMapper.selectById(po.getId()));
                })
                .subscribeOn(blockingIoSchedulers.jdbc());
    }
}
