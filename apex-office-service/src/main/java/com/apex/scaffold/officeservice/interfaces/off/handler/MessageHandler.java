package com.apex.scaffold.officeservice.interfaces.off.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.off.application.dto.InboxCursorItemDTO;
import com.apex.scaffold.office.off.application.dto.MessageDTO;
import com.apex.scaffold.office.off.application.dto.MessageDeliveryDTO;
import com.apex.scaffold.office.off.application.dto.SendMessageRequest;
import com.apex.scaffold.office.off.application.service.OffMessageService;
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
import java.util.Collections;
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
 * 消息管理处理器（函数式端点）。
 *
 * <p>提供站内消息的发送、接收和管理功能，支持用户之间的消息通信、
 * 系统通知推送、消息状态跟踪等功能。本类为微服务化 Phase 5 从单体
 * {@code apex-interfaces} 迁移而来，不再继承旧应用专有的 {@code BaseHandler}，
 * 改用办公服务接口层的 {@link RequestHandlerUtil} 与安全上下文
 * {@link AuthenticatedUser} 获取当前登录用户。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>消息发送：支持向指定用户发送站内消息</li>
 *   <li>收件箱管理：查询当前用户收到的消息列表</li>
 *   <li>发件箱管理：查询当前用户发送的消息列表</li>
 *   <li>消息状态：标记已读、批量标记全部已读</li>
 *   <li>未读统计：统计当前用户未读消息数量</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 * @see OffMessageService
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "消息管理", description = "消息推送和查询相关接口")
public class MessageHandler {

    private final OffMessageService messageService;
    private final RequestHandlerUtil requestHandlerUtil;

    /**
     * 处理发送消息请求。
     *
     * <p>解析并校验请求体为 {@link SendMessageRequest}，以当前登录用户作为发送方，
     * 调用应用服务向指定用户发送站内消息。
     *
     * @param request HTTP 请求对象，请求体为消息发送参数 JSON
     * @return 包含已发送消息信息的统一成功响应，失败时返回错误响应
     */
    @RequirePermission("office:message:send")
    @Operation(
            operationId = "sendMessage",
            summary = "发送消息",
            description = "发送站内消息给指定用户",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = SendMessageRequest.class))))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "发送成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MessageResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "消息管理",
            type = OperationLog.OperationType.INSERT,
            description = "发送消息",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> sendMessage(ServerRequest request) {
        return requestHandlerUtil
                .validateAndMap(request, SendMessageRequest.class)
                .flatMap(req -> currentUserId().flatMap(userId -> messageService.sendMessage(req, userId)))
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .onErrorResume(this::handleError);
    }

    /**
     * 处理获取收件箱请求。
     *
     * <p>分页查询当前登录用户的收件箱消息列表。
     *
     * @param request HTTP 请求对象，查询参数包含 pageNum、pageSize
     * @return 包含收件箱消息列表的统一成功响应，失败时返回错误响应
     */
    @RequirePermission("office:message:list")
    @Operation(operationId = "getInbox", summary = "获取收件箱", description = "分页查询当前用户收件箱，data 为消息数组")
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
        @ApiResponse(
                responseCode = "200",
                description = "获取成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MessageListResult.class)))
    })
    @OperationLog(
            module = "消息管理",
            type = OperationLog.OperationType.SELECT,
            description = "获取收件箱",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getInbox(ServerRequest request) {
        Integer pageNum =
                RequestHandlerUtil.safeParseInt(request.queryParam("pageNum").orElse("1"), 1);
        Integer pageSize =
                RequestHandlerUtil.safeParseInt(request.queryParam("pageSize").orElse("10"), 10);

        return currentUserId()
                .flatMapMany(userId -> messageService.getInbox(userId, pageNum, pageSize))
                .collectList()
                .map(list -> list.isEmpty() ? Collections.emptyList() : list)
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .onErrorResume(this::handleError);
    }

    /**
     * 处理获取发件箱请求。
     *
     * <p>分页查询当前登录用户已发送的消息列表。
     *
     * @param request HTTP 请求对象，查询参数包含 pageNum、pageSize
     * @return 包含已发送消息列表的统一成功响应，失败时返回错误响应
     */
    @RequirePermission("office:message:list")
    @Operation(operationId = "getSentMessages", summary = "获取发件箱", description = "分页查询当前用户发件箱，data 为消息数组")
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
        @ApiResponse(
                responseCode = "200",
                description = "获取成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MessageListResult.class)))
    })
    @OperationLog(
            module = "消息管理",
            type = OperationLog.OperationType.SELECT,
            description = "获取发件箱",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getSentMessages(ServerRequest request) {
        Integer pageNum =
                RequestHandlerUtil.safeParseInt(request.queryParam("pageNum").orElse("1"), 1);
        Integer pageSize =
                RequestHandlerUtil.safeParseInt(request.queryParam("pageSize").orElse("10"), 10);

        return currentUserId()
                .flatMapMany(userId -> messageService.getSentMessages(userId, pageNum, pageSize))
                .collectList()
                .map(list -> list.isEmpty() ? Collections.emptyList() : list)
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .onErrorResume(this::handleError);
    }

    /**
     * 处理获取消息详情请求。
     *
     * <p>根据路径中的消息 ID 查询消息详情，ID 缺失或非法时返回参数错误，
     * 消息不存在时返回 HTTP 400，业务码为 NOT_FOUND。
     *
     * @param request HTTP 请求对象，路径参数包含消息 ID
     * @return 包含消息详情的统一成功响应，不存在时返回 HTTP 400 错误响应
     */
    @RequirePermission("office:message:query")
    @Parameters({
        @Parameter(
                name = "messageId",
                in = ParameterIn.PATH,
                required = true,
                description = "消息ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @Operation(
            operationId = "getMessageDetail",
            summary = "获取消息详情",
            description = "查询当前用户可访问的消息；不存在时返回 HTTP 400、NOT_FOUND 业务码")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "获取成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MessageResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "消息不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "消息管理",
            type = OperationLog.OperationType.SELECT,
            description = "获取消息详情",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getMessageDetail(ServerRequest request) {
        Long messageId = RequestHandlerUtil.safeParseLong(request.pathVariable("messageId"));
        if (messageId == null) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "消息ID无效");
        }

        return currentUserId()
                .flatMap(userId -> messageService.getMessageDetail(messageId, userId))
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .switchIfEmpty(requestHandlerUtil.buildErrorResponse(ResultCode.NOT_FOUND, "消息不存在"))
                .onErrorResume(this::handleError);
    }

    /**
     * 处理标记消息为已读请求。
     *
     * <p>将当前登录用户的指定消息标记为已读状态，消息 ID 缺失或非法时返回参数错误。
     *
     * @param request HTTP 请求对象，路径参数包含消息 ID
     * @return 标记成功的统一成功响应，失败时返回错误响应
     */
    @RequirePermission("office:message:list")
    @Parameters({
        @Parameter(
                name = "messageId",
                in = ParameterIn.PATH,
                required = true,
                description = "消息ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @Operation(operationId = "markAsRead", summary = "标记消息为已读", description = "将指定消息标记为已读状态，data 为空")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "标记成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MessageVoidResult.class)))
    })
    @OperationLog(
            module = "消息管理",
            type = OperationLog.OperationType.UPDATE,
            description = "标记消息为已读",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> markAsRead(ServerRequest request) {
        Long messageId = RequestHandlerUtil.safeParseLong(request.pathVariable("messageId"));
        if (messageId == null) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "消息ID无效");
        }

        return currentUserId()
                .flatMap(userId -> messageService.markAsRead(messageId, userId))
                .then(Mono.defer(() -> requestHandlerUtil.buildSuccessResponse(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 处理全部标记为已读请求。
     *
     * <p>将当前登录用户的所有未读消息批量标记为已读状态。
     *
     * @param request HTTP 请求对象
     * @return 标记成功的统一成功响应，失败时返回错误响应
     */
    @RequirePermission("office:message:list")
    @Operation(operationId = "markAllAsRead", summary = "全部标记为已读", description = "将当前用户的所有未读消息标记为已读，data 为空")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "标记成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MessageVoidResult.class)))
    })
    @OperationLog(
            module = "消息管理",
            type = OperationLog.OperationType.UPDATE,
            description = "全部标记为已读",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> markAllAsRead(ServerRequest request) {
        return currentUserId()
                .flatMap(messageService::markAllAsRead)
                .then(Mono.defer(() -> requestHandlerUtil.buildSuccessResponse(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 处理获取未读消息数请求。
     *
     * <p>统计并返回当前登录用户的未读消息数量。
     *
     * @param request HTTP 请求对象
     * @return 包含未读消息数量的统一成功响应，失败时返回错误响应
     */
    @RequirePermission("office:message:query")
    @Operation(operationId = "getUnreadCount", summary = "获取未读消息数", description = "获取当前用户的未读消息数量")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "获取成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MessageCountResult.class)))
    })
    @OperationLog(
            module = "消息管理",
            type = OperationLog.OperationType.SELECT,
            description = "获取未读消息数",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getUnreadCount(ServerRequest request) {
        return currentUserId()
                .flatMap(messageService::countUnread)
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .onErrorResume(this::handleError);
    }

    /**
     * 处理收件箱增量游标查询请求。
     *
     * <p>按接收记录 id 升序返回当前登录用户的收件箱条目，
     * {@code afterId} 传上一次返回的最大 {@code recipientRecordId}，首次传 0。
     *
     * @param request HTTP 请求对象，查询参数包含 afterId、limit
     * @return 包含收件箱条目列表的统一成功响应，失败时返回错误响应
     */
    @RequirePermission("office:message:list")
    @Operation(
            operationId = "getInboxCursor",
            summary = "增量查询收件箱",
            description = "按接收记录 id 游标增量查询当前用户收件箱，data 为条目数组，recipientRecordId 作为下一页游标")
    @Parameters({
        @Parameter(
                name = "afterId",
                in = ParameterIn.QUERY,
                description = "游标：仅返回接收记录 id 大于该值的条目",
                schema = @Schema(type = "integer", format = "int64", defaultValue = "0")),
        @Parameter(
                name = "limit",
                in = ParameterIn.QUERY,
                description = "返回条数上限，最大 100",
                schema = @Schema(type = "integer", defaultValue = "20"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "获取成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = InboxCursorListResult.class)))
    })
    @OperationLog(
            module = "消息管理",
            type = OperationLog.OperationType.SELECT,
            description = "增量查询收件箱",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getInboxCursor(ServerRequest request) {
        Long afterId =
                RequestHandlerUtil.safeParseLong(request.queryParam("afterId").orElse("0"));
        if (afterId == null) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "afterId 无效");
        }
        Integer limit =
                RequestHandlerUtil.safeParseInt(request.queryParam("limit").orElse("20"), 20);

        return currentUserId()
                .flatMapMany(userId -> messageService.getInboxCursor(userId, afterId, limit))
                .collectList()
                .map(list -> list.isEmpty() ? Collections.emptyList() : list)
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .onErrorResume(this::handleError);
    }

    /**
     * 处理查询消息渠道投递记录请求。
     *
     * <p>发送人可见全部接收人的投递记录，接收人仅可见本人的投递记录；
     * 消息不存在或无权限时返回空列表。
     *
     * @param request HTTP 请求对象，路径参数包含消息 ID
     * @return 包含投递记录列表的统一成功响应，失败时返回错误响应
     */
    @RequirePermission("office:message:query")
    @Parameters({
        @Parameter(
                name = "messageId",
                in = ParameterIn.PATH,
                required = true,
                description = "消息ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @Operation(
            operationId = "getMessageDeliveries",
            summary = "查询消息投递记录",
            description = "发送人可见全量投递，接收人仅可见本人投递，data 为投递记录数组")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "获取成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MessageDeliveryListResult.class)))
    })
    @OperationLog(
            module = "消息管理",
            type = OperationLog.OperationType.SELECT,
            description = "查询消息投递记录",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getMessageDeliveries(ServerRequest request) {
        Long messageId = RequestHandlerUtil.safeParseLong(request.pathVariable("messageId"));
        if (messageId == null) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "消息ID无效");
        }

        return currentUserId()
                .flatMapMany(userId -> messageService.getMessageDeliveries(messageId, userId))
                .collectList()
                .map(list -> list.isEmpty() ? Collections.emptyList() : list)
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .onErrorResume(this::handleError);
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 当前登录用户 ID 的 Mono，未认证时以未授权业务异常终止
     */
    private Mono<Long> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .ofType(AuthenticatedUser.class)
                .map(AuthenticatedUser::userId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "未登录")));
    }

    /**
     * 记录异常并委托 {@link RequestHandlerUtil} 统一构建错误响应。
     *
     * @param throwable 异常
     * @return 错误响应 Mono
     */
    private Mono<ServerResponse> handleError(Throwable throwable) {
        log.error("消息管理处理失败: {}", throwable.getMessage(), throwable);
        return requestHandlerUtil.handleError(throwable);
    }

    /** MessageResult Message 统一响应包装。 */
    @Schema(name = "InternalMessageResult")
    public static class MessageResult extends Result<MessageDTO> {}

    /** MessageListResult Message 列表统一响应包装。 */
    @Schema(name = "InternalMessageListResult")
    public static class MessageListResult extends Result<List<MessageDTO>> {}

    /** MessageVoidResult 无数据统一响应包装。 */
    @Schema(name = "InternalMessageVoidResult")
    public static class MessageVoidResult extends Result<Void> {}

    /** MessageCountResult 计数结果统一响应包装。 */
    @Schema(name = "InternalMessageCountResult")
    public static class MessageCountResult extends Result<Long> {}

    /** InboxCursorListResult InboxCursorItem 列表统一响应包装。 */
    @Schema(name = "InternalInboxCursorListResult")
    public static class InboxCursorListResult extends Result<List<InboxCursorItemDTO>> {}

    /** MessageDeliveryListResult MessageDelivery 列表统一响应包装。 */
    @Schema(name = "InternalMessageDeliveryListResult")
    public static class MessageDeliveryListResult extends Result<List<MessageDeliveryDTO>> {}
}
