package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.domain.repository.SystemMessageRepository;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 站内信读写处理器（函数式端点）。
 *
 * <p>提供站内信的读侧闭环接口：分页查询收件箱、统计未读数、单条标记已读与
 * 全部标记已读。所有接口均以认证上下文中的当前登录用户为过滤条件，避免越权
 * 访问他人消息。消息的写入与实时推送由
 * {@link com.apex.scaffold.infrastructure.notification.NotificationService} 负责。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET  /api/v1/messages/inbox - 分页查询当前用户站内信</li>
 *   <li>GET  /api/v1/messages/unread-count - 查询当前用户未读消息数</li>
 *   <li>POST /api/v1/messages/{id}/read - 标记单条消息已读（幂等）</li>
 *   <li>POST /api/v1/messages/read-all - 标记当前用户全部消息已读</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 * @see SystemMessageRepository
 */
@Component
@Tag(name = "站内信", description = "站内信读写：收件箱查询、未读数统计与已读标记")
public class SystemMessageHandler extends BaseHandler {

    /** 默认页码 */
    private static final int DEFAULT_PAGE = 1;

    /** 默认每页大小 */
    private static final int DEFAULT_SIZE = 10;

    /** 站内信仓储 */
    private final SystemMessageRepository systemMessageRepository;

    /** 用户服务，用于将认证的 openId 解析为业务用户 ID */
    private final UserService userService;

    /**
     * 创建站内信处理器。
     *
     * @param systemMessageRepository 站内信仓储
     * @param userService             用户服务
     * @param messageUtils            国际化消息工具
     */
    public SystemMessageHandler(
            SystemMessageRepository systemMessageRepository, UserService userService, MessageUtils messageUtils) {
        super(messageUtils);
        this.systemMessageRepository = systemMessageRepository;
        this.userService = userService;
    }

    /**
     * 分页查询当前登录用户的站内信收件箱。
     *
     * <p>支持 {@code page}、{@code size} 分页参数与 {@code unreadOnly} 未读过滤，
     * 缺省时按第 1 页、每页 10 条返回。</p>
     *
     * @param request HTTP 请求（携带认证 Principal）
     * @return 统一 JSON 响应，data 为分页消息列表
     */
    @Operation(
            operationId = "listInboxMessages",
            summary = "分页查询站内信收件箱",
            description = "按创建时间倒序分页返回当前登录用户的站内信，可通过 unreadOnly 过滤未读消息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "分页查询成功"),
        @ApiResponse(responseCode = "401", description = "未授权，用户未登录或认证信息无效")
    })
    public Mono<ServerResponse> inbox(ServerRequest request) {
        int page = safeParseInt(request.queryParam("page").orElse(null), DEFAULT_PAGE);
        int size = safeParseInt(request.queryParam("size").orElse(null), DEFAULT_SIZE);
        boolean unreadOnly = Boolean.parseBoolean(request.queryParam("unreadOnly").orElse("false"));
        return currentUserId(request, userService)
                .flatMap(userId -> systemMessageRepository.pageByUser(userId, page, size, unreadOnly))
                .flatMap(this::buildSuccess);
    }

    /**
     * 查询当前登录用户的未读站内信数量。
     *
     * @param request HTTP 请求（携带认证 Principal）
     * @return 统一 JSON 响应，data 为未读消息数
     */
    @Operation(
            operationId = "countUnreadMessages",
            summary = "查询未读站内信数量",
            description = "统计当前登录用户尚未标记已读的站内信数量，可用于角标展示")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "统计成功"),
        @ApiResponse(responseCode = "401", description = "未授权，用户未登录或认证信息无效")
    })
    public Mono<ServerResponse> unreadCount(ServerRequest request) {
        return currentUserId(request, userService)
                .flatMap(systemMessageRepository::countUnread)
                .flatMap(this::buildSuccess);
    }

    /**
     * 将当前登录用户的单条站内信标记为已读。
     *
     * <p>仅能操作归属当前用户的消息；重复标记已读幂等返回成功。</p>
     *
     * @param request HTTP 请求（路径参数 {@code id} 为消息 ID）
     * @return 统一 JSON 响应，data 为本次是否由未读转为已读
     */
    @Operation(
            operationId = "markMessageRead",
            summary = "标记站内信已读",
            description = "将当前登录用户的指定站内信标记为已读，重复调用幂等返回成功")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "标记成功"),
        @ApiResponse(responseCode = "400", description = "消息 ID 格式错误"),
        @ApiResponse(responseCode = "401", description = "未授权，用户未登录或认证信息无效")
    })
    public Mono<ServerResponse> markRead(ServerRequest request) {
        return Mono.defer(() -> {
            long messageId = requirePositiveId(request);
            return currentUserId(request, userService)
                    .flatMap(userId -> systemMessageRepository.markRead(userId, messageId))
                    .flatMap(this::buildSuccess);
        });
    }

    /**
     * 将当前登录用户的全部未读站内信标记为已读。
     *
     * @param request HTTP 请求（携带认证 Principal）
     * @return 统一 JSON 响应，data 为本次实际标记已读的消息数量
     */
    @Operation(
            operationId = "markAllMessagesRead",
            summary = "全部标记已读",
            description = "将当前登录用户的所有未读站内信一次性标记为已读，返回本次实际标记的数量")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "标记成功"),
        @ApiResponse(responseCode = "401", description = "未授权，用户未登录或认证信息无效")
    })
    public Mono<ServerResponse> markAllRead(ServerRequest request) {
        return currentUserId(request, userService)
                .flatMap(systemMessageRepository::markAllRead)
                .flatMap(this::buildSuccess);
    }
}
