package com.apex.scaffold.knowledge.application.service;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.DocumentParserPort;
import com.apex.scaffold.knowledge.domain.gateway.EmbeddingPort;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentLifecyclePort;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeSpaceRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeVectorIndexPort;
import com.apex.scaffold.knowledge.domain.model.IndexedChunk;
import com.apex.scaffold.knowledge.domain.model.KnowledgeChunk;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocument;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocumentLease;
import com.apex.scaffold.knowledge.domain.model.KnowledgeSpace;
import com.apex.scaffold.knowledge.domain.service.ChunkingService;
import com.apex.scaffold.knowledge.support.EmbeddingCache;
import com.apex.scaffold.storage.StorageRegistry;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 摄取管线编排服务：解析 → 分块 → 向量化 → 索引。
 *
 * <p>独立订阅从可信 TenantContext 捕获租户，数据库租约串行协调摄取/重建/删除。
 * 每次状态与分块写均验证 fencing token 及未删除主档；索引任务成功确认后才标记 INDEXED。
 * 远程超时或取消不能证明任务不会迟到，删除 tombstone 长期定向复核实现最终收敛。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeSpaceRepository spaceRepository;
    private final DocumentParserPort parserPort;
    private final EmbeddingPort embeddingPort;
    private final KnowledgeVectorIndexPort vectorIndexPort;
    private final ChunkingService chunkingService;
    private final KnowledgeProperties properties;
    private final StorageRegistry storageRegistry;
    private final EmbeddingCache embeddingCache;
    private final KnowledgeDocumentLifecyclePort lifecycle;
    private final KnowledgeDocumentLeaseRunner leaseRunner;

    /**
     * 认领后执行摄取；缺少可信租户直接拒绝，不使用公共租户或 0 回退。
     * 认领冲突返回完成信号；管线错误只尝试写仍有效且未删除的主档，不恢复已删除文档。
     *
     * @param documentId 待摄取文档 ID
     * @return 摄取结束或认领冲突时的完成信号；租户缺失与不可恢复错误发出错误
     */
    public Mono<Void> ingest(Long documentId) {
        return TenantContext.currentTenantIdReactive()
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "可信租户上下文缺失")))
                .flatMap(tenant -> lifecycle
                        .claimIngestion(documentId)
                        .flatMap(lease -> Mono.usingWhen(
                                Mono.just(lease),
                                owned -> leaseRunner
                                        .run(
                                                owned,
                                                documentRepository
                                                        .findById(documentId)
                                                        .switchIfEmpty(Mono.error(
                                                                new BusinessException(ResultCode.NOT_FOUND, "知识文档不存在")))
                                                        .flatMap(document -> runPipeline(owned, document)))
                                        .onErrorResume(error -> markFailed(owned, error)),
                                lifecycle::release,
                                (owned, error) -> lifecycle.release(owned),
                                lifecycle::release))
                        .contextWrite(TenantContext.withTenant(tenant)));
    }

    private Mono<Void> runPipeline(KnowledgeDocumentLease lease, KnowledgeDocument document) {
        boolean parseNeeded =
                document.getContentText() == null || document.getContentText().isEmpty();
        Mono<KnowledgeDocument> parsed = parseNeeded ? parseAndPersist(lease, document) : Mono.just(document);
        return parsed.flatMap(doc -> chunkAndIndex(lease, doc));
    }

    private Mono<KnowledgeDocument> parseAndPersist(KnowledgeDocumentLease lease, KnowledgeDocument document) {
        document.setParseStatus(KnowledgeDocument.ParseStatus.PARSING);
        document.setParseError(null);
        return lifecycle
                .persistDocument(lease, document)
                .then(parseText(document))
                .flatMap(parsed -> {
                    document.setContentText(parsed.text());
                    document.setParseStatus(KnowledgeDocument.ParseStatus.PARSED);
                    return lifecycle.persistDocument(lease, document);
                });
    }

    private Mono<Void> chunkAndIndex(KnowledgeDocumentLease lease, KnowledgeDocument document) {
        List<KnowledgeChunk> chunks = chunkingService.chunk(document.getContentText());
        return lifecycle
                .replaceChunks(lease, chunks)
                .flatMap(count -> {
                    if (count != chunks.size()) {
                        return Mono.error(new IllegalStateException("分块写入数量不符"));
                    }
                    document.setChunkCount(count);
                    document.setParseError(null);
                    document.setIndexStatus(
                            properties.getMeilisearch().isEnabled()
                                    ? KnowledgeDocument.IndexStatus.PENDING
                                    : KnowledgeDocument.IndexStatus.NONE);
                    return lifecycle.persistDocument(lease, document);
                })
                .flatMap(updated -> indexChunks(lease, updated, chunks));
    }

    private Mono<Void> indexChunks(
            KnowledgeDocumentLease lease, KnowledgeDocument document, List<KnowledgeChunk> chunks) {
        if (!properties.getMeilisearch().isEnabled()) {
            return Mono.empty();
        }
        boolean embeddingEnabled = properties.getEmbedding().isEnabled();
        Mono<List<float[]>> vectors = embeddingEnabled ? embedWithCache(chunks) : Mono.just(List.of());
        return Mono.zip(loadSpace(document), vectors).flatMap(tuple -> {
            KnowledgeSpace space = tuple.getT1();
            List<float[]> embeddings = tuple.getT2();
            List<IndexedChunk> indexed = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk chunk = chunks.get(i);
                indexed.add(IndexedChunk.builder()
                        .chunkId(chunk.getId())
                        .documentId(document.getId())
                        .spaceId(document.getSpaceId())
                        .tenantId(lease.tenantId())
                        .seq(chunk.getSeq())
                        .content(chunk.getContent())
                        .contentHash(chunk.getContentHash())
                        .documentTitle(document.getTitle())
                        .spaceName(space.getName())
                        .contentType(document.getContentType())
                        .embedding(embeddings.isEmpty() ? null : embeddings.get(i))
                        .updateTime(document.getUpdateTime())
                        .build());
            }
            String endpoint = properties.getMeilisearch().getUrl();
            String indexName = properties.getMeilisearch().getIndexPrefix() + "kb_chunks";
            return lifecycle
                    .recordIndexTarget(lease, endpoint, indexName)
                    .then(vectorIndexPort.ensureIndex(
                            embeddingEnabled, properties.getEmbedding().getDimensions()))
                    .then(Mono.defer(() -> lifecycle.recordIndexTarget(lease, endpoint, indexName)))
                    .then(vectorIndexPort.deleteDocumentChunks(endpoint, indexName, lease.tenantId(), document.getId()))
                    .then(Mono.defer(() -> lifecycle.recordIndexTarget(lease, endpoint, indexName)))
                    .then(Mono.defer(() -> vectorIndexPort.upsertChunks(indexed)))
                    .then(Mono.defer(() -> {
                        document.setIndexStatus(
                                embeddingEnabled
                                        ? KnowledgeDocument.IndexStatus.INDEXED
                                        : KnowledgeDocument.IndexStatus.INDEXED_LEXICAL);
                        return lifecycle.persistDocument(lease, document);
                    }))
                    .then();
        });
    }

    private Mono<KnowledgeSpace> loadSpace(KnowledgeDocument document) {
        return spaceRepository
                .findById(document.getSpaceId())
                .switchIfEmpty(Mono.just(KnowledgeSpace.builder()
                        .id(document.getSpaceId())
                        .name("")
                        .build()));
    }

    private Mono<List<float[]>> embedWithCache(List<KnowledgeChunk> chunks) {
        List<float[]> cached = new ArrayList<>(chunks.size());
        List<Integer> misses = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            float[] vector = embeddingCache.get(chunks.get(i).getContentHash());
            cached.add(vector);
            if (vector == null) {
                misses.add(i);
            }
        }
        if (misses.isEmpty()) {
            return Mono.just(cached);
        }
        return embeddingPort
                .embed(misses.stream().map(i -> chunks.get(i).getContent()).toList())
                .map(vectors -> {
                    if (vectors.size() != misses.size()) {
                        throw new IllegalStateException("向量响应数量不符");
                    }
                    for (int i = 0; i < misses.size(); i++) {
                        float[] vector = vectors.get(i);
                        embeddingCache.put(chunks.get(misses.get(i)).getContentHash(), vector);
                        cached.set(misses.get(i), vector);
                    }
                    return cached;
                });
    }

    private Mono<com.apex.scaffold.knowledge.domain.model.ParsedDocument> parseText(KnowledgeDocument document) {
        return Mono.defer(() -> {
            var storage = document.isLocationSnapshot()
                    ? storageRegistry.require(document.getStorageProvider())
                    : storageRegistry.defaultStorage();
            String bucket = document.isLocationSnapshot()
                    ? document.getStorageBucket()
                    : properties.getStorage().getBucket();
            return storage.withDownload(
                            bucket,
                            document.getObjectKey(),
                            stream -> parserPort.parse(document.getTitle(), document.getContentType(), () -> stream))
                    .switchIfEmpty(Mono.error(new BusinessException(ResultCode.STORAGE_ERROR, "对象不存在或已被清理")));
        });
    }

    private Mono<Void> markFailed(KnowledgeDocumentLease lease, Throwable error) {
        log.warn(
                "知识文档摄取失败: documentId={}, errorType={}",
                lease.documentId(),
                error.getClass().getSimpleName());
        return documentRepository
                .findById(lease.documentId())
                .flatMap(document -> {
                    boolean parseFailure = document.getParseStatus() == KnowledgeDocument.ParseStatus.PENDING
                            || document.getParseStatus() == KnowledgeDocument.ParseStatus.PARSING;
                    if (parseFailure) {
                        document.setParseStatus(KnowledgeDocument.ParseStatus.FAILED);
                    } else {
                        document.setIndexStatus(KnowledgeDocument.IndexStatus.FAILED);
                    }
                    document.setParseError(parseFailure ? "文档解析失败，请检查文件格式后重试" : "索引未完成，请检查供应商配置后重试");
                    return lifecycle.persistDocument(lease, document).then();
                })
                .onErrorResume(markError -> {
                    log.warn("摄取失败状态未写入（文档已删除或认领失效）: documentId={}", lease.documentId());
                    return Mono.empty();
                });
    }
}
