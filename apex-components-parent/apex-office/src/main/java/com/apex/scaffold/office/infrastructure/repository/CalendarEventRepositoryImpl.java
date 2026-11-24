package com.apex.scaffold.office.infrastructure.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.office.domain.calendar.CalendarEvent;
import com.apex.scaffold.office.domain.calendar.CalendarEventRepository;
import com.apex.scaffold.office.infrastructure.converter.CalendarEventConverter;
import com.apex.scaffold.office.infrastructure.mapper.CalendarEventMapper;
import com.apex.scaffold.office.infrastructure.po.CalendarEventPO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 日历事件仓储实现
 *
 * <p>实现CalendarEventRepository接口，负责日历事件的持久化操作。
 * 使用BlockingIoSchedulers隔离JDBC阻塞操作，保证响应式编程的非阻塞特性。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CalendarEventRepositoryImpl implements CalendarEventRepository {

    private final CalendarEventMapper mapper;
    private final CalendarEventConverter converter;
    private final BlockingIoSchedulers blockingIoSchedulers;

    @Override
    public Mono<CalendarEvent> save(CalendarEvent event) {
        return Mono.fromCallable(() -> {
                    CalendarEventPO po = converter.toPO(event);
                    mapper.insert(po);
                    return converter.toEntity(po);
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(savedEvent -> log.debug("日历事件保存成功 - id: {}", savedEvent.getId()))
                .doOnError(error -> log.error("日历事件保存失败", error));
    }

    @Override
    public Mono<CalendarEvent> findById(Long id) {
        return Mono.fromCallable(() -> {
                    CalendarEventPO po = mapper.selectById(id);
                    return po != null ? converter.toEntity(po) : null;
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(event -> log.debug("根据ID查询日历事件 - id: {}, found: {}", id, event != null))
                .doOnError(error -> log.error("根据ID查询日历事件失败 - id: {}", id, error));
    }

    @Override
    public Mono<CalendarEvent> findByPlatformAndEventId(String platform, String platformEventId) {
        return Mono.fromCallable(() -> {
                    CalendarEventPO po = mapper.selectByPlatformAndEventId(platform, platformEventId);
                    return po != null ? converter.toEntity(po) : null;
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(event -> log.debug(
                        "根据平台和事件ID查询 - platform: {}, eventId: {}, found: {}", platform, platformEventId, event != null))
                .doOnError(error ->
                        log.error("根据平台和事件ID查询失败 - platform: {}, eventId: {}", platform, platformEventId, error));
    }

    @Override
    public Mono<List<CalendarEvent>> findByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return Mono.fromCallable(() -> {
                    List<CalendarEventPO> poList = mapper.selectByTimeRange(startTime, endTime);
                    return poList.stream().map(converter::toEntity).collect(Collectors.toList());
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(list -> log.debug(
                        "根据时间范围查询日历事件 - startTime: {}, endTime: {}, count: {}", startTime, endTime, list.size()))
                .doOnError(
                        error -> log.error("根据时间范围查询日历事件失败 - startTime: {}, endTime: {}", startTime, endTime, error));
    }

    @Override
    public Mono<List<CalendarEvent>> findByOrganizer(String organizerId) {
        return Mono.fromCallable(() -> {
                    List<CalendarEventPO> poList = mapper.selectByOrganizer(organizerId);
                    return poList.stream().map(converter::toEntity).collect(Collectors.toList());
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(list -> log.debug("根据组织者查询日历事件 - organizerId: {}, count: {}", organizerId, list.size()))
                .doOnError(error -> log.error("根据组织者查询日历事件失败 - organizerId: {}", organizerId, error));
    }

    @Override
    public Mono<List<CalendarEvent>> findByParticipant(String participantId) {
        return Mono.fromCallable(() -> {
                    List<CalendarEventPO> poList = mapper.selectByParticipant(participantId);
                    return poList.stream().map(converter::toEntity).collect(Collectors.toList());
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(
                        list -> log.debug("根据参与人查询日历事件 - participantId: {}, count: {}", participantId, list.size()))
                .doOnError(error -> log.error("根据参与人查询日历事件失败 - participantId: {}", participantId, error));
    }

    @Override
    public Mono<List<CalendarEvent>> findByMeetingRoom(
            Long tenantId, String meetingRoomId, LocalDateTime startTime, LocalDateTime endTime) {
        return Mono.fromCallable(() -> {
                    List<CalendarEventPO> poList =
                            mapper.selectByMeetingRoom(tenantId, meetingRoomId, startTime, endTime);
                    return poList.stream().map(converter::toEntity).collect(Collectors.toList());
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(list -> log.debug(
                        "根据会议室查询日历事件 - tenantId: {}, meetingRoomId: {}, count: {}",
                        tenantId,
                        meetingRoomId,
                        list.size()))
                .doOnError(error -> log.error("根据会议室查询日历事件失败 - meetingRoomId: {}", meetingRoomId, error));
    }

    @Override
    public Mono<CalendarEvent> update(CalendarEvent event) {
        return Mono.fromCallable(() -> {
                    CalendarEventPO po = converter.toPO(event);
                    // 显式刷新 update_time：填充器只填 null（strict 语义），若 PO 从实体带回了
                    // 旧的 updateTime，更新语句会写入旧值，导致 update_time 冻结。
                    po.setUpdateTime(LocalDateTime.now());
                    mapper.updateById(po);
                    return converter.toEntity(po);
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(updatedEvent -> log.debug("日历事件更新成功 - id: {}", updatedEvent.getId()))
                .doOnError(error -> log.error("日历事件更新失败 - id: {}", event.getId(), error));
    }

    @Override
    public Mono<Boolean> deleteById(Long id) {
        // 逻辑删除由 MyBatis-Plus 改写为 UPDATE ... SET update_time=?, deleted=1；
        // @TableLogic 字段不会被 entity 更新路径带上，故此处用 UpdateWrapper 直接写列，
        // 并显式带上 update_time，保证删除动作刷新审计时间。
        return Mono.fromCallable(() -> {
                    // 用 UpdateWrapper.set 直接写列：@TableLogic 字段不会被 entity 更新路径带上，
                    // update_time 也在此显式刷新。
                    com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<CalendarEventPO> wrapper =
                            new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
                    wrapper.eq("id", id)
                            .eq("deleted", 0)
                            .set("deleted", 1)
                            .set("update_time", java.time.LocalDateTime.now());
                    return mapper.update(null, wrapper) > 0;
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(deleted -> log.debug("日历事件删除 - id: {}, deleted: {}", id, deleted))
                .doOnError(error -> log.error("日历事件删除失败 - id: {}", id, error));
    }
}
