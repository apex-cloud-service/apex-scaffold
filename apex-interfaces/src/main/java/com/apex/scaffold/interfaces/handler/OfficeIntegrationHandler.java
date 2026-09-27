package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.ApprovalProcessDTO;
import com.apex.scaffold.application.dto.CalendarEventDTO;
import com.apex.scaffold.application.service.OfficeIntegrationService;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.util.MessageUtils;
import com.apex.scaffold.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 办公集成处理器（函数式端点）
 *
 * <p>提供与第三方办公平台（如飞书、钉钉、企业微信等）的集成接口，
 * 包括审批流程管理和日历事件管理功能。支持查询审批列表、处理待审批事项、
 * 管理日历事件等办公协同场景。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>审批管理：查询审批列表、获取审批详情、处理待审批事项</li>
 *   <li>日历管理：查询日历事件、创建日历事件、更新日历事件</li>
 *   <li>数据同步：从第三方平台同步审批和日历数据</li>
 *   <li>状态跟踪：跟踪审批状态变更和日历事件提醒</li>
 * </ul>
 *
 * <h2>集成平台</h2>
 * <ul>
 *   <li>飞书（Lark）：审批流程、日历事件</li>
 *   <li>钉钉（DingTalk）：待办审批、日程管理</li>
 *   <li>企业微信（WeChat Work）：审批应用、日历应用</li>
 * </ul>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /api/v1/office/approvals - 获取审批列表</li>
 *   <li>GET /api/v1/office/approvals/{id} - 获取审批详情</li>
 *   <li>GET /api/v1/office/approvals/pending - 获取待审批列表</li>
 *   <li>GET /api/v1/office/calendar/events - 获取日历事件列表</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see com.apex.scaffold.application.service.OfficeIntegrationService
 */
@Slf4j
@Component
@Tag(name = "办公集成管理", description = "审批和日历集成接口")
public class OfficeIntegrationHandler extends BaseHandler {

    private final OfficeIntegrationService officeService;
    private final UserService userService;
    private final RequestHandlerUtil requestHandlerUtil;

    public OfficeIntegrationHandler(
            OfficeIntegrationService officeService,
            UserService userService,
            RequestHandlerUtil requestHandlerUtil,
            MessageUtils messageUtils) {
        super(messageUtils);
        this.officeService = officeService;
        this.userService = userService;
        this.requestHandlerUtil = requestHandlerUtil;
    }

    /**
     * 分页获取当前用户的审批流程列表。
     *
     * <p>根据当前登录用户 ID 分页查询审批流程，可按审批状态筛选，页码默认 1、每页默认 10 条；
     * 查询失败时返回统一错误响应。</p>
     *
     * @param request 服务器请求，查询参数 pageNum（页码，默认 1）、pageSize（每页数量，默认 10）、status（审批状态）
     * @return 包含审批列表分页数据的 {@code Result} 响应（Mono，查询异常时发出错误信号）
     */
    @Operation(summary = "获取审批列表", description = "分页获取审批流程列表")
    @Parameters({
        @Parameter(name = "pageNum", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "1")),
        @Parameter(name = "pageSize", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "10")),
        @Parameter(name = "status", in = ParameterIn.QUERY, description = "审批状态")
    })
    @OperationLog(module = "办公集成", type = OperationLog.OperationType.SELECT, description = "获取审批列表")
    public Mono<ServerResponse> getApprovalList(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Integer pageNum = safeParseInt(request.queryParam("pageNum").orElse("1"), 1);
        Integer pageSize = safeParseInt(request.queryParam("pageSize").orElse("10"), 10);
        String status = request.queryParam("status").orElse(null);

        return currentUserId(request, userService)
                .flatMap(userId ->
                        officeService.getApprovalPage(pageNum, pageSize, String.valueOf(userId), status, null, null))
                .flatMap(this::buildSuccess)
                .onErrorResume(e -> {
                    log.error("获取审批列表失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 根据审批 ID 获取审批流程详情。
     *
     * <p>路径中的 id 解析失败时返回参数错误，审批不存在时返回 404。</p>
     *
     * @param request 服务器请求，路径变量 id 为审批 ID
     * @return 包含审批详情的 {@code Result} 响应（Mono，ID 非法返回参数错误、审批不存在时发出错误信号）
     */
    @Parameters({@Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "审批ID")})
    @Operation(summary = "获取审批详情")
    @OperationLog(module = "办公集成", type = OperationLog.OperationType.SELECT, description = "获取审批详情")
    public Mono<ServerResponse> getApprovalDetail(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long id = safeParseLong(request.pathVariable("id"));
        if (id == null) {
            return buildError(ResultCode.PARAM_ERROR, traceId);
        }

        return officeService
                .getApprovalById(id)
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildError(ResultCode.NOT_FOUND, traceId));
    }

    /**
     * 获取当前用户的待审批列表。
     *
     * <p>查询当前登录用户名下所有待处理的审批事项并汇总为列表返回；查询失败时返回统一错误响应。</p>
     *
     * @param request 服务器请求，无业务参数，当前用户由登录态解析
     * @return 包含待审批列表的 {@code Result} 响应（Mono，查询异常时发出错误信号）
     */
    @Operation(summary = "获取待审批列表")
    @OperationLog(module = "办公集成", type = OperationLog.OperationType.SELECT, description = "获取待审批列表")
    public Mono<ServerResponse> getPendingApprovals(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return currentUserId(request, userService)
                .flatMapMany(userId -> officeService.getPendingApprovals(String.valueOf(userId)))
                .collectList()
                .flatMap(this::buildSuccess)
                .onErrorResume(e -> {
                    log.error("获取待审批列表失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 分页获取当前用户的日历事件列表。
     *
     * <p>根据当前登录用户 ID 分页查询日历事件，可按事件类型筛选，页码默认 1、每页默认 10 条；
     * 查询失败时返回统一错误响应。</p>
     *
     * @param request 服务器请求，查询参数 pageNum（页码，默认 1）、pageSize（每页数量，默认 10）、eventType（事件类型）
     * @return 包含日历事件分页数据的 {@code Result} 响应（Mono，查询异常时发出错误信号）
     */
    @Operation(summary = "获取日历事件列表")
    @Parameters({
        @Parameter(name = "pageNum", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "1")),
        @Parameter(name = "pageSize", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "10")),
        @Parameter(name = "eventType", in = ParameterIn.QUERY, description = "事件类型")
    })
    @OperationLog(module = "办公集成", type = OperationLog.OperationType.SELECT, description = "获取日历事件列表")
    public Mono<ServerResponse> getCalendarEventList(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Integer pageNum = safeParseInt(request.queryParam("pageNum").orElse("1"), 1);
        Integer pageSize = safeParseInt(request.queryParam("pageSize").orElse("10"), 10);
        String eventType = request.queryParam("eventType").orElse(null);

        return currentUserId(request, userService)
                .flatMap(userId -> officeService.getCalendarEventPage(
                        pageNum, pageSize, String.valueOf(userId), eventType, null, null, null))
                .flatMap(this::buildSuccess)
                .onErrorResume(e -> {
                    log.error("获取日历事件列表失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 根据事件 ID 获取日历事件详情。
     *
     * <p>路径中的 id 解析失败时返回参数错误，事件不存在时返回 404。</p>
     *
     * @param request 服务器请求，路径变量 id 为日历事件 ID
     * @return 包含日历事件详情的 {@code Result} 响应（Mono，ID 非法返回参数错误、事件不存在时发出错误信号）
     */
    @Parameters({@Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "事件ID")})
    @Operation(summary = "获取日历事件详情")
    @OperationLog(module = "办公集成", type = OperationLog.OperationType.SELECT, description = "获取日历事件详情")
    public Mono<ServerResponse> getCalendarEventDetail(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long id = safeParseLong(request.pathVariable("id"));
        if (id == null) {
            return buildError(ResultCode.PARAM_ERROR, traceId);
        }

        return officeService
                .getCalendarEventById(id)
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildError(ResultCode.NOT_FOUND, traceId));
    }

    /**
     * 获取当前用户即将到来的日历事件。
     *
     * <p>按开始时间临近的顺序查询当前登录用户即将开始的日历事件，最多返回 limit 条，
     * 默认 10 条；查询失败时返回统一错误响应。</p>
     *
     * @param request 服务器请求，查询参数 limit 为返回条数上限（默认 10）
     * @return 包含即将到来事件列表的 {@code Result} 响应（Mono，查询异常时发出错误信号）
     */
    @Operation(summary = "获取即将到来的事件")
    @Parameters({
        @Parameter(name = "limit", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "10"))
    })
    @OperationLog(module = "办公集成", type = OperationLog.OperationType.SELECT, description = "获取即将到来的事件")
    public Mono<ServerResponse> getUpcomingEvents(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Integer limit = safeParseInt(request.queryParam("limit").orElse("10"), 10);

        return currentUserId(request, userService)
                .flatMapMany(userId -> officeService.getUpcomingEvents(String.valueOf(userId), limit))
                .collectList()
                .flatMap(this::buildSuccess)
                .onErrorResume(e -> {
                    log.error("获取即将到来的事件失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 创建审批流程。
     *
     * <p>校验请求体并转换为 {@link ApprovalProcessDTO}，调用应用服务创建新的审批流程。</p>
     *
     * @param request 服务器请求，请求体为 {@link ApprovalProcessDTO} JSON
     * @return 包含新建审批流程信息的 {@code Result} 响应（Mono，参数校验失败或创建异常时发出错误信号）
     */
    @Operation(summary = "创建审批流程")
    @OperationLog(module = "办公集成", type = OperationLog.OperationType.INSERT, description = "创建审批流程")
    public Mono<ServerResponse> createApproval(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return requestHandlerUtil
                .validateAndMap(request, ApprovalProcessDTO.class)
                .flatMap(officeService::createApproval)
                .flatMap(this::buildSuccess)
                .onErrorResume(e -> {
                    log.error("创建审批流程失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 更新审批流程。
     *
     * <p>根据路径中的审批 ID，用校验后的请求体数据更新该审批流程；id 解析失败返回参数错误，
     * 审批不存在返回 404。</p>
     *
     * @param request 服务器请求，路径变量 id 为审批 ID，请求体为 {@link ApprovalProcessDTO} JSON
     * @return 包含更新后审批流程信息的 {@code Result} 响应（Mono，ID 非法、审批不存在或更新异常时发出错误信号）
     */
    @Parameters({@Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "审批ID")})
    @Operation(summary = "更新审批流程")
    @OperationLog(module = "办公集成", type = OperationLog.OperationType.UPDATE, description = "更新审批流程")
    public Mono<ServerResponse> updateApproval(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long id = safeParseLong(request.pathVariable("id"));
        if (id == null) {
            return buildError(ResultCode.PARAM_ERROR, traceId);
        }

        return requestHandlerUtil
                .validateAndMap(request, ApprovalProcessDTO.class)
                .flatMap(dto -> officeService.updateApproval(id, dto))
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildError(ResultCode.NOT_FOUND, traceId))
                .onErrorResume(e -> {
                    log.error("更新审批流程失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 删除审批流程。
     *
     * <p>根据路径中的审批 ID 删除对应审批流程，id 解析失败返回参数错误；删除失败时返回统一错误响应。</p>
     *
     * @param request 服务器请求，路径变量 id 为审批 ID
     * @return 删除成功返回成功 {@code Result}（data 为 null）（Mono，ID 非法或删除异常时发出错误信号）
     */
    @Parameters({@Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "审批ID")})
    @Operation(summary = "删除审批流程")
    @OperationLog(module = "办公集成", type = OperationLog.OperationType.DELETE, description = "删除审批流程")
    public Mono<ServerResponse> deleteApproval(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long id = safeParseLong(request.pathVariable("id"));
        if (id == null) {
            return buildError(ResultCode.PARAM_ERROR, traceId);
        }

        return officeService.deleteApproval(id).then(buildSuccess(null)).onErrorResume(e -> {
            log.error("删除审批流程失败: {}", e.getMessage(), e);
            return buildError(ResultCode.ERROR, traceId);
        });
    }

    /**
     * 创建日历事件。
     *
     * <p>校验请求体并转换为 {@link CalendarEventDTO}，调用应用服务创建新的日历事件。</p>
     *
     * @param request 服务器请求，请求体为 {@link CalendarEventDTO} JSON
     * @return 包含新建日历事件信息的 {@code Result} 响应（Mono，参数校验失败或创建异常时发出错误信号）
     */
    @Operation(summary = "创建日历事件")
    @OperationLog(module = "办公集成", type = OperationLog.OperationType.INSERT, description = "创建日历事件")
    public Mono<ServerResponse> createCalendarEvent(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return requestHandlerUtil
                .validateAndMap(request, CalendarEventDTO.class)
                .flatMap(officeService::createCalendarEvent)
                .flatMap(this::buildSuccess)
                .onErrorResume(e -> {
                    log.error("创建日历事件失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 更新日历事件。
     *
     * <p>根据路径中的事件 ID，用校验后的请求体数据更新该日历事件；id 解析失败返回参数错误，
     * 事件不存在返回 404。</p>
     *
     * @param request 服务器请求，路径变量 id 为日历事件 ID，请求体为 {@link CalendarEventDTO} JSON
     * @return 包含更新后日历事件信息的 {@code Result} 响应（Mono，ID 非法、事件不存在或更新异常时发出错误信号）
     */
    @Parameters({@Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "事件ID")})
    @Operation(summary = "更新日历事件")
    @OperationLog(module = "办公集成", type = OperationLog.OperationType.UPDATE, description = "更新日历事件")
    public Mono<ServerResponse> updateCalendarEvent(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long id = safeParseLong(request.pathVariable("id"));
        if (id == null) {
            return buildError(ResultCode.PARAM_ERROR, traceId);
        }

        return requestHandlerUtil
                .validateAndMap(request, CalendarEventDTO.class)
                .flatMap(dto -> officeService.updateCalendarEvent(id, dto))
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildError(ResultCode.NOT_FOUND, traceId))
                .onErrorResume(e -> {
                    log.error("更新日历事件失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 删除日历事件。
     *
     * <p>根据路径中的事件 ID 删除对应日历事件，id 解析失败返回参数错误；删除失败时返回统一错误响应。</p>
     *
     * @param request 服务器请求，路径变量 id 为日历事件 ID
     * @return 删除成功返回成功 {@code Result}（data 为 null）（Mono，ID 非法或删除异常时发出错误信号）
     */
    @Parameters({@Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "事件ID")})
    @Operation(summary = "删除日历事件")
    @OperationLog(module = "办公集成", type = OperationLog.OperationType.DELETE, description = "删除日历事件")
    public Mono<ServerResponse> deleteCalendarEvent(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long id = safeParseLong(request.pathVariable("id"));
        if (id == null) {
            return buildError(ResultCode.PARAM_ERROR, traceId);
        }

        return officeService.deleteCalendarEvent(id).then(buildSuccess(null)).onErrorResume(e -> {
            log.error("删除日历事件失败: {}", e.getMessage(), e);
            return buildError(ResultCode.ERROR, traceId);
        });
    }

    /**
     * 查询指定时间段内会议室的可用时段。
     *
     * <p>按会议室 ID 与开始/结束时间（ISO-8601 格式，如 {@code 2026-01-01T09:00:00}）查询可用时段；
     * 参数缺失、时间格式解析失败返回参数错误，查询失败返回统一错误响应。</p>
     *
     * @param request 服务器请求，查询参数 meetingRoomId（会议室 ID）、startTime（开始时间）、endTime（结束时间），均必填
     * @return 包含可用时段列表的 {@code Result} 响应（Mono，参数非法返回参数错误、查询异常时发出错误信号）
     */
    @Operation(summary = "查询会议室可用性")
    @Parameters({
        @Parameter(name = "meetingRoomId", in = ParameterIn.QUERY, required = true, description = "会议室ID"),
        @Parameter(name = "startTime", in = ParameterIn.QUERY, required = true, description = "开始时间"),
        @Parameter(name = "endTime", in = ParameterIn.QUERY, required = true, description = "结束时间")
    })
    @OperationLog(module = "办公集成", type = OperationLog.OperationType.SELECT, description = "查询会议室可用性")
    public Mono<ServerResponse> getMeetingRoomAvailability(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        String meetingRoomId = request.queryParam("meetingRoomId").orElse(null);
        if (meetingRoomId == null) {
            return buildError(ResultCode.PARAM_ERROR, traceId);
        }

        try {
            String startTimeStr = request.queryParam("startTime").orElse(null);
            String endTimeStr = request.queryParam("endTime").orElse(null);
            if (startTimeStr == null || endTimeStr == null) {
                return buildError(ResultCode.PARAM_ERROR, traceId);
            }

            LocalDateTime startTime = LocalDateTime.parse(startTimeStr);
            LocalDateTime endTime = LocalDateTime.parse(endTimeStr);

            return officeService
                    .getMeetingRoomAvailability(meetingRoomId, startTime, endTime)
                    .collectList()
                    .flatMap(this::buildSuccess)
                    .onErrorResume(e -> {
                        log.error("查询会议室可用性失败: {}", e.getMessage(), e);
                        return buildError(ResultCode.ERROR, traceId);
                    });
        } catch (Exception e) {
            log.error("解析时间参数失败: {}", e.getMessage(), e);
            return buildError(ResultCode.PARAM_ERROR, traceId);
        }
    }
}
