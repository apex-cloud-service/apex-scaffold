package com.apex.scaffold.knowledge.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.knowledge.domain.gateway.KbConversationRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeChunkRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeSpaceRepository;
import com.apex.scaffold.knowledge.domain.model.KbConversation;
import com.apex.scaffold.knowledge.domain.model.KbMessage;
import com.apex.scaffold.knowledge.domain.model.KnowledgeChunk;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocument;
import com.apex.scaffold.knowledge.domain.model.KnowledgeSpace;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 知识库仓储 H2 回归测试：空间/文档/分块/会话消息的读写闭环。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@SpringBootTest(classes = com.apex.scaffold.knowledge.KnowledgeTestApplication.class)
class KnowledgeRepositoryTest {

    private final KnowledgeSpaceRepository spaceRepository;

    private final KnowledgeDocumentRepository documentRepository;

    private final KnowledgeChunkRepository chunkRepository;

    private final KbConversationRepository conversationRepository;
    private final com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentLifecyclePort lifecycle;

    /**
     * 构造器注入仓储（spring.test.constructor.autowire.mode=all，禁用字段 @Autowired）。
     *
     * @param spaceRepository 空间仓储
     * @param documentRepository 文档仓储
     * @param chunkRepository 分块仓储
     * @param conversationRepository 会话仓储
     */
    public KnowledgeRepositoryTest(
            KnowledgeSpaceRepository spaceRepository,
            KnowledgeDocumentRepository documentRepository,
            KnowledgeChunkRepository chunkRepository,
            KbConversationRepository conversationRepository,
            com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentLifecyclePort lifecycle) {
        this.spaceRepository = spaceRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.conversationRepository = conversationRepository;
        this.lifecycle = lifecycle;
    }

    @BeforeEach
    void setUpTenant() {
        TenantContext.setTenantId("tenant-1");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void spaceCrudRoundTrip() {
        KnowledgeSpace created = spaceRepository
                .insert(KnowledgeSpace.builder()
                        .name("产品知识")
                        .description("产品说明文档")
                        .status(1)
                        .build())
                .block();
        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();

        assertThat(spaceRepository.existsByName("产品知识", null).block()).isTrue();
        assertThat(spaceRepository.existsByName("产品知识", created.getId()).block())
                .isFalse();

        KnowledgeSpace updated = spaceRepository
                .update(KnowledgeSpace.builder()
                        .id(created.getId())
                        .name("产品知识库")
                        .description("更新后的描述")
                        .status(1)
                        .build())
                .block();
        assertThat(updated.getName()).isEqualTo("产品知识库");

        assertThat(spaceRepository.findById(created.getId()).block().getName()).isEqualTo("产品知识库");
        assertThat(spaceRepository.findPage("产品", 1, 10).block().getList()).isNotEmpty();
        assertThat(spaceRepository.findAllActive().block()).isNotEmpty();

        assertThat(spaceRepository.deleteById(created.getId()).block()).isTrue();
        assertThat(spaceRepository.findById(created.getId()).block()).isNull();
    }

    @Test
    void documentAndChunksRoundTrip() {
        KnowledgeSpace space = spaceRepository
                .insert(KnowledgeSpace.builder().name("文档测试空间").status(1).build())
                .block();
        KnowledgeDocument doc = documentRepository
                .insert(KnowledgeDocument.builder()
                        .spaceId(space.getId())
                        .title("操作手册")
                        .sourceType(KnowledgeDocument.SourceType.UPLOAD)
                        .contentType("application/pdf")
                        .objectKey("kb/doc-1")
                        .parseStatus(KnowledgeDocument.ParseStatus.PENDING)
                        .indexStatus(KnowledgeDocument.IndexStatus.NONE)
                        .chunkCount(0)
                        .build())
                .block();

        List<KnowledgeChunk> chunks = List.of(
                KnowledgeChunk.builder()
                        .seq(0)
                        .content("第一段")
                        .contentHash("hash-0")
                        .tokenEstimate(3)
                        .build(),
                KnowledgeChunk.builder()
                        .seq(1)
                        .content("第二段")
                        .contentHash("hash-1")
                        .tokenEstimate(3)
                        .build());
        var lease = lifecycle.claimIngestion(doc.getId()).block();
        assertThat(lifecycle.replaceChunks(lease, chunks).block()).isEqualTo(2);
        assertThat(chunkRepository.findByDocument(doc.getId()).block()).hasSize(2);
        assertThat(chunkRepository.countByDocument(doc.getId()).block()).isEqualTo(2L);

        KnowledgeChunk loaded =
                chunkRepository.findByDocument(doc.getId()).block().getFirst();
        assertThat(chunkRepository.findByIds(List.of(loaded.getId())).block()).hasSize(1);

        // 全量替换（重摄取幂等）
        List<KnowledgeChunk> replaced = List.of(KnowledgeChunk.builder()
                .seq(0)
                .content("替换后内容")
                .contentHash("hash-2")
                .tokenEstimate(5)
                .build());
        assertThat(lifecycle.replaceChunks(lease, replaced).block()).isEqualTo(1);
        assertThat(chunkRepository.findByDocument(doc.getId()).block()).hasSize(1);

        lifecycle.release(lease).block();
        assertThat(lifecycle
                        .acceptDelete(doc.getId(), "http://meili", "kb_chunks")
                        .block())
                .isTrue();
        var cleanupLease = lifecycle.claimCleanup("tenant-1", doc.getId()).block();
        lifecycle.deleteChunks(cleanupLease).block();
        assertThat(chunkRepository.findByDocument(doc.getId()).block()).isEmpty();
        assertThat(documentRepository.findById(doc.getId()).block()).isNull();
    }

    @Test
    void conversationAndMessagesRoundTrip() {
        KbConversation conversation = conversationRepository
                .insert(KbConversation.builder().title("测试会话").userId(42L).build())
                .block();

        conversationRepository
                .insertMessage(KbMessage.builder()
                        .conversationId(conversation.getId())
                        .role(KbMessage.Role.USER)
                        .content("第一问")
                        .build())
                .block();
        conversationRepository
                .insertMessage(KbMessage.builder()
                        .conversationId(conversation.getId())
                        .role(KbMessage.Role.ASSISTANT)
                        .content("第一答")
                        .citations(List.of(new KbMessage.Citation(1, 1L, 1L, "文档", 1L, "片段")))
                        .build())
                .block();

        assertThat(conversationRepository.countMessages(conversation.getId()).block())
                .isEqualTo(2L);
        List<KbMessage> messages =
                conversationRepository.findMessages(conversation.getId(), 10).block();
        assertThat(messages).hasSize(2);
        assertThat(messages.getFirst().getRole()).isEqualTo(KbMessage.Role.USER);
        assertThat(messages.getLast().getCitations()).hasSize(1);
        assertThat(messages.getLast().getCitations().getFirst().documentTitle()).isEqualTo("文档");

        assertThat(conversationRepository
                        .findByIdAndUser(conversation.getId(), 42L)
                        .block())
                .isNotNull();
        assertThat(conversationRepository
                        .findByIdAndUser(conversation.getId(), 43L)
                        .block())
                .isNull();
        assertThat(conversationRepository.findPageByUser(42L, 1, 10).block().getTotal())
                .isEqualTo(1L);
        assertThat(conversationRepository
                        .deleteByIdAndUser(conversation.getId(), 42L)
                        .block())
                .isTrue();
    }

    @Test
    void staleDocumentScanReturnsMatchingIds() {
        KnowledgeSpace space = spaceRepository
                .insert(KnowledgeSpace.builder().name("扫描测试空间").status(1).build())
                .block();
        documentRepository
                .insert(KnowledgeDocument.builder()
                        .spaceId(space.getId())
                        .title("滞留文档")
                        .sourceType(KnowledgeDocument.SourceType.MANUAL)
                        .contentText("正文")
                        .parseStatus(KnowledgeDocument.ParseStatus.FAILED)
                        .indexStatus(KnowledgeDocument.IndexStatus.NONE)
                        .chunkCount(0)
                        .build())
                .block();

        List<Long> ids = documentRepository
                .findIdsByStatusAndUpdatedBefore(
                        List.of("PENDING", "PARSING", "FAILED"),
                        java.time.LocalDateTime.now().plusMinutes(1),
                        10)
                .block();
        assertThat(ids).isNotEmpty();

        List<Long> empty = documentRepository
                .findIdsByIndexStatusAndUpdatedBefore(
                        List.of("FAILED"), java.time.LocalDateTime.now().minusDays(1), 10)
                .block();
        assertThat(empty).isEmpty();
    }
}
