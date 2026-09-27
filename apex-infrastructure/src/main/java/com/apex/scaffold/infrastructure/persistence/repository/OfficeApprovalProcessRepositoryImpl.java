package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.OfficeApprovalProcessEntity;
import com.apex.scaffold.domain.repository.OfficeApprovalProcessRepository;
import com.apex.scaffold.infrastructure.persistence.converter.OfficeApprovalProcessPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.OfficeApprovalProcessMapper;
import com.apex.scaffold.infrastructure.persistence.po.BasePO;
import com.apex.scaffold.infrastructure.persistence.po.OfficeApprovalProcessPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Office审批流程Repository实现
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class OfficeApprovalProcessRepositoryImpl implements OfficeApprovalProcessRepository {

    private final OfficeApprovalProcessMapper mapper;
    private final OfficeApprovalProcessPoConverter converter;

    @Override
    public Mono<OfficeApprovalProcessEntity> findById(Long id) {
        return Mono.fromCallable(() -> mapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toDomain(po)));
    }

    @Override
    public Mono<OfficeApprovalProcessEntity> findByPlatformInstanceId(String platform, String platformInstanceId) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<OfficeApprovalProcessPO>()
                        .eq(OfficeApprovalProcessPO::getPlatform, platform)
                        .eq(OfficeApprovalProcessPO::getPlatformInstanceId, platformInstanceId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toDomain(po)));
    }

    @Override
    public Flux<OfficeApprovalProcessEntity> findByInitiatorId(String initiatorId) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OfficeApprovalProcessPO>()
                        .eq(OfficeApprovalProcessPO::getInitiatorId, initiatorId)
                        .orderByDesc(BasePO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }

    @Override
    public Flux<OfficeApprovalProcessEntity> findByStatus(String status) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OfficeApprovalProcessPO>()
                        .eq(OfficeApprovalProcessPO::getStatus, status)
                        .orderByDesc(BasePO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }

    @Override
    public Flux<OfficeApprovalProcessEntity> findByInitiatorAndStatus(String initiatorId, String status) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OfficeApprovalProcessPO>()
                        .eq(OfficeApprovalProcessPO::getInitiatorId, initiatorId)
                        .eq(OfficeApprovalProcessPO::getStatus, status)
                        .orderByDesc(BasePO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }

    @Override
    public Flux<OfficeApprovalProcessEntity> findPendingApprovals(String approverId) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OfficeApprovalProcessPO>()
                        .like(OfficeApprovalProcessPO::getCurrentApproverIds, approverId)
                        .in(OfficeApprovalProcessPO::getStatus, "PENDING", "IN_PROGRESS")
                        .orderByDesc(BasePO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }

    @Override
    public Mono<Integer> insert(OfficeApprovalProcessEntity entity) {
        return Mono.fromCallable(() -> {
                    OfficeApprovalProcessPO po = converter.toPo(entity);
                    mapper.insert(po);
                    entity.setId(po.getId());
                    return 1;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> update(OfficeApprovalProcessEntity entity) {
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
    public Mono<PageResponse<OfficeApprovalProcessEntity>> findPage(
            Integer pageNum,
            Integer pageSize,
            String initiatorId,
            String status,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        return Mono.fromCallable(() -> {
                    Page<OfficeApprovalProcessPO> page = new Page<>(pageNum, pageSize);
                    LambdaQueryWrapper<OfficeApprovalProcessPO> wrapper = new LambdaQueryWrapper<>();
                    if (initiatorId != null) {
                        wrapper.eq(OfficeApprovalProcessPO::getInitiatorId, initiatorId);
                    }
                    if (status != null) {
                        wrapper.eq(OfficeApprovalProcessPO::getStatus, status);
                    }
                    if (startTime != null) {
                        wrapper.ge(OfficeApprovalProcessPO::getInitiateTime, startTime);
                    }
                    if (endTime != null) {
                        wrapper.le(OfficeApprovalProcessPO::getInitiateTime, endTime);
                    }
                    wrapper.orderByDesc(BasePO::getCreateTime);
                    mapper.selectPage(page, wrapper);

                    PageResponse<OfficeApprovalProcessEntity> response = new PageResponse<>();
                    response.setTotal(page.getTotal());
                    response.setRecords(
                            page.getRecords().stream().map(converter::toDomain).toList());
                    return response;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
