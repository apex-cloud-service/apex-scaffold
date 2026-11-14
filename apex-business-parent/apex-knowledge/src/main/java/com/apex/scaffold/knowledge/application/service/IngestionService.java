package com.apex.scaffold.knowledge.application.service;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.DocumentParserPort;
import com.apex.scaffold.knowledge.domain.gateway.EmbeddingPort;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeChunkRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeSpaceRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeVectorIndexPort;
import com.apex.scaffold.knowledge.domain.model.IndexedChunk;
import com.apex.scaffold.knowledge.domain.model.KnowledgeChunk;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocument;
import com.apex.scaffold.knowledge.domain.model.KnowledgeSpace;
import com.apex.scaffold.knowledge.domain.service.ChunkingService;
import com.apex.scaffold.knowledge.support.EmbeddingCache;
import com.apex.scaffold.storage.StorageRegistry;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 摄取管线编排服务：解析 → 分块 → 向量化 → 索引，失败路径显式落库。
 *
 * <p>状态机推进规则：解析失败标记 {@code PARSE_FAILED} 并终止；索引失败标记
 * {@code INDEX_FAILED}（分块已入库，可重建）；embedding 未启用时仅写词法索引。
 * 管线吞掉最终错误转为状态落库与日志，保证触发方（上传/校准 Job）不被拖垮。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Slf4j
@Service
public class IngestionService {

    private final KnowledgeDocumentRepository documentRepository;

    private final KnowledgeChunkRepository chunkRepository;

    private final KnowledgeSpaceRepository spaceRepository;

    private final DocumentParserPort parserPort;

    private final EmbeddingPort embeddingPort;

    private final KnowledgeVectorIndexPort vectorIndexPort;

    private final ChunkingService chunkingService;

    private final KnowledgeProperties properties;

    private final StorageRegistry storageRegistry;

    private final EmbeddingCache embeddingCache;

    /**
     * 构建摄取编排服务。
     *
     * @param documentRepository 文档仓储
     * @param chunkRepository 分块仓储
     * @param spaceRepository 空间仓储
     * @param parserPort 解析端口
     * @param embeddingPort 向量化端口
     * @param vectorIndexPort 索引端口
     * @param chunkingService 分块服务
     * @param properties 运行配置
     * @param storageRegistry 存储注册表
     * @param embeddingCache 向量缓存
     */
    public IngestionService(
            KnowledgeDocumentRepository documentRepository,
            KnowledgeChunkRepository chunkRepository,
            KnowledgeSpaceRepository spaceRepository,
            DocumentParserPort parserPort,
            EmbeddingPort embeddingPort,
            KnowledgeVectorIndexPort vectorIndexPort,
            ChunkingService chunkingService,
            KnowledgeProperties properties,
            StorageRegistry storageRegistry,
            EmbeddingCache embeddingCache) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.spaceRepository = spaceRepository;
        this.parserPort = parserPort;
        this.embeddingPort = embeddingPort;
        this.vectorIndexPort = vectorIndexPort;
        this.chunkingService = chunkingService;
        this.properties = properties;
        this.storageRegistry = storageRegistry;
        this.embeddingCache = embeddingCache;
    }

    /**
     * 执行全量摄取（幂等：重复触发先清理旧分块再重建）。
     *
     * @param documentId 文档 ID
     * @return 完成信号（失败已落库并记录日志，不向触发方传播）
     */
    public Mono<Void> ingest(Long documentId) {
        return documentRepository
                .findById(documentId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "知识文档不存在: " + documentId)))
                .flatMap(this::runPipeline)
                .onErrorResume(error -> markFailed(documentId, error).then(Mono.empty()));
    }

    /**
     * 管线主体：解析（可跳过）→ 分块入库 → 向量化 → 索引 → 状态收敛。
     *
     * @param document 文档
     * @return 完成信号
     */
    private Mono<Void> runPipeline(KnowledgeDocument document) {
        boolean parseNeeded =
                document.getContentText() == null || document.getContentText().isEmpty();
        Mono<KnowledgeDocument> parsed = parseNeeded ? parseAndPersist(document) : Mono.just(document);
        return parsed.flatMap(this::chunkAndPersist)
                .flatMap(docWithChunks -> indexChunks(docWithChunks.getKey(), docWithChunks.getValue()))
                .then();
    }

    /**
     * 解析文档正文并持久化（UPLOAD 来源走对象存储下载，MANUAL 来源已有正文直接跳过）。
     *
     * @param document 文档
     * @return 更新后的文档
     */
    private Mono<KnowledgeDocument> parseAndPersist(KnowledgeDocument document) {
        return documentRepository
                .update(KnowledgeDocument.builder()
                        .id(document.getId())
                        .title(document.getTitle())
                        .sourceType(document.getSourceType())
                        .contentType(document.getContentType())
                        .objectKey(document.getObjectKey())
                        .fileSize(document.getFileSize())
                        .contentText(null)
                        .parseStatus(KnowledgeDocument.ParseStatus.PARSING)
                        .parseError(null)
                        .chunkCount(document.getChunkCount())
                        .indexStatus(document.getIndexStatus())
                        .build())
                .then(parseText(document))
                .flatMap(parsed -> documentRepository.update(KnowledgeDocument.builder()
                        .id(document.getId())
                        .title(document.getTitle())
                        .sourceType(document.getSourceType())
                        .contentType(document.getContentType())
                        .objectKey(document.getObjectKey())
                        .fileSize(document.getFileSize())
                        .contentText(parsed.text())
                        .parseStatus(KnowledgeDocument.ParseStatus.PARSED)
                        .parseError(null)
                        .chunkCount(document.getChunkCount())
                        .indexStatus(document.getIndexStatus())
                        .build()));
    }

    /**
     * 分块入库并更新主档分块数。
     *
     * @param document 已解析文档
     * @return 文档 + 分块列表
     */
    private Mono<java.util.AbstractMap.SimpleEntry<KnowledgeDocument, List<KnowledgeChunk>>> chunkAndPersist(
            KnowledgeDocument document) {
        List<KnowledgeChunk> chunks = chunkingService.chunk(document.getContentText());
        return chunkRepository
                .replaceByDocument(document.getId(), chunks)
                .then(documentRepository.update(KnowledgeDocument.builder()
                        .id(document.getId())
                        .title(document.getTitle())
                        .sourceType(document.getSourceType())
                        .contentType(document.getContentType())
                        .objectKey(document.getObjectKey())
                        .fileSize(document.getFileSize())
                        .contentText(document.getContentText())
                        .parseStatus(document.getParseStatus())
                        .parseError(null)
                        .chunkCount(chunks.size())
                        .indexStatus(KnowledgeDocument.IndexStatus.PENDING)
                        .build()))
                .map(updated -> new java.util.AbstractMap.SimpleEntry<>(updated, chunks));
    }

    /**
     * 向量化（带缓存）并写入索引；embedding 未启用时仅词法索引。
     *
     * @param document 文档
     * @param chunks 分块列表
     * @return 完成信号
     */
    private Mono<Void> indexChunks(KnowledgeDocument document, List<KnowledgeChunk> chunks) {
        if (chunks.isEmpty()) {
            return documentRepository
                    .update(KnowledgeDocument.builder()
                            .id(document.getId())
                            .title(document.getTitle())
                            .sourceType(document.getSourceType())
                            .contentType(document.getContentType())
                            .objectKey(document.getObjectKey())
                            .fileSize(document.getFileSize())
                            .contentText(document.getContentText())
                            .parseStatus(document.getParseStatus())
                            .parseError(null)
                            .chunkCount(0)
                            .indexStatus(KnowledgeDocument.IndexStatus.INDEXED)
                            .build())
                    .then();
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
                        .tenantId(TenantContext.currentTenantId().orElse("0"))
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
            KnowledgeDocument.IndexStatus status = embeddingEnabled
                    ? KnowledgeDocument.IndexStatus.INDEXED
                    : KnowledgeDocument.IndexStatus.INDEXED_LEXICAL;
            return vectorIndexPort
                    .ensureIndex(embeddingEnabled, properties.getEmbedding().getDimensions())
                    .then(vectorIndexPort.upsertChunks(indexed))
                    .then(documentRepository.update(KnowledgeDocument.builder()
                            .id(document.getId())
                            .title(document.getTitle())
                            .sourceType(document.getSourceType())
                            .contentType(document.getContentType())
                            .objectKey(document.getObjectKey())
                            .fileSize(document.getFileSize())
                            .contentText(document.getContentText())
                            .parseStatus(document.getParseStatus())
                            .parseError(null)
                            .chunkCount(chunks.size())
                            .indexStatus(status)
                            .build()))
                    .then();
        });
    }

    /**
     * 加载文档所属空间（索引文档需要空间名称展示）。
     *
     * @param document 文档
     * @return 空间模型
     */
    private Mono<KnowledgeSpace> loadSpace(KnowledgeDocument document) {
        return spaceRepository
                .findById(document.getSpaceId())
                .switchIfEmpty(Mono.just(KnowledgeSpace.builder()
                        .id(document.getSpaceId())
                        .name("")
                        .build()));
    }

    /**
     * 带缓存的批量向量化：命中缓存的分块不调用供应商。
     *
     * @param chunks 分块
     * @return 与分块顺序一致的向量列表
     */
    private Mono<List<float[]>> embedWithCache(List<KnowledgeChunk> chunks) {
        List<float[]> cached = new ArrayList<>(chunks.size());
        List<Integer> missIndexes = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            float[] vector = embeddingCache.get(chunks.get(i).getContentHash());
            if (vector != null) {
                cached.add(vector);
            } else {
                cached.add(null);
                missIndexes.add(i);
            }
        }
        if (missIndexes.isEmpty()) {
            return Mono.just(cached);
        }
        List<String> texts = missIndexes.stream()
                .map(index -> chunks.get(index).getContent())
                .toList();
        return embeddingPort
                .embed(texts)
                .doOnNext(vectors -> {
                    for (int i = 0; i < missIndexes.size(); i++) {
                        float[] vector = vectors.get(i);
                        embeddingCache.put(chunks.get(missIndexes.get(i)).getContentHash(), vector);
                        cached.set(missIndexes.get(i), vector);
                    }
                })
                .thenReturn(cached);
    }

    /**
     * 从对象存储下载并解析正文（UPLOAD 来源）。
     *
     * @param document 文档
     * @return 解析结果
     */
    private Mono<com.apex.scaffold.knowledge.domain.model.ParsedDocument> parseText(KnowledgeDocument document) {
        return storageRegistry
                .defaultStorage()
                .download(properties.getStorage().getBucket(), document.getObjectKey())
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.STORAGE_ERROR, "对象不存在或已被清理")))
                .flatMap(stream -> parserPort.parse(document.getTitle(), document.getContentType(), () -> stream));
    }

    /**
     * 管线失败收敛：解析失败标记 PARSE_FAILED，其余标记 INDEX_FAILED。
     *
     * @param documentId 文档 ID
     * @param error 失败原因
     * @return 完成信号
     */
    private Mono<Void> markFailed(Long documentId, Throwable error) {
        log.error("知识文档摄取失败: documentId={}", documentId, error);
        String message = truncate(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        return documentRepository
                .findById(documentId)
                .flatMap(document -> {
                    boolean parseFailure = document.getParseStatus() == KnowledgeDocument.ParseStatus.PENDING
                            || document.getParseStatus() == KnowledgeDocument.ParseStatus.PARSING;
                    return documentRepository
                            .update(KnowledgeDocument.builder()
                                    .id(document.getId())
                                    .title(document.getTitle())
                                    .sourceType(document.getSourceType())
                                    .contentType(document.getContentType())
                                    .objectKey(document.getObjectKey())
                                    .fileSize(document.getFileSize())
                                    .contentText(document.getContentText())
                                    .parseStatus(
                                            parseFailure
                                                    ? KnowledgeDocument.ParseStatus.FAILED
                                                    : document.getParseStatus())
                                    .parseError(message)
                                    .chunkCount(document.getChunkCount())
                                    .indexStatus(
                                            parseFailure
                                                    ? document.getIndexStatus()
                                                    : KnowledgeDocument.IndexStatus.FAILED)
                                    .build())
                            .then();
                })
                .onErrorResume(markError -> {
                    log.error("摄取失败状态落库失败: documentId={}", documentId, markError);
                    return Mono.empty();
                });
    }

    private String truncate(String message) {
        return message.length() <= 500 ? message : message.substring(0, 500) + "…";
    }
}
