package com.apex.scaffold.knowledge.application.service;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.knowledge.application.assembler.KnowledgeAssembler;
import com.apex.scaffold.knowledge.application.dto.KbAskRequest;
import com.apex.scaffold.knowledge.application.dto.KbChatErrorPayload;
import com.apex.scaffold.knowledge.application.dto.KbConversationDTO;
import com.apex.scaffold.knowledge.application.dto.KbMessageDTO;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.ChatPort;
import com.apex.scaffold.knowledge.domain.gateway.KbConversationRepository;
import com.apex.scaffold.knowledge.domain.model.ChatMessage;
import com.apex.scaffold.knowledge.domain.model.KbConversation;
import com.apex.scaffold.knowledge.domain.model.KbMessage;
import com.apex.scaffold.knowledge.domain.model.VectorSearchHit;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 知识问答应用服务：检索增强生成（RAG）编排，SSE 流式应答并持久化引用溯源。
 *
 * <p>SSE 事件协议：{@code citations}（引用列表 JSON，先于正文发出）→ 若干 {@code delta}
 * （增量文本）→ {@code done}（conversationId/messageId JSON）；失败以 {@code error}
 * 事件承载 code 与 message。会话与消息受租户拦截器及归属用户双重校验；检索为空且
 * 策略为 fallback 时不调用模型，直接返回兜底回复以控制无效调用成本。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Slf4j
@Service
public class KnowledgeChatAppService {

    /** 检索为空时的兜底回复模板。 */
    private static final String EMPTY_CONTEXT_FALLBACK = "知识库中未检索到与问题相关的内容，请先上传相关文档或调整提问方式。";

    private static final int TITLE_MAX_LENGTH = 30;

    private final KbConversationRepository conversationRepository;

    private final KnowledgeSearchAppService searchService;

    private final ChatPort chatPort;

    private final KnowledgeProperties properties;

    private final KnowledgeAssembler assembler;

    private final ObjectMapper objectMapper;

    /**
     * 构建问答编排服务。
     *
     * @param conversationRepository 会话仓储
     * @param searchService 检索服务
     * @param chatPort 模型对话端口
     * @param properties 运行配置
     * @param assembler 装配器
     * @param objectMapper JSON 序列化器
     */
    public KnowledgeChatAppService(
            KbConversationRepository conversationRepository,
            KnowledgeSearchAppService searchService,
            ChatPort chatPort,
            KnowledgeProperties properties,
            KnowledgeAssembler assembler,
            ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.searchService = searchService;
        this.chatPort = chatPort;
        this.properties = properties;
        this.assembler = assembler;
        this.objectMapper = objectMapper;
    }

    /**
     * 发起问答，返回 SSE 事件流。
     *
     * <p>会话建立阶段的错误（租户缺失、会话不存在）作为响应式错误交由调用方处理；
     * HTTP Handler 将其转换为 HTTP 200 的 {@code error} SSE，不是 HTTP 错误响应。
     * 流式阶段的错误在本服务内转为 {@code error} SSE；两阶段 data 均为
     * {@code {code:string,message:string}} JSON，技术异常不对外透传。
     * 安全链及权限切面仍在调用前拒绝访问，保持既有 HTTP 401/403。
     *
     * @param userId 当前用户 ID
     * @param request 问答请求
     * @return SSE 事件流
     */
    public Flux<ServerSentEvent<String>> ask(Long userId, KbAskRequest request) {
        return ensureConversation(userId, request).flatMapMany(conversation -> askStream(conversation, request));
    }

    /**
     * 分页查询用户会话。
     *
     * @param userId 用户 ID
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 会话分页 DTO
     */
    public Mono<PageResponse<KbConversationDTO>> listConversations(Long userId, int pageNum, int pageSize) {
        return conversationRepository
                .findPageByUser(userId, pageNum, pageSize)
                .map(page -> PageResponse.of(
                        page.getList().stream()
                                .map(assembler::toConversationDTO)
                                .toList(),
                        page.getTotal(),
                        pageNum,
                        pageSize));
    }

    /**
     * 查询会话消息。
     *
     * @param userId 用户 ID
     * @param conversationId 会话 ID
     * @param limit 最大条数
     * @return 消息 DTO 列表
     */
    public Mono<List<KbMessageDTO>> listMessages(Long userId, Long conversationId, int limit) {
        return requireConversation(conversationId, userId)
                .flatMap(conversation -> conversationRepository.findMessages(conversationId, limit))
                .map(messages -> messages.stream().map(assembler::toMessageDTO).toList());
    }

    /**
     * 删除会话（仅归属用户）。
     *
     * @param userId 用户 ID
     * @param conversationId 会话 ID
     * @return 删除结果
     */
    public Mono<Boolean> deleteConversation(Long userId, Long conversationId) {
        return conversationRepository.deleteByIdAndUser(conversationId, userId);
    }

    /**
     * 确保会话存在：无会话 ID 时以提问截断为标题新建。
     *
     * @param userId 用户 ID
     * @param request 问答请求
     * @return 会话模型
     */
    private Mono<KbConversation> ensureConversation(Long userId, KbAskRequest request) {
        String tenantId = TenantContext.currentTenantId().orElse(null);
        if (tenantId == null || tenantId.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "租户上下文缺失，拒绝知识问答"));
        }
        if (request.conversationId() == null) {
            return conversationRepository.insert(KbConversation.builder()
                    .title(truncateTitle(request.question()))
                    .userId(userId)
                    .build());
        }
        return requireConversation(request.conversationId(), userId);
    }

    /**
     * 流式问答主体：历史装载 → 用户消息落库 → 检索 → 模型流式应答 → 回答落库。
     *
     * @param conversation 会话
     * @param request 问答请求
     * @return SSE 事件流
     */
    private Flux<ServerSentEvent<String>> askStream(KbConversation conversation, KbAskRequest request) {
        int historySize = Math.max(0, properties.getChat().getHistorySize());
        return conversationRepository
                .findMessages(conversation.getId(), historySize)
                .map(messages -> messages.stream()
                        .map(message -> new ChatMessage(
                                message.getRole() == KbMessage.Role.USER
                                        ? ChatMessage.ROLE_USER
                                        : ChatMessage.ROLE_ASSISTANT,
                                message.getContent()))
                        .toList())
                .flatMap(history -> conversationRepository
                        .insertMessage(KbMessage.builder()
                                .conversationId(conversation.getId())
                                .role(KbMessage.Role.USER)
                                .content(request.question())
                                .build())
                        .then(searchService.retrieveTopK(
                                request.question(),
                                request.spaceIds(),
                                properties.getSearch().getTopK()))
                        .map(hits -> new AskContext(conversation, history, request.question(), hits)))
                .flatMapMany(this::emitAnswer)
                .onErrorResume(error -> {
                    log.error("知识问答流式应答失败: conversationId={}", conversation.getId(), error);
                    return Flux.just(errorEvent(error));
                });
    }

    /**
     * 组装并发出回答事件流（citations → delta… → done），回答完成即落库。
     *
     * @param context 问答上下文
     * @return SSE 事件流
     */
    private Flux<ServerSentEvent<String>> emitAnswer(AskContext context) {
        List<KbMessage.Citation> citations = assembler.toCitations(context.hits());
        ServerSentEvent<String> citationsEvent = sse("citations", citationsJson(citations));
        if (context.hits().isEmpty()
                && "fallback".equalsIgnoreCase(properties.getChat().getEmptyContextStrategy())) {
            return emitModelAnswer(context, citations, Flux.just(EMPTY_CONTEXT_FALLBACK), citationsEvent);
        }
        String systemPrompt = buildSystemPrompt(context.hits());
        return emitModelAnswer(
                context,
                citations,
                chatPort.streamChat(systemPrompt, context.history(), context.question()),
                citationsEvent);
    }

    /**
     * 发送增量文本并在完成时持久化回答消息。
     *
     * @param context 问答上下文
     * @param citations 引用溯源
     * @param deltas 增量文本流
     * @param citationsEvent citations 事件
     * @return SSE 事件流
     */
    private Flux<ServerSentEvent<String>> emitModelAnswer(
            AskContext context,
            List<KbMessage.Citation> citations,
            Flux<String> deltas,
            ServerSentEvent<String> citationsEvent) {
        StringBuilder answer = new StringBuilder();
        return Flux.just(citationsEvent)
                .concatWith(deltas.map((String delta) -> {
                    answer.append(delta);
                    return sse("delta", delta);
                }))
                .concatWith(Mono.defer(() -> persistAnswer(context, citations, answer.toString()))
                        .map(saved -> doneEvent(context.conversation().getId(), saved.getId())));
    }

    /**
     * 持久化回答消息并刷新会话更新时间。
     *
     * @param context 问答上下文
     * @param citations 引用溯源
     * @param answer 完整回答文本
     * @return 已保存消息
     */
    private Mono<KbMessage> persistAnswer(AskContext context, List<KbMessage.Citation> citations, String answer) {
        return conversationRepository
                .insertMessage(KbMessage.builder()
                        .conversationId(context.conversation().getId())
                        .role(KbMessage.Role.ASSISTANT)
                        .content(answer)
                        .citations(citations)
                        .build())
                .flatMap(saved -> conversationRepository
                        .update(KbConversation.builder()
                                .id(context.conversation().getId())
                                .title(context.conversation().getTitle())
                                .userId(context.conversation().getUserId())
                                .build())
                        .thenReturn(saved));
    }

    /**
     * 构建带引用标记的系统提示词；上下文超出配置上限时截断。
     *
     * @param hits 检索命中
     * @return 系统提示词
     */
    private String buildSystemPrompt(List<VectorSearchHit> hits) {
        int maxChars = properties.getChat().getMaxContextChars();
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            VectorSearchHit hit = hits.get(i);
            String snippet = hit.getContent() == null ? "" : hit.getContent();
            if (context.length() + snippet.length() > maxChars) {
                break;
            }
            context.append("[")
                    .append(i + 1)
                    .append("] 文档《")
                    .append(hit.getDocumentTitle())
                    .append("》：")
                    .append(snippet)
                    .append("\n\n");
        }
        return """
                你是企业知识库助手。仅根据下方检索到的知识片段回答用户问题；
                片段不足以回答时，明确说明未检索到相关内容，不要编造。
                回答中引用片段时使用 [n] 标记，n 为片段编号。

                检索片段：
                %s"""
                .formatted(context);
    }

    /**
     * 引用列表 JSON（字段与 {@code KbMessageDTO.CitationDTO} 一致）。
     *
     * @param citations 引用溯源
     * @return JSON 字符串
     */
    private String citationsJson(List<KbMessage.Citation> citations) {
        try {
            return objectMapper.writeValueAsString(citations.stream()
                    .map(citation -> objectMapper.valueToTree(Map.of(
                            "index",
                            citation.index(),
                            "chunkId",
                            citation.chunkId(),
                            "documentId",
                            citation.documentId(),
                            "documentTitle",
                            citation.documentTitle(),
                            "spaceId",
                            citation.spaceId(),
                            "snippet",
                            citation.snippet())))
                    .toList());
        } catch (Exception e) {
            log.warn("引用列表序列化失败", e);
            return "[]";
        }
    }

    /**
     * 会话不存在或跨用户访问时的统一错误。
     *
     * @param conversationId 会话 ID
     * @param userId 用户 ID
     * @return 会话模型 Mono
     */
    private Mono<KbConversation> requireConversation(Long conversationId, Long userId) {
        return conversationRepository
                .findByIdAndUser(conversationId, userId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "问答会话不存在")));
    }

    private ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder(data).event(event).build();
    }

    private ServerSentEvent<String> doneEvent(Long conversationId, Long messageId) {
        try {
            return sse(
                    "done",
                    objectMapper.writeValueAsString(Map.of(
                            "conversationId", String.valueOf(conversationId), "messageId", String.valueOf(messageId))));
        } catch (Exception e) {
            return sse("done", "{}");
        }
    }

    private ServerSentEvent<String> errorEvent(Throwable error) {
        return sse("error", KbChatErrorPayload.from(error).toJson(objectMapper));
    }

    private String truncateTitle(String question) {
        String cleaned = question == null ? "新会话" : question.strip();
        return cleaned.length() <= TITLE_MAX_LENGTH ? cleaned : cleaned.substring(0, TITLE_MAX_LENGTH);
    }

    /**
     * 单轮问答上下文。
     *
     * @param conversation 会话
     * @param history 历史消息（不含本轮提问）
     * @param question 本轮提问
     * @param hits 检索命中
     */
    private record AskContext(
            KbConversation conversation, List<ChatMessage> history, String question, List<VectorSearchHit> hits) {}
}
