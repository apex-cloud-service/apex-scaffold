package com.apex.scaffold.officeservice.interfaces.handler.office;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.office.application.OfficeCalendarService;
import com.apex.scaffold.office.domain.calendar.CalendarEventType;
import com.apex.scaffold.officeservice.interfaces.assembler.office.CalendarEventAssembler;
import com.apex.scaffold.officeservice.interfaces.dto.office.calendar.CalendarEventResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.calendar.CreateCalendarEventRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.calendar.UpdateCalendarEventRequest;
import com.apex.scaffold.officeservice.interfaces.security.AuthenticatedUser;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

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
@Tag(name = "办公平台日历管理", description = "办公平台日历事件创建、查询、更新、删除等相关接口")
public class OfficeCalendarHandler {

    private final OfficeCalendarService calendarService;
    private final CalendarEventAssembler assembler;
    private final RequestHandlerUtil requestHandlerUtil;

    /** 日历事件详情的统一响应。 */
    @Schema(name = "OfficeCalendarEventResult")
    public static class CalendarEventResult extends Result<CalendarEventResponse> {}

    /** 日历事件列表的统一响应。 */
    @Schema(name = "OfficeCalendarEventListResult")
    public static class CalendarEventListResult extends Result<List<CalendarEventResponse>> {}

    /** 日历事件写操作的布尔结果。 */
    @Schema(name = "OfficeCalendarBooleanResult")
    public static class CalendarBooleanResult extends Result<Boolean> {}

    /**
     * 创建日历事件
     *
     * <p>在指定的办公平台创建一个新的日历事件，支持设置标题、时间、参与者等信息。
     *
     * @param request ServerRequest对象，包含请求体和参数
     * @return Mono&lt;ServerResponse&gt; 包含创建成功的事件信息
     */
    @RequirePermission("office:calendar:create")
    @Operation(operationId = "createCalendarEvent", summary = "创建日历事件", description = "在指定办公平台创建日历事件，支持设置标题、时间、参与者和提醒")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "日历事件创建请求信息",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateCalendarEventRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CalendarEventResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "创建失败",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeCalendar",
            type = OperationLog.OperationType.INSERT,
            description = "createCalendarEvent",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> createCalendarEvent(ServerRequest request) {
        log.info("创建日历事件请求");

        return requestHandlerUtil
                .validateAndMap(request, CreateCalendarEventRequest.class)
                .flatMap(req -> getCurrentUserId().flatMap(userId -> {
                    CalendarEventType eventType = CalendarEventType.valueOf(req.getEventType());

                    return calendarService.createEvent(
                            req.getPlatform(),
                            req.getTitle(),
                            req.getDescription(),
                            eventType,
                            req.getOrganizerId() != null ? req.getOrganizerId() : userId,
                            req.getOrganizerName(),
                            req.getStartTime(),
                            req.getEndTime(),
                            req.getIsAllDay(),
                            req.getLocation(),
                            req.getAttendeeIds() != null ? req.getAttendeeIds().toArray(new String[0]) : new String[0],
                            req.getAttendeeNames() != null
                                    ? req.getAttendeeNames().toArray(new String[0])
                                    : new String[0],
                            null, // 会议室 ID 暂不接线，保留空值由后续会议模块补齐
                            req.getReminderMinutes());
                }))
                .map(assembler::toResponse)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询日历事件详情
     *
     * <p>根据事件ID查询日历事件的详细信息。
     *
     * @param request ServerRequest对象，路径参数包含eventId
     * @return Mono&lt;ServerResponse&gt; 包含事件详细信息
     */
    @RequirePermission("office:calendar:query")
    @Operation(operationId = "getCalendarEventDetail", summary = "查询日历事件详情", description = "根据事件ID查询日历事件的详细信息")
    @Parameters({
        @Parameter(
                name = "eventId",
                in = ParameterIn.PATH,
                required = true,
                description = "事件ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CalendarEventResult.class))),
        @ApiResponse(
                responseCode = "404",
                description = "事件不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeCalendar",
            type = OperationLog.OperationType.SELECT,
            description = "getCalendarEventDetail",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getCalendarEventDetail(ServerRequest request) {
        String eventIdStr = request.pathVariable("eventId");
        Long eventId = Long.parseLong(eventIdStr);
        log.info("查询日历事件详情 - eventId: {}", eventId);

        return calendarService
                .getEventDetail(eventId)
                .map(assembler::toResponse)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 更新日历事件
     *
     * <p>更新指定日历事件的信息，包括标题、时间、参与者等。
     *
     * @param request ServerRequest对象，包含eventId和更新内容
     * @return 包含更新操作布尔结果的统一响应
     */
    @RequirePermission("office:calendar:update")
    @Operation(
            operationId = "updateCalendarEvent",
            summary = "更新日历事件",
            description = "更新指定日历事件的标题、描述、时间、参与者和地点，data为布尔结果")
    @Parameters({
        @Parameter(
                name = "eventId",
                in = ParameterIn.PATH,
                required = true,
                description = "事件ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "日历事件更新请求信息",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateCalendarEventRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CalendarBooleanResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "404",
                description = "事件不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeCalendar",
            type = OperationLog.OperationType.UPDATE,
            description = "updateCalendarEvent",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> updateCalendarEvent(ServerRequest request) {
        String eventIdStr = request.pathVariable("eventId");
        Long eventId = Long.parseLong(eventIdStr);
        log.info("更新日历事件 - eventId: {}", eventId);

        return requestHandlerUtil
                .validateAndMap(request, UpdateCalendarEventRequest.class)
                .flatMap(req -> calendarService.updateEvent(
                        eventId,
                        req.getTitle(),
                        req.getDescription(),
                        req.getStartTime(),
                        req.getEndTime(),
                        req.getAttendeeIds() != null ? req.getAttendeeIds().toArray(new String[0]) : new String[0],
                        req.getLocation(),
                        req.getIsAllDay()))
                .flatMap(success -> requestHandlerUtil.ok(Result.success(success)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 删除日历事件
     *
     * <p>删除指定的日历事件。
     *
     * @param request ServerRequest对象，路径参数包含eventId
     * @return Mono&lt;ServerResponse&gt; 包含删除结果
     */
    @RequirePermission("office:calendar:delete")
    @Operation(operationId = "deleteCalendarEvent", summary = "删除日历事件", description = "删除指定的日历事件，data为布尔结果")
    @Parameters({
        @Parameter(
                name = "eventId",
                in = ParameterIn.PATH,
                required = true,
                description = "事件ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "删除成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CalendarBooleanResult.class))),
        @ApiResponse(
                responseCode = "404",
                description = "事件不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeCalendar",
            type = OperationLog.OperationType.DELETE,
            description = "deleteCalendarEvent",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> deleteCalendarEvent(ServerRequest request) {
        String eventIdStr = request.pathVariable("eventId");
        Long eventId = Long.parseLong(eventIdStr);
        log.info("删除日历事件 - eventId: {}", eventId);

        return getCurrentUserId()
                .flatMap(userId -> calendarService.deleteEvent(eventId, userId))
                .flatMap(success -> requestHandlerUtil.ok(Result.success(success)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询时间范围内的日历事件
     *
     * <p>查询指定时间范围内的所有日历事件。
     *
     * @param request ServerRequest对象，查询参数包含不带时区的startTime、endTime
     * @return Mono&lt;ServerResponse&gt; 包含事件列表
     */
    @RequirePermission("office:calendar:list")
    @Operation(operationId = "getCalendarEventsByTimeRange", summary = "查询时间范围内的日历事件", description = "查询指定时间范围内的所有日历事件")
    @Parameters({
        @Parameter(
                name = "startTime",
                in = ParameterIn.QUERY,
                required = true,
                description = "开始时间（ISO格式）",
                schema = @Schema(type = "string", format = "date-time")),
        @Parameter(
                name = "endTime",
                in = ParameterIn.QUERY,
                required = true,
                description = "结束时间（ISO格式）",
                schema = @Schema(type = "string", format = "date-time"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CalendarEventListResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeCalendar",
            type = OperationLog.OperationType.SELECT,
            description = "getCalendarEventsByTimeRange",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getCalendarEventsByTimeRange(ServerRequest request) {
        String startTimeStr = request.queryParam("startTime").orElse("");
        String endTimeStr = request.queryParam("endTime").orElse("");

        log.info("查询时间范围内的日历事件 - startTime: {}, endTime: {}", startTimeStr, endTimeStr);

        LocalDateTime startTime = LocalDateTime.parse(startTimeStr);
        LocalDateTime endTime = LocalDateTime.parse(endTimeStr);

        return calendarService
                .listEvents(startTime, endTime)
                .map(events -> events.stream().map(assembler::toResponse).collect(java.util.stream.Collectors.toList()))
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询我的日历事件
     *
     * <p>按请求中的userId查询该用户组织的日历事件，未传时使用空字符串。
     *
     * @param request ServerRequest对象，查询参数包含userId
     * @return Mono&lt;ServerResponse&gt; 包含事件列表
     */
    @RequirePermission("office:calendar:my")
    @Operation(
            operationId = "getMyCalendarEvents",
            summary = "查询用户组织的日历事件",
            description = "按查询参数userId查询该用户组织的事件；省略时使用空字符串")
    @Parameters({
        @Parameter(
                name = "userId",
                in = ParameterIn.QUERY,
                required = true,
                description = "用户ID",
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CalendarEventListResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeCalendar",
            type = OperationLog.OperationType.SELECT,
            description = "getMyCalendarEvents",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getMyCalendarEvents(ServerRequest request) {
        String userId = request.queryParam("userId").orElse("");
        log.info("查询我的日历事件 - userId: {}", userId);

        return calendarService
                .getMyOrganizedEvents(userId)
                .map(events -> events.stream().map(assembler::toResponse).collect(java.util.stream.Collectors.toList()))
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询即将到来的日历事件
     *
     * <p>查询指定用户即将到来的日历事件（未来7天内）。
     *
     * @param request ServerRequest对象，查询参数包含userId
     * @return Mono&lt;ServerResponse&gt; 包含事件列表
     */
    @RequirePermission("office:calendar:my")
    @Operation(
            operationId = "getUpcomingEvents",
            summary = "查询即将到来的日历事件",
            description = "按userId筛选未来7天组织或参与的事件；省略时使用空字符串")
    @Parameters({
        @Parameter(
                name = "userId",
                in = ParameterIn.QUERY,
                required = true,
                description = "用户ID",
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CalendarEventListResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeCalendar",
            type = OperationLog.OperationType.SELECT,
            description = "getUpcomingEvents",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getUpcomingEvents(ServerRequest request) {
        String userId = request.queryParam("userId").orElse("");
        log.info("查询即将到来的事件 - userId: {}", userId);

        // 查询未来7天的事件
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime futureTime = now.plusDays(7);

        return calendarService
                .listEvents(now, futureTime)
                .map(events -> events.stream()
                        .filter(event -> event.getOrganizerId().equals(userId)
                                || (event.getParticipantIds() != null
                                        && event.getParticipantIds().contains(userId)))
                        .map(assembler::toResponse)
                        .collect(java.util.stream.Collectors.toList()))
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 获取当前登录用户ID
     *
     * @return 当前用户ID
     */
    private Mono<String> getCurrentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .ofType(AuthenticatedUser.class)
                .map(user -> String.valueOf(user.userId()))
                .switchIfEmpty(Mono.error(new IllegalStateException("当前请求未包含有效认证用户")));
    }
}
