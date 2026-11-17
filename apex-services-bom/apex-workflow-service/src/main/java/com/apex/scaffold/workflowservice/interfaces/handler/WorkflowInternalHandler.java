package com.apex.scaffold.workflowservice.interfaces.handler;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.contract.dto.internal.InternalTodoItem;
import com.apex.scaffold.workflow.application.dto.WorkflowTaskDTO;
import com.apex.scaffold.workflow.application.query.WorkflowInternalStatsService;
import com.apex.scaffold.workflow.application.query.WorkflowQueryService;
import com.apex.scaffold.workflowservice.interfaces.security.AuthenticatedUser;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 工作流域内部端点 Handler（方案 D5 只读统计）。
 *
 * <p>承载 {@code GET /internal/v1/workflow/stats}：返回当前租户进行中流程实例数，
 * 契约固定为 {@code data} 含 {@code inProgressInstances} 字段的 JSON 对象，供 system 聚合门面
 * （{@code GET /internal/v1/overview}）消费，字段名勿改。
 *
 * <p>租户口径：该路径在安全配置中要求已认证，租户取自验签 JWT 建立的可信
 * Reactor Context（由 {@code WorkflowInternalStatsService} fail-closed 强制），
 * 不接受请求参数指定租户。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "内部契约", description = "集群内服务直连接口，不经网关")
public class WorkflowInternalHandler {

    /** 默认页码。 */
    private static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页大小。 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页大小上限：内部待办仅用于个人工作台聚合，防止一次性拉取全量任务。 */
    private static final int MAX_PAGE_SIZE = 50;

    private final WorkflowInternalStatsService workflowInternalStatsService;

    private final WorkflowQueryService workflowQueryService;

    /**
     * 进行中流程实例统计（D5 聚合门面消费；契约固定勿改字段名）。
     *
     * @param request 当前请求
     * @return {@code Result<{ "inProgressInstances": N }>}
     */
    @Operation(
            operationId = "internalWorkflowStats",
            summary = "进行中流程实例统计（内部）",
            description = "集群内服务直连接口，不经网关。返回当前租户进行中流程实例数，"
                    + "供 system 聚合门面（D5）消费；租户取自验签 JWT 建立的可信 Reactor Context，"
                    + "不接受请求参数指定租户")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为 {\"inProgressInstances\": 进行中实例数}",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(type = "object", example = "{\"inProgressInstances\": 12}"))),
        @ApiResponse(responseCode = "401", description = "未认证或缺少可信租户上下文"),
        @ApiResponse(responseCode = "500", description = "统计查询失败")
    })
    public Mono<ServerResponse> stats(ServerRequest request) {
        return workflowInternalStatsService
                .countInProgressInstances()
                .flatMap(inProgress ->
                        ServerResponse.ok().bodyValue(Result.success(Map.of("inProgressInstances", inProgress))))
                .onErrorResume(this::toErrorResponse);
    }

    /**
     * 当前用户待办任务分页（统一待办门面消费；契约固定勿改字段名）。
     *
     * @param request 当前请求（查询参数 {@code pageNum}、{@code pageSize}，缺省 1/10）
     * @return {@code Result<PageResponse<InternalTodoItem>>}
     */
    @Operation(
            operationId = "internalWorkflowTodoTasks",
            summary = "当前用户待办任务分页（内部）",
            description = "集群内服务直连接口，不经网关。返回当前用户待办任务（PENDING）分页，"
                    + "供 system 统一待办门面（/api/v2/todo）透传调用方凭证消费；"
                    + "用户取自验签 JWT 建立的认证上下文，未认证返回 401")
    @Parameter(
            name = "pageNum",
            in = ParameterIn.QUERY,
            description = "页码，从 1 开始",
            schema = @Schema(type = "integer", defaultValue = "1"))
    @Parameter(
            name = "pageSize",
            in = ParameterIn.QUERY,
            description = "每页大小，上限 50",
            schema = @Schema(type = "integer", defaultValue = "10"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为统一待办条目分页",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "401", description = "未认证"),
        @ApiResponse(responseCode = "500", description = "查询失败")
    })
    public Mono<ServerResponse> todoTasks(ServerRequest request) {
        int pageNum = parseIntOrDefault(request.queryParam("pageNum").orElse(null), DEFAULT_PAGE_NUM);
        int pageSize = Math.min(
                MAX_PAGE_SIZE, parseIntOrDefault(request.queryParam("pageSize").orElse(null), DEFAULT_PAGE_SIZE));
        return getCurrentUserId()
                .flatMap(userId -> workflowQueryService.listMyTasks(userId, pageNum, pageSize))
                .map(this::toTodoPage)
                .flatMap(data -> ServerResponse.ok().bodyValue(Result.success(data)))
                .onErrorResume(this::toErrorResponse);
    }

    /**
     * 将待办任务分页映射为统一待办条目分页。
     *
     * @param page 待办任务分页
     * @return 统一待办条目分页
     */
    private PageResponse<InternalTodoItem> toTodoPage(PageResponse<WorkflowTaskDTO> page) {
        List<InternalTodoItem> items = page.getRecords().stream()
                .map(task -> InternalTodoItem.builder()
                        .itemType("workflow_task")
                        .title(task.getTaskName())
                        .occurredAt(task.getCreateTime())
                        .status("PENDING")
                        .refId(task.getId() == null ? null : String.valueOf(task.getId()))
                        .build())
                .toList();
        return PageResponse.of(items, page.getTotal(), page.getPageNum(), page.getPageSize());
    }

    /**
     * 从认证上下文解析当前用户 ID（与 {@code WorkflowHandler} 同一模式）。
     *
     * @return 当前用户 ID
     */
    private Mono<Long> getCurrentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .ofType(AuthenticatedUser.class)
                .map(AuthenticatedUser::userId)
                .switchIfEmpty(Mono.error(new IllegalStateException("当前请求未包含有效认证用户")));
    }

    /**
     * 解析整型查询参数，缺失或非法时使用默认值。
     *
     * @param value        原始参数
     * @param defaultValue 默认值
     * @return 解析结果
     */
    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    /**
     * 异常映射为响应：租户上下文缺失按 401 处理，其余业务失败保持 HTTP 200 + Result 错误码。
     *
     * @param error 异常
     * @return 错误响应
     */
    private Mono<ServerResponse> toErrorResponse(Throwable error) {
        if (error instanceof com.apex.scaffold.common.exception.BusinessException businessException) {
            int code = businessException.getCode() == null ? ResultCode.ERROR.getCode() : businessException.getCode();
            return ServerResponse.ok().bodyValue(Result.error(code, businessException.getMsg()));
        }
        if (error instanceof IllegalStateException) {
            return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                    .bodyValue(Result.error(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg()));
        }
        log.error("工作流内部统计端点处理失败", error);
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyValue(Result.error(ResultCode.ERROR.getCode(), ResultCode.ERROR.getMsg()));
    }
}
