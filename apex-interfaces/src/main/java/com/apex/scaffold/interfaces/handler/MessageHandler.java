package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.MessageDTO;
import com.apex.scaffold.application.dto.SendMessageRequest;
import com.apex.scaffold.application.service.OffMessageService;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.util.MessageUtils;
import com.apex.scaffold.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 消息管理处理器（函数式端点）
 *
 * <p>提供站内消息的发送、接收和管理功能，支持用户之间的消息通信、
 * 系统通知推送、消息状态跟踪等功能。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>消息发送：支持向指定用户发送站内消息</li>
 *   <li>收件箱管理：查询当前用户收到的消息列表</li>
 *   <li>发件箱管理：查询当前用户发送的消息列表</li>
 *   <li>消息状态：标记已读/未读，删除消息</li>
 *   <li>消息模板：基于预定义模板批量发送消息</li>
 * </ul>
 *
 * <h2>消息类型</h2>
 * <ul>
 *   <li>用户消息：用户之间点对点的消息通信</li>
 *   <li>系统通知：系统自动推送的通知消息</li>
 *   <li>审批消息：审批流程相关的提醒消息</li>
 *   <li>任务消息：任务分配和进度相关的消息</li>
 * </ul>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>POST /api/v1/messages/send - 发送消息</li>
 *   <li>GET /api/v1/messages/inbox - 获取收件箱</li>
 *   <li>GET /api/v1/messages/sent - 获取发件箱</li>
 *   <li>PUT /api/v1/messages/{id}/read - 标记消息为已读</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see com.apex.scaffold.application.service.OffMessageService
 */
@Slf4j
@Component
@Tag(name = "消息管理", description = "消息推送和查询相关接口")
public class MessageHandler extends BaseHandler {

    private final OffMessageService messageService;
    private final UserService userService;
    private final RequestHandlerUtil requestHandlerUtil;

    /**
     * 创建消息管理处理器。
     *
     * @param messageService 站内消息服务
     * @param userService 用户服务
     * @param requestHandlerUtil 请求参数处理工具
     * @param messageUtils 国际化消息工具
     */
    public MessageHandler(
            OffMessageService messageService,
            UserService userService,
            RequestHandlerUtil requestHandlerUtil,
            MessageUtils messageUtils) {
        super(messageUtils);
        this.messageService = messageService;
        this.userService = userService;
        this.requestHandlerUtil = requestHandlerUtil;
    }

    /**
     * 处理发送消息请求。
     *
     * <p>解析并校验请求体为 {@link SendMessageRequest}，以当前登录用户作为发送方，
     * 调用应用服务向指定用户发送站内消息。
     *
     * @param request HTTP 请求对象，请求体为消息发送参数 JSON
     * @return 包含已发送消息信息的统一成功响应，失败时返回错误响应
     */
    @Operation(summary = "发送消息", description = "发送站内消息给指定用户")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "发送成功",
                content =
                        @Content(mediaType = "application/json", schema = @Schema(implementation = MessageDTO.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(module = "消息管理", type = OperationLog.OperationType.INSERT, description = "发送消息")
    public Mono<ServerResponse> sendMessage(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return requestHandlerUtil
                .validateAndMap(request, SendMessageRequest.class)
                .flatMap(req ->
                        currentUserId(request, userService).flatMap(userId -> messageService.sendMessage(req, userId)))
                .flatMap(this::buildSuccess)
                .onErrorResume(e -> {
                    log.error("发送消息失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 处理获取收件箱请求。
     *
     * <p>分页查询当前登录用户的收件箱消息列表，未登录时返回空列表。
     *
     * @param request HTTP 请求对象，查询参数包含 pageNum、pageSize
     * @return 包含收件箱消息列表的统一成功响应，失败时返回错误响应
     */
    @Operation(summary = "获取收件箱", description = "获取当前用户的收件箱消息列表")
    @Parameters({
        @Parameter(
                name = "pageNum",
                in = ParameterIn.QUERY,
                description = "页码",
                schema = @Schema(type = "integer", defaultValue = "1")),
        @Parameter(
                name = "pageSize",
                in = ParameterIn.QUERY,
                description = "每页数量",
                schema = @Schema(type = "integer", defaultValue = "10"))
    })
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功", content = @Content(mediaType = "application/json"))
    })
    @OperationLog(module = "消息管理", type = OperationLog.OperationType.SELECT, description = "获取收件箱")
    public Mono<ServerResponse> getInbox(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Integer pageNum = safeParseInt(request.queryParam("pageNum").orElse("1"), 1);
        Integer pageSize = safeParseInt(request.queryParam("pageSize").orElse("10"), 10);

        return currentUserId(request, userService)
                .flatMapMany(userId -> messageService.getInbox(userId, pageNum, pageSize))
                .collectList()
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildSuccess(java.util.Collections.emptyList()))
                .onErrorResume(e -> {
                    log.error("获取收件箱失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 处理获取发件箱请求。
     *
     * <p>分页查询当前登录用户已发送的消息列表，未登录时返回空列表。
     *
     * @param request HTTP 请求对象，查询参数包含 pageNum、pageSize
     * @return 包含已发送消息列表的统一成功响应，失败时返回错误响应
     */
    @Operation(summary = "获取发件箱", description = "获取当前用户的发件箱消息列表")
    @Parameters({
        @Parameter(
                name = "pageNum",
                in = ParameterIn.QUERY,
                description = "页码",
                schema = @Schema(type = "integer", defaultValue = "1")),
        @Parameter(
                name = "pageSize",
                in = ParameterIn.QUERY,
                description = "每页数量",
                schema = @Schema(type = "integer", defaultValue = "10"))
    })
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功", content = @Content(mediaType = "application/json"))
    })
    @OperationLog(module = "消息管理", type = OperationLog.OperationType.SELECT, description = "获取发件箱")
    public Mono<ServerResponse> getSentMessages(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Integer pageNum = safeParseInt(request.queryParam("pageNum").orElse("1"), 1);
        Integer pageSize = safeParseInt(request.queryParam("pageSize").orElse("10"), 10);

        return currentUserId(request, userService)
                .flatMapMany(userId -> messageService.getSentMessages(userId, pageNum, pageSize))
                .collectList()
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildSuccess(java.util.Collections.emptyList()))
                .onErrorResume(e -> {
                    log.error("获取发件箱失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 处理获取消息详情请求。
     *
     * <p>根据路径中的消息 ID 查询消息详情，ID 缺失或非法时返回参数错误，
     * 消息不存在时返回 404 响应。
     *
     * @param request HTTP 请求对象，路径参数包含消息 ID
     * @return 包含消息详情的统一成功响应，不存在时返回 404 错误响应
     */
    @Parameters({@Parameter(name = "messageId", in = ParameterIn.PATH, required = true, description = "消息ID")})
    @Operation(summary = "获取消息详情", description = "获取指定消息的详细信息")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "获取成功",
                content =
                        @Content(mediaType = "application/json", schema = @Schema(implementation = MessageDTO.class))),
        @ApiResponse(
                responseCode = "404",
                description = "消息不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(module = "消息管理", type = OperationLog.OperationType.SELECT, description = "获取消息详情")
    public Mono<ServerResponse> getMessageDetail(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long messageId = safeParseLong(request.pathVariable("messageId"));
        if (messageId == null) {
            return buildError(ResultCode.PARAM_ERROR, traceId);
        }

        return messageService
                .getMessageDetail(messageId)
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildError(ResultCode.NOT_FOUND, traceId));
    }

    /**
     * 处理标记消息为已读请求。
     *
     * <p>将当前登录用户的指定消息标记为已读状态，消息 ID 缺失或非法时返回参数错误。
     *
     * @param request HTTP 请求对象，路径参数包含消息 ID
     * @return 标记成功的统一成功响应，失败时返回错误响应
     */
    @Parameters({@Parameter(name = "messageId", in = ParameterIn.PATH, required = true, description = "消息ID")})
    @Operation(summary = "标记消息为已读", description = "将指定消息标记为已读状态")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "标记成功", content = @Content(mediaType = "application/json"))
    })
    @OperationLog(module = "消息管理", type = OperationLog.OperationType.UPDATE, description = "标记消息为已读")
    public Mono<ServerResponse> markAsRead(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long messageId = safeParseLong(request.pathVariable("messageId"));
        if (messageId == null) {
            return buildError(ResultCode.PARAM_ERROR, traceId);
        }

        return currentUserId(request, userService)
                .flatMap(userId -> messageService.markAsRead(messageId, userId))
                .then(buildSuccess(null))
                .onErrorResume(e -> {
                    log.error("标记消息已读失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 处理全部标记为已读请求。
     *
     * <p>将当前登录用户的所有未读消息批量标记为已读状态。
     *
     * @param request HTTP 请求对象
     * @return 标记成功的统一成功响应，失败时返回错误响应
     */
    @Operation(summary = "全部标记为已读", description = "将当前用户的所有未读消息标记为已读")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "标记成功", content = @Content(mediaType = "application/json"))
    })
    @OperationLog(module = "消息管理", type = OperationLog.OperationType.UPDATE, description = "全部标记为已读")
    public Mono<ServerResponse> markAllAsRead(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return currentUserId(request, userService)
                .flatMap(messageService::markAllAsRead)
                .then(buildSuccess(null))
                .onErrorResume(e -> {
                    log.error("全部标记已读失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }

    /**
     * 处理获取未读消息数请求。
     *
     * <p>统计并返回当前登录用户的未读消息数量。
     *
     * @param request HTTP 请求对象
     * @return 包含未读消息数量的统一成功响应，失败时返回错误响应
     */
    @Operation(summary = "获取未读消息数", description = "获取当前用户的未读消息数量")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功", content = @Content(mediaType = "application/json"))
    })
    @OperationLog(module = "消息管理", type = OperationLog.OperationType.SELECT, description = "获取未读消息数")
    public Mono<ServerResponse> getUnreadCount(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return currentUserId(request, userService)
                .flatMap(messageService::countUnread)
                .flatMap(this::buildSuccess)
                .onErrorResume(e -> {
                    log.error("获取未读消息数失败: {}", e.getMessage(), e);
                    return buildError(ResultCode.ERROR, traceId);
                });
    }
}
