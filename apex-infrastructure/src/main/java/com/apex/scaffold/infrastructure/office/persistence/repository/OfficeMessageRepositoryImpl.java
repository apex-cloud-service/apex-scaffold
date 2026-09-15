package com.apex.scaffold.infrastructure.office.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.office.message.MessageStatus;
import com.apex.scaffold.domain.office.message.OfficeMessage;
import com.apex.scaffold.domain.office.message.OfficeMessageRepository;
import com.apex.scaffold.infrastructure.office.persistence.converter.OfficeMessageConverter;
import com.apex.scaffold.infrastructure.office.persistence.mapper.OfficeMessageMapper;
import com.apex.scaffold.infrastructure.office.persistence.po.OfficeMessagePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 办公消息仓储实现
 *
 * <p>实现办公消息的持久化操作，使用 MyBatis-Plus 进行数据库访问。
 * 所有阻塞操作通过 {@code Schedulers.boundedElastic()} 隔离到独立线程池。
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
    
    @Override
    public Mono<OfficeMessage> save(OfficeMessage message) {
        return Mono.fromCallable(() -> {
            OfficeMessagePO po = messageConverter.toPO(message);
            
            if (po.getId() == null) {
                // 插入
                messageMapper.insert(po);
                log.debug("插入消息记录，ID：{}", po.getId());
            } else {
                // 更新
                messageMapper.updateById(po);
                log.debug("更新消息记录，ID：{}", po.getId());
            }
            
            return messageConverter.toEntity(po);
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<OfficeMessage> findById(Long id) {
        return Mono.fromCallable(() -> {
            Long tenantId = TenantContext.currentTenantId();
            
            LambdaQueryWrapper<OfficeMessagePO> wrapper = Wrappers.lambdaQueryWrapper();
            wrapper.eq(OfficeMessagePO::getId, id)
                   .eq(OfficeMessagePO::getTenantId, tenantId);
            
            OfficeMessagePO po = messageMapper.selectOne(wrapper);
            return po != null ? messageConverter.toEntity(po) : null;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<OfficeMessage> findByPlatformMessageId(String platformMessageId) {
        return Mono.fromCallable(() -> {
            Long tenantId = TenantContext.currentTenantId();
            
            OfficeMessagePO po = messageMapper.findByPlatformMessageId(platformMessageId, tenantId);
            return po != null ? messageConverter.toEntity(po) : null;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Flux<OfficeMessage> findFailedMessages(int limit) {
        return Mono.fromCallable(() -> {
            Long tenantId = TenantContext.currentTenantId();
            return messageMapper.findFailedMessages(tenantId, limit);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable)
        .map(messageConverter::toEntity);
    }
    
    @Override
    public Flux<OfficeMessage> findByConditions(Long tenantId, String platform, 
                                                 MessageStatus status, int offset, int limit) {
        return Mono.fromCallable(() -> {
            LambdaQueryWrapper<OfficeMessagePO> wrapper = Wrappers.lambdaQueryWrapper();
            wrapper.eq(OfficeMessagePO::getTenantId, tenantId);
            
            if (platform != null && !platform.isEmpty()) {
                wrapper.eq(OfficeMessagePO::getPlatform, platform);
            }
            
            if (status != null) {
                wrapper.eq(OfficeMessagePO::getStatus, status.name());
            }
            
            wrapper.orderByDesc(OfficeMessagePO::getCreatedAt);
            wrapper.last("LIMIT " + offset + ", " + limit);
            
            return messageMapper.selectList(wrapper);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable)
        .map(messageConverter::toEntity);
    }
    
    @Override
    public Mono<Long> countByConditions(Long tenantId, String platform, MessageStatus status) {
        return Mono.fromCallable(() -> {
            LambdaQueryWrapper<OfficeMessagePO> wrapper = Wrappers.lambdaQueryWrapper();
            wrapper.eq(OfficeMessagePO::getTenantId, tenantId);
            
            if (platform != null && !platform.isEmpty()) {
                wrapper.eq(OfficeMessagePO::getPlatform, platform);
            }
            
            if (status != null) {
                wrapper.eq(OfficeMessagePO::getStatus, status.name());
            }
            
            return messageMapper.selectCount(wrapper);
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<Boolean> deleteById(Long id) {
        return Mono.fromCallable(() -> {
            Long tenantId = TenantContext.currentTenantId();
            
            LambdaQueryWrapper<OfficeMessagePO> wrapper = Wrappers.lambdaQueryWrapper();
            wrapper.eq(OfficeMessagePO::getId, id)
                   .eq(OfficeMessagePO::getTenantId, tenantId);
            
            int result = messageMapper.delete(wrapper);
            return result > 0;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
}
