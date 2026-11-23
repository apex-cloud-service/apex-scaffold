package com.apex.scaffold.knowledgeservice.interfaces.handler;

import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.knowledge.application.dto.KbAskRequest;
import com.apex.scaffold.knowledge.application.dto.KbConversationDTO;
import com.apex.scaffold.knowledge.application.dto.KbMessageDTO;
import com.apex.scaffold.knowledge.application.service.KnowledgeChatAppService;
import com.apex.scaffold.knowledgeservice.interfaces.util.KnowledgeRequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * Handler 内的校验、会话建立及流式失败仍为 HTTP 200 SSE，error data 始终为
 * {@code {code:string,message:string}} JSON；业务消息保留，技术错误使用中文兜底。
 * Handler 外的安全链与权限切面拒绝保持既有 HTTP 401/403，不转换为 SSE。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "知识库-问答会话", description = "SSE 流式知识问答与会话管理；按用户归属隔离，权限码 kb:chat:*")
public class KnowledgeChatHandler {

    /** 会话分页的统一响应结构，仅用于接口文档。 */
    @Schema(name = "KbConversationPageResult")
    public static class KbConversationPageResult extends Result<java.util.List<KbConversationDTO>> {}

    /** 会话消息列表的统一响应结构，仅用于接口文档。 */
    @Schema(name = "KbMessageListResult")
    public static class KbMessageListResult extends Result<java.util.List<KbMessageDTO>> {}

    /** 会话删除的布尔结果，仅用于接口文档。 */
    @Schema(name = "KbConversationBooleanResult")
    public static class KbConversationBooleanResult extends Result<Boolean> {}

    private final KnowledgeChatAppService chatAppService;

    private final KnowledgeRequestHandlerUtil requestUtil;

    /**
     * 发起问答（SSE）。
     *
     * @param request 请求（JSON body）
     * @return SSE 事件流响应
     */
    @RequirePermission("kb:chat:ask")
    @Operation(
            operationId = "kbAsk",
            summary = "知识问答（SSE 流式）",
            description = "以 text/event-stream 流式返回回答，事件类型为 citations（引用溯源）/delta（增量文本）"
                    + "/done（结束）/error（错误）。校验失败、会话建立失败与流式失败均为 HTTP 200 SSE，"
                    + "error 事件 data 为 {code,message} JSON 且技术错误使用中文兜底；"
                    + "Handler 外的认证与鉴权拒绝保持 HTTP 401/403，不转为 SSE。需 kb:chat:ask 权限")
    @RequestBody(
            required = true,
            description = "问答请求；conversationId 为空时新建会话",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = KbAskRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "SSE 事件流；事件类型 citations/delta/done/error，错误以 error 事件承载",
                content = @Content(mediaType = "text/event-stream", schema = @Schema(type = "string"))),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 kb:chat:ask 权限")
    })
    public Mono<ServerResponse> ask(ServerRequest request) {
        return requestUtil
                .currentUser()
                .zipWith(requestUtil.handleRequestBody(request, KbAskRequest.class))
                .flatMapMany(tuple -> chatAppService.ask(tuple.getT1().userId(), tuple.getT2()))
                .onErrorResume(error -> Flux.just(requestUtil.buildErrorEvent(error)))
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
    @Operation(
            operationId = "kbPageConversations",
            summary = "分页查询我的问答会话",
            description = "仅返回当前登录用户自己的会话，按更新时间倒序。需 kb:chat:ask 权限")
    @Parameter(
            name = "page",
            in = ParameterIn.QUERY,
            description = "页码，从 1 开始，缺省 1",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "1", minimum = "1"))
    @Parameter(
            name = "size",
            in = ParameterIn.QUERY,
            description = "每页数量，缺省 20",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "20", minimum = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为会话列表",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KbConversationPageResult.class))),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效")
    })
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
    @Operation(operationId = "kbListMessages", summary = "查询会话消息", description = "返回指定会话的消息列表；会话必须归属当前登录用户。回答消息附带引用溯源")
    @Parameter(
            name = "conversationId",
            in = ParameterIn.PATH,
            required = true,
            description = "会话 ID",
            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    @Parameter(
            name = "limit",
            in = ParameterIn.QUERY,
            description = "返回消息条数上限，缺省 50",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "50", minimum = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为消息列表",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KbMessageListResult.class))),
        @ApiResponse(responseCode = "400", description = "会话 ID 格式非法"),
        @ApiResponse(responseCode = "404", description = "会话不存在或不属于当前用户"),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效")
    })
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
    @Operation(operationId = "kbDeleteConversation", summary = "删除问答会话", description = "删除指定会话及其消息；会话必须归属当前登录用户，不可恢复")
    @Parameter(
            name = "conversationId",
            in = ParameterIn.PATH,
            required = true,
            description = "会话 ID",
            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "删除成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KbConversationBooleanResult.class))),
        @ApiResponse(responseCode = "400", description = "会话 ID 格式非法"),
        @ApiResponse(responseCode = "404", description = "会话不存在或不属于当前用户"),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效")
    })
    public Mono<ServerResponse> deleteConversation(ServerRequest request) {
        Long conversationId = Long.valueOf(request.pathVariable("conversationId"));
        return requestUtil
                .currentUser()
                .flatMap(user -> chatAppService.deleteConversation(user.userId(), conversationId))
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }
}
