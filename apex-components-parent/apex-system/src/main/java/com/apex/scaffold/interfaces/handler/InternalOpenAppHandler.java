package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.internal.OpenAppRateLimitTierResponse;
import com.apex.scaffold.application.openplatform.OpenAppAdminService;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 开放应用限流档内部查询 Handler（方案 D2 按应用分档限流）。
 *
 * <p>承载 {@code GET /internal/v1/open-apps/{clientId}/rate-limit-tier}：供网关
 * 开放接口限流过滤器消费（{@code /open-api/**} 按 {@code clientId} 分档限流）。
 * 集群内服务直连，不经网关；未知客户端与查询异常均以 {@code default} 档数据化返回
 * （HTTP 200），便于网关 fail-open 判定与负结果缓存。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "内部契约-开放应用限流", description = "供网关按 clientId 消费的分档限流查询；集群内直连，不经网关")
public class InternalOpenAppHandler {

    /** 默认限流档（与 {@code OpenAppAdminService} 兜底口径一致） */
    private static final String DEFAULT_TIER = "default";

    /** clientId 路径参数长度上限（与客户端注册约束对齐的防御性校验） */
    private static final int MAX_CLIENT_ID_LENGTH = 128;

    private final OpenAppAdminService openAppAdminService;

    /**
     * 查询开放应用限流档。
     *
     * @param request 当前请求（路径参数 {@code clientId}）
     * @return {@code Result<OpenAppRateLimitTierResponse>}
     */
    @Operation(
            operationId = "internalOpenAppRateLimitTier",
            summary = "查询开放应用限流档（内部）",
            description = "集群内服务直连接口，不经网关。返回开放应用注册声明的限流档编码；"
                    + "非开放应用、未声明档位或未知客户端以 default 档数据化返回（HTTP 200），"
                    + "查询异常同样回退 default，供网关 fail-open 判定与负结果缓存")
    @Parameter(
            name = "clientId",
            in = ParameterIn.PATH,
            required = true,
            description = "开放应用客户端 ID（长度上限 128）",
            schema = @Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Result 封装的限流档快照（未知客户端返回 default 档）",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OpenAppRateLimitTierResponse.class))),
        @ApiResponse(responseCode = "400", description = "clientId 缺失或超长")
    })
    public Mono<ServerResponse> getRateLimitTier(ServerRequest request) {
        String clientId = request.pathVariable("clientId");
        if (clientId == null || clientId.isBlank() || clientId.length() > MAX_CLIENT_ID_LENGTH) {
            return ServerResponse.badRequest().bodyValue(Result.error(ResultCode.PARAM_ERROR.getMsg()));
        }
        return openAppAdminService
                .getRateLimitTier(clientId)
                .map(tier -> new OpenAppRateLimitTierResponse(clientId, tier))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(error -> {
                    log.warn("开放应用限流档查询失败，回退默认档: clientId={}", clientId, error);
                    return ServerResponse.ok()
                            .bodyValue(Result.success(new OpenAppRateLimitTierResponse(clientId, DEFAULT_TIER)));
                });
    }
}
