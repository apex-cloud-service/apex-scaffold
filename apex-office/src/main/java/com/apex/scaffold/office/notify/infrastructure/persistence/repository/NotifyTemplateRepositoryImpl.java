package com.apex.scaffold.office.notify.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.office.notify.domain.NotifyTemplate;
import com.apex.scaffold.office.notify.domain.NotifyTemplateRepository;
import com.apex.scaffold.office.notify.infrastructure.persistence.converter.NotifyTemplatePoConverter;
import com.apex.scaffold.office.notify.infrastructure.persistence.mapper.NotifyTemplateMapper;
import com.apex.scaffold.office.notify.infrastructure.persistence.po.NotifyTemplatePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 统一通知模板的 MyBatis 持久化适配器。
 *
 * <p>所有数据库访问均延迟到订阅后在 JDBC 调度器执行；租户条件一律显式拼入查询，
 * 不依赖客户端可覆盖的入参。模板按（租户，模板编码，逻辑删除）唯一，
 * 逻辑删除由 MyBatis-Plus 逻辑删除插件维护。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Repository
@RequiredArgsConstructor
public class NotifyTemplateRepositoryImpl implements NotifyTemplateRepository {

    private final NotifyTemplateMapper mapper;
    private final NotifyTemplatePoConverter converter;

    @Override
    public Mono<NotifyTemplate> findByCode(String tenantId, String templateCode) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<NotifyTemplatePO>()
                        .eq(NotifyTemplatePO::getTenantId, tenantId)
                        .eq(NotifyTemplatePO::getTemplateCode, templateCode)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toEntity(po)));
    }

    @Override
    public Mono<NotifyTemplate> findById(String tenantId, Long id) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<NotifyTemplatePO>()
                        .eq(NotifyTemplatePO::getTenantId, tenantId)
                        .eq(NotifyTemplatePO::getId, id)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toEntity(po)));
    }

    @Override
    public Mono<NotifyTemplate> insert(NotifyTemplate entity) {
        return Mono.fromCallable(() -> {
                    NotifyTemplatePO po = converter.toPo(entity);
                    mapper.insert(po);
                    entity.setId(po.getId());
                    return converter.toEntity(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<NotifyTemplate> update(NotifyTemplate entity) {
        return Mono.fromCallable(() -> {
                    NotifyTemplatePO po = converter.toPo(entity);
                    mapper.updateById(po);
                    return converter.toEntity(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Boolean> deleteById(String tenantId, Long id) {
        return Mono.fromCallable(() -> mapper.delete(new LambdaQueryWrapper<NotifyTemplatePO>()
                                .eq(NotifyTemplatePO::getTenantId, tenantId)
                                .eq(NotifyTemplatePO::getId, id))
                        > 0)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<PageResponse<NotifyTemplate>> findPage(
            String tenantId, Integer pageNum, Integer pageSize, String channel, String keyword) {
        return Mono.fromCallable(() -> {
                    Page<NotifyTemplatePO> page = new Page<>(pageNum, pageSize);
                    LambdaQueryWrapper<NotifyTemplatePO> wrapper =
                            new LambdaQueryWrapper<NotifyTemplatePO>().eq(NotifyTemplatePO::getTenantId, tenantId);
                    if (channel != null && !channel.isBlank()) {
                        wrapper.eq(NotifyTemplatePO::getChannel, channel);
                    }
                    if (keyword != null && !keyword.isBlank()) {
                        wrapper.and(w -> w.like(NotifyTemplatePO::getTemplateCode, keyword)
                                .or()
                                .like(NotifyTemplatePO::getTitle, keyword));
                    }
                    wrapper.orderByDesc(NotifyTemplatePO::getCreateTime);
                    mapper.selectPage(page, wrapper);
                    return PageResponse.of(
                            page.getRecords().stream().map(converter::toEntity).toList(),
                            page.getTotal(),
                            pageNum,
                            pageSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Flux<NotifyTemplate> findAllEnabled(String tenantId) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<NotifyTemplatePO>()
                        .eq(NotifyTemplatePO::getTenantId, tenantId)
                        .eq(NotifyTemplatePO::getStatus, NotifyTemplate.STATUS_ENABLED)
                        .orderByDesc(NotifyTemplatePO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }
}
