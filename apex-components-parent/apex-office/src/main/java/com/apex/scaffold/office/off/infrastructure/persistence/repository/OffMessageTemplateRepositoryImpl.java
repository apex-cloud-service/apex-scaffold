package com.apex.scaffold.office.off.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.office.off.domain.entity.OffMessageTemplateEntity;
import com.apex.scaffold.office.off.domain.repository.OffMessageTemplateRepository;
import com.apex.scaffold.office.off.infrastructure.persistence.converter.OffMessageTemplatePoConverter;
import com.apex.scaffold.office.off.infrastructure.persistence.mapper.OffMessageTemplateMapper;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffMessageTemplatePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 消息模板Repository实现
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class OffMessageTemplateRepositoryImpl implements OffMessageTemplateRepository {

    private final OffMessageTemplateMapper mapper;
    private final OffMessageTemplatePoConverter converter;

    @Override
    public Mono<OffMessageTemplateEntity> findById(Long id) {
        return Mono.fromCallable(() -> mapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toEntity(po)));
    }

    @Override
    public Mono<OffMessageTemplateEntity> findByTemplateCode(String templateCode) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<OffMessageTemplatePO>()
                        .eq(OffMessageTemplatePO::getTemplateCode, templateCode)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toEntity(po)));
    }

    @Override
    public Flux<OffMessageTemplateEntity> findByTemplateType(String templateType) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OffMessageTemplatePO>()
                        .eq(OffMessageTemplatePO::getTemplateType, templateType)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Flux<OffMessageTemplateEntity> findAllEnabled() {
        return Mono.fromCallable(() -> mapper.selectList(
                        new LambdaQueryWrapper<OffMessageTemplatePO>().eq(OffMessageTemplatePO::getEnabled, true)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toEntity);
    }

    @Override
    public Mono<Integer> insert(OffMessageTemplateEntity entity) {
        return Mono.fromCallable(() -> {
                    OffMessageTemplatePO po = converter.toPo(entity);
                    mapper.insert(po);
                    entity.setId(po.getId());
                    return 1;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> update(OffMessageTemplateEntity entity) {
        return Mono.fromCallable(() -> {
                    OffMessageTemplatePO po = converter.toPo(entity);
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
    public Mono<PageResponse<OffMessageTemplateEntity>> findPage(
            Integer pageNum, Integer pageSize, String templateType, Boolean enabled) {
        return Mono.fromCallable(() -> {
                    Page<OffMessageTemplatePO> page = new Page<>(pageNum, pageSize);
                    LambdaQueryWrapper<OffMessageTemplatePO> wrapper = new LambdaQueryWrapper<>();
                    if (templateType != null) {
                        wrapper.eq(OffMessageTemplatePO::getTemplateType, templateType);
                    }
                    if (enabled != null) {
                        wrapper.eq(OffMessageTemplatePO::getEnabled, enabled);
                    }
                    wrapper.orderByDesc(OffMessageTemplatePO::getCreateTime);
                    mapper.selectPage(page, wrapper);

                    PageResponse<OffMessageTemplateEntity> response = new PageResponse<>();
                    response.setTotal(page.getTotal());
                    response.setRecords(converter.toEntityList(page.getRecords()));
                    return response;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
