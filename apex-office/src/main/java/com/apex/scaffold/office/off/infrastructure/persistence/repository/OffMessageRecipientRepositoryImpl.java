package com.apex.scaffold.office.off.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.office.off.domain.entity.OffMessageRecipientEntity;
import com.apex.scaffold.office.off.domain.repository.OffMessageRecipientRepository;
import com.apex.scaffold.office.off.infrastructure.persistence.converter.OffMessageRecipientPoConverter;
import com.apex.scaffold.office.off.infrastructure.persistence.mapper.OffMessageRecipientMapper;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffMessageRecipientPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 消息接收人Repository实现
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class OffMessageRecipientRepositoryImpl implements OffMessageRecipientRepository {

    private final OffMessageRecipientMapper mapper;
    private final OffMessageRecipientPoConverter converter;

    @Override
    public Mono<OffMessageRecipientEntity> findById(Long id) {
        return Mono.fromCallable(() -> mapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toEntity(po)));
    }

    @Override
    public Flux<OffMessageRecipientEntity> findByMessageId(Long messageId) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OffMessageRecipientPO>()
                        .eq(OffMessageRecipientPO::getMessageId, messageId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Flux<OffMessageRecipientEntity> findByRecipientId(Long recipientId) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OffMessageRecipientPO>()
                        .eq(OffMessageRecipientPO::getRecipientId, recipientId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Mono<OffMessageRecipientEntity> findByMessageIdAndRecipientId(Long messageId, Long recipientId) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<OffMessageRecipientPO>()
                        .eq(OffMessageRecipientPO::getMessageId, messageId)
                        .eq(OffMessageRecipientPO::getRecipientId, recipientId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toEntity(po)));
    }

    @Override
    public Mono<Integer> insert(OffMessageRecipientEntity entity) {
        return Mono.fromCallable(() -> {
                    OffMessageRecipientPO po = converter.toPo(entity);
                    mapper.insert(po);
                    entity.setId(po.getId());
                    return 1;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> batchInsert(List<OffMessageRecipientEntity> entities) {
        return Mono.fromCallable(() -> {
                    int count = 0;
                    for (OffMessageRecipientEntity entity : entities) {
                        OffMessageRecipientPO po = converter.toPo(entity);
                        mapper.insert(po);
                        entity.setId(po.getId());
                        count++;
                    }
                    return count;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> update(OffMessageRecipientEntity entity) {
        return Mono.fromCallable(() -> {
                    OffMessageRecipientPO po = converter.toPo(entity);
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
    public Mono<Long> countUnreadByRecipientId(Long recipientId) {
        return Mono.fromCallable(() -> mapper.selectCount(new LambdaQueryWrapper<OffMessageRecipientPO>()
                        .eq(OffMessageRecipientPO::getRecipientId, recipientId)
                        .eq(OffMessageRecipientPO::getReadStatus, false)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> markAsRead(Long messageId, Long recipientId) {
        return Mono.fromCallable(() -> mapper.update(
                        null,
                        new LambdaUpdateWrapper<OffMessageRecipientPO>()
                                .set(OffMessageRecipientPO::getReadStatus, true)
                                .set(OffMessageRecipientPO::getReadTime, LocalDateTime.now())
                                .eq(OffMessageRecipientPO::getMessageId, messageId)
                                .eq(OffMessageRecipientPO::getRecipientId, recipientId)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> markAllAsRead(Long recipientId) {
        return Mono.fromCallable(() -> mapper.update(
                        null,
                        new LambdaUpdateWrapper<OffMessageRecipientPO>()
                                .set(OffMessageRecipientPO::getReadStatus, true)
                                .set(OffMessageRecipientPO::getReadTime, LocalDateTime.now())
                                .eq(OffMessageRecipientPO::getRecipientId, recipientId)
                                .eq(OffMessageRecipientPO::getReadStatus, false)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
