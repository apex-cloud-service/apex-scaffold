package com.apex.scaffold.knowledge.domain.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 待写入向量索引的分块文档。
 *
 * <p>{@code embedding} 为空表示仅词法索引；租户 ID 与空间 ID 是索引侧强制过滤字段。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexedChunk {

    /** 分块 ID（同时作为索引文档主键，形如字符串化雪花 ID）。 */
    private Long chunkId;

    /** 所属文档 ID。 */
    private Long documentId;

    /** 所属空间 ID。 */
    private Long spaceId;

    /** 租户 ID（索引过滤字段）。 */
    private String tenantId;

    /** 文档内序号。 */
    private Integer seq;

    /** 分块正文。 */
    private String content;

    /** 分块内容摘要（十六进制 SHA-256）。 */
    private String contentHash;

    /** 所属文档标题。 */
    private String documentTitle;

    /** 所属空间名称。 */
    private String spaceName;

    /** 内容类型（文档 contentType）。 */
    private String contentType;

    /** 向量（可为空）。 */
    private float[] embedding;

    private LocalDateTime updateTime;
}
