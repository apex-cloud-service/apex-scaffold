package com.apex.scaffold.interfaces.handler.office;

import com.apex.scaffold.application.office.OfficeCalendarService;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.domain.office.calendar.CalendarEventType;
import com.apex.scaffold.interfaces.assembler.office.CalendarEventAssembler;
import com.apex.scaffold.interfaces.dto.office.calendar.CalendarEventResponse;
import com.apex.scaffold.interfaces.dto.office.calendar.CreateCalendarEventRequest;
import com.apex.scaffold.interfaces.dto.office.calendar.UpdateCalendarEventRequest;
import com.apex.scaffold.interfaces.util.RequestHandlerUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 办公平台日历事件处理器
 *
 * <p>处理日历事件相关的HTTP请求，包括创建、查询、更新、删除日历事件等操作。
 * 使用函数式编程风格，每个方法对应一个具体的业务用例。
 *
 * <p>主要功能：
 * <ul>
 *   <li>创建日历事件 - 在指定办公平台创建日历事件</li>
 *   <li>查询事件详情 - 根据事件ID查询详细信息</li>
 *   <li>更新事件信息 - 修改事件的标题、时间、参与者等</li>
 *   <li>删除日历事件 - 删除指定的日历事件</li>
 *   <li>查询时间范围内的事件 - 按开始时间和结束时间查询</li>
 *   <li>查询我的日历事件 - 查询当前用户组织或参与的事件</li>
 * </ul>
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfficeCalendarHandler {

    private final OfficeCalendarService calendarService;
    private final CalendarEventAssembler assembler;

    /**
     * 创建日历事件
     *
     * <p>在指定的办公平台创建一个新的日历事件，支持设置标题、时间、参与者等信息。
     *
     * @param request ServerRequest对象，包含请求体和参数
     * @return Mono&lt;ServerResponse&gt; 包含创建成功的事件信息
     */
    public Mono<ServerResponse> createCalendarEvent(ServerRequest request) {
        log.info("创建日历事件请求");
        
        return RequestHandlerUtil.validateAndMap(request, CreateCalendarEventRequest.class)
                .flatMap(req -> {
                    CalendarEventType eventType = CalendarEventType.valueOf(req.getEventType());
                    
                    return calendarService.createCalendarEvent(
                            req.getPlatform(),
                            req.getTitle(),
                            req.getDescription(),
                            req.getStartTime(),
                            req.getEndTime(),
                            req.getIsAllDay() != null && req.getIsAllDay(),
                            eventType,
                            req.getLocation(),
                            req.getAttendeeIds(),
                            req.getAttendeeNames(),
                            req.getReminderMinutes(),
                            req.getIsRecurring() != null && req.getIsRecurring(),
                            req.getRecurrenceRule(),
                            req.getOrganizerId(),
                            req.getOrganizerName()
                    );
                })
                .map(assembler::toResponse)
                .flatMap(response -> RequestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 查询日历事件详情
     *
     * <p>根据事件ID查询日历事件的详细信息。
     *
     * @param request ServerRequest对象，路径参数包含eventId
     * @return Mono&lt;ServerResponse&gt; 包含事件详细信息
     */
    public Mono<ServerResponse> getCalendarEventDetail(ServerRequest request) {
        String eventIdStr = request.pathVariable("eventId");
        Long eventId = Long.parseLong(eventIdStr);
        log.info("查询日历事件详情 - eventId: {}", eventId);

        return calendarService.getCalendarEventDetail(eventId)
                .map(assembler::toResponse)
                .flatMap(response -> RequestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 更新日历事件
     *
     * <p>更新指定日历事件的信息，包括标题、时间、参与者等。
     *
     * @param request ServerRequest对象，包含eventId和更新内容
     * @return Mono&lt;ServerResponse&gt; 包含更新后的事件信息
     */
    public Mono<ServerResponse> updateCalendarEvent(ServerRequest request) {
        String eventIdStr = request.pathVariable("eventId");
        Long eventId = Long.parseLong(eventIdStr);
        log.info("更新日历事件 - eventId: {}", eventId);

        return RequestHandlerUtil.validateAndMap(request, UpdateCalendarEventRequest.class)
                .flatMap(req -> calendarService.updateCalendarEvent(
                        eventId,
                        req.getTitle(),
                        req.getDescription(),
                        req.getStartTime(),
                        req.getEndTime(),
                        req.getIsAllDay(),
                        req.getLocation(),
                        req.getAttendeeIds(),
                        req.getAttendeeNames(),
                        req.getReminderMinutes(),
                        req.getRecurrenceRule()
                ))
                .map(assembler::toResponse)
                .flatMap(response -> RequestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 删除日历事件
     *
     * <p>删除指定的日历事件。
     *
     * @param request ServerRequest对象，路径参数包含eventId
     * @return Mono&lt;ServerResponse&gt; 包含删除结果
     */
    public Mono<ServerResponse> deleteCalendarEvent(ServerRequest request) {
        String eventIdStr = request.pathVariable("eventId");
        Long eventId = Long.parseLong(eventIdStr);
        log.info("删除日历事件 - eventId: {}", eventId);

        return calendarService.deleteCalendarEvent(eventId)
                .flatMap(success -> RequestHandlerUtil.ok(Result.success(success)))
                .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 查询时间范围内的日历事件
     *
     * <p>查询指定时间范围内的所有日历事件。
     *
     * @param request ServerRequest对象，查询参数包含organizerId、startTime、endTime
     * @return Mono&lt;ServerResponse&gt; 包含事件列表
     */
    public Mono<ServerResponse> getCalendarEventsByTimeRange(ServerRequest request) {
        String organizerId = request.queryParam("organizerId").orElse("");
        String startTimeStr = request.queryParam("startTime").orElse("");
        String endTimeStr = request.queryParam("endTime").orElse("");
        
        log.info("查询时间范围内的日历事件 - organizerId: {}, startTime: {}, endTime: {}", 
                organizerId, startTimeStr, endTimeStr);

        LocalDateTime startTime = LocalDateTime.parse(startTimeStr);
        LocalDateTime endTime = LocalDateTime.parse(endTimeStr);

        return calendarService.getCalendarEventsByTimeRange(organizerId, startTime, endTime)
                .map(assembler::toResponseList)
                .flatMap(response -> RequestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 查询我的日历事件
     *
     * <p>查询当前用户组织或参与的所有日历事件。
     *
     * @param request ServerRequest对象，查询参数包含userId
     * @return Mono&lt;ServerResponse&gt; 包含事件列表
     */
    public Mono<ServerResponse> getMyCalendarEvents(ServerRequest request) {
        String userId = request.queryParam("userId").orElse("");
        log.info("查询我的日历事件 - userId: {}", userId);

        return calendarService.getMyCalendarEvents(userId)
                .map(assembler::toResponseList)
                .flatMap(response -> RequestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(RequestHandlerUtil::handleError);
    }

    /**
     * 查询即将到来的日历事件
     *
     * <p>查询指定用户即将到来的日历事件（未来7天内）。
     *
     * @param request ServerRequest对象，查询参数包含userId
     * @return Mono&lt;ServerResponse&gt; 包含事件列表
     */
    public Mono<ServerResponse> getUpcomingEvents(ServerRequest request) {
        String userId = request.queryParam("userId").orElse("");
        log.info("查询即将到来的日历事件 - userId: {}", userId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = now.plusDays(7);

        return calendarService.getCalendarEventsByTimeRange(userId, now, endTime)
                .map(assembler::toResponseList)
                .flatMap(response -> RequestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(RequestHandlerUtil::handleError);
    }
}
