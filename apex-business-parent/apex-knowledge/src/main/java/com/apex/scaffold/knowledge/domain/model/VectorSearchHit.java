package com.apex.scaffold.knowledge.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识检索命中分块。
 *
 * @param chunkId 分块 ID
 * @param documentId 文档 ID
 * @param spaceId 空间 ID
 * @param documentTitle 文档标题
 * @param spaceName 空间名称
 * @param content 分块正文
 * @param highlight 高亮片段（索引引擎返回，可能为空）
 * @param score 相关度得分
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchHit {

    private Long chunkId;

    private Long documentId;

    private Long spaceId;

    private String documentTitle;

    private String spaceName;

    private String content;

    private String highlight;

    private double score;
}
