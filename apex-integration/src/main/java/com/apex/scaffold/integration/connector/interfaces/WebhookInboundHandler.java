package com.apex.scaffold.integration.connector.interfaces;

import com.apex.scaffold.integration.connector.application.WebhookInboundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 入站 webhook Handler（方案 M5 / 批次 E2）。
 *
 * <p>POST {@code /integration/v1/webhook/{connector}}：{@code connector} 为
 * 租户内连接器配置标识（{@code int_connector_config.config_key}）；签名与时间戳
 * 取 {@code X-Signature} / {@code X-Timestamp} 头，租户取可信上下文（缺失时回退
 * {@code tenant} 查询参数，仅限定配置查找范围，验签密钥仍是真实认证手段）。
 * 该端点面向外部系统投递，不经过认证与权限切面。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "集成中心-入站 Webhook", description = "外部系统 webhook 投递入口（验签鉴权，不走认证与权限切面）")
public class WebhookInboundHandler {

    private static final String SIGNATURE_HEADER = "X-Signature";

    private static final String TIMESTAMP_HEADER = "X-Timestamp";

    private final WebhookInboundService inboundService;

    /**
     * 受理一次 webhook 投递。
     *
     * @param request 当前请求（path: connector；header: X-Signature/X-Timestamp；query: tenant 可选）
     * @return 受理结果（重复投递幂等成功）
     */
    @Operation(
            operationId = "integrationWebhookInbound",
            summary = "受理一次 webhook 投递",
            description = "面向外部系统的入站投递入口，不经过认证与权限切面，验签密钥是真实认证手段。"
                    + "签名与时间戳取 X-Signature/X-Timestamp 头；租户取可信上下文，缺失时回退 tenant 查询参数"
                    + "（仅限定配置查找范围）；重复投递幂等成功")
    @Parameters({
        @Parameter(
                name = "connector",
                in = ParameterIn.PATH,
                required = true,
                description = "租户内连接器配置标识（int_connector_config.config_key）",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "tenant",
                in = ParameterIn.QUERY,
                required = false,
                description = "租户标识回退值（仅在缺少可信上下文时限定配置查找范围）",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "X-Signature",
                in = ParameterIn.HEADER,
                required = true,
                description = "请求签名",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "X-Timestamp",
                in = ParameterIn.HEADER,
                required = true,
                description = "请求时间戳（用于防重放）",
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "受理结果（accepted/duplicate/eventId/message）",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = WebhookInboundService.WebhookAck.class))),
        @ApiResponse(responseCode = "401", description = "缺少签名请求头或验签失败"),
        @ApiResponse(responseCode = "400", description = "连接器配置不存在或请求非法")
    })
    public Mono<ServerResponse> accept(ServerRequest request) {
        String connectorKey = request.pathVariable("connector");
        String signature = request.headers().firstHeader(SIGNATURE_HEADER);
        String timestamp = request.headers().firstHeader(TIMESTAMP_HEADER);
        String fallbackTenant = request.queryParam("tenant").orElse(null);
        if (signature == null || signature.isBlank() || timestamp == null || timestamp.isBlank()) {
            return IntegrationResponses.error(new com.apex.scaffold.common.exception.BusinessException(
                    com.apex.scaffold.common.result.ResultCode.UNAUTHORIZED, "缺少签名请求头"));
        }
        return request.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> inboundService.accept(fallbackTenant, connectorKey, signature, timestamp, body))
                .flatMap(ack -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(com.apex.scaffold.common.result.Result.success(ack)))
                .onErrorResume(IntegrationResponses::error);
    }
}
