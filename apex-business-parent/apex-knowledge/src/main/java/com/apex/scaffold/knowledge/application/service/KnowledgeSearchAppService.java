package com.apex.scaffold.knowledge.application.service;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.knowledge.application.assembler.KnowledgeAssembler;
import com.apex.scaffold.knowledge.application.dto.KbSearchRequest;
import com.apex.scaffold.knowledge.application.dto.KbSearchResultDTO;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.EmbeddingPort;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeVectorIndexPort;
import com.apex.scaffold.knowledge.domain.model.VectorSearchHit;
import com.apex.scaffold.knowledge.domain.model.VectorSearchQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 知识检索应用服务：hybrid 优先、词法兜底、租户强制过滤。
 *
 * <p>embedding 供应商未启用时查询向量保持为空，直接词法检索；
 * 供应商调用失败不阻断检索（降级词法），错误仅记录日志。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchAppService {

    private final KnowledgeVectorIndexPort vectorIndexPort;

    private final EmbeddingPort embeddingPort;

    private final KnowledgeProperties properties;

    private final KnowledgeAssembler assembler;

    /**
     * 执行知识检索。
     *
     * @param request 检索请求
     * @return 检索响应 DTO
     */
    public Mono<KbSearchResultDTO> search(KbSearchRequest request) {
        String tenantId = TenantContext.currentTenantId().orElse(null);
        if (tenantId == null || tenantId.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "租户上下文缺失，拒绝知识检索"));
        }
        int page = request.page() == null || request.page() < 1 ? 1 : request.page();
        int size = request.size() == null || request.size() < 1 ? 10 : Math.min(request.size(), 50);
        return searchInternal(request, tenantId, (page - 1) * size, size).map(assembler::toSearchResultDTO);
    }

    /**
     * 检索内部编排：有查询向量走 hybrid，无向量（embedding 未启用或调用失败）降级词法。
     *
     * @param request 检索请求
     * @param tenantId 租户 ID
     * @param offset 偏移量
     * @param size 页大小
     * @return 领域检索结果
     */
    private Mono<com.apex.scaffold.knowledge.domain.model.VectorSearchResult> searchInternal(
            KbSearchRequest request, String tenantId, int offset, int size) {
        return embedQuery(request.keyword())
                .flatMap(vector -> vectorIndexPort.search(VectorSearchQuery.builder()
                        .keyword(request.keyword())
                        .queryEmbedding(vector)
                        .tenantId(tenantId)
                        .spaceIds(request.spaceIds())
                        .offset(offset)
                        .limit(size)
                        .semanticRatio(properties.getSearch().getSemanticRatio())
                        .build()))
                .switchIfEmpty(Mono.defer(() -> vectorIndexPort.search(VectorSearchQuery.builder()
                        .keyword(request.keyword())
                        .queryEmbedding(null)
                        .tenantId(tenantId)
                        .spaceIds(request.spaceIds())
                        .offset(offset)
                        .limit(size)
                        .semanticRatio(properties.getSearch().getSemanticRatio())
                        .build())));
    }

    /**
     * 检索 topK 分块（问答引用上下文使用；租户强制过滤）。
     *
     * @param keyword 检索关键词
     * @param spaceIds 限定空间（可空）
     * @param topK 返回条数
     * @return 命中分块列表（索引未启用或失败时为空列表）
     */
    public Mono<List<VectorSearchHit>> retrieveTopK(String keyword, List<Long> spaceIds, int topK) {
        return search(new KbSearchRequest(keyword, spaceIds, 1, Math.max(1, topK)))
                .map(result -> result.items().stream()
                        .map(item -> VectorSearchHit.builder()
                                .chunkId(item.chunkId())
                                .documentId(item.documentId())
                                .spaceId(item.spaceId())
                                .documentTitle(item.documentTitle())
                                .spaceName(item.spaceName())
                                .content(item.content())
                                .highlight(item.highlight())
                                .score(item.score())
                                .build())
                        .toList())
                .onErrorReturn(List.of());
    }

    /**
     * 查询向量化：embedding 未启用或调用失败时返回空向量（词法检索）。
     *
     * @param keyword 检索关键词
     * @return 查询向量（可为空）
     */
    private Mono<float[]> embedQuery(String keyword) {
        if (!properties.getEmbedding().isEnabled()) {
            return Mono.empty();
        }
        return embeddingPort
                .embed(List.of(keyword))
                .flatMap(vectors -> vectors.isEmpty() ? Mono.<float[]>empty() : Mono.just(vectors.get(0)))
                .onErrorResume(error -> {
                    log.warn("查询向量化失败，降级词法检索: {}", error.getMessage());
                    return Mono.empty();
                });
    }
}
