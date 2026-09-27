package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.OfficeCalendarEventEntity;
import com.apex.scaffold.domain.repository.OfficeCalendarEventRepository;
import com.apex.scaffold.infrastructure.persistence.converter.OfficeCalendarEventPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.OfficeCalendarEventMapper;
import com.apex.scaffold.infrastructure.persistence.po.OfficeCalendarEventPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Office日历事件Repository实现
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class OfficeCalendarEventRepositoryImpl implements OfficeCalendarEventRepository {

    private final OfficeCalendarEventMapper mapper;
    private final OfficeCalendarEventPoConverter converter;

    @Override
    public Mono<OfficeCalendarEventEntity> findById(Long id) {
        return Mono.fromCallable(() -> mapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toDomain(po)));
    }

    @Override
    public Mono<OfficeCalendarEventEntity> findByPlatformEventId(String platform, String platformEventId) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<OfficeCalendarEventPO>()
                        .eq(OfficeCalendarEventPO::getPlatform, platform)
                        .eq(OfficeCalendarEventPO::getPlatformEventId, platformEventId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(converter.toDomain(po)));
    }

    @Override
    public Flux<OfficeCalendarEventEntity> findByOrganizerId(String organizerId) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OfficeCalendarEventPO>()
                        .eq(OfficeCalendarEventPO::getOrganizerId, organizerId)
                        .orderByDesc(OfficeCalendarEventPO::getStartTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }

    @Override
    public Flux<OfficeCalendarEventEntity> findByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OfficeCalendarEventPO>()
                        .ge(OfficeCalendarEventPO::getStartTime, startTime)
                        .le(OfficeCalendarEventPO::getEndTime, endTime)
                        .orderBy(true, true, OfficeCalendarEventPO::getStartTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }

    @Override
    public Flux<OfficeCalendarEventEntity> findByParticipant(
            String participantId, LocalDateTime startTime, LocalDateTime endTime) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OfficeCalendarEventPO>()
                        .like(OfficeCalendarEventPO::getParticipantIds, participantId)
                        .ge(OfficeCalendarEventPO::getStartTime, startTime)
                        .le(OfficeCalendarEventPO::getEndTime, endTime)
                        .orderBy(true, true, OfficeCalendarEventPO::getStartTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }

    @Override
    public Flux<OfficeCalendarEventEntity> findByMeetingRoom(
            String meetingRoomId, LocalDateTime startTime, LocalDateTime endTime) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OfficeCalendarEventPO>()
                        .eq(OfficeCalendarEventPO::getMeetingRoomId, meetingRoomId)
                        .ge(OfficeCalendarEventPO::getStartTime, startTime)
                        .le(OfficeCalendarEventPO::getEndTime, endTime)
                        .orderBy(true, true, OfficeCalendarEventPO::getStartTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }

    @Override
    public Flux<OfficeCalendarEventEntity> findUpcomingEvents(String userId, LocalDateTime fromTime, Integer limit) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<OfficeCalendarEventPO>()
                        .and(w -> w.eq(OfficeCalendarEventPO::getOrganizerId, userId)
                                .or()
                                .like(OfficeCalendarEventPO::getParticipantIds, userId))
                        .ge(OfficeCalendarEventPO::getStartTime, fromTime)
                        .orderBy(true, true, OfficeCalendarEventPO::getStartTime)
                        .last("LIMIT " + limit)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(converter::toDomain);
    }

    @Override
    public Mono<Integer> insert(OfficeCalendarEventEntity entity) {
        return Mono.fromCallable(() -> {
                    OfficeCalendarEventPO po = converter.toPo(entity);
                    mapper.insert(po);
                    entity.setId(po.getId());
                    return 1;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Integer> update(OfficeCalendarEventEntity entity) {
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
    public Mono<PageResponse<OfficeCalendarEventEntity>> findPage(
            Integer pageNum,
            Integer pageSize,
            String organizerId,
            String eventType,
            String status,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        return Mono.fromCallable(() -> {
                    Page<OfficeCalendarEventPO> page = new Page<>(pageNum, pageSize);
                    LambdaQueryWrapper<OfficeCalendarEventPO> wrapper = new LambdaQueryWrapper<>();
                    if (organizerId != null) {
                        wrapper.eq(OfficeCalendarEventPO::getOrganizerId, organizerId);
                    }
                    if (eventType != null) {
                        wrapper.eq(OfficeCalendarEventPO::getEventType, eventType);
                    }
                    if (status != null) {
                        wrapper.eq(OfficeCalendarEventPO::getStatus, status);
                    }
                    if (startTime != null) {
                        wrapper.ge(OfficeCalendarEventPO::getStartTime, startTime);
                    }
                    if (endTime != null) {
                        wrapper.le(OfficeCalendarEventPO::getEndTime, endTime);
                    }
                    wrapper.orderByDesc(OfficeCalendarEventPO::getStartTime);
                    mapper.selectPage(page, wrapper);

                    PageResponse<OfficeCalendarEventEntity> response = new PageResponse<>();
                    response.setTotal(page.getTotal());
                    response.setRecords(
                            page.getRecords().stream().map(converter::toDomain).toList());
                    return response;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
