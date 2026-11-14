package com.apex.scaffold.office.off.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.office.off.domain.entity.OffMessageDeliveryEntity;
import com.apex.scaffold.office.off.domain.repository.OffMessageDeliveryRepository;
import com.apex.scaffold.office.off.infrastructure.persistence.converter.OffMessageDeliveryPoConverter;
import com.apex.scaffold.office.off.infrastructure.persistence.mapper.OffMessageDeliveryMapper;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffMessageDeliveryPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 消息渠道投递记录的 MyBatis 持久化适配器。
 *
 * <p>所有数据库访问均延迟到订阅后在 JDBC 调度器执行；按（消息、接收人、渠道）
 * 唯一键做插入或更新，重复上报投递结果不会产生重复记录。租户条件由服务装配的
 * 消息租户拦截器根据可信上下文注入，本类不做业务授权。</p>
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Repository
@RequiredArgsConstructor
public class OffMessageDeliveryRepositoryImpl implements OffMessageDeliveryRepository {

    private final OffMessageDeliveryMapper mapper;
    private final OffMessageDeliveryPoConverter converter;

    @Override
    public Mono<OffMessageDeliveryEntity> upsert(OffMessageDeliveryEntity entity) {
        return Mono.fromCallable(() -> {
                    OffMessageDeliveryPO existing = mapper.selectOne(new LambdaQueryWrapper<OffMessageDeliveryPO>()
                            .eq(OffMessageDeliveryPO::getMessageId, entity.getMessageId())
                            .eq(OffMessageDeliveryPO::getRecipientId, entity.getRecipientId())
                            .eq(OffMessageDeliveryPO::getChannel, entity.getChannel()));
                    if (existing == null) {
                        OffMessageDeliveryPO po = converter.toPo(entity);
                        mapper.insert(po);
                        entity.setId(po.getId());
                        return entity;
                    }
                    OffMessageDeliveryPO po = converter.toPo(entity);
                    po.setId(existing.getId());
                    mapper.updateById(po);
                    entity.setId(existing.getId());
                    return entity;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Flux<OffMessageDeliveryEntity> findByMessageId(Long messageId) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OffMessageDeliveryPO>()
                        .eq(OffMessageDeliveryPO::getMessageId, messageId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Flux<OffMessageDeliveryEntity> findByMessageIdAndRecipientId(Long messageId, Long recipientId) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OffMessageDeliveryPO>()
                        .eq(OffMessageDeliveryPO::getMessageId, messageId)
                        .eq(OffMessageDeliveryPO::getRecipientId, recipientId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }
}
