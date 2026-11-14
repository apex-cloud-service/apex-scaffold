package com.apex.scaffold.office.meetingroom.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.office.meetingroom.domain.entity.MeetingRoomEntity;
import com.apex.scaffold.office.meetingroom.domain.repository.MeetingRoomRepository;
import com.apex.scaffold.office.meetingroom.infrastructure.persistence.converter.MeetingRoomPoConverter;
import com.apex.scaffold.office.meetingroom.infrastructure.persistence.mapper.MeetingRoomMapper;
import com.apex.scaffold.office.meetingroom.infrastructure.persistence.po.MeetingRoomPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 会议室资源仓储实现
 *
 * <p>所有 JDBC 访问通过 {@link BlockingIoSchedulers#jdbc()} 隔离，租户条件一律显式拼入查询，
 * 不依赖客户端可覆盖的入参。
 *
 * @author Apex Scaffold Team
 * @since 1.3.2
 */
@Repository
@RequiredArgsConstructor
public class MeetingRoomRepositoryImpl implements MeetingRoomRepository {

    private final MeetingRoomMapper mapper;
    private final MeetingRoomPoConverter converter;

    @Override
    public Mono<MeetingRoomEntity> findById(String tenantId, Long id) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<MeetingRoomPO>()
                        .eq(MeetingRoomPO::getTenantId, tenantId)
                        .eq(MeetingRoomPO::getId, id)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toDomain(po)));
    }

    @Override
    public Mono<MeetingRoomEntity> findByRoomCode(String tenantId, String roomCode) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<MeetingRoomPO>()
                        .eq(MeetingRoomPO::getTenantId, tenantId)
                        .eq(MeetingRoomPO::getRoomCode, roomCode)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toDomain(po)));
    }

    @Override
    public Mono<MeetingRoomEntity> findByRoomName(String tenantId, String roomName) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<MeetingRoomPO>()
                        .eq(MeetingRoomPO::getTenantId, tenantId)
                        .eq(MeetingRoomPO::getRoomName, roomName)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toDomain(po)));
    }

    @Override
    public Mono<MeetingRoomEntity> insert(MeetingRoomEntity entity) {
        return Mono.fromCallable(() -> {
                    MeetingRoomPO po = converter.toPo(entity);
                    mapper.insert(po);
                    entity.setId(po.getId());
                    return converter.toDomain(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<MeetingRoomEntity> update(MeetingRoomEntity entity) {
        return Mono.fromCallable(() -> {
                    MeetingRoomPO po = converter.toPo(entity);
                    mapper.updateById(po);
                    return converter.toDomain(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Boolean> deleteById(String tenantId, Long id) {
        return Mono.fromCallable(() -> mapper.delete(new LambdaQueryWrapper<MeetingRoomPO>()
                                .eq(MeetingRoomPO::getTenantId, tenantId)
                                .eq(MeetingRoomPO::getId, id))
                        > 0)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<PageResponse<MeetingRoomEntity>> findPage(
            String tenantId, Integer pageNum, Integer pageSize, String status, String keyword) {
        return Mono.fromCallable(() -> {
                    Page<MeetingRoomPO> page = new Page<>(pageNum, pageSize);
                    LambdaQueryWrapper<MeetingRoomPO> wrapper =
                            new LambdaQueryWrapper<MeetingRoomPO>().eq(MeetingRoomPO::getTenantId, tenantId);
                    if (status != null && !status.isBlank()) {
                        wrapper.eq(MeetingRoomPO::getStatus, status);
                    }
                    if (keyword != null && !keyword.isBlank()) {
                        wrapper.and(w -> w.like(MeetingRoomPO::getRoomName, keyword)
                                .or()
                                .like(MeetingRoomPO::getRoomCode, keyword));
                    }
                    wrapper.orderByAsc(MeetingRoomPO::getRoomName);
                    mapper.selectPage(page, wrapper);
                    return PageResponse.of(
                            page.getRecords().stream().map(converter::toDomain).toList(),
                            page.getTotal(),
                            pageNum,
                            pageSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Flux<MeetingRoomEntity> findAllEnabled(String tenantId) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<MeetingRoomPO>()
                        .eq(MeetingRoomPO::getTenantId, tenantId)
                        .eq(MeetingRoomPO::getStatus, MeetingRoomEntity.STATUS_ENABLED)
                        .orderByAsc(MeetingRoomPO::getRoomName)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }
}
