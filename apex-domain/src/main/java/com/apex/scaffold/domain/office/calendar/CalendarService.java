package com.apex.scaffold.domain.office.calendar;

import java.time.LocalDateTime;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 日历服务领域接口
 *
 * <p>定义与办公平台日历系统交互的领域服务接口。
 * 负责事件创建、查询、更新、删除和会议室预订等操作。
 *
 * @author Apex Team
 * @since 1.3.0
 */
public interface CalendarService {

    /**
     * 创建日历事件
     *
     * @param tenantId 租户ID
     * @param appId 应用ID
     * @param event 事件信息
     * @return 创建的事件ID
     */
    Mono<String> createEvent(Long tenantId, Long appId, CalendarEvent event);

    /**
     * 查询日历事件详情
     *
     * @param tenantId 租户ID
     * @param appId 应用ID
     * @param eventId 事件ID
     * @return 事件详情
     */
    Mono<CalendarEvent> getEvent(Long tenantId, Long appId, String eventId);

    /**
     * 更新日历事件
     *
     * @param tenantId 租户ID
     * @param appId 应用ID
     * @param event 事件信息
     * @return 是否更新成功
     */
    Mono<Boolean> updateEvent(Long tenantId, Long appId, CalendarEvent event);

    /**
     * 删除日历事件
     *
     * @param tenantId 租户ID
     * @param appId 应用ID
     * @param eventId 事件ID
     * @return 是否删除成功
     */
    Mono<Boolean> deleteEvent(Long tenantId, Long appId, String eventId);

    /**
     * 查询用户的日历事件列表
     *
     * @param tenantId 租户ID
     * @param appId 应用ID
     * @param userId 用户ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 事件列表
     */
    Flux<CalendarEvent> listUserEvents(
            Long tenantId, Long appId, String userId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询会议室空闲时段
     *
     * @param tenantId 租户ID
     * @param appId 应用ID
     * @param roomId 会议室ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 空闲时段列表
     */
    Flux<Map<String, Object>> getFreeBusySlots(
            Long tenantId, Long appId, String roomId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 回复会议邀请
     *
     * @param tenantId 租户ID
     * @param appId 应用ID
     * @param eventId 事件ID
     * @param userId 用户ID
     * @param status 回复状态（ACCEPT/DECLINE/TENTATIVE）
     * @return 是否回复成功
     */
    Mono<Boolean> respondToInvitation(Long tenantId, Long appId, String eventId, String userId, String status);
}
