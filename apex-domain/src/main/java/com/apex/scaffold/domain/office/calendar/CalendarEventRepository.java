package com.apex.scaffold.domain.office.calendar;

import java.time.LocalDateTime;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 日历事件仓储接口
 *
 * <p>定义日历事件聚合根的持久化操作，遵循DDD仓储模式。
 * 所有方法返回Mono类型以支持响应式编程，实现类需要使用BlockingIoSchedulers隔离阻塞I/O操作。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
public interface CalendarEventRepository {

    /**
     * 保存日历事件
     *
     * @param event 日历事件聚合根
     * @return 保存后的日历事件（包含生成的ID）
     */
    Mono<CalendarEvent> save(CalendarEvent event);

    /**
     * 根据ID查询日历事件
     *
     * @param id 日历事件ID
     * @return 日历事件，如果不存在则返回空Mono
     */
    Mono<CalendarEvent> findById(Long id);

    /**
     * 根据平台和平台事件ID查询日历事件
     *
     * @param platform 办公平台类型
     * @param platformEventId 平台中的事件ID
     * @return 日历事件，如果不存在则返回空Mono
     */
    Mono<CalendarEvent> findByPlatformAndEventId(String platform, String platformEventId);

    /**
     * 查询指定时间范围内的日历事件
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 日历事件列表
     */
    Mono<List<CalendarEvent>> findByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询指定组织者的日历事件
     *
     * @param organizerId 组织者ID
     * @return 日历事件列表
     */
    Mono<List<CalendarEvent>> findByOrganizer(String organizerId);

    /**
     * 查询指定参与人的日历事件
     *
     * @param participantId 参与人ID
     * @return 日历事件列表
     */
    Mono<List<CalendarEvent>> findByParticipant(String participantId);

    /**
     * 查询指定会议室的日历事件
     *
     * @param meetingRoomId 会议室ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 日历事件列表
     */
    Mono<List<CalendarEvent>> findByMeetingRoom(String meetingRoomId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 更新日历事件
     *
     * @param event 日历事件聚合根
     * @return 更新后的日历事件
     */
    Mono<CalendarEvent> update(CalendarEvent event);

    /**
     * 删除日历事件（逻辑删除）
     *
     * @param id 日历事件ID
     * @return 删除成功返回true，否则返回false
     */
    Mono<Boolean> deleteById(Long id);
}
