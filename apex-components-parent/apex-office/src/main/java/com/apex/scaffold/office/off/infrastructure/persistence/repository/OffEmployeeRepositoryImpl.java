package com.apex.scaffold.office.off.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.persistence.po.BasePO;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.office.off.domain.entity.OffEmployeeEntity;
import com.apex.scaffold.office.off.domain.repository.OffEmployeeRepository;
import com.apex.scaffold.office.off.infrastructure.persistence.converter.OffEmployeePoConverter;
import com.apex.scaffold.office.off.infrastructure.persistence.mapper.OffEmployeeMapper;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffEmployeePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 员工Repository实现
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class OffEmployeeRepositoryImpl implements OffEmployeeRepository {

    private final OffEmployeeMapper mapper;
    private final OffEmployeePoConverter converter;

    @Override
    public Mono<OffEmployeeEntity> findById(Long id) {
        return Mono.fromCallable(() -> mapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toDomain(po)));
    }

    @Override
    public Mono<OffEmployeeEntity> findByEmployeeCode(String employeeCode) {
        return Mono.fromCallable(() -> mapper.selectOne(
                        new LambdaQueryWrapper<OffEmployeePO>().eq(OffEmployeePO::getEmployeeCode, employeeCode)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toDomain(po)));
    }

    @Override
    public Mono<OffEmployeeEntity> findByUserId(Long userId) {
        return Mono.fromCallable(() ->
                        mapper.selectOne(new LambdaQueryWrapper<OffEmployeePO>().eq(OffEmployeePO::getUserId, userId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toDomain(po)));
    }

    @Override
    public Flux<OffEmployeeEntity> findByDeptId(Long deptId) {
        return Mono.fromCallable(() ->
                        mapper.selectList(new LambdaQueryWrapper<OffEmployeePO>().eq(OffEmployeePO::getDeptId, deptId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }

    @Override
    public Flux<OffEmployeeEntity> findByExternalId(String syncSource, String externalId) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OffEmployeePO>()
                        .eq(OffEmployeePO::getSyncSource, syncSource)
                        .eq(OffEmployeePO::getExternalId, externalId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }

    @Override
    public Mono<Integer> insert(OffEmployeeEntity entity) {
        return Mono.fromCallable(() -> {
                    OffEmployeePO po = converter.toPo(entity);
                    mapper.insert(po);
                    entity.setId(po.getId());
                    return 1;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> batchInsert(List<OffEmployeeEntity> entities) {
        return Mono.fromCallable(() -> {
                    int count = 0;
                    for (OffEmployeeEntity entity : entities) {
                        OffEmployeePO po = converter.toPo(entity);
                        mapper.insert(po);
                        entity.setId(po.getId());
                        count++;
                    }
                    return count;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> update(OffEmployeeEntity entity) {
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
    public Mono<PageResponse<OffEmployeeEntity>> findPage(
            Integer pageNum, Integer pageSize, Long deptId, String status) {
        return Mono.fromCallable(() -> {
                    Page<OffEmployeePO> page = new Page<>(pageNum, pageSize);
                    LambdaQueryWrapper<OffEmployeePO> wrapper = new LambdaQueryWrapper<>();
                    if (deptId != null) {
                        wrapper.eq(OffEmployeePO::getDeptId, deptId);
                    }
                    if (status != null) {
                        wrapper.eq(OffEmployeePO::getStatus, status);
                    }
                    wrapper.orderByDesc(BasePO::getCreateTime);
                    mapper.selectPage(page, wrapper);

                    PageResponse<OffEmployeeEntity> response = new PageResponse<>();
                    response.setTotal(page.getTotal());
                    response.setRecords(
                            page.getRecords().stream().map(converter::toDomain).toList());
                    return response;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Flux<OffEmployeeEntity> findAll() {
        return Mono.fromCallable(() ->
                        mapper.selectList(new LambdaQueryWrapper<OffEmployeePO>().orderByDesc(BasePO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }
}
