package com.apex.scaffold.office.off.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.office.off.domain.entity.OffMessageEntity;
import com.apex.scaffold.office.off.domain.repository.OffMessageRepository;
import com.apex.scaffold.office.off.infrastructure.persistence.converter.OffMessagePoConverter;
import com.apex.scaffold.office.off.infrastructure.persistence.mapper.OffMessageMapper;
import com.apex.scaffold.office.off.infrastructure.persistence.mapper.OffMessageRecipientMapper;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffMessagePO;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffMessageRecipientPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 办公消息的 MyBatis 持久化适配器。
 *
 * <p>所有数据库访问均延迟到订阅后在 JDBC 调度器执行，数据库异常以错误信号传播。
 * 租户条件由服务装配的消息租户拦截器根据可信上下文注入，调用链须传播该上下文；
 * 仓储不校验收发件人的访问权限，调用方仍须完成业务授权。
 * 本类不创建事务，也不因返回响应式类型而继承调用线程的 JDBC 事务。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class OffMessageRepositoryImpl implements OffMessageRepository {

    private final OffMessageMapper mapper;
    private final OffMessageRecipientMapper recipientMapper;
    private final OffMessagePoConverter converter;

    /**
     * 在 JDBC 调度器按主键查询消息主体，不加载接收人记录。
     *
     * @param id 消息主键
     * @return 转换后的消息；当前租户内不存在可见记录时为空
     */
    @Override
    public Mono<OffMessageEntity> findById(Long id) {
        return Mono.fromCallable(() -> mapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toEntity(po)));
    }

    /**
     * 在 JDBC 调度器按业务编码查询单条消息，不验证当前用户是否为收件人。
     *
     * @param messageCode 消息业务编码
     * @return 当前租户内可见的消息，不存在时为空；多条匹配等查询异常通过错误信号传播
     */
    @Override
    public Mono<OffMessageEntity> findByMessageCode(String messageCode) {
        return Mono.fromCallable(() -> mapper.selectOne(
                        new LambdaQueryWrapper<OffMessagePO>().eq(OffMessagePO::getMessageCode, messageCode)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toEntity(po)));
    }

    /**
     * 在 JDBC 调度器按接收记录主键倒序选取一页，再批量加载消息主体并按发送时间倒序排列。
     *
     * <p>发送时间为空的消息排在页内末尾；关联查询未建立事务快照，不保证并发变更时两次读取一致。
     *
     * @param recipientId 已通过业务授权的接收用户标识
     * @param pageNum 从 1 开始的页码，调用方负责校验
     * @param pageSize 每页记录数，调用方负责校验
     * @return 当前页可见消息，无记录时为空流
     */
    @Override
    public Flux<OffMessageEntity> findByRecipientId(Long recipientId, Integer pageNum, Integer pageSize) {
        return Mono.fromCallable(() -> {
                    // 接收人维度数据在关联表 off_message_recipient，先按接收人分页取消息 ID，再批量加载消息本体
                    Page<OffMessageRecipientPO> page = new Page<>(pageNum, pageSize);
                    recipientMapper.selectPage(
                            page,
                            new LambdaQueryWrapper<OffMessageRecipientPO>()
                                    .eq(OffMessageRecipientPO::getRecipientId, recipientId)
                                    .orderByDesc(OffMessageRecipientPO::getId));
                    List<Long> messageIds = page.getRecords().stream()
                            .map(OffMessageRecipientPO::getMessageId)
                            .toList();
                    if (messageIds.isEmpty()) {
                        return List.<OffMessageEntity>of();
                    }
                    return mapper.selectBatchIds(messageIds).stream()
                            .sorted(Comparator.comparing(
                                    OffMessagePO::getSendTime, Comparator.nullsLast(Comparator.reverseOrder())))
                            .map(converter::toEntity)
                            .toList();
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * 在 JDBC 调度器查询指定发送人的一页消息，按发送时间倒序返回。
     *
     * @param senderId 已通过业务授权的发送用户标识
     * @param pageNum 从 1 开始的页码，调用方负责校验
     * @param pageSize 每页记录数，调用方负责校验
     * @return 当前租户内可见的发件消息，无记录时为空流
     */
    @Override
    public Flux<OffMessageEntity> findBySenderId(Long senderId, Integer pageNum, Integer pageSize) {
        return Mono.fromCallable(() -> {
                    Page<OffMessagePO> page = new Page<>(pageNum, pageSize);
                    mapper.selectPage(
                            page,
                            new LambdaQueryWrapper<OffMessagePO>()
                                    .eq(OffMessagePO::getSenderId, senderId)
                                    .orderByDesc(OffMessagePO::getSendTime));
                    return page.getRecords();
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    /**
     * 在 JDBC 调度器插入消息主体，并将数据库生成的主键回填到传入实体。
     *
     * <p>不同时保存接收人记录；需要主体与接收人原子提交时，应使用业务层的同步事务写入路径。
     *
     * @param entity 待保存的消息实体，订阅执行时会修改其主键
     * @return Mapper 调用正常结束时固定返回 {@code 1}，不直接返回 Mapper 的影响行数
     */
    @Override
    public Mono<Integer> insert(OffMessageEntity entity) {
        return Mono.fromCallable(() -> {
                    OffMessagePO po = converter.toPo(entity);
                    mapper.insert(po);
                    entity.setId(po.getId());
                    return 1;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 在 JDBC 调度器按主键更新消息主体，字段写入策略遵循 MyBatis 实体映射配置。
     *
     * @param entity 包含消息主键及待更新字段的实体
     * @return 数据库影响行数，未匹配可见记录时为零
     */
    @Override
    public Mono<Integer> update(OffMessageEntity entity) {
        return Mono.fromCallable(() -> {
                    OffMessagePO po = converter.toPo(entity);
                    return mapper.updateById(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 在 JDBC 调度器调用消息主体的主键删除，不同步删除接收人记录。
     *
     * @param id 已通过业务授权的消息主键
     * @return 删除调用完成信号，未匹配记录仍正常完成；逻辑删除行为遵循实体映射配置
     */
    @Override
    public Mono<Void> deleteById(Long id) {
        return Mono.fromCallable(() -> mapper.deleteById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }

    /**
     * 在 JDBC 调度器统计当前租户内指定用户的接收记录，不关联检查消息主体状态。
     *
     * @param recipientId 已通过业务授权的接收用户标识
     * @return 接收记录数，无记录时为零
     */
    @Override
    public Mono<Long> countByRecipientId(Long recipientId) {
        return Mono.fromCallable(() -> recipientMapper.selectCount(new LambdaQueryWrapper<OffMessageRecipientPO>()
                        .eq(OffMessageRecipientPO::getRecipientId, recipientId)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 在 JDBC 调度器统计当前租户内阅读状态为 {@code false} 的接收记录。
     *
     * @param recipientId 已通过业务授权的接收用户标识
     * @return 未读接收记录数，不关联检查消息主体状态
     */
    @Override
    public Mono<Long> countUnreadByRecipientId(Long recipientId) {
        return Mono.fromCallable(() -> recipientMapper.selectCount(new LambdaQueryWrapper<OffMessageRecipientPO>()
                        .eq(OffMessageRecipientPO::getRecipientId, recipientId)
                        .eq(OffMessageRecipientPO::getReadStatus, false)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 在 JDBC 调度器按可选状态和类型查询消息主体，按发送时间倒序分页。
     *
     * <p>该查询不添加收发件人条件，调用方须事先验证管理或相应查询权限。
     * 返回对象仅显式设置总数与记录列表，其他分页字段沿用响应对象默认值。
     *
     * @param pageNum 从 1 开始的页码，调用方负责校验
     * @param pageSize 每页记录数，调用方负责校验
     * @param status 精确匹配的消息状态，{@code null} 时不限制
     * @param messageType 精确匹配的消息类型，{@code null} 时不限制
     * @return 当前租户内可见消息的总数与当前页记录
     */
    @Override
    public Mono<PageResponse<OffMessageEntity>> findPage(
            Integer pageNum, Integer pageSize, String status, String messageType) {
        return Mono.fromCallable(() -> {
                    Page<OffMessagePO> page = new Page<>(pageNum, pageSize);
                    LambdaQueryWrapper<OffMessagePO> wrapper = new LambdaQueryWrapper<>();
                    if (status != null) {
                        wrapper.eq(OffMessagePO::getStatus, status);
                    }
                    if (messageType != null) {
                        wrapper.eq(OffMessagePO::getMessageType, messageType);
                    }
                    wrapper.orderByDesc(OffMessagePO::getSendTime);
                    mapper.selectPage(page, wrapper);

                    PageResponse<OffMessageEntity> response = new PageResponse<>();
                    response.setTotal(page.getTotal());
                    response.setRecords(converter.toEntityList(page.getRecords()));
                    return response;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
