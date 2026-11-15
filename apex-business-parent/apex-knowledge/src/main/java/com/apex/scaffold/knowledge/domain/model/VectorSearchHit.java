package com.apex.scaffold.knowledge.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识检索命中分块。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchHit {

    /** 分块 ID。 */
    private Long chunkId;

    /** 文档 ID。 */
    private Long documentId;

    /** 空间 ID。 */
    private Long spaceId;

    /** 文档标题。 */
    private String documentTitle;

    /** 空间名称。 */
    private String spaceName;

    /** 分块正文。 */
    private String content;

    /** 高亮片段（索引引擎返回，可能为空）。 */
    private String highlight;

    /** 相关度得分。 */
    private double score;
}
