package com.apex.scaffold.officeservice.interfaces.handler;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.application.port.OfficeMessageStatsQueryPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * office 域内部端点 Handler（方案 D4 能力计量 / D5 只读统计）.
 *
 * <p>承载 {@code GET /internal/v1/office/messages/stats}：返回租户站内信总量，
 * 契约固定为 {@code Result<{ "messageCount": N }>，供 system 能力计量
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

    private final OfficeMessageStatsQueryPort statsQueryPort;

    /**
     * 租户消息量统计（D4 计量消费；契约固定勿改字段名）。
     *
     * @param request 当前请求（可选查询参数 {@code tenantId}）
     * @return {@code Result<{ "messageCount": N }>}
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
     * 异常映射为响应：安全类错误映射对应 HTTP 状态，其余业务失败保持 HTTP 200 + Result 错误码。
     *
     * @param error 异常
     * @return 错误响应
     */
    private Mono<ServerResponse> toErrorResponse(Throwable error) {
        if (error instanceof BusinessException businessException) {
            int code = businessException.getCode() == null ? ResultCode.ERROR.getCode() : businessException.getCode();
            HttpStatus status =
                    switch (code) {
                        case 400, 40001 -> HttpStatus.BAD_REQUEST;
                        case 401 -> HttpStatus.UNAUTHORIZED;
                        case 403 -> HttpStatus.FORBIDDEN;
                        default -> HttpStatus.OK;
                    };
            return ServerResponse.status(status).bodyValue(Result.error(code, businessException.getMsg()));
        }
        log.error("office 内部统计端点处理失败", error);
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyValue(Result.error(ResultCode.ERROR.getCode(), ResultCode.ERROR.getMsg()));
    }
}
