package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.OffDepartmentEntity;
import com.apex.scaffold.domain.repository.OffDepartmentRepository;
import com.apex.scaffold.infrastructure.persistence.converter.OffDepartmentPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.OffDepartmentMapper;
import com.apex.scaffold.infrastructure.persistence.po.OffDepartmentPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 部门Repository实现
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class OffDepartmentRepositoryImpl implements OffDepartmentRepository {

    private final OffDepartmentMapper mapper;
    private final OffDepartmentPoConverter converter;

    @Override
    public Mono<OffDepartmentEntity> findById(Long id) {
        return Mono.fromCallable(() -> mapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toDomain(po)));
    }

    @Override
    public Mono<OffDepartmentEntity> findByDeptCode(String deptCode) {
        return Mono.fromCallable(() -> mapper.selectOne(
                        new LambdaQueryWrapper<OffDepartmentPO>().eq(OffDepartmentPO::getDeptCode, deptCode)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toDomain(po)));
    }

    @Override
    public Flux<OffDepartmentEntity> findByParentId(Long parentId) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OffDepartmentPO>()
                        .eq(OffDepartmentPO::getParentId, parentId)
                        .orderByAsc(OffDepartmentPO::getSortOrder)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }

    @Override
    public Flux<OffDepartmentEntity> findRootDepartments() {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OffDepartmentPO>()
                        .isNull(OffDepartmentPO::getParentId)
                        .orderByAsc(OffDepartmentPO::getSortOrder)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }

    @Override
    public Flux<OffDepartmentEntity> findByExternalId(String syncSource, String externalId) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OffDepartmentPO>()
                        .eq(OffDepartmentPO::getSyncSource, syncSource)
                        .eq(OffDepartmentPO::getExternalId, externalId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }

    @Override
    public Mono<Integer> insert(OffDepartmentEntity entity) {
        return Mono.fromCallable(() -> {
                    OffDepartmentPO po = converter.toPo(entity);
                    mapper.insert(po);
                    entity.setId(po.getId());
                    return 1;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> batchInsert(List<OffDepartmentEntity> entities) {
        return Mono.fromCallable(() -> {
                    int count = 0;
                    for (OffDepartmentEntity entity : entities) {
                        OffDepartmentPO po = converter.toPo(entity);
                        mapper.insert(po);
                        entity.setId(po.getId());
                        count++;
                    }
                    return count;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> update(OffDepartmentEntity entity) {
        return Mono.fromCallable(() -> mapper.updateById(converter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Void> deleteById(Long id) {
        return Mono.fromCallable(() -> mapper.deleteById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }

    @Override
    public Mono<PageResponse<OffDepartmentEntity>> findPage(Integer pageNum, Integer pageSize, String status) {
        return Mono.fromCallable(() -> {
                    Page<OffDepartmentPO> page = new Page<>(pageNum, pageSize);
                    LambdaQueryWrapper<OffDepartmentPO> wrapper = new LambdaQueryWrapper<>();
                    if (status != null) {
                        wrapper.eq(OffDepartmentPO::getStatus, status);
                    }
                    wrapper.orderByAsc(OffDepartmentPO::getDeptLevel, OffDepartmentPO::getSortOrder);
                    mapper.selectPage(page, wrapper);

                    PageResponse<OffDepartmentEntity> response = new PageResponse<>();
                    response.setTotal(page.getTotal());
                    response.setRecords(
                            page.getRecords().stream().map(converter::toDomain).toList());
                    return response;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Flux<OffDepartmentEntity> findAll() {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OffDepartmentPO>()
                        .orderByAsc(OffDepartmentPO::getDeptLevel, OffDepartmentPO::getSortOrder)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }
}
