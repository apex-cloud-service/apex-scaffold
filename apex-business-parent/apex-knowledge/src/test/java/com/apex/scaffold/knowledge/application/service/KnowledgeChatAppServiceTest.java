package com.apex.scaffold.knowledge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.knowledge.application.assembler.KnowledgeAssembler;
import com.apex.scaffold.knowledge.application.dto.KbAskRequest;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.ChatPort;
import com.apex.scaffold.knowledge.domain.gateway.KbConversationRepository;
import com.apex.scaffold.knowledge.domain.model.KbConversation;
import com.apex.scaffold.knowledge.domain.model.KbMessage;
import com.apex.scaffold.knowledge.domain.model.VectorSearchHit;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 知识问答编排单元测试：SSE 事件序列、兜底与错误路径。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeChatAppServiceTest {

    @Mock
    private KbConversationRepository conversationRepository;

    @Mock
    private KnowledgeSearchAppService searchService;

    @Mock
    private ChatPort chatPort;

    /**
     * 构建被测服务。
     *
     * @param emptyStrategy 检索为空策略
     * @return 问答编排服务
     */
    private KnowledgeChatAppService service(String emptyStrategy) {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getChat().setEmptyContextStrategy(emptyStrategy);
        return new KnowledgeChatAppService(
                conversationRepository,
                searchService,
                chatPort,
                properties,
                new KnowledgeAssembler(),
                new ObjectMapper());
    }

    @BeforeEach
    void setUpTenant() {
        TenantContext.setTenantId("t1");
        lenient()
                .when(conversationRepository.insert(any(KbConversation.class)))
                .thenAnswer(invocation -> Mono.just(withId((KbConversation) invocation.getArgument(0), 10L)));
        lenient().when(conversationRepository.findMessages(anyLong(), anyInt())).thenReturn(Mono.just(List.of()));
        lenient()
                .when(conversationRepository.insertMessage(any(KbMessage.class)))
                .thenAnswer(invocation -> Mono.just(withId((KbMessage) invocation.getArgument(0), 11L)));
        lenient()
                .when(conversationRepository.update(any(KbConversation.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /**
     * 为模型补齐 ID 字段。
     *
     * @param conversation 原模型
     * @param id 目标 ID
     * @return 带 ID 的副本
     */
    private KbConversation withId(KbConversation conversation, Long id) {
        return KbConversation.builder()
                .id(id)
                .title(conversation.getTitle())
                .userId(conversation.getUserId())
                .build();
    }

    /**
     * 为消息补齐 ID 字段。
     *
     * @param message 原消息
     * @param id 目标 ID
     * @return 带 ID 的副本
     */
    private KbMessage withId(KbMessage message, Long id) {
        return KbMessage.builder()
                .id(id)
                .conversationId(message.getConversationId())
                .role(message.getRole())
                .content(message.getContent())
                .citations(message.getCitations())
                .build();
    }

    @Test
    void emptyHitsWithFallbackStrategyAnswersWithoutModelCall() {
        when(searchService.retrieveTopK(anyString(), any(), anyInt())).thenReturn(Mono.just(List.of()));

        List<ServerSentEvent<String>> events = service("fallback")
                .ask(1L, new KbAskRequest(null, null, "什么是质量方针"))
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events.stream().map(e -> String.valueOf(e.event()))).containsExactly("citations", "delta", "done");
        assertThat(events.get(1).data()).contains("未检索到");
        verify(chatPort, never()).streamChat(anyString(), anyList(), anyString());
    }

    @Test
    void modelAnswerStreamsDeltasAndPersistsCitations() {
        when(searchService.retrieveTopK(anyString(), any(), anyInt()))
                .thenReturn(Mono.just(List.of(VectorSearchHit.builder()
                        .chunkId(7L)
                        .documentId(1L)
                        .spaceId(2L)
                        .documentTitle("质量手册")
                        .spaceName("空间")
                        .content("质量方针是……")
                        .score(0.9d)
                        .build())));
        when(chatPort.streamChat(anyString(), anyList(), anyString())).thenReturn(Flux.just("质量方针是", "以客户为中心"));

        List<ServerSentEvent<String>> events = service("answer")
                .ask(1L, new KbAskRequest(null, null, "质量方针是什么"))
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events.stream().map(e -> String.valueOf(e.event())))
                .containsExactly("citations", "delta", "delta", "done");
        assertThat(events.get(0).data()).contains("质量手册").contains("chunkId");
        ArgumentCaptor<KbMessage> saved = ArgumentCaptor.forClass(KbMessage.class);
        verify(conversationRepository, org.mockito.Mockito.times(2)).insertMessage(saved.capture());
        // 两次 insertMessage：用户消息与助手消息；助手消息含引用
        assertThat(saved.getAllValues()).hasSize(2);
        assertThat(saved.getAllValues().get(1).getCitations()).isNotEmpty();
    }

    @Test
    void chatPortFailureIsConvertedToErrorEvent() {
        when(searchService.retrieveTopK(anyString(), any(), anyInt()))
                .thenReturn(Mono.just(List.of(VectorSearchHit.builder()
                        .chunkId(7L)
                        .documentId(1L)
                        .spaceId(2L)
                        .documentTitle("质量手册")
                        .spaceName("空间")
                        .content("内容")
                        .score(0.9d)
                        .build())));
        when(chatPort.streamChat(anyString(), anyList(), anyString()))
                .thenReturn(Flux.error(new IllegalStateException("模型超时")));

        List<ServerSentEvent<String>> events = service("answer")
                .ask(1L, new KbAskRequest(null, null, "提问"))
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events.getLast().event()).isEqualTo("error");
    }

    @Test
    void missingTenantContextIsRejectedBeforeStreaming() {
        TenantContext.clear();
        StepVerifier.create(service("fallback").ask(1L, new KbAskRequest(null, null, "提问")))
                .expectError(BusinessException.class)
                .verify();

        verify(conversationRepository, never()).insert(any(KbConversation.class));
    }

    @Test
    void askIntoForeignConversationIsRejected() {
        when(conversationRepository.findByIdAndUser(eq(99L), eq(1L))).thenReturn(Mono.empty());

        StepVerifier.create(service("fallback").ask(1L, new KbAskRequest(99L, null, "提问")))
                .expectError(BusinessException.class)
                .verify();
    }
}
