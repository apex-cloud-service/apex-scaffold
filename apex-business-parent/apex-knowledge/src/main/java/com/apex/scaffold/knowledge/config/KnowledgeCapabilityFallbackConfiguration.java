package com.apex.scaffold.knowledge.config;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.knowledge.domain.gateway.ChatPort;
import com.apex.scaffold.knowledge.domain.gateway.EmbeddingPort;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeVectorIndexPort;
import com.apex.scaffold.knowledge.domain.model.IndexedChunk;
import com.apex.scaffold.knowledge.domain.model.VectorSearchQuery;
import com.apex.scaffold.knowledge.domain.model.VectorSearchResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 知识库能力降级装配：未启用对应供应商时注册空实现。
 *
 * <p>语义为「功能未启用」而非静默吞错：向量索引未启用 → 检索恒返回空、摄取只入库；
 * embedding 未启用 → 调用方显式失败（摄取编排据此降级为词法）；chat 未启用 →
 * 问答编排显式失败并由接口层转为 error 事件。真实适配器装配后由
 * {@code @ConditionalOnMissingBean} 让位。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class KnowledgeCapabilityFallbackConfiguration {

    /**
     * 未启用 embedding 供应商时的显式失败实现。
     *
     * @return embedding 端口
     */
    @Bean
    @ConditionalOnMissingBean(EmbeddingPort.class)
    public EmbeddingPort disabledEmbeddingPort() {
        return texts -> Mono.error(
                new BusinessException(ResultCode.BUSINESS_ERROR, "文本向量化未启用（apex.knowledge.embedding.enabled=false）"));
    }

    /**
     * 未启用 chat 供应商时的显式失败实现。
     *
     * @return 对话端口
     */
    @Bean
    @ConditionalOnMissingBean(ChatPort.class)
    public ChatPort disabledChatPort() {
        return (systemPrompt, history, question) -> Flux.error(
                new BusinessException(ResultCode.BUSINESS_ERROR, "智能问答未启用（apex.knowledge.chat.enabled=false）"));
    }

    /**
     * 未启用 Meilisearch 时的 no-op 索引实现（检索恒返回空）。
     *
     * @return 索引端口
     */
    @Bean
    @ConditionalOnMissingBean(KnowledgeVectorIndexPort.class)
    public KnowledgeVectorIndexPort disabledVectorIndexPort() {
        log.info("知识索引引擎未启用（apex.knowledge.meilisearch.enabled=false）：检索返回空结果，摄取只入库");
        return new KnowledgeVectorIndexPort() {
            @Override
            public Mono<Void> ensureIndex(boolean embeddingEnabled, int dimensions) {
                return Mono.empty();
            }

            @Override
            public Mono<Void> upsertChunks(List<IndexedChunk> chunks) {
                log.debug("知识索引未启用，跳过分块索引写入: {} 条", chunks == null ? 0 : chunks.size());
                return Mono.empty();
            }

            @Override
            public Mono<Void> deleteDocumentChunks(String tenantId, Long documentId) {
                return Mono.error(new IllegalStateException("知识索引未启用，不能确认清理完成"));
            }

            @Override
            public Mono<VectorSearchResult> search(VectorSearchQuery query) {
                return Mono.just(new VectorSearchResult(List.of(), 0L, false));
            }

            @Override
            public Mono<Long> rebuild(List<Long> ids, Function<List<Long>, Mono<List<IndexedChunk>>> chunkLoader) {
                return Mono.just(0L);
            }

            @Override
            public LocalDateTime lastSyncMarker() {
                return null;
            }
        };
    }
}
