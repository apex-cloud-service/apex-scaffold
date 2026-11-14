package com.apex.scaffold.knowledgeservice.interfaces.handler;

import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.knowledge.application.dto.KbAskRequest;
import com.apex.scaffold.knowledge.application.service.KnowledgeChatAppService;
import com.apex.scaffold.knowledgeservice.interfaces.util.KnowledgeRequestHandlerUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 知识问答 Handler：SSE 流式问答与会话管理（业务端，按用户归属隔离）。
 *
 * <p>问答为 SSE 流式端点：响应以 {@code text/event-stream} 承载
 * citations/delta/done/error 四类事件；30s 全局响应超时由网关对
 * {@code Accept: text/event-stream} 的豁免处理（与站内通知流同口径）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeChatHandler {

    private final KnowledgeChatAppService chatAppService;

    private final KnowledgeRequestHandlerUtil requestUtil;

    /**
     * 发起问答（SSE）。
     *
     * @param request 请求（JSON body）
     * @return SSE 事件流响应
     */
    @RequirePermission("kb:chat:ask")
    public Mono<ServerResponse> ask(ServerRequest request) {
        return requestUtil
                .currentUser()
                .zipWith(requestUtil.handleRequestBody(request, KbAskRequest.class))
                .flatMapMany(tuple -> chatAppService.ask(tuple.getT1().userId(), tuple.getT2()))
                .onErrorResume(error ->
                        Flux.just(ServerSentEvent.builder("处理失败").event("error").build()))
                .as(events -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(
                                events,
                                new org.springframework.core.ParameterizedTypeReference<ServerSentEvent<String>>() {}));
    }

    /**
     * 分页查询当前用户会话。
     *
     * @param request 请求（page/size 查询参数）
     * @return 会话分页响应
     */
    public Mono<ServerResponse> conversations(ServerRequest request) {
        int pageNum = KnowledgeRequestHandlerUtil.safeParseInt(
                request.queryParam("page").orElse(null), 1);
        int pageSize = KnowledgeRequestHandlerUtil.safeParseInt(
                request.queryParam("size").orElse(null), 20);
        return requestUtil
                .currentUser()
                .flatMap(user -> chatAppService.listConversations(user.userId(), pageNum, pageSize))
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 查询会话消息。
     *
     * @param request 请求（conversationId 路径变量 + limit 查询参数）
     * @return 消息列表响应
     */
    public Mono<ServerResponse> messages(ServerRequest request) {
        Long conversationId = Long.valueOf(request.pathVariable("conversationId"));
        int limit = KnowledgeRequestHandlerUtil.safeParseInt(
                request.queryParam("limit").orElse(null), 50);
        return requestUtil
                .currentUser()
                .flatMap(user -> chatAppService.listMessages(user.userId(), conversationId, limit))
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 删除会话。
     *
     * @param request 请求（conversationId 路径变量）
     * @return 删除结果响应
     */
    public Mono<ServerResponse> deleteConversation(ServerRequest request) {
        Long conversationId = Long.valueOf(request.pathVariable("conversationId"));
        return requestUtil
                .currentUser()
                .flatMap(user -> chatAppService.deleteConversation(user.userId(), conversationId))
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }
}
