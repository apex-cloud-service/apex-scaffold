package com.apex.scaffold.knowledge.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识问答消息领域模型。
 *
 * <p>回答消息必须携带 {@code citations} 引用溯源（命中的分块及其文档），
 * 便于用户核对答案来源；引用数据以 JSON 持久化于消息行内。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbMessage {

    /** 消息 ID（雪花 ID）。 */
    private Long id;

    /** 所属会话 ID。 */
    private Long conversationId;

    /** 消息角色。 */
    private Role role;

    /** 消息正文。 */
    private String content;

    /** 引用溯源列表（回答消息专用）。 */
    private List<Citation> citations;

    private LocalDateTime createTime;

    /** 消息角色。 */
    public enum Role {
        /** 用户提问。 */
        USER,
        /** 助手回答。 */
        ASSISTANT
    }

    /**
     * 引用溯源：回答中 [n] 标记对应的分块与文档信息。
     *
     * @param index 引用序号（从 1 开始，与提示词内标记一致）
     * @param chunkId 命中分块 ID
     * @param documentId 分块所属文档 ID
     * @param documentTitle 分块所属文档标题
     * @param spaceId 分块所属空间 ID
     * @param snippet 命中片段（截断后的分块正文）
     */
    public record Citation(
            int index, Long chunkId, Long documentId, String documentTitle, Long spaceId, String snippet) {}
}
