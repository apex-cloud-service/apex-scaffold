package com.apex.scaffold.officeservice.interfaces.handler;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.InternalEndpointErrors;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.contract.dto.internal.InternalTodoItem;
import com.apex.scaffold.contract.dto.internal.OfficeTodoInternalResponse;
import com.apex.scaffold.office.application.OfficeApprovalService;
import com.apex.scaffold.office.application.port.OfficeMessageStatsQueryPort;
import com.apex.scaffold.office.domain.approval.ApprovalProcess;
import com.apex.scaffold.office.off.application.dto.MessageDTO;
import com.apex.scaffold.office.off.application.service.OffMessageService;
import com.apex.scaffold.officeservice.interfaces.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
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
 * office 域内部端点 Handler（方案 D4 能力计量 / D5 只读统计）.
 *
 * <p>承载 {@code GET /internal/v1/office/messages/stats}：返回租户站内信总量，
 * 契约固定为 {@code data} 含 {@code messageCount} 字段的 JSON 对象，供 system 能力计量
 * （{@code /api/v2/capability-usage} 消息量维度）与聚合门面消费，字段名勿改。
 *
 * <p>信任模型：{@code /internal/v1/**} 在 office 服务为放行路径（集群内服务直连，
 * 不经网关）。租户标识以身份中继建立的可信租户上下文为准：上下文缺失返回 401；
 * 请求参数 {@code tenantId} 仅作一致性校验，与上下文不一致时返回 403，
 * 不允许客户端参数覆盖可信上下文。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "内部契约", description = "集群内服务直连接口，不经网关")
public class OfficeInternalHandler {

    /** 待办条目默认截断上限。 */
    private static final int DEFAULT_TODO_LIMIT = 10;

    /** 待办条目截断上限边界。 */
    private static final int MAX_TODO_LIMIT = 50;

    private final OfficeMessageStatsQueryPort statsQueryPort;

    private final OfficeApprovalService officeApprovalService;

    private final OffMessageService offMessageService;

    /**
     * 租户消息量统计（D4 计量消费；契约固定勿改字段名）。
     *
     * <p>租户以可信租户上下文为准：上下文缺失返回 401；查询参数 {@code tenantId} 仅做一致性
     * 校验，与上下文不一致返回 403。统计包含全部消息状态、排除逻辑删除行，异常统一由
     * 内部端点错误映射转换为对应 HTTP 状态。
     *
     * @param request 当前请求（可选查询参数 {@code tenantId}，可为空；非空时必须与可信上下文一致）
     * @return 200 响应，{@code data} 为 {@code {"messageCount": N}}；错误场景返回对应错误响应
     */
    @Operation(
            operationId = "internalOfficeMessageStats",
            summary = "租户消息量统计（内部）",
            description = "集群内服务直连接口，不经网关。返回租户站内信总量（含全部状态，逻辑删除行除外），"
                    + "供 system 能力计量（D4 消息量维度）与聚合门面（D5）消费；"
                    + "租户标识以可信租户上下文为准，上下文缺失返回 401，"
                    + "tenantId 参数与上下文不一致返回 403")
    @Parameter(
            name = "tenantId",
            in = ParameterIn.QUERY,
            required = false,
            description = "租户标识（计量门面显式传递，须与可信租户上下文一致，否则返回 403）",
            schema = @Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为 {\"messageCount\": 站内信总量}",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(type = "object", example = "{\"messageCount\": 512}"))),
        @ApiResponse(responseCode = "400", description = "参数错误"),
        @ApiResponse(responseCode = "401", description = "缺少可信租户标识"),
        @ApiResponse(responseCode = "403", description = "tenantId 参数与可信租户上下文不一致"),
        @ApiResponse(responseCode = "500", description = "统计查询失败")
    })
    public Mono<ServerResponse> messageStats(ServerRequest request) {
        return resolveTenant(request)
                .flatMap(statsQueryPort::countMessages)
                .flatMap(count -> ServerResponse.ok().bodyValue(Result.success(Map.of("messageCount", count))))
                .onErrorResume(this::toErrorResponse);
    }

    /**
     * 解析租户标识：以可信租户上下文为准，请求参数仅做一致性校验。
     *
     * @param request 当前请求
     * @return 租户标识
     */
    private Mono<String> resolveTenant(ServerRequest request) {
        String paramTenant = request.queryParam("tenantId")
                .map(String::trim)
                .filter(tenantId -> !tenantId.isBlank())
                .orElse(null);
        return Mono.deferContextual(context -> {
            String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            if (tenant == null || tenant.isBlank()) {
                return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "缺少可信租户标识"));
            }
            if (paramTenant != null && !paramTenant.equals(tenant)) {
                return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "租户标识与认证上下文不一致"));
            }
            return Mono.just(tenant);
        });
    }

    /**
     * 当前用户统一待办（统一待办门面消费；契约固定勿改字段名）。
     *
     * <p>单端点返回当前用户待审批流程与未读站内信两组条目：
     * 条目按 {@code approvalLimit}/{@code messageLimit} 截断，总数为截断前真实数量。
     * 用户取自身份中继建立的认证上下文，未认证返回 401。
     *
     * <p>两个分区并行查询后合并为一个响应，任一分区失败即整体失败并映射为错误响应；
     * 上限参数缺失或非数字时回落到默认值，越界收敛到 {@code [1, 50]}。
     *
     * @param request 当前请求（可选查询参数 {@code approvalLimit}、{@code messageLimit}，缺省 10，上限 50）
     * @return 200 响应，{@code data} 为待审批与未读消息两组条目及各自总数；未认证返回 401
     */
    @Operation(
            operationId = "internalOfficeTodo",
            summary = "当前用户统一待办（内部）",
            description = "集群内服务直连接口，不经网关。返回当前用户待审批流程与未读站内信两组条目，"
                    + "供 system 统一待办门面（/api/v2/todo）透传调用方凭证消费；"
                    + "用户取自身份中继建立的认证上下文，未认证返回 401")
    @Parameter(
            name = "approvalLimit",
            in = ParameterIn.QUERY,
            description = "待审批条目返回上限",
            schema = @Schema(type = "integer", defaultValue = "10"))
    @Parameter(
            name = "messageLimit",
            in = ParameterIn.QUERY,
            description = "未读消息条目返回上限",
            schema = @Schema(type = "integer", defaultValue = "10"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为待审批与未读消息两组条目",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OfficeTodoInternalResponse.class))),
        @ApiResponse(responseCode = "401", description = "未认证"),
        @ApiResponse(responseCode = "500", description = "查询失败")
    })
    public Mono<ServerResponse> todo(ServerRequest request) {
        int approvalLimit = parseLimit(request.queryParam("approvalLimit").orElse(null));
        int messageLimit = parseLimit(request.queryParam("messageLimit").orElse(null));
        return currentUserId()
                .flatMap(userId -> Mono.zip(
                        pendingApprovalSection(userId, approvalLimit), unreadMessageSection(userId, messageLimit)))
                .map(tuple -> OfficeTodoInternalResponse.builder()
                        .approvals(tuple.getT1().items())
                        .approvalTotal(tuple.getT1().total())
                        .messages(tuple.getT2().items())
                        .unreadTotal(tuple.getT2().total())
                        .build())
                .flatMap(data -> ServerResponse.ok().bodyValue(Result.success(data)))
                .onErrorResume(this::toErrorResponse);
    }

    /**
     * 查询当前用户待审批条目：全量列表计数，条目按上限截断。
     *
     * @param userId 当前用户 ID
     * @param limit  条目上限
     * @return 待审批条目与总数
     */
    private Mono<ApprovalSection> pendingApprovalSection(Long userId, int limit) {
        return officeApprovalService
                .getPendingApprovals(String.valueOf(userId))
                .collectList()
                .map(processes -> new ApprovalSection(
                        processes.stream()
                                .limit(limit)
                                .map(this::toApprovalItem)
                                .toList(),
                        (long) processes.size()));
    }

    /**
     * 查询当前用户未读站内信条目与总数。
     *
     * <p>收件箱查询复用 {@code OffMessageService#getInbox}（按未读过滤后截断），
     * 总数取 {@code countUnread} 的真实未读数。
     *
     * @param userId 当前用户 ID
     * @param limit  条目上限
     * @return 未读消息条目与总数
     */
    private Mono<MessageSection> unreadMessageSection(Long userId, int limit) {
        return Mono.zip(
                        offMessageService
                                .getInbox(userId, 1, limit)
                                .filter(message -> !Boolean.TRUE.equals(message.getReadStatus()))
                                .take(limit)
                                .collectList(),
                        offMessageService.countUnread(userId))
                .map(tuple -> new MessageSection(
                        tuple.getT1().stream().map(this::toMessageItem).toList(), tuple.getT2()));
    }

    /**
     * 将审批流程映射为统一待办条目。
     *
     * @param process 审批流程
     * @return 统一待办条目
     */
    private InternalTodoItem toApprovalItem(ApprovalProcess process) {
        return InternalTodoItem.builder()
                .itemType("office_approval")
                .title(process.getTitle())
                .occurredAt(process.getInitiateTime())
                .status("PENDING")
                .refId(process.getId() == null ? null : String.valueOf(process.getId()))
                .build();
    }

    /**
     * 将站内信映射为统一待办条目；主题缺失时回退消息内容。
     *
     * @param message 站内信
     * @return 统一待办条目
     */
    private InternalTodoItem toMessageItem(MessageDTO message) {
        String subject = message.getSubject();
        String title = subject == null || subject.isBlank() ? message.getContent() : subject;
        return InternalTodoItem.builder()
                .itemType("office_message")
                .title(title)
                .occurredAt(message.getSendTime())
                .status("UNREAD")
                .refId(message.getId() == null ? null : String.valueOf(message.getId()))
                .build();
    }

    /**
     * 从身份中继建立的认证上下文解析当前用户 ID（与 {@code MessageHandler} 同一模式）。
     *
     * @return 当前用户 ID
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
     * 解析待办条目上限参数；缺失或非法时使用默认值，越界收敛到 [1, 50]。
     *
     * @param value 原始参数
     * @return 有效上限
     */
    private int parseLimit(String value) {
        int parsed = DEFAULT_TODO_LIMIT;
        if (value != null && !value.isBlank()) {
            try {
                parsed = Integer.parseInt(value.trim());
            } catch (NumberFormatException ex) {
                parsed = DEFAULT_TODO_LIMIT;
            }
        }
        return Math.min(MAX_TODO_LIMIT, Math.max(1, parsed));
    }

    /** 待审批条目分组：条目（已截断）与总数。 */
    private record ApprovalSection(List<InternalTodoItem> items, Long total) {}

    /** 未读消息条目分组：条目（已截断）与总数。 */
    private record MessageSection(List<InternalTodoItem> items, Long total) {}

    /**
     * 异常映射为响应：安全类错误映射对应 HTTP 状态，其余业务失败保持 HTTP 200 + Result 错误码。
     *
     * @param error 异常
     * @return 错误响应
     */
    private Mono<ServerResponse> toErrorResponse(Throwable error) {
        return InternalEndpointErrors.toResponse(error, "office 内部统计端点处理失败");
    }
}
