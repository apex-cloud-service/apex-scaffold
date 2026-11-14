package com.apex.scaffold.knowledge.application.assembler;

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
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 知识库领域模型 ↔ DTO 手工装配器。
 *
 * <p>新模块暂无 MapStruct 基建，字段映射规模有限，采用显式手工映射保证可读性；
 * 字段增多后可平滑迁移至 MapStruct Converter。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Component
public class KnowledgeAssembler {

    /**
     * 空间模型转 DTO。
     *
     * @param space 空间模型
     * @return 空间 DTO
     */
    public KnowledgeSpaceDTO toSpaceDTO(KnowledgeSpace space) {
        return new KnowledgeSpaceDTO(
                space.getId(),
                space.getName(),
                space.getDescription(),
                space.getStatus(),
                space.getCreateTime(),
                space.getUpdateTime());
    }

    /**
     * 文档模型转 DTO。
     *
     * @param document 文档模型
     * @return 文档 DTO
     */
    public KnowledgeDocumentDTO toDocumentDTO(KnowledgeDocument document) {
        return new KnowledgeDocumentDTO(
                document.getId(),
                document.getSpaceId(),
                document.getTitle(),
                document.getSourceType().name(),
                document.getContentType(),
                document.getFileSize(),
                document.getParseStatus().name(),
                document.getParseError(),
                document.getChunkCount(),
                document.getIndexStatus().name(),
                document.getCreateTime(),
                document.getUpdateTime());
    }

    /**
     * 分块模型转预览 DTO。
     *
     * @param chunk 分块模型
     * @return 分块 DTO
     */
    public KnowledgeChunkDTO toChunkDTO(KnowledgeChunk chunk) {
        return new KnowledgeChunkDTO(chunk.getId(), chunk.getSeq(), chunk.getContent(), chunk.getTokenEstimate());
    }

    /**
     * 检索结果转 DTO。
     *
     * @param result 领域检索结果
     * @return 检索响应 DTO
     */
    public KbSearchResultDTO toSearchResultDTO(VectorSearchResult result) {
        List<KbSearchResultDTO.KbSearchItemDTO> items =
                result.getHits().stream().map(this::toSearchItemDTO).toList();
        return new KbSearchResultDTO(result.getTotal(), result.isHybridUsed(), items);
    }

    /**
     * 会话模型转 DTO。
     *
     * @param conversation 会话模型
     * @return 会话 DTO
     */
    public KbConversationDTO toConversationDTO(KbConversation conversation) {
        return new KbConversationDTO(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreateTime(),
                conversation.getUpdateTime());
    }

    /**
     * 消息模型转 DTO。
     *
     * @param message 消息模型
     * @return 消息 DTO
     */
    public KbMessageDTO toMessageDTO(KbMessage message) {
        List<KbMessageDTO.CitationDTO> citations = message.getCitations() == null
                ? List.of()
                : message.getCitations().stream().map(this::toCitationDTO).toList();
        return new KbMessageDTO(
                message.getId(),
                message.getConversationId(),
                message.getRole().name(),
                message.getContent(),
                citations,
                message.getCreateTime());
    }

    /**
     * 检索命中转引用溯源列表（回答消息持久化结构）。
     *
     * @param hits 检索命中
     * @return 引用溯源列表
     */
    public List<KbMessage.Citation> toCitations(List<VectorSearchHit> hits) {
        List<KbMessage.Citation> citations = new ArrayList<>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            VectorSearchHit hit = hits.get(i);
            citations.add(new KbMessage.Citation(
                    i + 1,
                    hit.getChunkId(),
                    hit.getDocumentId(),
                    hit.getDocumentTitle(),
                    hit.getSpaceId(),
                    truncate(hit.getContent(), 300)));
        }
        return citations;
    }

    private KbSearchResultDTO.KbSearchItemDTO toSearchItemDTO(VectorSearchHit hit) {
        return new KbSearchResultDTO.KbSearchItemDTO(
                hit.getChunkId(),
                hit.getDocumentId(),
                hit.getDocumentTitle(),
                hit.getSpaceId(),
                hit.getSpaceName(),
                hit.getContent(),
                hit.getHighlight(),
                hit.getScore());
    }

    private KbMessageDTO.CitationDTO toCitationDTO(KbMessage.Citation citation) {
        return new KbMessageDTO.CitationDTO(
                citation.index(),
                citation.chunkId(),
                citation.documentId(),
                citation.documentTitle(),
                citation.spaceId(),
                citation.snippet());
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }
}
