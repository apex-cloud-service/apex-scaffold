package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.infrastructure.notification.NotificationService;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 站内通知 SSE 推送处理器（函数式端点）。
 *
 * <p>提供基于 SSE（Server-Sent Events）的实时通知推送（C6），客户端通过
 * {@code EventSource} 或 fetch 流式接收本站内通知。消息由业务方经
 * {@link NotificationService#publish(String, String)} 发布。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /api/v1/notifications/stream - 订阅当前用户通知流（SSE）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 * @see NotificationService
 */
@Component
@Tag(name = "通知推送", description = "站内通知 SSE 实时推送")
public class NotificationSseHandler extends BaseHandler {

    /** 通知推送服务 */
    private final NotificationService notificationService;

    /**
     * 创建 SSE 通知处理器。
     *
     * @param notificationService 通知推送服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public NotificationSseHandler(
            NotificationService notificationService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.notificationService = notificationService;
    }

    /**
     * 订阅当前用户的通知流（SSE）。
     *
     * <p>响应为 {@code text/event-stream}，认证用户建立长连接后实时接收
     * 本站内通知；空消息每 30s 发送心跳保活（{@code :heartbeat} 注释行）。</p>
     *
     * @param request HTTP 请求（携带认证 Principal）
     * @return SSE 事件流响应
     */
    @Operation(
            operationId = "streamNotifications",
            summary = "订阅当前用户通知流（SSE）",
            description = "建立SSE长连接，实时推送站内通知给当前登录用户。事件类型为 'notification'，每30秒发送心跳以保持连接活跃。需要用户认证"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "SSE连接建立成功，开始推送通知流",
                    content = @Content(
                            mediaType = "text/event-stream",
                            schema = @Schema(implementation = String.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "未授权，用户未登录或认证信息无效"
            )
    })
    public Mono<ServerResponse> stream(ServerRequest request) {
        return request.principal()
                .flatMap(principal -> {
                    String recipient = principal.getName();
                    Flux<org.springframework.http.codec.ServerSentEvent<String>> events = notificationService.stream(
                                    recipient)
                            .map(msg -> org.springframework.http.codec.ServerSentEvent.builder(msg.content())
                                    .event("notification")
                                    .build())
                            .mergeWith(Flux.interval(Duration.ofSeconds(30))
                                    .map(i -> org.springframework.http.codec.ServerSentEvent.<String>builder()
                                            .comment("heartbeat")
                                            .build()));
                    return ServerResponse.ok()
                            .contentType(MediaType.TEXT_EVENT_STREAM)
                            .body(events, org.springframework.http.codec.ServerSentEvent.class);
                })
                .switchIfEmpty(Mono.error(new com.apex.scaffold.common.exception.BusinessException(
                        com.apex.scaffold.common.result.ResultCode.UNAUTHORIZED)));
    }
}
