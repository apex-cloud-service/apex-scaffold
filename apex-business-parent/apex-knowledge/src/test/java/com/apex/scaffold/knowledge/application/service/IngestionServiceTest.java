package com.apex.scaffold.knowledge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.DocumentParserPort;
import com.apex.scaffold.knowledge.domain.gateway.EmbeddingPort;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeChunkRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeSpaceRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeVectorIndexPort;
import com.apex.scaffold.knowledge.domain.model.IndexedChunk;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocument;
import com.apex.scaffold.knowledge.domain.model.KnowledgeSpace;
import com.apex.scaffold.knowledge.domain.model.ParsedDocument;
import com.apex.scaffold.knowledge.domain.service.ChunkingService;
import com.apex.scaffold.knowledge.support.EmbeddingCache;
import com.apex.scaffold.storage.ObjectStorage;
import com.apex.scaffold.storage.StorageRegistry;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

/**
 * 摄取管线编排单元测试：状态机推进与降级路径。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private KnowledgeDocumentRepository documentRepository;

    @Mock
    private KnowledgeChunkRepository chunkRepository;

    @Mock
    private KnowledgeSpaceRepository spaceRepository;

    @Mock
    private DocumentParserPort parserPort;

    @Mock
    private EmbeddingPort embeddingPort;

    @Mock
    private KnowledgeVectorIndexPort vectorIndexPort;

    @Mock
    private StorageRegistry storageRegistry;

    @Mock
    private ObjectStorage objectStorage;

    @Mock
    private InputStream inputStream;

    /**
     * 构建被测服务。
     *
     * @param embeddingEnabled 是否启用向量化
     * @return 摄取编排服务
     */
    private IngestionService service(boolean embeddingEnabled) {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getEmbedding().setEnabled(embeddingEnabled);
        properties.getEmbedding().setDimensions(4);
        lenient().when(storageRegistry.defaultStorage()).thenReturn(objectStorage);
        lenient().when(objectStorage.download(anyString(), anyString())).thenReturn(Mono.just(inputStream));
        return new IngestionService(
                documentRepository,
                chunkRepository,
                spaceRepository,
                parserPort,
                embeddingPort,
                vectorIndexPort,
                new ChunkingService(100, 10),
                properties,
                storageRegistry,
                new EmbeddingCache(64));
    }

    /**
     * 构建待摄取的 UPLOAD 文档。
     *
     * @return 文档模型
     */
    private KnowledgeDocument uploadDocument() {
        return KnowledgeDocument.builder()
                .id(1L)
                .spaceId(2L)
                .title("测试文档")
                .sourceType(KnowledgeDocument.SourceType.UPLOAD)
                .contentType("application/pdf")
                .objectKey("kb/x")
                .parseStatus(KnowledgeDocument.ParseStatus.PENDING)
                .indexStatus(KnowledgeDocument.IndexStatus.NONE)
                .chunkCount(0)
                .build();
    }

    /**
     * 更新仓储回显入参，空间查询返回默认空间，索引端口全部放行。
     */
    private void stubReposEchoUpdate() {
        when(documentRepository.update(any(KnowledgeDocument.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(documentRepository.findById(anyLong())).thenReturn(Mono.just(uploadDocument()));
        when(chunkRepository.replaceByDocument(anyLong(), anyList())).thenReturn(Mono.just(1));
        // 生产实现 update 会从库回读完整 PO（spaceId 等字段不丢）；测试桩以 any() 匹配模拟该语义
        lenient()
                .when(spaceRepository.findById(org.mockito.ArgumentMatchers.any()))
                .thenReturn(
                        Mono.just(KnowledgeSpace.builder().id(2L).name("默认空间").build()));
        lenient().when(vectorIndexPort.ensureIndex(anyBoolean(), anyInt())).thenReturn(Mono.empty());
        lenient().when(vectorIndexPort.upsertChunks(anyList())).thenReturn(Mono.empty());
    }

    @Test
    void uploadDocumentIsParsedChunkedAndLexicallyIndexedWhenEmbeddingDisabled() {
        IngestionService ingestion = service(false);
        stubReposEchoUpdate();
        when(parserPort.parse(anyString(), anyString(), any()))
                .thenReturn(Mono.just(new ParsedDocument("知识正文内容", "application/pdf")));

        ingestion.ingest(1L).block();

        ArgumentCaptor<KnowledgeDocument> updates = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(documentRepository, org.mockito.Mockito.atLeastOnce()).update(updates.capture());
        assertThat(updates.getAllValues())
                .extracting(KnowledgeDocument::getIndexStatus)
                .contains(KnowledgeDocument.IndexStatus.INDEXED_LEXICAL);
        verify(vectorIndexPort).ensureIndex(false, 4);
        ArgumentCaptor<List<IndexedChunk>> chunks = ArgumentCaptor.forClass(List.class);
        verify(vectorIndexPort).upsertChunks(chunks.capture());
        assertThat(chunks.getValue())
                .allSatisfy(chunk -> assertThat(chunk.getEmbedding()).isNull());
    }

    @Test
    void uploadDocumentIsVectorIndexedWhenEmbeddingEnabled() {
        IngestionService ingestion = service(true);
        stubReposEchoUpdate();
        when(parserPort.parse(anyString(), anyString(), any()))
                .thenReturn(Mono.just(new ParsedDocument("知识正文内容", "application/pdf")));
        when(embeddingPort.embed(anyList())).thenReturn(Mono.just(List.of(new float[] {0.1f, 0.2f, 0.3f, 0.4f})));

        ingestion.ingest(1L).block();

        verify(vectorIndexPort).ensureIndex(true, 4);
        ArgumentCaptor<List<IndexedChunk>> chunks = ArgumentCaptor.forClass(List.class);
        verify(vectorIndexPort).upsertChunks(chunks.capture());
        assertThat(chunks.getValue().get(0).getEmbedding()).isNotNull();
        ArgumentCaptor<KnowledgeDocument> updates = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(documentRepository, org.mockito.Mockito.atLeastOnce()).update(updates.capture());
        assertThat(updates.getAllValues())
                .extracting(KnowledgeDocument::getIndexStatus)
                .contains(KnowledgeDocument.IndexStatus.INDEXED);
    }

    @Test
    void parseFailureMarksDocumentFailedWithoutIndexing() {
        IngestionService ingestion = service(false);
        when(documentRepository.update(any(KnowledgeDocument.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(documentRepository.findById(anyLong())).thenReturn(Mono.just(uploadDocument()));
        when(parserPort.parse(anyString(), anyString(), any()))
                .thenReturn(Mono.error(new IllegalStateException("无法解析加密 PDF")));

        ingestion.ingest(1L).block();

        ArgumentCaptor<KnowledgeDocument> updates = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(documentRepository, org.mockito.Mockito.atLeastOnce()).update(updates.capture());
        KnowledgeDocument last = updates.getAllValues().getLast();
        assertThat(last.getParseStatus()).isEqualTo(KnowledgeDocument.ParseStatus.FAILED);
        assertThat(last.getParseError()).contains("无法解析");
        org.mockito.Mockito.verifyNoInteractions(vectorIndexPort);
    }

    @Test
    void manualDocumentSkipsParsing() {
        IngestionService ingestion = service(false);
        when(documentRepository.update(any(KnowledgeDocument.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        KnowledgeDocument manual = KnowledgeDocument.builder()
                .id(3L)
                .spaceId(2L)
                .title("在线录入")
                .sourceType(KnowledgeDocument.SourceType.MANUAL)
                .contentType("text/plain")
                .contentText("在线录入的正文")
                .parseStatus(KnowledgeDocument.ParseStatus.PARSED)
                .indexStatus(KnowledgeDocument.IndexStatus.PENDING)
                .chunkCount(0)
                .build();
        when(documentRepository.findById(anyLong())).thenReturn(Mono.just(manual));
        when(chunkRepository.replaceByDocument(anyLong(), anyList())).thenReturn(Mono.just(1));
        lenient()
                .when(spaceRepository.findById(org.mockito.ArgumentMatchers.any()))
                .thenReturn(
                        Mono.just(KnowledgeSpace.builder().id(2L).name("默认空间").build()));

        ingestion.ingest(3L).block();

        org.mockito.Mockito.verifyNoInteractions(parserPort);
        verify(vectorIndexPort).ensureIndex(false, 4);
    }
}
