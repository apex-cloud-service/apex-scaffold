package com.apex.scaffold.application.office;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.office.calendar.*;
import com.apex.scaffold.infrastructure.office.client.CalendarClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 办公平台日历应用服务
 *
 * <p>负责日历事件的用例编排和业务逻辑协调，是Application层的核心服务。
 * 主要职责包括：
 * <ul>
 *   <li>协调领域对象和基础设施组件完成日历事件操作</li>
 *   <li>管理事务边界，确保数据一致性</li>
 *   <li>处理跨平台日历的统一逻辑</li>
 *   <li>维护租户隔离和安全控制</li>
 * </ul>
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeCalendarService {

    private final CalendarEventRepository calendarEventRepository;
    private final Map<String, CalendarClient> calendarClients;

    /**
     * 创建日历事件
     *
     * <p>在指定的办公平台创建日历事件，并在本地数据库保存事件记录。
     *
     * @param platform 办公平台类型（feishu/dingtalk/wecom）
     * @param title 事件标题
     * @param description 事件描述
     * @param eventType 事件类型
     * @param organizerId 组织者ID
     * @param organizerName 组织者姓名
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param isAllDay 是否全天事件
     * @param location 事件地点
     * @param participantIds 参与人ID数组
     * @param participantNames 参与人姓名数组
     * @param meetingRoomId 会议室ID
     * @param reminderMinutes 提醒时间（分钟数）
     * @return 创建成功的日历事件
     */
    public Mono<CalendarEvent> createEvent(
            String platform,
            String title,
            String description,
            CalendarEventType eventType,
            String organizerId,
            String organizerName,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Boolean isAllDay,
            String location,
            String[] participantIds,
            String[] participantNames,
            String meetingRoomId,
            Integer reminderMinutes
    ) {
        log.info("创建日历事件 - platform: {}, title: {}, organizerId: {}, startTime: {}, endTime: {}",
                platform, title, organizerId, startTime, endTime);

        CalendarClient client = getCalendarClient(platform);

        return client.createEvent(organizerId, title, description, startTime, endTime,
                        participantIds, location, reminderMinutes)
                .flatMap(platformEventId -> {
                    CalendarEvent event = CalendarEvent.builder()
                            .tenantId(TenantContext.currentTenantId())
                            .platform(platform)
                            .platformEventId(platformEventId)
                            .title(title)
                            .description(description)
                            .eventType(eventType)
                            .organizerId(organizerId)
                            .organizerName(organizerName)
                            .startTime(startTime)
                            .endTime(endTime)
                            .isAllDay(isAllDay)
                            .location(location)
                            .participantIds(convertToJson(participantIds))
                            .participantNames(convertToJson(participantNames))
                            .meetingRoomId(meetingRoomId)
                            .reminderMinutes(reminderMinutes)
                            .isRecurring(false)
                            .status("CONFIRMED")
                            .build();

                    return calendarEventRepository.save(event);
                })
                .doOnSuccess(event -> log.info("日历事件创建成功 - eventId: {}, platformEventId: {}",
                        event.getId(), event.getPlatformEventId()))
                .doOnError(error -> log.error("日历事件创建失败 - title: {}", title, error));
    }

    /**
     * 查询日历事件详情
     *
     * <p>根据事件ID查询日历事件的详细信息。
     *
     * @param eventId 日历事件ID
     * @return 日历事件详情
     */
    public Mono<CalendarEvent> getEventDetail(Long eventId) {
        log.info("查询日历事件详情 - eventId: {}", eventId);

        return calendarEventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new RuntimeException("日历事件不存在: " + eventId)))
                .flatMap(event -> {
                    CalendarClient client = getCalendarClient(event.getPlatform());
                    return client.getEventDetail(event.getPlatformEventId())
                            .map(detail -> {
                                // 可以根据平台返回的最新信息更新事件
                                return event;
                            });
                })
                .doOnSuccess(event -> log.info("日历事件详情查询成功 - eventId: {}", eventId))
                .doOnError(error -> log.error("日历事件详情查询失败 - eventId: {}", eventId, error));
    }

    /**
     * 更新日历事件
     *
     * <p>更新日历事件的基本信息。
     *
     * @param eventId 日历事件ID
     * @param title 事件标题
     * @param description 事件描述
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param participantIds 参与人ID数组
     * @param location 事件地点
     * @return 更新是否成功
     */
    public Mono<Boolean> updateEvent(
            Long eventId,
            String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String[] participantIds,
            String location
    ) {
        log.info("更新日历事件 - eventId: {}, title: {}", eventId, title);

        return calendarEventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new RuntimeException("日历事件不存在: " + eventId)))
                .flatMap(event -> {
                    if (event.isCancelled()) {
                        return Mono.error(new RuntimeException("已取消的事件无法更新"));
                    }

                    CalendarClient client = getCalendarClient(event.getPlatform());
                    return client.updateEvent(
                            event.getPlatformEventId(),
                            title,
                            description,
                            startTime,
                            endTime,
                            participantIds,
                            location
                    ).flatMap(success -> {
                        if (success) {
                            event.setTitle(title);
                            event.setDescription(description);
                            event.updateTime(startTime, endTime);
                            event.setParticipantIds(convertToJson(participantIds));
                            event.setLocation(location);

                            return calendarEventRepository.update(event)
                                    .thenReturn(true);
                        }
                        return Mono.just(false);
                    });
                })
                .doOnSuccess(success -> log.info("日历事件更新成功 - eventId: {}, result: {}", eventId, success))
                .doOnError(error -> log.error("日历事件更新失败 - eventId: {}", eventId, error));
    }

    /**
     * 取消日历事件
     *
     * <p>取消已创建的日历事件。
     *
     * @param eventId 日历事件ID
     * @param organizerId 组织者ID
     * @return 取消操作是否成功
     */
    public Mono<Boolean> cancelEvent(Long eventId, String organizerId) {
        log.info("取消日历事件 - eventId: {}, organizerId: {}", eventId, organizerId);

        return calendarEventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new RuntimeException("日历事件不存在: " + eventId)))
                .flatMap(event -> {
                    if (!event.getOrganizerId().equals(organizerId)) {
                        return Mono.error(new RuntimeException("只有组织者可以取消事件"));
                    }

                    if (event.isCancelled()) {
                        return Mono.error(new RuntimeException("事件已经被取消"));
                    }

                    CalendarClient client = getCalendarClient(event.getPlatform());
                    return client.cancelEvent(event.getPlatformEventId(), organizerId)
                            .flatMap(success -> {
                                if (success) {
                                    event.cancel();
                                    return calendarEventRepository.update(event)
                                            .thenReturn(true);
                                }
                                return Mono.just(false);
                            });
                })
                .doOnSuccess(success -> log.info("日历事件取消成功 - eventId: {}, result: {}", eventId, success))
                .doOnError(error -> log.error("日历事件取消失败 - eventId: {}", eventId, error));
    }

    /**
     * 删除日历事件
     *
     * <p>删除日历事件（逻辑删除）。
     *
     * @param eventId 日历事件ID
     * @param organizerId 组织者ID
     * @return 删除操作是否成功
     */
    public Mono<Boolean> deleteEvent(Long eventId, String organizerId) {
        log.info("删除日历事件 - eventId: {}, organizerId: {}", eventId, organizerId);

        return calendarEventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new RuntimeException("日历事件不存在: " + eventId)))
                .flatMap(event -> {
                    if (!event.getOrganizerId().equals(organizerId)) {
                        return Mono.error(new RuntimeException("只有组织者可以删除事件"));
                    }

                    CalendarClient client = getCalendarClient(event.getPlatform());
                    return client.deleteEvent(event.getPlatformEventId(), organizerId)
                            .flatMap(success -> {
                                if (success) {
                                    return calendarEventRepository.deleteById(eventId);
                                }
                                return Mono.just(false);
                            });
                })
                .doOnSuccess(success -> log.info("日历事件删除成功 - eventId: {}, result: {}", eventId, success))
                .doOnError(error -> log.error("日历事件删除失败 - eventId: {}", eventId, error));
    }

    /**
     * 查询指定时间范围内的事件列表
     *
     * <p>查询指定时间范围内的所有日历事件。
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 日历事件列表
     */
    public Mono<List<CalendarEvent>> listEvents(LocalDateTime startTime, LocalDateTime endTime) {
        log.info("查询日历事件列表 - startTime: {}, endTime: {}", startTime, endTime);

        return calendarEventRepository.findByTimeRange(startTime, endTime)
                .doOnSuccess(list -> log.info("日历事件列表查询成功 - count: {}", list.size()))
                .doOnError(error -> log.error("日历事件列表查询失败", error));
    }

    /**
     * 查询我组织的事件列表
     *
     * <p>查询指定组织者组织的所有日历事件。
     *
     * @param organizerId 组织者ID
     * @return 日历事件列表
     */
    public Mono<List<CalendarEvent>> getMyOrganizedEvents(String organizerId) {
        log.info("查询我组织的事件列表 - organizerId: {}", organizerId);

        return calendarEventRepository.findByOrganizer(organizerId)
                .doOnSuccess(list -> log.info("我组织的事件列表查询成功 - organizerId: {}, count: {}",
                        organizerId, list.size()))
                .doOnError(error -> log.error("我组织的事件列表查询失败 - organizerId: {}", organizerId, error));
    }

    /**
     * 查询我参与的事件列表
     *
     * <p>查询指定参与人参与的所有日历事件。
     *
     * @param participantId 参与人ID
     * @return 日历事件列表
     */
    public Mono<List<CalendarEvent>> getMyParticipatedEvents(String participantId) {
        log.info("查询我参与的事件列表 - participantId: {}", participantId);

        return calendarEventRepository.findByParticipant(participantId)
                .doOnSuccess(list -> log.info("我参与的事件列表查询成功 - participantId: {}, count: {}",
                        participantId, list.size()))
                .doOnError(error -> log.error("我参与的事件列表查询失败 - participantId: {}", participantId, error));
    }

    /**
     * 检查会议室可用性
     *
     * <p>检查指定会议室在指定时间范围内是否可用。
     *
     * @param meetingRoomId 会议室ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 是否可用
     */
    public Mono<Boolean> checkMeetingRoomAvailability(String meetingRoomId, LocalDateTime startTime, LocalDateTime endTime) {
        log.info("检查会议室可用性 - meetingRoomId: {}, startTime: {}, endTime: {}",
                meetingRoomId, startTime, endTime);

        return calendarEventRepository.findByMeetingRoom(meetingRoomId, startTime, endTime)
                .map(events -> events.isEmpty())
                .doOnSuccess(available -> log.info("会议室可用性检查完成 - meetingRoomId: {}, available: {}",
                        meetingRoomId, available))
                .doOnError(error -> log.error("会议室可用性检查失败 - meetingRoomId: {}", meetingRoomId, error));
    }

    /**
     * 根据平台类型获取日历客户端
     *
     * @param platform 平台类型
     * @return 日历客户端实例
     */
    private CalendarClient getCalendarClient(String platform) {
        CalendarClient client = calendarClients.get(platform + "CalendarClient");
        if (client == null) {
            throw new RuntimeException("不支持的办公平台: " + platform);
        }
        return client;
    }

    /**
     * 将对象转换为JSON字符串
     *
     * @param obj 对象
     * @return JSON字符串
     */
    private String convertToJson(Object obj) {
        // 实际实现中应使用ObjectMapper
        // 这里简化处理
        return obj != null ? obj.toString() : null;
    }
}
