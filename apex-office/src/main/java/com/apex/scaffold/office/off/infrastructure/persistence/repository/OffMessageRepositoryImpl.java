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
 * 消息Repository实现
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

    @Override
    public Mono<OffMessageEntity> findById(Long id) {
        return Mono.fromCallable(() -> mapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toEntity(po)));
    }

    @Override
    public Mono<OffMessageEntity> findByMessageCode(String messageCode) {
        return Mono.fromCallable(() -> mapper.selectOne(
                        new LambdaQueryWrapper<OffMessagePO>().eq(OffMessagePO::getMessageCode, messageCode)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toEntity(po)));
    }

    @Override
    public Flux<OffMessageEntity> findByRecipientId(Long recipientId, Integer pageNum, Integer pageSize) {
        return Mono.fromCallable(() -> {
                    // 接收人维度数据在关联表 off_message_recipient，先按接收人分页取消息 ID，再批量加载消息本体
                    Page<OffMessageRecipientPO> page = new Page<>(pageNum, pageSize);
                    recipientMapper.selectPage(
                            page,
                            new LambdaQueryWrapper<OffMessageRecipientPO>()
                                    .eq(OffMessageRecipientPO::getRecipientId, recipientId));
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

    @Override
    public Mono<Integer> update(OffMessageEntity entity) {
        return Mono.fromCallable(() -> {
                    OffMessagePO po = converter.toPo(entity);
                    return mapper.updateById(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Void> deleteById(Long id) {
        return Mono.fromCallable(() -> mapper.deleteById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }

    @Override
    public Mono<Long> countByRecipientId(Long recipientId) {
        return Mono.fromCallable(() -> recipientMapper.selectCount(new LambdaQueryWrapper<OffMessageRecipientPO>()
                        .eq(OffMessageRecipientPO::getRecipientId, recipientId)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Long> countUnreadByRecipientId(Long recipientId) {
        return Mono.fromCallable(() -> recipientMapper.selectCount(new LambdaQueryWrapper<OffMessageRecipientPO>()
                        .eq(OffMessageRecipientPO::getRecipientId, recipientId)
                        .eq(OffMessageRecipientPO::getReadStatus, false)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

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
