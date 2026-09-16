package com.apex.scaffold.infrastructure.office.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.office.message.OfficeMessage;
import com.apex.scaffold.domain.office.message.OfficeMessageRepository;
import com.apex.scaffold.infrastructure.office.persistence.converter.OfficeMessageConverter;
import com.apex.scaffold.infrastructure.office.persistence.mapper.OfficeMessageMapper;
import com.apex.scaffold.infrastructure.office.persistence.po.OfficeMessagePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 办公平台消息仓储实现
 *
 * <p>使用MyBatis-Plus实现消息的持久化操作，所有阻塞I/O操作通过专用调度器隔离。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OfficeMessageRepositoryImpl implements OfficeMessageRepository {

    private final OfficeMessageMapper mapper;
    private final OfficeMessageConverter converter;
    private final BlockingIoSchedulers blockingIoSchedulers;

    @Override
    public Mono<OfficeMessage> save(OfficeMessage message) {
        return Mono.fromCallable(() -> {
                    OfficeMessagePO po = converter.toPO(message);
                    mapper.insert(po);
                    return converter.toEntity(po);
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(saved -> log.debug("消息已保存，ID：{}", saved.getId()));
    }

    @Override
    public Mono<OfficeMessage> findById(Long id) {
        return Mono.fromCallable(() -> {
                    OfficeMessagePO po = mapper.selectById(id);
                    return po != null ? converter.toEntity(po) : null;
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(found -> {
                    if (found != null) {
                        log.debug("查询消息成功，ID：{}", id);
                    }
                });
    }

    @Override
    public Mono<OfficeMessage> findByPlatformMessageId(String platformMessageId) {
        return Mono.fromCallable(() -> {
                    Long tenantId = TenantContext.currentTenantId()
                            .map(Long::valueOf)
                            .orElseThrow(() -> new IllegalStateException("租户上下文未设置"));
                    LambdaQueryWrapper<OfficeMessagePO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(OfficeMessagePO::getTenantId, tenantId)
                            .eq(OfficeMessagePO::getPlatformMessageId, platformMessageId)
                            .last("LIMIT 1");
                    OfficeMessagePO po = mapper.selectOne(wrapper);
                    return po != null ? converter.toEntity(po) : null;
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(found -> {
                    if (found != null) {
                        log.debug("根据平台消息ID查询成功：{}", platformMessageId);
                    }
                });
    }

    @Override
    public Mono<OfficeMessage> updateStatus(OfficeMessage message) {
        return Mono.fromCallable(() -> {
                    OfficeMessagePO po = converter.toPO(message);
                    mapper.updateById(po);
                    return converter.toEntity(mapper.selectById(po.getId()));
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(updated -> log.debug("消息状态已更新，ID：{}", updated.getId()));
    }
}
