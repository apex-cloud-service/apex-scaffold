package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.internal.UnifiedTodoResponse;
import com.apex.scaffold.application.service.TodoFacadeService;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 统一待办聚合 Handler（S4-a 对外口径）。
 *
 * <p>承载 {@code GET /api/v2/todo}：聚合当前用户在工作流（待办任务）、
 * office（待审批/未读站内信）与 system（未读通知）三个域的待办条目。
 * 条目为用户个人数据，仅需认证（无需权限码）；各域条目的查看/办理权限
 * 由归属域校验，聚合层只做组合不做鉴权判断。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@Tag(name = "统一待办", description = "跨域待办聚合：工作流任务、office 审批与站内信、系统通知")
public class UnifiedTodoHandler extends BaseHandler {

    private final TodoFacadeService todoFacadeService;

    private final UserService userService;

    /**
     * 创建统一待办处理器。
     *
     * @param todoFacadeService 统一待办聚合门面
     * @param userService       用户服务，用于将认证的 openId 解析为业务用户 ID
     * @param messageUtils      国际化消息工具
     */
    public UnifiedTodoHandler(TodoFacadeService todoFacadeService, UserService userService, MessageUtils messageUtils) {
        super(messageUtils);
        this.todoFacadeService = todoFacadeService;
        this.userService = userService;
    }

    /**
     * 查询当前用户的统一待办聚合。
     *
     * @param request 当前请求（无参数；用户取自认证上下文）
     * @return 聚合响应（{@code Result<UnifiedTodoResponse>}）
     */
    @Operation(
            operationId = "unifiedTodo",
            summary = "查询当前用户统一待办",
            description = "聚合当前用户在工作流（待办任务）、office（待审批/未读站内信）与 system（未读通知）"
                    + "的待办条目；任一数据源不可达时该源按 DEGRADED 降级（部分可用），响应含 degraded 标记；"
                    + "条目为用户个人数据，仅要求认证，实时聚合不缓存")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Result 封装的聚合响应（降级源以 DEGRADED 呈现）",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = UnifiedTodoResponse.class))),
        @ApiResponse(responseCode = "401", description = "未授权，用户未登录或认证信息无效"),
        @ApiResponse(responseCode = "500", description = "聚合失败")
    })
    @OperationLog(
            module = "UnifiedTodo",
            type = OperationLog.OperationType.SELECT,
            description = "unifiedTodo",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> todo(ServerRequest request) {
        String authorization = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
        return currentUserId(request, userService)
                .flatMap(userId -> todoFacadeService.getUnifiedTodo(userId, authorization))
                .flatMap(this::buildSuccess)
                .onErrorResume(this::toErrorResponse);
    }

    /**
     * 异常映射为响应：未认证保持 401，其余聚合失败返回 500；
     * 数据源级降级已在门面内消化，不会到达此处。
     *
     * @param error 异常
     * @return 错误响应
     */
    private Mono<ServerResponse> toErrorResponse(Throwable error) {
        if (error instanceof BusinessException businessException
                && businessException.getCode() != null
                && businessException.getCode() == ResultCode.UNAUTHORIZED.getCode()) {
            return buildError(ResultCode.UNAUTHORIZED, TraceIdUtil.getTraceId());
        }
        log.error("统一待办聚合查询失败", error);
        return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
    }
}
