package com.apex.scaffold.knowledge.application.assembler;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.knowledge.application.dto.KbConversationDTO;
import com.apex.scaffold.knowledge.application.dto.KbMessageDTO;
import com.apex.scaffold.knowledge.application.dto.KbSearchResultDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeChunkDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeDocumentDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeSpaceDTO;
import com.apex.scaffold.knowledge.domain.model.KbConversation;
import com.apex.scaffold.knowledge.domain.model.KbMessage;
import com.apex.scaffold.knowledge.domain.model.KnowledgeChunk;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocument;
import com.apex.scaffold.knowledge.domain.model.KnowledgeSpace;
import com.apex.scaffold.knowledge.domain.model.VectorSearchHit;
import com.apex.scaffold.knowledge.domain.model.VectorSearchResult;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 知识库装配器测试：领域模型 ↔ DTO 全量映射。
 *
 * @author Apex Team
 * @since 1.3.2
 */
class KnowledgeAssemblerTest {

    private final KnowledgeAssembler assembler = new KnowledgeAssembler();

    @Test
    void spaceToDtoCarriesAllFields() {
        KnowledgeSpaceDTO dto = assembler.toSpaceDTO(KnowledgeSpace.builder()
                .id(1L)
                .name("空间")
                .description("描述")
                .status(1)
                .createTime(LocalDateTime.of(2026, 11, 14, 10, 0))
                .build());
        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.name()).isEqualTo("空间");
        assertThat(dto.status()).isEqualTo(1);
    }

    @Test
    void documentAndChunkToDtoCarryEnumNames() {
        KnowledgeDocumentDTO document = assembler.toDocumentDTO(KnowledgeDocument.builder()
                .id(2L)
                .spaceId(3L)
                .title("文档")
                .sourceType(KnowledgeDocument.SourceType.MANUAL)
                .parseStatus(KnowledgeDocument.ParseStatus.PARSED)
                .indexStatus(KnowledgeDocument.IndexStatus.INDEXED_LEXICAL)
                .chunkCount(2)
                .build());
        assertThat(document.sourceType()).isEqualTo("MANUAL");
        assertThat(document.parseStatus()).isEqualTo("PARSED");
        assertThat(document.indexStatus()).isEqualTo("INDEXED_LEXICAL");

        KnowledgeChunkDTO chunk = assembler.toChunkDTO(KnowledgeChunk.builder()
                .id(4L)
                .seq(1)
                .content("内容")
                .tokenEstimate(3)
                .build());
        assertThat(chunk.seq()).isEqualTo(1);
    }

    @Test
    void searchResultMappingKeepsHybridFlag() {
        KbSearchResultDTO dto = assembler.toSearchResultDTO(new VectorSearchResult(
                List.of(VectorSearchHit.builder()
                        .chunkId(5L)
                        .documentId(6L)
                        .spaceId(7L)
                        .documentTitle("文档")
                        .spaceName("空间")
                        .content("正文")
                        .highlight("<em>正文</em>")
                        .score(0.9d)
                        .build()),
                1L,
                true));
        assertThat(dto.hybridUsed()).isTrue();
        assertThat(dto.items().getFirst().chunkId()).isEqualTo(5L);
        assertThat(dto.items().getFirst().highlight()).isEqualTo("<em>正文</em>");
    }

    @Test
    void citationsAreNumberedAndTruncated() {
        VectorSearchHit hit = VectorSearchHit.builder()
                .chunkId(8L)
                .documentId(9L)
                .spaceId(10L)
                .documentTitle("文档")
                .spaceName("空间")
                .content("片段".repeat(200))
                .score(0.9d)
                .build();
        List<KbMessage.Citation> citations = assembler.toCitations(List.of(hit, hit));

        assertThat(citations).hasSize(2);
        assertThat(citations.get(0).index()).isEqualTo(1);
        assertThat(citations.get(1).index()).isEqualTo(2);
        assertThat(citations.get(0).snippet().length()).isLessThanOrEqualTo(302);
        assertThat(citations.get(0).snippet()).endsWith("…");
    }

    @Test
    void conversationAndMessageDtoMapping() {
        KbConversationDTO conversation = assembler.toConversationDTO(
                KbConversation.builder().id(11L).title("会话").build());
        assertThat(conversation.id()).isEqualTo(11L);

        KbMessageDTO message = assembler.toMessageDTO(KbMessage.builder()
                .id(12L)
                .conversationId(11L)
                .role(KbMessage.Role.ASSISTANT)
                .content("回答")
                .citations(List.of(new KbMessage.Citation(1, 8L, 9L, "文档", 10L, "片段")))
                .build());
        assertThat(message.role()).isEqualTo("ASSISTANT");
        assertThat(message.citations().getFirst().chunkId()).isEqualTo(8L);

        KbMessageDTO noCitation = assembler.toMessageDTO(KbMessage.builder()
                .id(13L)
                .conversationId(11L)
                .role(KbMessage.Role.USER)
                .content("提问")
                .build());
        assertThat(noCitation.citations()).isEmpty();
    }
}
