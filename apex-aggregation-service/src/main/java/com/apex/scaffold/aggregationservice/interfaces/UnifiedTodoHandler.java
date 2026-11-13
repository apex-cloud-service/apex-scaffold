package com.apex.scaffold.aggregationservice.interfaces;

import com.apex.scaffold.aggregationservice.application.UnifiedTodoAggregationService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.contract.dto.internal.UnifiedTodoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 统一待办查询 Handler（S4-c）。
 *
 * <p>承载 {@code GET /api/v2/todo}：聚合当前用户在工作流（待办任务）与 office
 * （待审批 / 未读站内信）的待办条目。任一数据源超时、不可达或舱壁饱和时，
 * 该源降级为 {@code DEGRADED}，整体返回部分可用，不因单源失败返回错误。
 *
 * <p>身份取自本服务验签 JWT 构建的 {@link Principal}（{@code getName()} 为用户标识，
 * 见 {@code IdentityRelayAuthenticationFilter}）；未认证或标识非法返回 401。
 * 调用方 {@code Authorization} 头原样透传给下游内部端点；可信租户经内部签名过滤器
 * 转换为 {@code X-Internal-Tenant} 参与下游签名校验。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "统一待办", description = "跨域待办聚合：工作流任务、office 审批与站内信")
public class UnifiedTodoHandler {

    private final UnifiedTodoAggregationService aggregationService;

    /**
     * 查询当前用户统一待办。
     *
     * @param request 当前请求
     * @return {@code Result<UnifiedTodoResponse>}
     */
    @Operation(
            operationId = "unifiedTodo",
            summary = "查询当前用户统一待办",
            description = "聚合当前用户在工作流（待办任务）与 office（待审批/未读站内信）的待办条目；" + "任一数据源不可达时该源按 DEGRADED 降级（部分可用），不缓存、不合并并发请求")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为各数据源分组条目（degraded 标识是否部分降级）",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = UnifiedTodoResponse.class))),
        @ApiResponse(responseCode = "401", description = "未认证"),
        @ApiResponse(responseCode = "500", description = "聚合失败")
    })
    public Mono<ServerResponse> todo(ServerRequest request) {
        String authorization = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
        return request.principal()
                .flatMap(principal -> Mono.justOrEmpty(parseUserId(principal.getName())))
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED)))
                .flatMap(userId -> aggregationService.getUnifiedTodo(userId, authorization))
                .map(response -> Result.success(response).setTraceId(TraceIdUtil.getTraceId()))
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result))
                .onErrorResume(this::toErrorResponse);
    }

    /**
     * 解析用户标识字符串。
     *
     * @param name 主体名称
     * @return 用户 ID；非法时返回 {@code null}
     */
    private Long parseUserId(String name) {
        try {
            return name == null ? null : Long.valueOf(name);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 将异常映射为统一响应：未认证 401，其余按 500 处理。
     *
     * @param error 异常
     * @return 错误响应
     */
    private Mono<ServerResponse> toErrorResponse(Throwable error) {
        if (error instanceof BusinessException businessException) {
            Result<Void> body = Result.<Void>error(businessException.getCode(), businessException.getMsg())
                    .setTraceId(TraceIdUtil.getTraceId());
            if (businessException.getCode() != null
                    && businessException.getCode().equals(ResultCode.UNAUTHORIZED.getCode())) {
                return ServerResponse.status(HttpStatus.UNAUTHORIZED).bodyValue(body);
            }
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).bodyValue(body);
        }
        log.error("统一待办聚合失败", error);
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyValue(Result.error(ResultCode.ERROR).setTraceId(TraceIdUtil.getTraceId()));
    }
}
