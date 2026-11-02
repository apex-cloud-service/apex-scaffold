package com.apex.scaffold.office.notify.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.office.notify.domain.NotifyRequestRecord;
import com.apex.scaffold.office.notify.domain.NotifyRequestRecordRepository;
import com.apex.scaffold.office.notify.infrastructure.persistence.converter.NotifyRequestRecordPoConverter;
import com.apex.scaffold.office.notify.infrastructure.persistence.mapper.NotifyRequestRecordMapper;
import com.apex.scaffold.office.notify.infrastructure.persistence.po.NotifyRequestRecordPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 统一通知投递留痕的 MyBatis 持久化适配器。
 *
 * <p>投递留痕为追加式记录：仅插入与分页查询；租户条件一律显式拼入查询，
 * 不依赖客户端可覆盖的入参。所有数据库访问均延迟到订阅后在 JDBC 调度器执行。
 *
 * @author Apex Team
 * @since 1.4.0
 */
@Repository
@RequiredArgsConstructor
public class NotifyRequestRecordRepositoryImpl implements NotifyRequestRecordRepository {

    private final NotifyRequestRecordMapper mapper;
    private final NotifyRequestRecordPoConverter converter;

    @Override
    public Mono<NotifyRequestRecord> insert(NotifyRequestRecord record) {
        return Mono.fromCallable(() -> {
                    NotifyRequestRecordPO po = converter.toPo(record);
                    mapper.insert(po);
                    record.setId(po.getId());
                    return converter.toEntity(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<PageResponse<NotifyRequestRecord>> findPage(
            String tenantId, Integer pageNum, Integer pageSize, String channel, String templateCode, String status) {
        return Mono.fromCallable(() -> {
                    Page<NotifyRequestRecordPO> page = new Page<>(pageNum, pageSize);
                    LambdaQueryWrapper<NotifyRequestRecordPO> wrapper = new LambdaQueryWrapper<NotifyRequestRecordPO>()
                            .eq(NotifyRequestRecordPO::getTenantId, tenantId);
                    if (channel != null && !channel.isBlank()) {
                        wrapper.eq(NotifyRequestRecordPO::getChannel, channel);
                    }
                    if (templateCode != null && !templateCode.isBlank()) {
                        wrapper.eq(NotifyRequestRecordPO::getTemplateCode, templateCode);
                    }
                    if (status != null && !status.isBlank()) {
                        wrapper.eq(NotifyRequestRecordPO::getStatus, status);
                    }
                    wrapper.orderByDesc(NotifyRequestRecordPO::getCreateTime);
                    mapper.selectPage(page, wrapper);
                    return PageResponse.of(
                            page.getRecords().stream().map(converter::toEntity).toList(),
                            page.getTotal(),
                            pageNum,
                            pageSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
