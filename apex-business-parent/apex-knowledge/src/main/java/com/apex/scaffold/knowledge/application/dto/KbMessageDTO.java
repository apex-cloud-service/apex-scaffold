package com.apex.scaffold.knowledge.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识问答消息 DTO。
 *
 * @param id 消息 ID
 * @param conversationId 会话 ID
 * @param role 角色（USER/ASSISTANT）
 * @param content 消息正文
 * @param citations 引用溯源（回答消息专用，可为空列表）
 * @param createTime 创建时间
 * @author Apex Team
 * @since 1.3.2
 */
public record KbMessageDTO(
        Long id,
        Long conversationId,
        String role,
        String content,
        List<KbMessageDTO.CitationDTO> citations,
        LocalDateTime createTime) {

    /**
     * 引用溯源条目。
     *
     * @param index 引用序号（从 1 开始）
     * @param chunkId 分块 ID
     * @param documentId 文档 ID
     * @param documentTitle 文档标题
     * @param spaceId 空间 ID
     * @param snippet 命中片段
     */
    public record CitationDTO(
            int index, Long chunkId, Long documentId, String documentTitle, Long spaceId, String snippet) {}
}
