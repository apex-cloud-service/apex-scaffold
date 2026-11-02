package com.apex.scaffold.workflowservice.interfaces.handler;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.workflow.application.query.WorkflowInternalStatsService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 工作流域内部端点 Handler（方案 D5 只读统计）。
 *
 * <p>承载 {@code GET /internal/v1/workflow/stats}：返回当前租户进行中流程实例数，
 * 契约固定为 {@code Result<{ "inProgressInstances": N }>，供 system 聚合门面
 * （{@code GET /internal/v1/overview}）消费，字段名勿改。
 *
 * <p>租户口径：该路径在安全配置中要求已认证，租户取自验签 JWT 建立的可信
 * Reactor Context（由 {@code WorkflowInternalStatsService} fail-closed 强制），
 * 不接受请求参数指定租户。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowInternalHandler {

    private final WorkflowInternalStatsService workflowInternalStatsService;

    /**
     * 进行中流程实例统计（D5 聚合门面消费；契约固定勿改字段名）。
     *
     * @param request 当前请求
     * @return {@code Result<{ "inProgressInstances": N }>}
     */
    public Mono<ServerResponse> stats(ServerRequest request) {
        return workflowInternalStatsService
                .countInProgressInstances()
                .flatMap(inProgress ->
                        ServerResponse.ok().bodyValue(Result.success(Map.of("inProgressInstances", inProgress))))
                .onErrorResume(this::toErrorResponse);
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
