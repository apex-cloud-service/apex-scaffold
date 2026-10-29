package com.apex.scaffold.officeservice.interfaces.handler.office;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.application.OfficeMeetingRoomService;
import com.apex.scaffold.officeservice.interfaces.assembler.office.MeetingRoomAssembler;
import com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom.BookMeetingRoomRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom.CreateMeetingRoomRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom.MeetingRoomBookingResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom.MeetingRoomResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom.UpdateMeetingRoomRequest;
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
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 会议室资源处理器
 *
 * <p>提供会议室主数据维护、可用性查询与预订/取消接口：
 * <ul>
 *   <li>会议室增删改查 - 需要 {@code office:meeting-room:manage} / {@code office:meeting-room:read}</li>
 *   <li>可用性查询 - 按时间区间排除已被占用（未取消）的会议室</li>
 *   <li>预订与取消 - 需要 {@code office:meeting-room:book}，取消仅限预订人本人</li>
 * </ul>
 *
 * <p>租户与操作人均取自受信任的认证上下文，不接受客户端自行传入。
 *
 * @author Apex Scaffold Team
 * @since 1.4.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "办公会议室管理", description = "会议室资源维护、可用性查询与预订相关接口")
public class OfficeMeetingRoomHandler {

    private final OfficeMeetingRoomService meetingRoomService;
    private final MeetingRoomAssembler assembler;
    private final RequestHandlerUtil requestHandlerUtil;

    /** 会议室详情的统一响应。 */
    @Schema(name = "OfficeMeetingRoomResult")
    public static class MeetingRoomResult extends Result<MeetingRoomResponse> {}

    /** 会议室列表的统一响应，不含分页信息。 */
    @Schema(name = "OfficeMeetingRoomListResult")
    public static class MeetingRoomListResult extends Result<List<MeetingRoomResponse>> {}

    /** 会议室分页结果的统一响应。 */
    @Schema(name = "OfficeMeetingRoomPageResult")
    public static class MeetingRoomPageResult
            extends Result<com.apex.scaffold.common.dto.PageResponse<MeetingRoomResponse>> {}

    /** 会议室写操作的布尔结果。 */
    @Schema(name = "OfficeMeetingRoomBooleanResult")
    public static class MeetingRoomBooleanResult extends Result<Boolean> {}

    /** 会议室预订结果的统一响应。 */
    @Schema(name = "OfficeMeetingRoomBookingResult")
    public static class MeetingRoomBookingResult extends Result<MeetingRoomBookingResponse> {}

    /**
     * 创建会议室
     *
     * @param request HTTP 请求，请求体为会议室创建参数
     * @return 创建成功的会议室
     */
    @Operation(operationId = "createMeetingRoom", summary = "创建会议室", description = "创建会议室资源，编码与名称在租户内唯一")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "会议室创建请求",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateMeetingRoomRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MeetingRoomResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误或编码/名称重复",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequirePermission("office:meeting-room:manage")
    @OperationLog(
            module = "OfficeMeetingRoom",
            type = OperationLog.OperationType.INSERT,
            description = "createMeetingRoom",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> createMeetingRoom(ServerRequest request) {
        return trustedTenantId(request)
                .flatMap(tenantId -> requestHandlerUtil
                        .validateAndMap(request, CreateMeetingRoomRequest.class)
                        .flatMap(req -> meetingRoomService.createMeetingRoom(tenantId, assembler.toEntity(req))))
                .map(assembler::toResponse)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 更新会议室
     *
     * @param request HTTP 请求，路径参数 roomId，请求体为变更项
     * @return 更新后的会议室
     */
    @Operation(operationId = "updateMeetingRoom", summary = "更新会议室", description = "局部更新会议室资源，仅提交的字段生效")
    @Parameters({
        @Parameter(
                name = "roomId",
                in = ParameterIn.PATH,
                required = true,
                description = "会议室ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "会议室更新请求",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateMeetingRoomRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MeetingRoomResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误或编码/名称重复",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "404",
                description = "会议室不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequirePermission("office:meeting-room:manage")
    @OperationLog(
            module = "OfficeMeetingRoom",
            type = OperationLog.OperationType.UPDATE,
            description = "updateMeetingRoom",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> updateMeetingRoom(ServerRequest request) {
        Long roomId = RequestHandlerUtil.safeParseLong(request.pathVariable("roomId"));
        return trustedTenantId(request)
                .flatMap(tenantId -> requestHandlerUtil
                        .validateAndMap(request, UpdateMeetingRoomRequest.class)
                        .flatMap(req ->
                                meetingRoomService.updateMeetingRoom(tenantId, roomId, assembler.toChanges(req))))
                .map(assembler::toResponse)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 删除会议室
     *
     * @param request HTTP 请求，路径参数 roomId
     * @return 删除结果
     */
    @Operation(operationId = "deleteMeetingRoom", summary = "删除会议室", description = "逻辑删除会议室资源，data 为布尔结果")
    @Parameters({
        @Parameter(
                name = "roomId",
                in = ParameterIn.PATH,
                required = true,
                description = "会议室ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "删除成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MeetingRoomBooleanResult.class))),
        @ApiResponse(
                responseCode = "404",
                description = "会议室不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequirePermission("office:meeting-room:manage")
    @OperationLog(
            module = "OfficeMeetingRoom",
            type = OperationLog.OperationType.DELETE,
            description = "deleteMeetingRoom",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> deleteMeetingRoom(ServerRequest request) {
        Long roomId = RequestHandlerUtil.safeParseLong(request.pathVariable("roomId"));
        return trustedTenantId(request)
                .flatMap(tenantId -> meetingRoomService.deleteMeetingRoom(tenantId, roomId))
                .flatMap(deleted -> requestHandlerUtil.ok(Result.success(deleted)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询会议室详情
     *
     * @param request HTTP 请求，路径参数 roomId
     * @return 会议室详情
     */
    @Operation(operationId = "getMeetingRoom", summary = "查询会议室详情", description = "按会议室ID查询详情")
    @Parameters({
        @Parameter(
                name = "roomId",
                in = ParameterIn.PATH,
                required = true,
                description = "会议室ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MeetingRoomResult.class))),
        @ApiResponse(
                responseCode = "404",
                description = "会议室不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequirePermission("office:meeting-room:read")
    @OperationLog(
            module = "OfficeMeetingRoom",
            type = OperationLog.OperationType.SELECT,
            description = "getMeetingRoom",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getMeetingRoom(ServerRequest request) {
        Long roomId = RequestHandlerUtil.safeParseLong(request.pathVariable("roomId"));
        return trustedTenantId(request)
                .flatMap(tenantId -> meetingRoomService.getMeetingRoom(tenantId, roomId))
                .map(assembler::toResponse)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 分页查询会议室
     *
     * @param request HTTP 请求，查询参数 pageNum、pageSize、status、keyword
     * @return 会议室分页结果
     */
    @Operation(operationId = "listMeetingRooms", summary = "分页查询会议室", description = "按状态与关键字分页查询会议室")
    @Parameters({
        @Parameter(
                name = "pageNum",
                in = ParameterIn.QUERY,
                description = "页码，从1开始",
                schema = @Schema(type = "integer")),
        @Parameter(
                name = "pageSize",
                in = ParameterIn.QUERY,
                description = "每页条数，上限200",
                schema = @Schema(type = "integer")),
        @Parameter(
                name = "status",
                in = ParameterIn.QUERY,
                description = "状态：ENABLED / DISABLED",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "keyword",
                in = ParameterIn.QUERY,
                description = "名称或编码模糊匹配",
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MeetingRoomPageResult.class)))
    })
    @RequirePermission("office:meeting-room:read")
    @OperationLog(
            module = "OfficeMeetingRoom",
            type = OperationLog.OperationType.SELECT,
            description = "listMeetingRooms",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listMeetingRooms(ServerRequest request) {
        Integer pageNum =
                RequestHandlerUtil.safeParseInt(request.queryParam("pageNum").orElse(null), 1);
        Integer pageSize =
                RequestHandlerUtil.safeParseInt(request.queryParam("pageSize").orElse(null), 20);
        String status = request.queryParam("status").orElse(null);
        String keyword = request.queryParam("keyword").orElse(null);
        return trustedTenantId(request)
                .flatMap(tenantId -> meetingRoomService.listMeetingRooms(tenantId, pageNum, pageSize, status, keyword))
                .flatMap(page -> requestHandlerUtil.ok(Result.success(page)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询启用会议室
     *
     * @param request HTTP 请求
     * @return 启用状态的会议室列表
     */
    @Operation(operationId = "listEnabledMeetingRooms", summary = "查询启用会议室", description = "返回租户内全部启用状态的会议室")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MeetingRoomListResult.class)))
    })
    @RequirePermission("office:meeting-room:read")
    @OperationLog(
            module = "OfficeMeetingRoom",
            type = OperationLog.OperationType.SELECT,
            description = "listEnabledMeetingRooms",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listEnabledMeetingRooms(ServerRequest request) {
        return trustedTenantId(request)
                .flatMap(meetingRoomService::listEnabledMeetingRooms)
                .map(assembler::toResponseList)
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询时间区间内可用会议室
     *
     * @param request HTTP 请求，查询参数 startTime、endTime（ISO 本地时间）
     * @return 可用会议室列表
     */
    @Operation(operationId = "findAvailableMeetingRooms", summary = "查询可用会议室", description = "按时间区间返回未被占用的启用会议室")
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
                                schema = @Schema(implementation = MeetingRoomListResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "时间参数错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequirePermission("office:meeting-room:read")
    @OperationLog(
            module = "OfficeMeetingRoom",
            type = OperationLog.OperationType.SELECT,
            description = "findAvailableMeetingRooms",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> findAvailableMeetingRooms(ServerRequest request) {
        // 时间参数在订阅时解析，使格式错误经 handleError 收敛为 400，而不是抛出到 WebFlux 异常边界
        return Mono.defer(() -> {
                    LocalDateTime startTime =
                            parseDateTime(request.queryParam("startTime").orElse(null), "startTime");
                    LocalDateTime endTime =
                            parseDateTime(request.queryParam("endTime").orElse(null), "endTime");
                    return trustedTenantId(request)
                            .flatMap(tenantId ->
                                    meetingRoomService.findAvailableMeetingRooms(tenantId, startTime, endTime))
                            .map(assembler::toResponseList)
                            .flatMap(response -> requestHandlerUtil.ok(Result.success(response)));
                })
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 预订会议室
     *
     * @param request HTTP 请求，路径参数 roomId，请求体为预订参数
     * @return 预订生成的日历事件视图
     */
    @Operation(operationId = "bookMeetingRoom", summary = "预订会议室", description = "为当前用户预订指定会议室，冲突时拒绝")
    @Parameters({
        @Parameter(
                name = "roomId",
                in = ParameterIn.PATH,
                required = true,
                description = "会议室ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "会议室预订请求",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BookMeetingRoomRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "预订成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MeetingRoomBookingResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "时间无效、会议室停用或时段冲突",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "404",
                description = "会议室不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequirePermission("office:meeting-room:book")
    @OperationLog(
            module = "OfficeMeetingRoom",
            type = OperationLog.OperationType.INSERT,
            description = "bookMeetingRoom",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> bookMeetingRoom(ServerRequest request) {
        Long roomId = RequestHandlerUtil.safeParseLong(request.pathVariable("roomId"));
        return Mono.zip(trustedTenantId(request), currentUserId(request))
                .flatMap(context -> requestHandlerUtil
                        .validateAndMap(request, BookMeetingRoomRequest.class)
                        .flatMap(req -> meetingRoomService
                                .bookMeetingRoom(
                                        context.getT1(),
                                        roomId,
                                        context.getT2(),
                                        null,
                                        req.getTitle(),
                                        req.getStartTime(),
                                        req.getEndTime())
                                .flatMap(event -> meetingRoomService
                                        .getMeetingRoom(context.getT1(), roomId)
                                        .map(room -> assembler.toBookingResponse(event, room)))))
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 取消会议室预订
     *
     * @param request HTTP 请求，路径参数 roomId、eventId
     * @return 取消后的预订视图
     */
    @Operation(operationId = "cancelMeetingRoomBooking", summary = "取消会议室预订", description = "仅预订人本人可取消，重复取消幂等")
    @Parameters({
        @Parameter(
                name = "roomId",
                in = ParameterIn.PATH,
                required = true,
                description = "会议室ID",
                schema = @Schema(type = "integer", format = "int64")),
        @Parameter(
                name = "eventId",
                in = ParameterIn.PATH,
                required = true,
                description = "预订对应的日历事件ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "取消成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MeetingRoomBookingResult.class))),
        @ApiResponse(
                responseCode = "403",
                description = "非预订人本人",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "404",
                description = "会议室或预订记录不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequirePermission("office:meeting-room:book")
    @OperationLog(
            module = "OfficeMeetingRoom",
            type = OperationLog.OperationType.UPDATE,
            description = "cancelMeetingRoomBooking",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> cancelMeetingRoomBooking(ServerRequest request) {
        Long roomId = RequestHandlerUtil.safeParseLong(request.pathVariable("roomId"));
        Long eventId = RequestHandlerUtil.safeParseLong(request.pathVariable("eventId"));
        return Mono.zip(trustedTenantId(request), currentUserId(request))
                .flatMap(context -> meetingRoomService
                        .cancelBooking(context.getT1(), roomId, eventId, context.getT2())
                        .flatMap(event -> meetingRoomService
                                .getMeetingRoom(context.getT1(), roomId)
                                .map(room -> assembler.toBookingResponse(event, room))))
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /** 租户只能来自已验证的认证上下文，不接受客户端头覆盖。 */
    private Mono<String> trustedTenantId(ServerRequest request) {
        return request.principal()
                .filter(AuthenticatedUser.class::isInstance)
                .cast(AuthenticatedUser.class)
                .map(AuthenticatedUser::tenantId)
                .filter(tenantId -> tenantId != null && !tenantId.isBlank())
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "未认证或缺少租户上下文")));
    }

    /** 当前操作人标识；预订与取消都以认证用户为准。 */
    private Mono<String> currentUserId(ServerRequest request) {
        return request.principal()
                .filter(AuthenticatedUser.class::isInstance)
                .cast(AuthenticatedUser.class)
                .map(user -> String.valueOf(user.userId()))
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "未认证或缺少用户上下文")));
    }

    /** 解析查询参数中的 ISO 本地时间；缺失或格式错误返回参数错误。 */
    private LocalDateTime parseDateTime(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, field + "不能为空");
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (RuntimeException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, field + "格式错误，应为 ISO 本地时间");
        }
    }
}
