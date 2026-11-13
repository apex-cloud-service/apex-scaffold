package com.apex.scaffold.officeservice.interfaces.handler;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.office.notify.application.NotifyApplicationService;
import com.apex.scaffold.officeservice.interfaces.dto.office.notify.SendNotifyByTemplateRequest;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
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
 * 统一通知内部契约处理器（统一消息门面 / 批次 D1）
 *
 * <p>承载 {@code POST /internal/v1/notify}：集群内服务直连调用统一消息门面按模板投递。
 * 鉴权沿用既有内部契约模式——安全配置对 {@code /internal/v1/**} 放行 Spring Security 认证，
 * 网关对 {@code /internal/**} 拒绝公网流量（403），仅允许集群内服务经服务发现直连。
 * 租户标识由调用方按其已验证的租户上下文传入请求体，本处理器不信任任何客户端头。
 *
 * <p>投递失败由门面收敛为失败结果并留痕（HTTP 仍为 200，业务成败看 data.delivered）；
 * 模板不存在、已停用或参数缺失报业务异常（HTTP 400）。
 *
 * @author Apex Scaffold Team
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "内部契约", description = "集群内服务直连接口，不经网关")
public class InternalNotifyHandler {

    private final NotifyApplicationService notifyApplicationService;
    private final RequestHandlerUtil requestHandlerUtil;

    /** 按模板投递结果的统一响应，仅用于 OpenAPI 建模。 */
    @Schema(name = "InternalNotifySendResult", description = "Result 包装的按模板投递结果")
    public static class InternalNotifySendResult extends Result<NotifyApplicationService.NotifyResult> {}

    /**
     * 按模板投递单条通知。
     *
     * @param request HTTP 请求，请求体为投递参数（租户、模板编码、目标等）
     * @return 投递结果（成功与失败均以 data.delivered 区分）
     */
    @Operation(
            operationId = "internalNotifySendByTemplate",
            summary = "按模板投递通知（内部）",
            description = "集群内服务直连接口，不经网关。按模板编码与渠道投递单条通知；"
                    + "投递失败由门面收敛为失败结果并留痕（HTTP 仍为 200，业务成败看 data.delivered）；"
                    + "模板不存在、已停用或参数缺失返回 400。租户标识由调用方传入请求体，不信任任何客户端头")
    @RequestBody(
            description = "按模板投递参数（租户、模板编码、渠道、目标、模板参数）",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SendNotifyByTemplateRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "投递结果，data.delivered 区分业务成败",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = InternalNotifySendResult.class))),
        @ApiResponse(responseCode = "400", description = "模板不存在/已停用/参数缺失或校验失败"),
        @ApiResponse(responseCode = "401", description = "未认证")
    })
    public Mono<ServerResponse> sendByTemplate(ServerRequest request) {
        return requestHandlerUtil
                .validateAndMap(request, SendNotifyByTemplateRequest.class)
                .flatMap(req -> notifyApplicationService.sendByTemplate(
                        req.getTenantId(), req.getTemplateCode(), req.getChannel(), req.getTarget(), req.getParams()))
                .flatMap(result -> requestHandlerUtil.ok(Result.success(result)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }
}
